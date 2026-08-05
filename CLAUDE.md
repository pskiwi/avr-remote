# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android remote control for Denon and Marantz AV receivers (`de.pskiwi.avrremote`, GPL v3).
Talks to receivers on the local network — there is no backend and no account.

**[TODO.md](TODO.md) is the backlog** — known-broken behaviour, the next platform deadline, and the
housekeeping items, each with file and line. Read it before proposing work of your own; what looks
like an oversight is usually already listed there with the reason it was left alone. When you finish
one of the items, tick its checkbox in the same commit.

**[CONNECTION.md](CONNECTION.md) is the reference for everything network-facing** — the two
transports, the reconnect loop and its generation counter, who decides when to hang up, and what
Doze does to all of it. Read it before touching `core/ResilentConnector`, `core/Connector`,
`ActiveHandler` or `http/HTTPSupport`; the non-obvious parts there are answers to field bugs.

**[RELEASE.md](RELEASE.md) is the release procedure** — where the version lives, how signing is
wired up, the tag convention, and the Play Console checklist with its deadlines. The one thing worth
knowing before reading it: the `v*` tag workflow publishes to **GitHub Releases only**; there is no
Play Store automation and never has been.

## Build and run

The build needs **JDK 17** and **Android SDK Platform 36**:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17          # keg-only formula, not on PATH by default
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew build             # what CI runs: assemble + lint, both variants
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is deliberately untracked — the SDK path comes from `ANDROID_HOME`.

**There is almost no test coverage.** `src/test` holds six JVM test classes —
`http/HTTPSupportTest` (7 cases), `http/Series08ParserTest` (6), `core/ThreadHandlerTest` (5),
`models/ModelConfiguratorTest` (3), `ReceiverStatusTest` (3) and `http/AVRXMLInfoParserTest` (1),
JUnit 4 being the only dependency in the project — and there is no `src/androidTest` at all.
`./gradlew test` runs those twenty-five cases and nothing else (twice, in fact: once per build
variant), so do not report a change as verified because the build passed; verify on a device or
emulator instead. Four limits are worth knowing before writing more tests:

- These run on the desktop JVM, not on Android's OkHttp-backed stack. Anything Android-specific —
  the implicit `Accept-Encoding: gzip`, chunked request bodies — cannot be pinned here, only
  documented. `HTTPSupportTest` says so at the assertions concerned.
- `http/AVRXMLInfoParser` cannot be driven from a JVM test without changing the class: it reads only
  SAX `localName`, which stays empty on a standard non-namespace-aware `SAXParserFactory`. Android's
  Expat-based parser fills it anyway, which is the only reason the scraping path works. Same pattern
  in `core/RenameService`. Its XXE hardening is Android-specific for the same reason: Android's
  `SAXParserFactoryImpl` rejects `disallow-doctype-decl` with `SAXNotRecognizedException`, so the
  parser disables `external-general-entities` and `external-parameter-entities` instead — those two
  are supported. Verified on a Pixel 8; do not "simplify" it to the usual one-liner.
- A test that reads project files from disk rather than from the classpath — `res/` and `src/` are
  on neither — must be declared as a task input, or Gradle calls the test up to date on exactly the
  change it exists to catch. `ModelConfiguratorTest` reads `res/values/lists.xml`, hence the
  `tasks.withType(Test).configureEach { inputs.file(...) }` block at the bottom of `app/build.gradle`
  (it covers both variants — verified by watching `testReleaseUnitTest` re-run too). It also
  resolves its paths against both the module and the root directory, because the working directory
  depends on how the run was started.
- `core/ThreadHandlerTest` asserts on wall-clock time, as does `Series08ParserTest`'s
  `largeLineStaysFast`: it pins that tearing the reconnect thread down does not block its caller,
  which is the UI thread via
  `ActiveHandler.contextResumed()` → `forceReconnect()`. The threshold sits between "no wait" and
  the `join(1000)` it replaced (measured 1003 ms), so keep that margin if you touch it. It reaches
  `ResilentConnector.ThreadHandler` because that nested class is package-private for exactly this
  reason — the enclosing class cannot be built from a JVM test at all, because its constructor wants
  an `EnableManager` and that one builds a `Handler` in a field initialiser. Same trick as
  `ModelConfigurator.createModel(String)`. Note what
  it does *not* cover: that a detached thread publishes nothing after `stop()` returns. That is the
  load-bearing half of the argument, it lives in `Reconnector.run()`'s `isCurrent()` checks and
  `publishConnector()`, and no JVM test reaches it — read those before touching either.

