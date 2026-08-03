# TODO

Things worth doing, roughly in the order they will hurt if left alone.
Everything here was found while moving the build to SDK 36 in July 2026; none of it
blocks the current build, which is green.

## Broken today

- [x] **Sending logs does not work.** `SDLogger.getLogURI()` returned a `file://` URI which
      `log/FeedbackReporter` put into `Intent.EXTRA_STREAM` → `FileUriExposedException` since
      targetSdk 24. Now handed out as a content URI by `log/LogFileProvider`, with
      `FLAG_GRANT_READ_URI_PERMISSION` on the intent. The provider is written by hand rather than
      derived from `androidx.core`: the project has no dependencies (CLAUDE.md), and there is no
      platform `FileProvider` at any API level to fall back to — the class has only ever existed in
      the support library and AndroidX. It serves exactly one file name from the log directory,
      read-only, and is `exported="false"`.
- [x] **`SDLogger` writes to `Environment.getExternalStorageDirectory()`**, which fails under
      scoped storage (Android 10+). Moved to `getExternalFilesDir(null)`, falling back to
      `getFilesDir()`. The `MEDIA_MOUNTED`/`MEDIA_REMOVED` receiver went with it — an app-specific
      directory is always there — which also fixes the leak from `stopWatchingExternalStorage()`
      never having had a caller.
- [x] Once both are done, drop `WRITE_EXTERNAL_STORAGE` from the manifest — it has been a no-op
      since Android 11.

## Next platform deadline: targetSdk 37

- [ ] **Local Network Protection becomes mandatory** for apps targeting Android 17. It affects the
      core of this app: the subnet sweep in `scan/AVRScanner` and the raw sockets to the receiver.
      Needs the new local-network runtime permission and a rationale UI.
- [ ] Same area, do it in one pass: `scan/WiFiInfo` relies on `WifiManager.getDhcpInfo()` (netmask
      and local IP for the sweep) and `ConnectivityManager.getNetworkInfo(TYPE_WIFI)`, plus
      `AVRApplication.java:63`. Both deprecated — `getDhcpInfo()` since API 31, the
      `getNetworkInfo(int)` overload used here already since API 23 → `NetworkCallback` and
      `NetworkCapabilities`.

## Structural

- [x] **Test coverage was three files.** `http/HTTPSupportTest`, `http/Series08ParserTest` and
      `http/AVRXMLInfoParserTest` (added with the Apache removal) set up `src/test` and the JUnit
      dependency; everything else is still uncovered. The highest-value test to add next is one for `models/ModelConfigurator`, because it
      covers a failure mode the compiler cannot see: it resolves the 60 receiver classes **by
      reflection** from a preference string (`"AVR-3310"` → `AVR3310`). Rename a class or let an
      entry in `res/values/lists.xml` drift and there is no build error — the app silently falls
      back to `AVRGeneric` and the user just misses features. A JVM unit test that runs all 60
      entries of `@array/modelNames` through the same normalisation and instantiates them catches
      exactly that. Both sides hold 60 entries today, so the test starts green.
      → `models/ModelConfiguratorTest` does this in both directions (name → class, and every
      concrete `IAVRModel` has a list entry). The reflection moved out of `update()` into
      `ModelConfigurator.createModel(String)`, which is what the test drives; the dash and
      `(experimental)` stripping is deliberately spelled out a second time in the test, so that
      changing the convention shows up as a failure. `lists.xml` is declared as a test input in
      `app/build.gradle`, otherwise Gradle skips the test on exactly the change it guards.
- [ ] After that, `models/` (pure capability logic) and `core/ZoneState.java` (1237 lines) are the
      cheapest places to add coverage.
- [ ] **`http/AVRXMLInfoParser` only works on Android and cannot be unit-tested.** `startElement` and
      `endElement` read `localName`, which a standard `SAXParserFactory` leaves empty because it is
      not namespace-aware by default — on a JVM the parser silently collects nothing. Android's
      Expat-based SAX fills `localName` regardless, which is the only reason the scraping path works.
      Falling back to `qName` when `localName` is empty would make the parser portable and testable.
      `core/RenameService.java:109` has the same pattern and would need the same fix; the
      `Series08*Parser` classes are unaffected, they read with `BufferedReader` and regexes.
