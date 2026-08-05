# CONNECTION.md

How the app talks to a receiver: the two transports, the reconnect loop that keeps the telnet
socket alive, who decides when to hang up, and what Android's background restrictions do to all of
it.

Read this before touching `core/ResilentConnector`, `core/Connector` or `ActiveHandler`. Most of
what looks removable in there is load-bearing, and the reasons are field observations rather than
theory. Open work is in [TODO.md](TODO.md); this file describes what the code does today.

## Two independent transports

1. **Telnet, port 23** — the real control channel. `core/Connector` holds a raw socket, writes
   commands terminated with `\r`, and parses incoming lines into `InData`. Two daemon threads per
   connection, `receiver` and `sender`; the sender paces commands by `SEND_DELAY` (100 ms) because
   the receivers drop them otherwise.
2. **HTTP, port 80** — `http/AVRHTTPClient` scrapes the receiver's own web UI (`*.asp`, XML
   endpoints) for things the telnet protocol does not expose: input/zone names, quick-select
   presets, NET audio search. `http/Series08*` parse the 2008-series variant. Every request goes
   through `http/HTTPSupport` (`HttpURLConnection`, GET and form POST, nothing else); its three
   callers are `http/AVRHTTPClient`, `http/Series08Reader` and — easy to miss —
   `core/display/NetDisplay`.

Three things in `HTTPSupport` are deliberate and load-bearing against the receivers' 2008-era
GoAhead webservers, and all three look removable to someone who does not know why they exist:

- `Accept-Encoding: identity` — the old Apache client never asked for gzip, Android does.
- `setFixedLengthStreamingMode` — so the request body is never sent chunked.
- `CookieHandler.setDefault(new CookieManager())` in `AVRApplication.onCreate`. The receivers
  tested so far send no `Set-Cookie` at all, so this looks pointless — but `Series08Reader` fetches
  `r_option1.asp` purely to establish state that the following `d_option1.asp` reads back, and the
  Apache client it replaced carried a cookie store. Without a handler `HttpURLConnection` shares
  nothing between requests, and the failure would be silent (empty quick-select names).
  `readSeries08Info()` clears the store per run, because the old store was per-client and did not
  outlive one read.

Receivers speak plain HTTP, so `android:usesCleartextTraffic="true"` in the manifest is
load-bearing too — removing it kills the whole scraping path.

## The reconnect loop

`ResilentConnector` runs a long-lived loop on a plain daemon thread owned by `AVRApplication` —
**not** a Service. Killing or backgrounding the app kills the connection. That is a 2010 design
decision and still open in [TODO.md](TODO.md).

One pass of `Reconnector.run()`: probe reachability (`checkAddress()`), open a `Connector`, publish
it, then block in `waitUntilClosed()` until the socket drops; probe again, clear the state, wait,
repeat. The wait is `RECONNECT_DELAY` = 1, 2, 4, 8, 16 seconds, and **the index only resets after a
successful connection** — sitting in the foreground through a bad patch can leave you waiting 16 s
after the network is fine again. A resume escapes it, because `forceReconnect()` builds a fresh
`Reconnector` whose index starts at 0.

### Why nothing waits for the old thread

`stopConnector()` interrupts the reconnect thread and returns immediately. It does **not** join it,
because the thread is typically inside `ConnectionConfiguration.checkAddress()` →
`InetAddress.isReachable()` plus a few `testPort()` connects, and none of those react to
`interrupt()`. Waiting ran into its full timeout and then continued with the thread still alive —
a second of blocked UI thread, since `stopConnector()` is reached from `ActiveHandler` on the main
thread. `core/ThreadHandlerTest` pins that it returns promptly.

So a superseded thread keeps running for up to a few seconds. Everything that keeps it harmless
rests on one mechanism:

### The generation counter

`ResilentConnector.generation` is an `AtomicInteger`; every `Reconnector` captures its value as
`epoch` at construction. `stopConnector()` bumps the counter **before** tearing anything down, so a
superseded thread sees `isCurrent() == false` and must not touch shared state any more. Every write
in `run()` is guarded by it.

Two of those guards need more than a check:

- **`publishConnector(epoch, c)`** is `synchronized` and re-checks the epoch under the monitor,
  against `closeAndClearConnector()`. A plain `if (isCurrent()) connector = c;` would only narrow
  the window: if the bump lands between check and assignment, a superseded thread drops a **live**
  `Connector` into the field after the cleanup already ran. Nobody closes that socket, the receiver
  keeps a second telnet session open, and `isRunning()` reports the dead connection as current —
  which is exactly how the reconnect loop once went missing entirely.
- The block after the second `checkAddress()` is guarded because that call blocks about a second in
  ping and port timeouts, which is the widest window in the loop. The guard narrows it; what
  actually closes it is `publishConnector()` underneath.

Because nothing waits, **several reconnect threads can be alive at once**. Their log lines are
distinguishable: the thread is named `ResilentThreadHandler-<n>`, where `<n>` is the generation.

## Who decides when to hang up

`ActiveHandler` is the only owner of the disconnect policy. `AVRApplication.activityResumed()` and
`activityPaused()` feed it; every activity reports through them.

- **`contextPaused()`** schedules a `StopConnectorTask` on the `StopConnector-Timer` after the
  user's *disconnect time* (`AVRSettings.getDisconnectTimeout`, default 10 s, selectable up to
  7200 s). Nothing is torn down immediately — the screen going off just pauses the activity.
- **`contextResumed()`** picks one of two paths. Back within a minute *and* `isRunning()` → leave
  the connection alone. Otherwise → `forceReconnect()`, unconditionally.

