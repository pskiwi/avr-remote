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

## When a connection goes quiet

`Socket.isConnected()` stays `true` forever once a connect succeeded, including for a socket that
Doze or a network change severed minutes ago. Nothing else notices either: receivers send nothing
on their own, so no traffic is not evidence of anything. The reading loop therefore watches the
silence itself.

**The read watchdog.** The socket carries `setSoTimeout(READ_TIMEOUT)` (5 s), which is a tick rate
and not a deadline — a quiet connection is normal. After `IDLE_PROBE` (60 s) without a single byte
the receiver thread sends one `PW?` itself; if `PROBE_GRACE` (10 s) passes with no answer it closes
the connection and the reconnect loop builds a new one. `PW?` because every model answers it in
every state — a receiver in standby replies `PWSTANDBY`, which is where `StatusFlag.Power` comes
from in the first place.

Silence is counted in timeout ticks rather than by the clock, because the question is "were we
awake and heard nothing": a frozen thread stops counting, where the clock would run on.

**What it costs is one probe per minute, forever.** Nothing else sends on the control channel while
the user is idle, so the watchdog never finds the line busy: measured on an AVR-3310, four probes in
four minutes, each answered within 22–118 ms. A resting connection used to rest; now it ticks.

**Doze is less of a worry than it looks**, and for a reason outside this file: with the screen off
`ActiveHandler` hangs up 10 s after the activity pauses (*Who decides when to hang up*), so there is
no connection left for the watchdog to take down. Measured 2026-09-25 on a Pixel 8 in forced deep
idle:

```
19:50:04.415  ActiveHandler.activity paused AVRRemote
19:50:04.415  auto disconnect :10sec
19:50:09.438  read failed ... Software caused connection abort
                at Connector$Receiver.readChar(Connector.java:76)
19:50:09.446  Reconnector:connection to [192.168.10.30] closed
19:50:14.418  run stopConnectorRunnable
```

The abort came from the suspended network, not from the watchdog — it arrives through `readChar()`
like any other `IOException` and is passed on. What remains untested is a foreground connection
held across a real Doze window; there the ticks would accumulate while the network is down.

Two rules in the reading loop are load-bearing:

- **A timeout in the middle of a line gives up like any other.** The half-read message is lost, and
  that is right: the connection is being abandoned. Keeping it would mean switching the watchdog
  off for the rest of the line, and a connection that dies one byte into a message would hang for
  good. Every byte read resets the idle count, so a slow-but-alive sender is never mistaken for a
  dead one.
- **The teardown calls `Connector.close()`, not `socket.close()`**, because only that also
  interrupts the sender — otherwise it stays parked in `sendQueue.take()` and outlives the
  connection it served. That leak predates the watchdog; the watchdog just reaches the path often
  enough for it to matter.

`core/ConnectorTest` covers both on the JVM against a fake receiver on a local `ServerSocket`, with
the three timings injected through a package-private constructor so the suite spends milliseconds
where the app spends minutes.

### What the receiver does when its one session is taken

Worth knowing before building anything here, because it was assumed the other way round for a while.
The receiver allows exactly one telnet session, and Android kills the app's process on a package
update the same way it does on a force-stop — no `onDestroy`, no `finally`. The kernel sends the FIN
instead, unless the Wi-Fi is asleep or gone at that moment, and then the receiver holds a session
that no longer exists.

Measured on an AVR-3310 on 2026-09-25, in four arrangements — session held by another device,
session held by the app's own superseded connect, Wi-Fi lost and restored with the process alive,
and the real thing (Wi-Fi off, force-stop, Wi-Fi on, restart):

**It refuses.** `ECONNREFUSED` on port 23, every time, while ping and port 80 keep answering. It
never once accepted the socket and then stayed silent. That is why there is no handshake in the
connect path: the case it would catch has not been observed on this model, and what the user
actually gets is the busy-port dialog (*`Reachable` says nothing about port 23*), which fired
correctly in exactly this situation. Whether another model behaves differently is unknown — there
is one receiver to test against and 60 model classes.

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

### `Reachable` says nothing about port 23

`AVRTargetTester.testAddress()` pings, tests **port 80**, and for the scan also 5000/6666 (or
8080 + 111 on 2016-and-later models). The control port is never probed. So the app can report a
receiver as reachable while the one port it needs is refusing — and the *Connectivity Problem*
dialog then says "the receiver is not responding", which is simply untrue.