**Reading a log a user sent in** (`log/SDLogger` writes it, `log/FeedbackReporter` mails it). One
line looks like this, and a message can run over several lines — anything not matching the header is
a continuation:

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

The thread name is captured in `SDLogger.withThread()` at log time rather than in the formatter, so
it stays right no matter which thread does the writing. Seen so far: `main`, `receiver`, `sender`,
`StateCheckThread`, `LoadXMLStatus`, `StopConnector-Timer` and `ResilentThreadHandler-<n>`, where
`<n>` is the `generation` counter from `ResilentConnector` and several can be alive at once. logcat
has no such field because it carries its own tid column. What those names mean for a connection
problem, and what a healthy and a broken sequence look like: [CONNECTION.md](CONNECTION.md).

An exception is followed by its type, message and up to `MAX_TRACE` frames of stack, indented with
tabs, `Caused by:` per cause. Before 1.6.1 the formatter dropped the `Throwable` entirely, so older
logs carry only the message — `reading macros.txt failed` there could be the harmless
`FileNotFoundException` it usually is, or anything else.

`ReceiverStatus.toString()` walks `StatusFlag.values()` rather than its own map, so the flags always
appear in the same order and two status lines can be diffed as text. Over the map they could not:
3 of the 11 flag sets in that field log show up in more than one order, two of them in three.

Lint runs with `abortOnError = false`, so lint *errors* do not fail the build — check
`app/build/reports/lint-results-debug.html` explicitly when it matters.

Release builds are signed only when `~/keystore.properties` exists or the `KEY_ALIAS` /
`KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` env vars are set; otherwise
`app-release-unsigned.apk` is produced and cannot be installed — see [RELEASE.md](RELEASE.md).

Debug builds show a greyed-out line with branch, short commit hash and commit time above the status
bar (`R.id.textBuildInfo` in `tabhost.xml`, switched visible in `AVRRemote.onCreate`). The value comes
from `BuildConfig.BUILD_INFO`, which `app/build.gradle` fills by calling `git` at configuration time —
**only in the `debug` build type**; `defaultConfig` sets it to the empty string and that is what
release keeps, which is what makes the line disappear there. Commit time, not build time, so the field
stays stable between builds of the same commit; a `+` after the hash means the working tree was dirty.
Three things there are deliberate:

- `providers.exec`, not `ProcessBuilder`. Starting a process directly makes the configuration
  incompatible with the configuration cache — the build then *aborts* with "external process started"
  the moment anyone passes `--configuration-cache`, rather than degrading. Note the exec spec takes no
  `errorOutput`: a value source rejects an arbitrary stream and the whole provider fails to be created,
  which silently empties `BUILD_INFO`. Gradle discards the stderr anyway.
- On detached HEAD only `GITHUB_REF_NAME` is consulted, and the branch is left out entirely when that
  is unset. `git name-rev` would fill it, but with whatever ref happens to reach the commit —
  `tags/v1.0~1` (a *different* commit, and the `~1` is then stripped by the charset filter), a foreign
  branch, or `undefined`. No branch beats a wrong one.
- The value is filtered to `[A-Za-z0-9._/+: -]` because it is pasted into generated Java source. Keep
  the `-` last in that character class.

Every `git` call falls back to an empty string (no git binary, no repo, git failing for any reason),
and an empty `BUILD_INFO` just means no line — the build must never fail over this.

## Architecture

### Two independent transports

Telnet on port 23 is the real control channel (`core/Connector`); HTTP on port 80 scrapes the
receiver's own web UI for what telnet does not expose — input and zone names, quick-select presets,
NET audio search (`http/AVRHTTPClient`, `http/Series08*`, all through `http/HTTPSupport`).