The minute is `MAX_QUICK_RETURN_SEC` and is deliberately not the user's disconnect time. The
shortcut trusts `isRunning()`, which rests on `Socket.isConnected()` — and that stays `true`
forever once a connect succeeded, including for a socket Doze severed hours ago. The shortcut is
for rotation, dialogs and tab switches; anything longer gets a fresh connection.

`StopConnectorTask` also checks whether an activity became active again before it fires, and
reconnects itself if a resume slipped in between its check and the stop. Both belong to the Doze
story below.

## What Doze does

This is where the interesting failures come from. All of the following is observed, not theory.

**The process is frozen and broadcasts are delivered late.** This caused the one field report we
have a log for (2026-08-03). `SCREEN_OFF` was queued at 18:27 while the app was in the background
and arrived at 18:48:07.513 — 49 ms *after* the user brought the app back and `forceReconnect()` had
started a new reconnect thread. The old `ACTION_SCREEN_OFF` receiver called `connector.stop()`
unconditionally and invalidated exactly that thread. There is no way back from there: no further
`contextResumed()` comes, because the activity is already resumed. The app looked dead until it was
killed and restarted. The receiver is gone; `ActiveHandler` alone decides now.

**Timers catch up.** `java.util.Timer` fires tasks it missed while the process was frozen, so a
`StopConnectorTask` deferred by Doze can go off right after a resume and stop a connection that was
just built. `cancelCurrentTask()` in `contextResumed()` only wins that race sometimes, hence the
guard in the task, and the self-heal after it because check-then-act is not atomic.

**Wi-Fi power save makes a reachable receiver look unreachable.** Measured 2026-08-04 on a Pixel 8
against an AVR-3310 on 2.4 GHz, all three within a minute of each other:

| | ping to the receiver | port 23 |
| --- | --- | --- |
| Mac on the same LAN | 8 ms | connects |
| phone, awake for a while | 46–62 ms | connects |
| phone, just woken | fails entirely | 2500 ms connect timeout |

The radio sleeps between beacons and only wakes on DTIM. `AVRTargetTester.PING_TIMEOUT` is 250 ms,
so right after the user picks the phone up, `checkAddress()` reports "not reachable" for a receiver
that is plainly there. The connect is attempted regardless — the *"Auf jeden Fall versuchen"*
comment in `Reconnector.run()` covers that — so this is about displayed state and the backoff, not
about refusing to connect. Still open, see [TODO.md](TODO.md).

**The process can also simply be reclaimed**, and then the daemon thread dies with it. That is the
Service item in [TODO.md](TODO.md). Worth stressing: the field report above was *not* this. The
process had survived — `openend at` appears only at the app's own restart.

## How connection state reaches the UI

`EnableManager` drives view enablement from a small set of `StatusFlag`s, in this order:
`Logging`, `WLAN`, `Reachable`, `Connected`, `Power`, `Zone1`–`Zone4`. This is why most buttons are
greyed out until a receiver is actually connected — worth knowing when testing without hardware.

The flags are not independent. `setStatus()` cascades through a deliberate `switch` fallthrough:
clearing `Reachable` also clears `Connected`, `Power` and every zone. **One missed ping response
greys out the entire UI** — which is what makes the `PING_TIMEOUT` observation above more than
cosmetic. Setting cascades the other way: `Power` implies `Connected` implies `Reachable`.

## Reading a connection problem out of a log

See [CLAUDE.md](CLAUDE.md) for the log format itself (sort by `#seq`, split runs on `openend at`).
For connection questions the thread name carries most of the signal:

| thread | what it is |
| --- | --- |
| `main` | lifecycle, `forceReconnect`, everything from an activity |
| `ResilentThreadHandler-<n>` | a reconnect loop, `<n>` is its generation |
| `receiver` / `sender` | the two socket threads of one `Connector` |
| `StopConnector-Timer` | the auto-disconnect timer |

A healthy start looks like this — one generation, and data arriving:

```
#79  [main]                      Reconnector:start new connector 192.168.10.30
#81  [ResilentThreadHandler-2]   Reconnector:build new connection to [192.168.10.30]
#83  [ResilentThreadHandler-2]   Reconnector:reachable 192.168.10.30 : true
#145 [ResilentThreadHandler-2]   Reconnector:connection to [192.168.10.30] established
#156 [receiver]                  RECEIVED [PWSTANDBY(...)]
```

A handover, which is normal on resume — the old generation is detached, a new one takes over:

```
#439 [StopConnector-Timer]       Reconnector:connector detached (ResilentThreadHandler-2)
#440 [ResilentThreadHandler-2]   Reconnector:connector interrupted -> return
#467 [ResilentThreadHandler-5]   Reconnector:build new connection to [192.168.10.30]
```

What a dead loop looked like before the fix — a teardown with no `start new connector` after it,
and then nothing at all until the app was restarted:

```
activity resumed AVRRemote
Connector forceReconnect ip: [192.168.10.30]
Reconnector:start new connector 192.168.10.30
System StandBy ...                            <- broadcast from 20 minutes earlier
stop connector
Reconnector:connector stopped
```

So: **a teardown line that is not followed by a new generation starting is the smell.**

## Next platform deadline: targetSdk 37

Local Network Protection becomes mandatory for apps targeting **Android 17 (SDK 37)**. That
directly hits `scan/AVRScanner` (subnet sweep) and the raw receiver sockets — i.e. everything on
this page. At `targetSdk 36` it does not apply yet, but plan for a runtime local-network permission
before raising the target further. See [TODO.md](TODO.md) for the deprecated `WifiManager` calls to
replace in the same pass.
