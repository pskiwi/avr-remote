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
      `AVRApplication.java:63`. Both deprecated (API 31 / 29) → `NetworkCallback` and
      `NetworkCapabilities`.

## Structural

- [ ] **There are no tests at all.** Highest-value first test, because it covers a failure mode the
      compiler cannot see: `models/ModelConfigurator` resolves the 67 receiver classes **by
      reflection** from a preference string (`"AVR-3310"` → `AVR3310`). Rename a class or let an
      entry in `res/values/lists.xml` drift and there is no build error — the app silently falls
      back to `AVRGeneric` and the user just misses features. A JVM unit test that runs all 130
      entries of `@array/modelNames` through the same normalisation and instantiates them catches
      exactly that.
- [ ] After that, `models/` (pure capability logic) and `core/ZoneState.java` (1237 lines) are the
      cheapest places to add coverage.
- [ ] **The receiver connection lives on a daemon thread owned by `AVRApplication`, not a Service.**
      A design decision from 2010. Under modern background restrictions the process can be reclaimed
      and the connection dies with it. This is the most likely cause of "the app just stops
      responding" reports.

## Time bomb, no fuse length known

- [ ] **Apache HTTP** in `http/AVRHTTPClient`, `http/Series08Reader` and `core/display/NetDisplay`
      (49 references). Works today: `org.apache.http.legacy.jar` still ships with the android-36
      platform, and since only cleartext HTTP on the LAN is spoken, the ancient TLS stack does not
      matter. The risk is purely that Google drops the optional library from some future platform,
      turning this into unplanned work. It is only GET/POST with form bodies — `HttpURLConnection`
      would do.

## Housekeeping

- [ ] **Gradle 10 syntax**: `./gradlew --warning-mode all` flags exactly two spots —
      `namespace "…"` and `abortOnError false` need `=` assignment. Two characters.
- [ ] `versionCode` (124) and `versionName` (1.5.1) still sit in the manifest unchanged and must be
      raised before any release. The release workflow triggers on a `v*` tag and needs the
      `KEY_JKS`, `KEY_PASSWORD`, `KEY_ALIAS` and `STORE_PASSWORD` secrets.
- [ ] `misc/add-copyright.sh` and `misc/createicons.sh` still use pre-Gradle paths (`../src/...`
      instead of `app/src/main/...`). `createicons.sh` overwrites icons, so running it from the
      wrong directory is destructive.
- [ ] Lint reports 48 unused resources and 30 missing German translations.
      `res/values-v14/dimension.xml` holds a single `widget_margin` left over from an app widget
      that no longer exists.
- [ ] `allowBackup="true"` without `dataExtractionRules`. Not a bug — the API 31 default backs
      everything up, including receiver IPs in the SharedPreferences — but an explicit rule would be
      cleaner.
- [ ] Dead version check: `StatusbarManager.java:50` gates on `SDK_INT >= JELLY_BEAN`, always true
      at minSdk 24. The `if` wraps the whole method body.

## Large, no deadline

- [ ] **UI modernisation**: AndroidX/AppCompat, `TabActivity` / `ListActivity` /
      `ExpandableListActivity` / `PreferenceActivity` → Fragments and `androidx.preference`,
      `AsyncTask` (5 files) → executors, `ProgressDialog` (4 files) → inline progress,
      `startActivityForResult` → Activity Result API, 12× `new Handler()` → `Handler(Looper)`.
      All deprecated, all still compiles. Worth doing only when the look is to change — at which
      point it is unavoidable, because the pre-Holo platform themes do not allow modern styling.
- [ ] The custom-background picker (`AVRSettings.java:62`) stores the picked URI without
      `takePersistableUriPermission()`, so it does not survive a restart. Part of the same
      Activity-Result-API rewrite.
- [ ] `OnScreenDisplayActivity` was never exercised during the SDK 36 verification — reaching it
      needs receiver display data. Worth a manual pass on a real receiver.

## Open question

- [ ] The README links to the **Google Play Store**, but the release workflow builds an APK for
      GitHub Releases. If the app is still maintained on Play, check whether an App Bundle is now
      required there instead of an APK.