**[CONNECTION.md](CONNECTION.md) is the reference for both**, and worth reading before touching
either: several things in `HTTPSupport` and in the reconnect loop look removable and are not.

### State flow

```
ResilentConnector (daemon thread, reconnect loop)  ->  Connector (socket)
        -> AVRState.received(InData)  ->  ZoneState[zone]  ->  listeners
        -> IGUIExecutor (Handler.post) ->  main thread
```

`ResilentConnector` runs a long-lived reconnect loop on a plain daemon thread owned by
`AVRApplication` — **not** a Service. Killing or backgrounding the app kills the connection. How
that loop behaves, who tears it down and what Doze does to it: [CONNECTION.md](CONNECTION.md).

`AVRApplication` is the central object graph: `getAvrState()`, `getConnector()`, `getEnableManager()`,
`getModelConfigurator()`, `getDisplayManager()`, `getRenameService()`, `getMacroManager()` and more.
Activities reach everything through `(AVRApplication) getApplication()`.

`EnableManager` drives view enablement from a small set of `StatusFlag`s (`Logging`, `WLAN`,
`Reachable`, `Connected`, `Power`, `Zone1`–`Zone4`). This is why most buttons are greyed out until a
receiver is actually connected — worth knowing when testing without hardware. The flags cascade into
each other, see [CONNECTION.md](CONNECTION.md).

`IStateFilter` decides which screen currently receives state updates, so background activities do not
fight over the display listener.

`core/display/` parses the receiver's on-screen display into `DisplayLine`s. `NetDisplay` (1182
lines), `TunerDisplay` (1059) and `BDDisplay` (393) are three separate parsers for different input
types.

Up to 4 zones and up to 3 receivers are supported; the receiver index threads through
`AVRSettings.getAVRModel(ctx, receiverNr)` and the `receiverSuffix()` preference-key convention.

### Receiver models — reflection registry

`models/` holds **60 receiver classes**, one per receiver family, each overriding capability
predicates (`hasZones()`, `hasQuick()`, `getSupportedLevels()`, `supportsDAB()`, …). The directory
has 67 `.java` files — the other seven are infrastructure: `AbstractModel`, `AbstractMarantzAV`,
`IAVRModel`, `ModelArea`, `ModelConfigurator` and the two `DynamicEQ*` enums. All 60 reach
`AbstractModel`, but only 33 extend it directly; the rest go through `AbstractMarantzAV` or another
model class (`AVR990 extends AVR3310`, `AVCA1HDA extends AVR5308`, …).

`ModelConfigurator.update()` resolves the model **by reflection** from the user's preference string:

```
"AVR-3310"  --strip "-"--> "AVR3310"  -->  Class.forName("de.pskiwi.avrremote.models.AVR3310")
```

A trailing `(experimental)` is stripped too, and any failure logs via `Logger.error` and falls back
to `AVRGeneric` — silent to the user, but visible in the log.

Consequences:
- Adding a receiver needs **two** changes: a class in `models/` *and* an entry in
  `@array/modelNames` in `res/values/lists.xml`. The names must correspond after dash-stripping.
  Both sides currently hold exactly 60 entries and resolve 1:1 — if they drift apart, the orphaned
  name falls back to `AVRGeneric` and the user just silently misses features. `ModelConfiguratorTest`
  is what turns that silent drift into a red build, in both directions; it drives the real lookup in
  `ModelConfigurator.createModel(String)`, split out of `update()` because there it sat behind a
  `Context`. Its own `expectedClassName` restates the dash and `(experimental)` stripping on purpose
  — that second copy is what pins the naming convention. (Note `lists.xml` holds 130 `<item>`s
  across 14 arrays; only the 60 in `modelNames` are receivers.)
- The model classes have no static references. Never enable R8/ProGuard shrinking without a
  keep rule for `de.pskiwi.avrremote.models.**`.

## Conventions and constraints

