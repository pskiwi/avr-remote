# TODO

Things worth doing, roughly in the order they will hurt if left alone.
Everything here was found while moving the build to SDK 36 in July 2026; none of it
blocks the current build, which is green.

## Broken today

- [ ] **Sending logs does not work.** `SDLogger.getLogURI()` returns a `file://` URI,
      `log/FeedbackReporter.java:93` puts it into `Intent.EXTRA_STREAM` → `FileUriExposedException`
      since targetSdk 24. This is the app's own support channel ("Feedback" in the options menu).
      Fix: add a `FileProvider` and hand out a content URI.
- [ ] **`SDLogger` writes to `Environment.getExternalStorageDirectory()`**, which fails under
      scoped storage (Android 10+). Move to `getExternalFilesDir(null)`.
- [ ] Once both are done, drop `WRITE_EXTERNAL_STORAGE` from the manifest — it has been a no-op
      since Android 11. (Until then it should at least carry `android:maxSdkVersion="28"`.)

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

- [ ] **Test coverage is two files.** `http/HTTPSupportTest` and `http/AVRXMLInfoParserTest` (added
      with the Apache removal) set up `src/test` and the JUnit dependency; everything else is still
      uncovered. The highest-value test to add next is one for `models/ModelConfigurator`, because it
      covers a failure mode the compiler cannot see: it resolves the 60 receiver classes **by
      reflection** from a preference string (`"AVR-3310"` → `AVR3310`). Rename a class or let an
      entry in `res/values/lists.xml` drift and there is no build error — the app silently falls
      back to `AVRGeneric` and the user just misses features. A JVM unit test that runs all 60
      entries of `@array/modelNames` through the same normalisation and instantiates them catches
      exactly that. Both sides hold 60 entries today, so the test starts green.
- [ ] After that, `models/` (pure capability logic) and `core/ZoneState.java` (1237 lines) are the
      cheapest places to add coverage.
- [ ] **`http/AVRXMLInfoParser` only works on Android and cannot be unit-tested.** `startElement` and
      `endElement` read `localName`, which a standard `SAXParserFactory` leaves empty because it is
      not namespace-aware by default — on a JVM the parser silently collects nothing. Android's
      Expat-based SAX fills `localName` regardless, which is the only reason the scraping path works.
      Falling back to `qName` when `localName` is empty would make the parser portable and testable.
      `core/RenameService.java:109` has the same pattern and would need the same fix; the
      `Series08*Parser` classes are unaffected, they read with `BufferedReader` and regexes.
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
- [ ] `misc/add-copyright.sh` still uses the pre-Gradle path (`../src/**/*.java` instead of
      `app/src/main/...`), so it currently matches nothing.
- [ ] `misc/createicons.sh` looks obsolete and should probably just be deleted: it writes 46 plain
      red placeholder squares (`convert -size 32x32 canvas:red …`) to `res/...` relative to the
      **current** directory. Run from `misc/` or the repo root it only creates a stray `res/` tree;
      run from `app/src/main` it would overwrite the real icons with red squares. `misc/mkicons.sh`
      is the maintained script and already uses the Gradle path (`TGT=../app/src/main/res`).
- [ ] Lint reports 48 unused resources and 30 missing German translations.
      `res/values-v14/dimension.xml` holds a single `widget_margin`, a left-over override of
      `res/values/dimension.xml:27` from an app widget that no longer exists; lint also flags the
      whole `-v14` qualifier as pointless at minSdk 24.
- [ ] `allowBackup="true"` without `dataExtractionRules`. Not a bug — the API 31 default backs
      everything up, including receiver IPs in the SharedPreferences — but an explicit rule would be
      cleaner.
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
