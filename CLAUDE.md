# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android remote control for Denon and Marantz AV receivers (`de.pskiwi.avrremote`, GPL v3).
Talks to receivers on the local network — there is no backend and no account.

**[TODO.md](TODO.md) is the backlog** — known-broken behaviour, the next platform deadline, and the
housekeeping items, each with file and line. Read it before proposing work of your own; what looks
like an oversight is usually already listed there with the reason it was left alone. When you finish
one of the items, tick its checkbox in the same commit.

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

**There is almost no test coverage.** `src/test` holds three JVM test classes —
`http/HTTPSupportTest` (7 cases), `http/Series08ParserTest` (5) and `http/AVRXMLInfoParserTest` (1),
JUnit 4 being the only dependency in the project — and there is no `src/androidTest` at all.
`./gradlew test` runs those thirteen cases and nothing else (twice, in fact: once per build
variant), so do not report a change as verified because the build passed; verify on a device or
emulator instead. Two limits are worth knowing before writing more tests:

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

Lint runs with `abortOnError false`, so lint *errors* do not fail the build — check
`app/build/reports/lint-results-debug.html` explicitly when it matters.

Release builds are signed only when `~/keystore.properties` exists or the `KEY_ALIAS` /
`KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` env vars are set; otherwise
`app-release-unsigned.apk` is produced and cannot be installed.

## Architecture

### Two independent transports

1. **Telnet, port 23** — the real control channel. `core/Connector` holds a raw socket, writes
   commands terminated with `\r`, and parses incoming lines into `InData`.
2. **HTTP** — `http/AVRHTTPClient` scrapes the receiver's own web UI (`*.asp`, XML endpoints) for
   things the telnet protocol does not expose: input/zone names, quick-select presets, NET audio
   search. `http/Series08*` parse the 2008-series variant. Every request goes through
   `http/HTTPSupport` (`HttpURLConnection`, GET and form POST, nothing else); its three callers are
   `http/AVRHTTPClient`, `http/Series08Reader` and — easy to miss — `core/display/NetDisplay`.
   Three things there are deliberate and load-bearing against the receivers' 2008-era GoAhead
   webservers, and all three look removable to someone who does not know why they exist:

   - `Accept-Encoding: identity` — the old Apache client never asked for gzip, Android does.
   - `setFixedLengthStreamingMode` — so the request body is never sent chunked.
   - `CookieHandler.setDefault(new CookieManager())` in `AVRApplication.onCreate`. The receivers
     tested so far send no `Set-Cookie` at all, so this looks pointless — but `Series08Reader`
     fetches `r_option1.asp` purely to establish state that the following `d_option1.asp` reads
     back, and the Apache client it replaced carried a cookie store. Without a handler
     `HttpURLConnection` shares nothing between requests, and the failure would be silent (empty
     quick-select names). `readSeries08Info()` clears the store per run, because the old store was
     per-client and did not outlive one read.

   Receivers speak plain HTTP, so `android:usesCleartextTraffic="true"` in the manifest is
   load-bearing too — removing it kills the whole scraping path.

### State flow

```
ResilentConnector (daemon thread, reconnect loop)  ->  Connector (socket)
        -> AVRState.received(InData)  ->  ZoneState[zone]  ->  listeners
        -> IGUIExecutor (Handler.post) ->  main thread
```

`ResilentConnector` runs a long-lived reconnect loop on a plain daemon thread owned by
`AVRApplication` — **not** a Service. Killing or backgrounding the app kills the connection.

`AVRApplication` is the central object graph: `getAvrState()`, `getConnector()`, `getEnableManager()`,
`getModelConfigurator()`, `getDisplayManager()`, `getRenameService()`, `getMacroManager()` and more.
Activities reach everything through `(AVRApplication) getApplication()`.

`EnableManager` drives view enablement from a small set of `StatusFlag`s (`Logging`, `WLAN`,
`Reachable`, `Connected`, `Power`, `Zone1`–`Zone4`). This is why most buttons are greyed out until a
receiver is actually connected — worth knowing when testing without hardware.

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
  name falls back to `AVRGeneric` and the user just silently misses features. (Note `lists.xml` holds
  130 `<item>`s across 14 arrays; only the 60 in `modelNames` are receivers.)
- The model classes have no static references. Never enable R8/ProGuard shrinking without a
  keep rule for `de.pskiwi.avrremote.models.**`.

## Conventions and constraints

- **No AndroidX, no third-party dependencies.** `app/build.gradle` has no `dependencies {}` block at
  all. Everything is raw framework API. Keep it that way unless explicitly asked — adding AndroidX
  would pull the whole legacy UI stack into a migration.
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
  pattern is still in 13 places (`core/Connector`, `core/MacroManager`, `log/FeedbackReporter`,
  `log/SDLogger`, the three `http/Series08*Parser`, `core/RenameService`, `scan/AVRTargetTester`);
  converting one is welcome when you are editing that code anyway, but do not sweep the tree as a
  side errand. The one reason to keep the manual form is when
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

- `log/SDLogger` writes to `Environment.getExternalStorageDirectory()` — fails under scoped storage
  since Android 10. `WRITE_EXTERNAL_STORAGE` in the manifest has been a no-op since Android 11.
- `SDLogger.getLogURI()` returns a `file://` URI that `log/FeedbackReporter` puts into
  `Intent.EXTRA_STREAM` — throws `FileUriExposedException`. Sending logs needs a `FileProvider`.
- The custom-background picker (`AVRSettings`) stores the URI without
  `takePersistableUriPermission()`, so it does not survive a restart.

## Next SDK step

Local Network Protection becomes mandatory for apps targeting **Android 17 (SDK 37)**. That directly
hits `scan/AVRScanner` (subnet sweep) and the raw receiver sockets — i.e. the core of the app. At
`targetSdk 36` it does not apply yet, but plan for a runtime local-network permission before raising
the target further. See [TODO.md](TODO.md) → *Next platform deadline: targetSdk 37* for the deprecated
`WifiManager` calls to replace in the same pass.
