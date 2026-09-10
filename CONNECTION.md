# CONNECTION.md

How the app talks to a receiver: the two transports, the reconnect loop that keeps the telnet
socket alive, who decides when to hang up, and what Android's background restrictions do to all of
it.

Read this before touching `core/ResilentConnector`, `core/Connector`, `ActiveHandler` or
`http/HTTPSupport`. Most of what looks removable in there is load-bearing, and the reasons are field
observations rather than theory. Open work is in [TODO.md](TODO.md); this file describes what the
code does today.

## Two independent transports

Ports 23 and 80 are the defaults; `ConnectionConfiguration` also parses
`IP[:controlport[:httpport[:httpip]]]`. That matters beyond the port number — with such an extended
config `checkAddress()` returns `true` without probing anything, so the reachability story below
does not apply to those users at all.

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
repeat. The wait comes from `RECONNECT_DELAY` and runs **2, 4, 8, 16 s** — the array's leading `1`
is never used, because the index is incremented before the sleep. **It only resets after a
successful connection**, so sitting in the foreground through a bad patch can leave you waiting 16 s
after the network is fine again. A resume escapes it: `forceReconnect()` builds a fresh
`Reconnector` whose index starts at 0.

### Why nothing waits for the old thread

`stopConnector()` interrupts the reconnect thread and returns immediately. It does **not** join it,
because the thread is typically inside `ConnectionConfiguration.checkAddress()` →
`InetAddress.isReachable()` plus a few `testPort()` connects, and none of those react to
`interrupt()`. Waiting ran into its full timeout and then continued with the thread still alive —
a second of blocked UI thread, since `stopConnector()` is reached from `ActiveHandler` on the main
thread. `core/ThreadHandlerTest` pins that it returns promptly.

So a superseded thread keeps running for a few seconds, and **several of them can be alive at
once**. Their log lines are distinguishable: the thread is named `ResilentThreadHandler-<n>`, where
`<n>` is the generation. Everything that keeps them harmless rests on one mechanism:

### The generation counter

`ResilentConnector.generation` is an `AtomicInteger`. `startConnector()` bumps it and hands the new
value to the `Reconnector` as its `epoch`; `stopConnector()` bumps it **before** tearing anything
down, so a superseded thread sees `isCurrent() == false` and must not touch shared state any more.
Every write in `run()` is guarded by it.

Two of those guards need more than a check:

- **`publishConnector(epoch, c)`** is `synchronized` and re-checks the epoch under the monitor,
  against `closeAndClearConnector()`. A plain `if (isCurrent()) connector = c;` would only narrow
  the window: if the bump lands between check and assignment, a superseded thread drops a **live**
  `Connector` into the field after the cleanup already ran. Nobody closes that socket, the receiver
  keeps a second telnet session open, and `isRunning()` reports the dead connection as current —
  the same shape as the failure in *What Doze does* below, reached by a different route. That one
  was observed; this one was reasoned about in PR #26 and guarded before it could happen.
- The block after the second `checkAddress()` is guarded because that call blocks about a second in
  ping and port timeouts. The guard narrows the window; what actually closes it is
  `publishConnector()` underneath.

## A connect is not a connection

The receiver allows exactly one telnet session. It still accepts the *socket* for a second one and
then says nothing on it — and it holds a session that no longer exists for as long as it pleases,
because nothing on the phone closed it. Nothing could: Android kills the process on a package
update the same way it does on a force-stop, so no `onDestroy`, no `onTerminate` and no `finally`
runs. The kernel sends the FIN in the app's place — unless the Wi-Fi is asleep, off or out of
range at that moment, and then the receiver never learns that the session ended. The user updates
the app, opens it, and the one session is taken by a ghost.

`Socket.connect()` succeeding says nothing about any of that, so two things check what it does not.

**The handshake.** `Connector.awaitResponse()` sends `PW?` and waits `HANDSHAKE_TIMEOUT` (3 s) for
the first data of any kind. `PW?` because every model answers it in every state — a receiver in
standby replies `PWSTANDBY`, which is where `StatusFlag.Power` comes from in the first place. *Any*
data counts, not only the reply, so a model that pushes state by itself passes too.
`Reconnector.run()` calls it before `publishConnector()`, and a failure throws `IOException`
straight into the backoff that was already there.

**An interrupt inside that wait has to close the connector by hand.** `forceReconnect()` fires at
any moment and `awaitResponse()` blocks for seconds, so this is not a corner case. The connector is
not published at that point, the shared field still holds `NULL_CONNECTOR`, and `stopConnector()`
therefore closes nothing — the socket and its two daemon threads would stay alive and keep holding
the one session the next attempt is about to ask for. Before the handshake existed there was
nothing interruptible between `new Connector(...)` and `publishConnector(...)`; the sleep inside
the constructor is covered by its own `finally`.