- **No AndroidX, no third-party dependencies.** `app/build.gradle`'s `dependencies {}` block holds a
  single line, `testImplementation 'junit:junit:4.13.2'` — nothing ships in the APK.
  Everything is raw framework API. Keep it that way unless explicitly asked — adding AndroidX
  would pull the whole legacy UI stack into a migration. This is why `log/LogFileProvider` is a
  hand-written `ContentProvider` and not `androidx.core.content.FileProvider`: it exists only to hand
  the zipped log to the mail app as a content URI (`log/SDLogger.getLogURI()` →
  `log/FeedbackReporter.sendMail()`, which must set `FLAG_GRANT_READ_URI_PERMISSION`). There is no
  platform equivalent to fall back to at any API level — `FileProvider` has only ever shipped in the
  support library and AndroidX; the framework has no such class. It serves exactly one file name out
  of `SDLogger.getLogDir()`, read-only. The traversal guard is the `ZIP_NAME` comparison in
  `resolve()`: the file name is a constant and is only *checked* against the URI, never taken from
  it. Do not "harden" that with a canonical-path check — it could not fail.
- The UI is built on deprecated bases: `TabActivity`, `ListActivity`, `ExpandableListActivity`,
  `PreferenceActivity`, `TabHost`/`TabWidget` layouts, and pre-Holo platform themes
  (`Theme.NoTitleBar`, plus `Theme.Light` and `Theme.Dialog` used directly from the manifest).
  They still compile; match the surrounding style rather than modernising piecemeal.
- **JDK 17 builds the project, but the source level is Java 11** (`sourceCompatibility`/
  `targetCompatibility VERSION_11`). No records (16), no switch expressions (14), no text blocks (15)
  — Java 11 syntax only.
- **Within Java 11, write modern Java.** The code dates from 2010 and mostly predates it, but new and
  touched code should not imitate that. In particular use **try-with-resources** rather than the
  manual `try { … } finally { x.close(); }` pattern — `http/HTTPSupport` is the reference. The old
  pattern is still in 12 places (`core/Connector`, `core/MacroManager`, `log/FeedbackReporter`,
  the three `http/Series08*Parser`, `core/RenameService`, `scan/AVRTargetTester`);
  converting one is welcome when you are editing that code anyway, but do not sweep the tree as a
  side errand — and note `core/Connector.java:168` is not convertible at all, it closes the socket
  only on the failure path (`if (!ok)`). The other reason to keep the manual form is when
  an exception from `close()` must be swallowed deliberately — see
  `HTTPSupportTest.serveOneRequest`, where letting it propagate would fake a test failure.
  This does **not** extend to the UI bases above: those are a migration, not a style choice.
- `minSdk 24`. Anything newer needs a `Build.VERSION.SDK_INT` guard. Lint reports this as `NewApi`,
  but `abortOnError false` means the build still succeeds — it will only fail on the device.
- Indentation is tabs. Comments are a mix of German and English.
- `android.nonFinalResIds=false` in `gradle.properties` is load-bearing: it keeps `R` fields final so
  the `switch`/`case R.id.*` in `menu/OptionsMenu.java` compiles. Do not remove it without rewriting
  that file to `if`/`else`.

### When adding an Activity

Three things are easy to forget and all are required at `targetSdk 36`:

1. `android:exported` in the manifest. For a component **with** an intent filter, omitting it is a
   build error; without a filter it is optional but set explicitly here for consistency.
2. `EdgeToEdge.apply(this)` right after `setContentView(...)`. Edge-to-edge is enforced with no
   opt-out; without it the content draws under the status and navigation bars, because none of the
   themes have an ActionBar to absorb the insets. Dialog-themed activities (`OptionActivity`) do not
   need it.
3. If the activity requests a runtime permission, gate the request on
   `savedInstanceState == null` — these activities are recreated on every rotation.

## Known-broken, pre-existing

Do not treat these as regressions; they predate the SDK 36 upgrade. Fixes are tracked in
[TODO.md](TODO.md) → *Broken today*:

- The custom-background picker (`AVRSettings`) stores the URI without
  `takePersistableUriPermission()`, so it does not survive a restart.

## Next SDK step

Local Network Protection becomes mandatory at **Android 17 (SDK 37)** and hits the core of the app —
the subnet sweep and the receiver sockets. See [CONNECTION.md](CONNECTION.md) → *Next platform
deadline* and [TODO.md](TODO.md) for the deprecated `WifiManager` calls to replace in the same pass.
