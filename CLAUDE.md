# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android remote control for Denon and Marantz AV receivers (`de.pskiwi.avrremote`, GPL v3).
Talks to receivers on the local network — there is no backend and no account.

## Build and run

The README still says JDK 8; that is outdated. The build needs **JDK 17** and **Android SDK 36**:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17          # keg-only formula, not on PATH by default
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew build             # what CI runs: assemble + lint, both variants
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is deliberately untracked — the SDK path comes from `ANDROID_HOME`.

**There are no tests.** No `src/test`, no `src/androidTest`, no test dependencies. `./gradlew test`
succeeds but runs nothing. Do not report a change as verified because the build passed; verify on a
device or emulator instead. Lint runs with `abortOnError false`, so lint *errors* do not fail the
build — check `app/build/reports/lint-results-debug.html` explicitly when it matters.

Release builds are signed only when `~/keystore.properties` exists or the `KEY_ALIAS` /
`KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` env vars are set; otherwise
`app-release-unsigned.apk` is produced and cannot be installed.

## Architecture

### Two independent transports

1. **Telnet, port 23** — the real control channel. `core/Connector` holds a raw socket, writes
   commands terminated with `\r`, and parses incoming lines into `InData`.
2. **HTTP** — `http/AVRHTTPClient` scrapes the receiver's own web UI (`*.asp`, XML endpoints) for
   things the telnet protocol does not expose: input/zone names, quick-select presets, NET audio
   search. `http/Series08*` parse the 2008-series variant. This is the only Apache-HTTP code left
   (`useLibrary 'org.apache.http.legacy'`).

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

`EnableManager` drives view enablement from a small set of `StatusFlag`s (`WLAN`, `Reachable`,
`Connected`, `Power`, `Zone1`–`Zone4`). This is why most buttons are greyed out until a receiver is
actually connected — worth knowing when testing without hardware.

`IStateFilter` decides which screen currently receives state updates, so background activities do not
fight over the display listener.

`core/display/` parses the receiver's on-screen display into `DisplayLine`s. `NetDisplay`,
`TunerDisplay` and `BDDisplay` are three separate, large parsers for different input types.

Up to 4 zones and up to 3 receivers are supported; the receiver index threads through
`AVRSettings.getAVRModel(ctx, receiverNr)` and the `receiverSuffix()` preference-key convention.

### Receiver models — reflection registry

`models/` holds 67 classes, one per receiver family, all extending `AbstractModel` and overriding
capability predicates (`hasZones()`, `hasQuick()`, `getSupportedLevels()`, `supportsDAB()`, …).

`ModelConfigurator.update()` resolves the model **by reflection** from the user's preference string:

```
"AVR-3310"  --strip "-"--> "AVR3310"  -->  Class.forName("de.pskiwi.avrremote.models.AVR3310")
```

A trailing `(experimental)` is stripped too, and any failure silently falls back to `AVRGeneric`.

Consequences:
- Adding a receiver needs **two** changes: a class in `models/` *and* an entry in
  `@array/modelNames` in `res/values/lists.xml`. The names must correspond after dash-stripping.
- The model classes have no static references. Never enable R8/ProGuard shrinking without a
  keep rule for `de.pskiwi.avrremote.models.**`.

## Conventions and constraints

- **No AndroidX, no third-party dependencies.** `app/build.gradle` has no `dependencies {}` block at
  all. Everything is raw framework API. Keep it that way unless explicitly asked — adding AndroidX
  would pull the whole legacy UI stack into a migration.
- The UI is built on deprecated bases: `TabActivity`, `ListActivity`, `ExpandableListActivity`,
  `PreferenceActivity`, `TabHost`/`TabWidget` layouts, and pre-Holo `Theme.NoTitleBar` themes.
  They still compile; match the surrounding style rather than modernising piecemeal.
- Indentation is tabs. Comments are a mix of German and English.
- `android.nonFinalResIds=false` in `gradle.properties` is load-bearing: it keeps `R` fields final so
  the `switch`/`case R.id.*` in `menu/OptionsMenu.java` compiles. Do not remove it without rewriting
  that file to `if`/`else`.

### When adding an Activity

Two things are easy to forget and both are required at `targetSdk 36`:

1. `android:exported="false"` in the manifest — missing it is a **build error**, not a warning.
2. `EdgeToEdge.apply(this)` right after `setContentView(...)`. Edge-to-edge is enforced with no
   opt-out; without it the content draws under the status and navigation bars, because none of the
   themes have an ActionBar to absorb the insets. Dialog-themed activities (`OptionActivity`) do not
   need it.

## Known-broken, pre-existing

Do not treat these as regressions; they predate the SDK 36 upgrade:

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
the target further.