It has a safety valve: after `MAX_SILENT_CONNECTS` (3) consecutive silent connects the connection
is used anyway, and a `Logger.error` line records that it was. 60 model classes and one receiver to
test against — a check this deep in the connect path must not be able to make a device permanently
unusable that worked before. The counter sits in the `Reconnector` next to `reconnectDelayIndex`,
so a `forceReconnect()` deliberately starts over.

**The read watchdog.** The socket carries `setSoTimeout(READ_TIMEOUT)` (5 s), which is a tick rate
and not a deadline — receivers send nothing on their own, so a quiet connection is normal. After
`IDLE_PROBE` (60 s) without a single byte the receiver thread sends one `PW?` itself; if
`PROBE_GRACE` (10 s) passes with no answer it closes the connection and the reconnect loop builds a
new one. This is the only thing that notices a socket Doze or a network change severed silently:
`Socket.isConnected()` never will, see *Who decides when to hang up* below.

Silence is counted in timeout ticks rather than by the clock, because the question is "were we
awake and heard nothing": a frozen thread stops counting, where the clock would run on. That helps
only where the process really is frozen, though. In a plain Doze window the thread keeps waking
every tick while the network is suspended, so the ticks accumulate normally and the watchdog takes
down a connection that might have survived to the next maintenance window — after which the
reconnect loop retries through its backoff for as long as Doze lasts. Not measured on a device yet;
with a long disconnect time the screen-off case is the one to watch.

Three rules in the reading loop are load-bearing:

- **A connection that has never said anything is left alone.** `stillAlive()` returns early while
  `firstDataSignal` has not fired. That case belongs to the valve above, not to the watchdog:
  without the rule the watchdog would tear down every 70 s exactly what the valve just let through,
  the valve would let the next one through again — `silentConnects` never resets once it has
  latched — and a receiver that answers nothing would flap forever, where before it at least held
  one stable connection that still carried commands.
- **A timeout in the middle of a line gives up like any other.** The half-read message is lost, and
  that is right: the connection is being abandoned. Keeping it would mean switching the watchdog
  off for the rest of the line, and a connection that dies one byte into a message would hang for
  good. Every byte read resets the idle count, so a slow-but-alive sender is never mistaken for a
  dead one.
- **The teardown calls `Connector.close()`, not `socket.close()`**, because only that also
  interrupts the sender — otherwise it stays parked in `sendQueue.take()` and outlives the
  connection it served. That leak predates the watchdog; the watchdog just reaches the path often
  enough for it to matter.

In a log a receiver holding a stale session now looks like this, instead of like a healthy start:

```
#81  [ResilentThreadHandler-2]   Reconnector:build new connection to [192.168.10.30]
#145 [sender]                    SEND [PW?]
#146 [ResilentThreadHandler-2]   Reconnector:IOException [192.168.10.30]
        java.io.IOException: no answer from [192.168.10.30] after connect (1)
```

The `SEND` line is the sender thread's, not the reconnect thread's — `awaitResponse()` only queues
the probe. What makes this readable at all is that there is no `RECEIVED` line between the two.

`core/ConnectorTest` covers all of it on the JVM against a fake receiver on a local `ServerSocket`.

## Who decides when to hang up

`ActiveHandler` is the only owner of the disconnect policy. `AVRApplication.activityResumed()` and
`activityPaused()` feed it; every activity reports through them.

- **`contextPaused()`** schedules a `StopConnectorTask` on the `StopConnector-Timer` after the
  user's *disconnect time* (`AVRSettings.getDisconnectTimeout`, default 10 s, selectable up to
  7200 s). Nothing is torn down immediately — the screen going off just pauses the activity.
- **`contextResumed()`** picks one of three paths, on a *quick return* — back within the **shorter**
  of the disconnect time and `MAX_QUICK_RETURN_SEC` (60 s), so 10 s at the default:
  - quick return and `isRunning()` → leave the connection alone,
  - quick return but not running → `reconfigure()`, which itself short-circuits if the config is
    unchanged and a loop is already running,
  - otherwise → `forceReconnect()`, unconditionally.

The 60 s are a cap, not the window, and the reason for the cap is that the disconnect time may be
set as high as two hours while `isRunning()` is only worth seconds: it rests on
`Socket.isConnected()`, which stays `true` forever once a connect succeeded, including for a socket
Doze severed long ago. The shortcut is for rotation, dialogs and tab switches. The read watchdog
does take such a socket down now, but only some 70 s after it went quiet and only while the
receiver thread is really running — which in the background is exactly what is not guaranteed. So
the cap stays.

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

The Mac is the control here: it kept answering throughout, so the receiver was demonstrably up the
whole time.

The radio sleeps between beacons and only wakes on DTIM. `AVRTargetTester.PING_TIMEOUT` is 250 ms
for the pre-connect probe and twice that for the one after a close (`cfgTest` doubles it), so right
after the user picks the phone up, `checkAddress()` reports "not reachable" for a receiver that is
plainly there. The connect is attempted regardless — the *"Auf jeden Fall versuchen"* comment in
`Reconnector.run()` covers that — so this is about displayed state and the backoff, not about
refusing to connect. Still open, see [TODO.md](TODO.md).