That case has its own text since September 2026. `Reconnector` sets `controlPortBusy` when a
connect fails while ping and port 80 answer, and `ConfigurationAssistant` picks
`AVRControlPortBusy` over `AVRReset` accordingly: the receiver is there and the control session it
allows is most likely taken — by another app, another device, or one that was never closed — and
only cutting mains power reliably frees it. Standby usually does not, because the network stays
awake; that is why the dialog's "Automatic" button, which sends an HTTP standby/on cycle, often
leaves the user exactly where they were (issue #11). The text hedges on purpose: what the app
knows is that port 80 answered and port 23 did not, which the receiver's own IP-control setting
can produce just as well.

The flag is deliberately a *message* selector and nothing more — it feeds no `StatusFlag` and
changes no connect behaviour. It also decides whether the dialog appears at all: `checkStatus()`
routes to `checkReset()` on `Reachable` **or** `controlPortBusy`, because `Reachable` additionally
demands the UPnP ports, and without that a model offering neither 5000/6666 nor 8080 + 111 would
get the "configure your IP" dialog for an address we had just been talking to.

Three details keep it from lying:

- **It says nothing for an extended config.** `checkAddress()` returns `true` there without
  probing (see the top of this file), so `reachable` is not a finding and the flag stays false —
  `checkControlPortBusy()` asks `ConnectionConfiguration.isProbing()` first. Without that, every
  failure for those users, unplugged receiver included, would claim the box had answered.
- **It is computed before `Reachable` is published, not after.** `setStatus(Reachable, false)`
  makes `Connected` *defined* by fallthrough, and that is the condition on which
  `ConnectionProgressMonitor` opens the dialog that reads the flag. The probe in between blocks
  about a second, so the other order leaves the previous run's value readable for that second.
  Being a second late with `Reachable` on a failed attempt costs nothing; the second `isCurrent()`
  after the probe is the same guard as at the loop's other blocking `checkAddress()`.
- **`clearState()` clears it.** Otherwise the finding survives an IP change or a `stop()`, and the
  next dialog recommends the mains plug for an address we never spoke to. The failure path in
  `Reconnector.run()` does not call `clearState()`, so the flag still outlives the run that sets
  it.

Why a dialog and not a log line: `debugMode` defaults to `adb`, so there is no file log on a
normal installation and no way to ask for one after the fact. Whatever the app does not say on
screen is lost.

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

## One network callback, and every socket bound to it

`scan/LocalNetwork` is the only place that knows anything about the local network. It registers one
`ConnectivityManager.NetworkCallback` in `AVRApplication.onCreate` and from it answers four
questions: is a Wi-Fi up, which `Network` object is it, what IPv4 address does the device hold in
it, and — when the answer is no — why not.

Two details there are answers to real failures, not taste:

- **The request carries no `NET_CAPABILITY_INTERNET`.** `NetworkRequest.Builder` defaults to
  `NOT_RESTRICTED`/`TRUSTED`/`NOT_VPN` only, and `registerNetworkCallback` reports *every* matching
  network rather than just the default one. That is what keeps a Wi-Fi without internet — dead WAN,
  captive portal, a deliberately isolated AV network — counted as connected while mobile data is
  active. The receiver is plainly there in that case; the app used to refuse the whole scan on it.
  `clearCapabilities()` would be the explicit way to say this and is API 30, above `minSdk 24`.
- **The listener runs on the main thread**, by `Handler.post()` from inside the callback. The
  `BroadcastReceiver` this replaced ran in `onReceive` on the main thread, and
  `EnableManager.setStatus()` is an unsynchronised read-modify-write (see [TODO.md](TODO.md)) — the
  thread change is not a free ride-along. The `Handler` overload of `registerNetworkCallback` would
  be the direct way to say it, and is API 26.

Everything that opens a socket to the receiver binds it to that `Network` first —
`core/Connector` (telnet 23), `http/HTTPSupport` (both entry points, via `Network.openConnection`),
`scan/AVRTargetTester` and the SSDP socket. Without the binding, `connect()` takes the default
network, which beside active mobile data is *not* the Wi-Fi, and the receiver is unreachable in
exactly the case the paragraph above works so hard to recognise. `bindSocket` per socket, never
`bindProcessToNetwork()` — the latter applies to the whole process, including connections that have
no business on the local network.

One thing cannot be bound: `InetAddress.isReachable()` offers no way to. The scan's ping may
therefore still leave over the wrong interface. It is only a fast negative filter ahead of the TCP
probe on port 80, so the fallback if it misbehaves on a device is to drop it, not to reach for
`bindProcessToNetwork()`.

### Local Network Protection

The binding is also what Android 17 requires. Local Network Protection is mandatory at
**targetSdk 37**, which this app now targets, and gates *every* socket into the local network behind
`ACCESS_LOCAL_NETWORK` — telnet 23 and the HTTP scraping as much as the search. Without it the app
is not scan-less, it is dead.

The permission is declared in the manifest and requested by
`AVRSettings.requestLocalNetworkPermission()`, from `AVRRemote.onCreate` (gated on
`savedInstanceState == null`) and again from `AVRScanner.scanIP()`, because the scan is reachable
from the options menu and the setup assistant without `AVRRemote` having started. A rationale
dialog appears only when Android asks for one, i.e. after a previous refusal. On a grant the
connector is told to reconnect, because the attempt made at startup went nowhere.

**LNP blocks silently.** Measured on a Pixel 8 with the restriction forced on: without the
permission the socket does not fail, it is swallowed — `InetAddress.isReachable()` says false and
`connect()` to port 23 ends in a `SocketTimeoutException` after 2.5 s, from the correct source
address. There is no `SecurityException` to catch and nothing in the failure that names a
permission. Left alone, the app would show its ordinary "not reachable" state and retry forever,
which reads as "receiver switched off or wrong IP" — the one diagnosis that sends a user looking in
entirely the wrong place. Hence two deliberate hints:

- `AVRRemote.onRequestPermissionsResult` shows the reason when the request is *refused*, and
- `AVRScanner.scan()` refuses to start and says why, instead of letting the user watch a
  progress dialog for ten seconds before "no receiver found".

A third one is where users actually meet it: `ConfigurationAssistant.checkStatus()` used to see
`WLAN` true and `Reachable` false and announce that the configured address was invalid, offering a
scan that could not work either — while the address was perfectly correct. Under LNP `Reachable` is
always false, because both the ping and the port-80 probe go nowhere. That is the dialog a user sees
at startup, so that is where the explanation belongs.

Nothing raises a dialog from `AVRRemote.onCreate` any more, including the rationale that
`shouldShowRequestPermissionRationale()` asks for. The connection progress dialog and the assistant
open on top of it within seconds, so it was never seen — and because the old code called
`requestPermissions()` from that dialog's button, the permission was never actually requested at
all. Asking directly, as the `POST_NOTIFICATIONS` path beside it does, is what makes the system
dialog appear.

All of it is gated on `AVRSettings.isLocalNetworkBlocked()`, which asks whether LNP applies **to
this build** — the device is Android 17 *and* `getApplicationInfo().targetSdkVersion` is 37 — not merely
whether the permission is missing. That was written while the target was still 36, where the
permission is absent without consequence and a warning about it would have been simply wrong;
reading the target from the running package rather than from a constant is what armed the hints by
itself on the day `build.gradle` moved to 37. `AVRSettings.requestLocalNetworkPermission()` carries
the same condition, so the app does not spend the user's one refusal on a permission that cannot yet
do anything.

To exercise the blocked state now, take the permission away:

```sh
adb shell pm revoke de.pskiwi.avrremote android.permission.ACCESS_LOCAL_NETWORK
```

Measured on a Pixel 8 that way in September 2026: `Reconnector:reachable ... : false` while the
receiver is plainly there, the assistant explains it instead of blaming the address, and the scan
stops with the same explanation. Granting it again produces `reachable true` and a reconnect within
the next cycle. The older route, `adb shell am compat enable RESTRICT_LOCAL_NETWORK`, forces the
restriction itself but **not** the hints, which are gated on `targetSdkVersion` — it was the way to
see the blocking before the target moved, and it is the weaker test now.

### Searching instead of sweeping

`scan/SSDPDiscovery` sends an `M-SEARCH` to `239.255.255.250:1900` (three times, ~800 ms apart,
collecting for three seconds — UDP is allowed to lose packets) and returns the addresses that
answered. Every candidate then goes through the existing `AVRTargetTester.testAddress()`, which is
what keeps printers, TVs and routers — they all answer `ssdp:all` — out of the result, and is why
nothing else about the scan had to change.

Separately from the search, `http/DeviceDescription` fetches the receiver's UPnP description once
an hour from `http://<ip>:8080/description.xml` — hard-coded port, because port 80 answers that path
with a 404 on an AVR-3310 and the authoritative `LOCATION` exists only during a scan. It runs on the
background thread in `StatusAreaManager` that already fetches the XML state, and on `Reachable`
rather than only on `Connected`: a receiver that answers ping and port 80 but holds its single
control channel is exactly the state field reports come from, and the description is what says which
device is really there — as opposed to which one the user picked from the list of 60. It lands in
the feedback report and nowhere else. `serialNumber` and `UDN` are deliberately not read: both carry
the device's MAC, and a report travels by mail.

Both searches hand their addresses to the same `AVRScanner.testAll()`, which fans them out over
`SCAN_THREADS` threads. That matters more for SSDP than it looks: a candidate costs up to four
connect timeouts, so testing a household's worth of UPnP devices one after another would take
longer than the whole sweep it is supposed to replace. A hit carries the way it was found
(`FoundBy.SSDP` or `FoundBy.SWEEP`) into the selection list, because the two say different things —
SSDP means the device announced itself as a UPnP device, the sweep only means the right ports are
open.

No `MulticastLock` is needed: replies to `M-SEARCH` come back unicast. A lock only gates *incoming*
multicast, i.e. unsolicited `NOTIFY`. If a device turns out to receive nothing,
`WifiManager.createMulticastLock()` is the next step — and `ACCESS_WIFI_STATE`, dropped from the
manifest when nothing used `WifiManager` any more, comes back with it.

The subnet sweep stays as the fallback: 254 addresses × up to four TCP connects, now driven by the
prefix length from `LocalNetwork.getIPv4()` rather than by a string comparison against
`255.255.255.0`, so `/25`…`/30` work instead of being refused. Under LNP a sweep is also precisely
the behaviour that makes the permission expensive to justify, which is the other reason SSDP goes
first.