- [ ] **Nothing in the 2008-series path was verified against hardware.** The Apache removal touched
      all of it and none of it could be exercised — no such receiver was available. Affected:
      `http/Series08Reader` (the cookie store it now clears per run, and the `r_option1.asp` →
      `d_option1.asp` sequence that depends on shared session state), plus
      `http/Series08ZoneRenameParser` and `http/Series08QuickSelectParser`, whose `OPTION_PATTERN`
      went from `matches()` with a wrapping `.*` to `find()` in a loop. `http/Series08ParserTest`
      pins the behaviour that could have broken — notably that the **last** match in a line wins,
      not the first, which is what the old greedy `.*` did — but those tests were written from the
      code, not from a real page. One run against a 2008-series receiver settles it: check that
      input names, zone names and quick-select names all still appear.
- [ ] Same area, latent NPE: `Series08Reader` has no length check on the response body where
      `AVRHTTPClient` has one. `Series08InputParser.findLine` returns null when the marker line is
      missing and the constructor then runs `OPTION_PATTERN.matcher(null)`. An empty or 404 answer
      triggers it. Pre-existing rather than a regression — but the Apache removal made the asymmetry
      between the two readers visible, and only one side got the guard.
- [ ] Same area, cookie lifetime: the store is cleared per Series08 read
      (`Series08Reader.readSeries08Info`), whereas the old `DefaultHttpClient` was per
      `AVRHTTPClient` instance, so it also covered the multi-zone path. Exact parity would clear it
      in the `AVRHTTPClient` constructor. Every receiver tested sends no `Set-Cookie` at all, so
      this only matters if a 2008-series device turns out to use sessions.
- [ ] Side effect of the XXE hardening in `http/AVRXMLInfoParser`: on a plain JVM the parser now
      rejects **any** XML carrying a DOCTYPE, because `disallow-doctype-decl` applies there (on
      Android it does not — see CLAUDE.md). Receivers never send one, so nothing breaks in the app,
      but whoever adds the JVM test the item above asks for will trip over it.