**The process can also simply be reclaimed**, and then the daemon thread dies with it. That is the
Service item in [TODO.md](TODO.md). Worth stressing: the field report above was *not* this. The
process had survived — `openend at` appears only at the app's own restart.

## How connection state reaches the UI

`EnableManager` drives view enablement from a small set of `StatusFlag`s, in this order:
`Logging`, `WLAN`, `Reachable`, `Connected`, `Power`, `Zone1`–`Zone4`. This is why most buttons are
greyed out until a receiver is actually connected — worth knowing when testing without hardware.

The flags are not independent. `setStatus()` cascades through a deliberate `switch` fallthrough:
clearing `Reachable` also clears `Connected`, `Power` and all four zones. **One missed ping response
greys out nearly the whole UI** — which is what makes the `PING_TIMEOUT` observation above more than
cosmetic. Setting cascades the other way: `Power` implies `Connected` implies `Reachable`.

`Zone4` was missing from that reset until August 2026, so on a four-zone receiver — `AVR5308`,
`AVR4308`, `AVR4810`, `AVR5805` — those controls stayed enabled after the connection dropped while
zones 1–3 greyed out. Worth knowing when reading an older log.

## Reading a log

`log/SDLogger` writes it, `log/FeedbackReporter` mails it. One line looks like this, and a message
can run over several lines — anything not matching the header is a continuation:

```
2026-08-04  20:22:07.667 #0 - INFO : [main] openend at Tue Aug 04 20:22:07 GMT+02:00 2026
```

**Sort by `#seq`, never by timestamp or line order.** Neither of those is the order the events
happened in: `java.util.logging` stamps the time when the `LogRecord` is built but writes later, so
threads overtake each other — the field log from 2026-08-03 has 7 inversions in its 2488 header
lines, one of them right where it mattered. (Count header lines only. A naive line-wise check counts
the 184 continuation lines too and reports 99, which is wrong.) Sorting by the timestamp does not
repair it either, because it only has millisecond resolution and 65 % of those lines share a
millisecond with another. `#seq` comes from `LogRecord`, assigned in the constructor from the same
instant as the time, and it is the one total order **within one process run** — on two runs from a
Pixel 8 it removed every inversion (9 and 4 respectively, 0 after sorting).

`#seq` restarts at `#0` in each process, and one log file usually holds several runs appended, so
**split on the `openend at` lines before sorting**. Sorting a whole file by `#seq` interleaves the
runs and produces garbage: on that same file it turned 13 inversions into 259. The counter can also
have gaps — it is JVM-global and a rotation boundary cuts the file mid-stream — though in practice
nothing else in the process uses `java.util.logging`, and one of those runs was gapless from `#0` to
`#689`.

An exception is followed by its type, message and up to `MAX_TRACE` frames of stack, indented with
tabs, `Caused by:` per cause. Before 1.6.1 the formatter dropped the `Throwable` entirely, so older
logs carry only the message — `reading macros.txt failed` there could be the harmless
`FileNotFoundException` it usually is, or anything else.

`ReceiverStatus.toString()` walks `StatusFlag.values()` rather than its own map, so the flags always
appear in the same order and two status lines can be diffed as text. Over the map they could not:
3 of the 11 flag sets in that field log show up in more than one order, two of them in three.

### What the thread names tell you

The name is captured in `SDLogger.withThread()` at log time rather than in the formatter, so it
stays right no matter which thread does the writing. logcat has no such field — it carries its own
tid column. For connection questions this is where most of the signal is:

| thread | what it is |
| --- | --- |
| `main` | lifecycle, `forceReconnect`, everything from an activity |
| `ResilentThreadHandler-<n>` | a reconnect loop, `<n>` is its generation; several can be alive |
| `receiver` / `sender` | the two socket threads of one `Connector` |
| `StopConnector-Timer` | the auto-disconnect timer |
| `StateCheckThread` | the re-query after a connection came up (`core/AVRState`) |
| `LoadXMLStatus` | the HTTP scraping from `StatusAreaManager` |

A healthy start looks like this — one generation, and data arriving:

```
#79  [main]                      Reconnector:start new connector 192.168.10.30
#81  [ResilentThreadHandler-2]   Reconnector:build new connection to [192.168.10.30]
#83  [ResilentThreadHandler-2]   Reconnector:reachable 192.168.10.30 : true
#145 [ResilentThreadHandler-2]   Reconnector:connection to [192.168.10.30] established
#156 [receiver]                  RECEIVED [PWSTANDBY(...)]
```

A normal teardown and re-establish — the auto-disconnect timer drops the connection while the app is
in the background, the user comes back 13 s later, a new generation takes over. Note that the two
sides run on different threads, which is how you tell them apart:

```
#439 [StopConnector-Timer]       Reconnector:connector detached (ResilentThreadHandler-2)
#440 [ResilentThreadHandler-2]   Reconnector:connector interrupted -> return
#465 [main]                      Reconnector:start new connector 192.168.10.30
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
