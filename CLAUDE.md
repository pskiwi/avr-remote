# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android remote control for Denon and Marantz AV receivers (`de.pskiwi.avrremote`, GPL v3).
Talks to receivers on the local network — there is no backend and no account.

**[TODO.md](TODO.md) is the backlog** — the next platform deadline, the structural items and the
housekeeping, each with file and line. Read it before proposing work of your own; what looks
like an oversight is usually already listed there with the reason it was left alone. When you finish
an item, **delete it** in the same commit rather than ticking it — the file only stays readable
because closed items do not accumulate, and the git history is where "what was done and why" lives.
If a closed item established something a live item still relies on, fold that into the live item.

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

**There is almost no test coverage.** `src/test` holds nine JVM test classes on JUnit 4, the only
dependency in the project — `http/HTTPSupportTest`, `http/Series08ParserTest`,
`core/ThreadHandlerTest`, `core/InDataTest`, `core/display/NetDisplayTest`,
`core/display/TunerDisplayTest`, `models/ModelConfiguratorTest`, `ReceiverStatusTest` and
`http/AVRXMLInfoParserTest` — and there is no `src/androidTest` at all. `./gradlew test` runs a few
dozen cases and nothing else (twice, in fact: once per build variant), so do not report a change as
verified because the build passed; verify on a device or emulator instead. Six limits are worth
knowing before writing more tests:

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
- Real device output belongs in `src/test/resources`, not next to the source or read from disk.
  `Series08ParserTest` parses two captured AVR-3808 pages from
  `src/test/resources/de/pskiwi/avrremote/http/` through `getResourceAsStream`; Gradle puts that
  directory on the test classpath and tracks it as a task input by itself, so it needs neither an
  `inputs.file` entry nor the module/root path dance above. Keep such captures byte-exact — one of
  the two has CRLF line endings and both pad their values with spaces, and the tests exist to pin
  what the parsers do with that.
- **A static initialiser that touches `android.os.Build` locks the whole class out of JVM tests.**
  The stub `android.jar` leaves those fields null, so the initialiser throws and every test using
  the class dies with `ExceptionInInitializerError` — including classes that merely *use* it, which
  makes the cause hard to see. `core/InData` had exactly that: an `EXTENDED_DEBUG` field calling
  `EmulationDetector.isEmulator()`, for a debug string. It is now read inside `toDebugString()`
  instead, which is what makes `core/InDataTest` and `core/display/NetDisplayTest` possible at all —
  `NetDisplay.DisplayStatusReader` takes an `InData`. Constructors and field initialisers of Android
  types are fine (`NetDisplay` builds a `Handler` in a field initialiser and still constructs on a
  JVM); it is *reading* `Build` that fails. So the display classes need no keep-alive trick: the
  test builds `new NetDisplay(null, null, DisplayType.NETWORK)` and reaches the inner
  `DisplayStatusReader` directly, because for `NETWORK` the constructor touches neither argument.
- `core/ThreadHandlerTest` asserts on wall-clock time, as does `Series08ParserTest`'s
  `largeLineStaysFast`. Its threshold sits between "no wait" and the `join(1000)` it replaced
  (measured 1003 ms), so keep that margin if you touch it. It reaches
  `ResilentConnector.ThreadHandler` because that nested class is package-private for exactly this
  reason — the enclosing class cannot be built from a JVM test at all, because its constructor wants
  an `EnableManager` and that one builds a `Handler` in a field initialiser. Same trick as
  `ModelConfigurator.createModel(String)`. What it does *not* cover is the load-bearing half of the
  argument — that a detached thread publishes nothing after `stop()` returns — and no JVM test
  reaches that; see [CONNECTION.md](CONNECTION.md) → *The generation counter*.

**A log a user sent in** (`log/SDLogger` writes it, `log/FeedbackReporter` mails it) has one
header line per entry and carries a `#seq` and a thread name. Sort by `#seq`, never by timestamp
or line order, and split runs on `openend at` first — the reasoning, and how to read a connection
problem out of one, is in [CONNECTION.md](CONNECTION.md) → *Reading a log*.

Lint runs with `abortOnError = false`, so lint *errors* do not fail the build — check
`app/build/reports/lint-results-debug.html` explicitly when it matters.

Release builds are only signed when a keystore is configured; otherwise the build silently produces
an unusable `app-release-unsigned.apk`. Conditions and setup: [RELEASE.md](RELEASE.md).

Debug builds show a greyed-out line with branch, short commit hash and commit time above the status
bar (`R.id.textBuildInfo` in `tabhost.xml`, switched visible in `AVRRemote.onCreate`), fed from
`BuildConfig.BUILD_INFO`. **The `buildInfo` block at the top of `app/build.gradle` explains
itself** in its comments — why `providers.exec` rather
than `ProcessBuilder`, why a detached HEAD falls back to `GITHUB_REF_NAME` instead of `git name-rev`,
why the value is charset-filtered, and why no `git` failure may break the build. Read the comments
there before changing any of it. One thing not said there: the exec spec deliberately takes no
`errorOutput`, because a value source rejects an arbitrary stream and the whole provider then fails
to be created, silently emptying `BUILD_INFO`.

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

The reconnect loop behind that first arrow, who tears it down and what Doze does to it:
[CONNECTION.md](CONNECTION.md).

`AVRApplication` is the central object graph: `getAvrState()`, `getConnector()`, `getEnableManager()`,
`getModelConfigurator()`, `getDisplayManager()`, `getRenameService()`, `getMacroManager()` and more.
Activities reach everything through `(AVRApplication) getApplication()`.

`EnableManager` drives view enablement from a small set of `StatusFlag`s, which is why most buttons
are greyed out until a receiver is actually connected — worth knowing when testing without hardware.
The flags and their cascade: [CONNECTION.md](CONNECTION.md).

`IStateFilter` decides which screen currently receives state updates, so background activities do not
fight over the display listener.

`core/display/` parses the receiver's on-screen display into `DisplayLine`s. `NetDisplay`,
`TunerDisplay` and `BDDisplay` are three separate, large parsers for different input types.

Up to 4 zones and up to 3 receivers are supported; the receiver index threads through
`AVRSettings.getAVRModel(ctx, receiverNr)` and the `receiverSuffix()` preference-key convention.

### Receiver models — reflection registry

`models/` holds **60 receiver classes**, one per receiver family, each overriding capability
predicates (`hasZones()`, `hasQuick()`, `getSupportedLevels()`, `supportsDAB()`, …). The other seven
files in that directory are infrastructure: `AbstractModel`, `AbstractMarantzAV`, `IAVRModel`,
`ModelArea`, `ModelConfigurator` and the two `DynamicEQ*` enums. All 60 reach `AbstractModel`, some
directly and some through `AbstractMarantzAV` or another model class (`AVR990 extends AVR3310`, …).

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
  manual `try { … } finally { x.close(); }` pattern — `http/HTTPSupport` is the reference. The tree
  was converted in August 2026 and `core/Connector.java:168` is the only manual block left: it is
  not convertible at all, it closes the socket only on the failure path (`if (!ok)`). The other reason to keep the manual form is when
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

## What comes next

The next platform deadline is Local Network Protection at Android 17, and it is the first item in
[TODO.md](TODO.md). The connection side of it is in [CONNECTION.md](CONNECTION.md).