- [ ] **`NetDisplay.doHTTPMove()` and `doHTTPSeries08Move()` are unreachable.**
      `AbstractModel:135` returns `DisplayMoveMode.Classic` and not one of the 60 model classes
      overrides `getDisplayMoveMode()`, so the `switch` in `ScreenMover` always takes the `default`
      branch. Both methods (and `DisplayMoveMode`'s other two constants) are dead. Left in place
      during the Apache removal — decide whether the feature was meant to be wired up or should go.
- [ ] **The receiver connection lives on a daemon thread owned by `AVRApplication`, not a Service.**
      A design decision from 2010. Under modern background restrictions the process can be reclaimed
      and the connection dies with it. This is the most likely cause of "the app just stops
      responding" reports.
- [ ] Two races in the resume path, flagged in PR #13 review, not fixed there because both predate
      that PR and are independent of its Doze-detection change:
      `ActiveHandler.contextResumed()` → `forceReconnect()` → `ResilentConnector.stopConnector()` →
      `threadHandler.join()` (`core/ResilentConnector.java:241`, unbounded — see also the `join(1000)`
      at `:47`) runs on the UI thread, so a slow `checkAddress()`/`connect()` in the old thread can
      block resume long enough to ANR. Separately, `java.util.Timer` catches up on missed ticks once
      the process thaws, so a `StopConnectorTask` deferred by Doze (`ActiveHandler.java`'s
      `StopConnectorTask.run()`) can fire just after resume and stop a connection
      `contextResumed()` just rebuilt; `cancelCurrentTask()` only helps if it wins that race.

## Time bomb, no fuse length known

- [x] **Apache HTTP** in `http/AVRHTTPClient`, `http/Series08Reader` and `core/display/NetDisplay`
      (26 `org.apache.http` imports across the three). Replaced by `http/HTTPSupport` on
      `HttpURLConnection`; `useLibrary` and the manifest `<uses-library>` are gone, so the app no
      longer depends on Google keeping the optional platform library around.

## Housekeeping

- [ ] **Gradle 10 syntax**: `./gradlew --warning-mode all` flags exactly two spots —
      `namespace "…"` and `abortOnError false` need `=` assignment. Two characters.
- [ ] `versionCode` (124) and `versionName` (1.5.1) still sit in the manifest unchanged and must be
      raised before any release. The release workflow triggers on a `v*` tag and needs the
      `KEY_JKS`, `KEY_PASSWORD`, `KEY_ALIAS` and `STORE_PASSWORD` secrets.
- [ ] **12 manual `try { … } finally { close(); }` blocks left** in `core/Connector`,
      `core/MacroManager`, `core/RenameService`, `scan/AVRTargetTester`, the three
      `http/Series08*Parser` and `log/FeedbackReporter`. try-with-resources is the
      convention now (see CLAUDE.md); `http/HTTPSupport` is the reference. Worth converting in
      passing rather than as its own sweep — `log/SDLogger` came for free with the FileProvider
      work above, `log/FeedbackReporter` was not touched there.
- [ ] `misc/add-copyright.sh` still uses the pre-Gradle path (`../src/**/*.java` instead of
      `app/src/main/...`), so it currently matches nothing.
- [ ] `misc/createicons.sh` looks obsolete and should probably just be deleted: it writes 46 plain
      red placeholder squares (`convert -size 32x32 canvas:red …`) to `res/...` relative to the
      **current** directory. Run from `misc/` or the repo root it only creates a stray `res/` tree;
      run from `app/src/main` it would overwrite the real icons with red squares. `misc/mkicons.sh`
      is the maintained script and already uses the Gradle path (`TGT=../app/src/main/res`).
- [ ] **Play-Store icon still shows the old 2010 raster.** The app icon is a vector now
      (`res/drawable/ic_launcher_foreground.xml` plus the adaptive icon in `mipmap-anydpi-v26/`),
      but the store listing needs a 512×512 PNG uploaded by hand in the Play Console — and it must
      be **opaque**, so the transparent adaptive background has to be flattened onto the black
      chassis for that export. Neither `misc/mkicons.sh` nor anything else in-tree can do it: there
      is no rasterizer set up locally. `res/drawable/icon_small.png` (32×32) is the last leftover of
      the old icon and is referenced nowhere.
- [ ] Lint reports 48 unused resources and 30 missing German translations.
      `res/values-v14/dimension.xml` holds a single `widget_margin`, a left-over override of
      `res/values/dimension.xml:27` from an app widget that no longer exists; lint also flags the
      whole `-v14` qualifier as pointless at minSdk 24.
- [ ] `allowBackup="true"` without `dataExtractionRules`. Not a bug — the API 31 default backs
      everything up, including receiver IPs in the SharedPreferences — but an explicit rule would be
      cleaner. Note this got wider when the log moved to `getExternalFilesDir(null)`: that directory
      is inside the default full-backup scope, the old shared-external `AVRRemote/` was not, so with
      logging enabled the log files and `avrremote.zip` — which contain the same receiver IPs, via
      `AVRSettings.getAll()` — now travel with the backup too.
- [ ] Dead version check: `StatusbarManager.java:50` gates on `SDK_INT >= JELLY_BEAN`, always true
      at minSdk 24 (lint: `ObsoleteSdkInt`). The `if` wraps lines 51–74; the `Context`/`Intent`/
      `PendingIntent` setup above it stays.

## Large, no deadline

- [ ] **UI modernisation**: AndroidX/AppCompat, `TabActivity` / `ListActivity` /
      `ExpandableListActivity` / `PreferenceActivity` → Fragments and `androidx.preference`,
      `AsyncTask` (5 files) → executors, `ProgressDialog` (4 files) → inline progress,
      `startActivityForResult` → Activity Result API, 12× `new Handler()` → `Handler(Looper)`.
      All deprecated, all still compiles. Worth doing only when the look is to change — at which
      point it is unavoidable, because the pre-Holo platform themes do not allow modern styling.
- [ ] The custom-background picker stores the picked URI (`AVRSettings.java:92`, listener registered
      at `:62`) without `takePersistableUriPermission()`, so it does not survive a restart. Note the
      fix is not just an added call: the picker uses `ACTION_PICK`, and persistable permissions need
      `ACTION_OPEN_DOCUMENT`. Part of the same Activity-Result-API rewrite.
- [ ] `OnScreenDisplayActivity` was never exercised during the SDK 36 verification — reaching it
      needs receiver display data. Worth a manual pass on a real receiver.

## Open question

- [ ] The README links to the **Google Play Store**, but the release workflow builds an APK for
      GitHub Releases. If the app is still maintained on Play, check whether an App Bundle is now
      required there instead of an APK.
