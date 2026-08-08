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
- [x] **`ConnectivityManager.getNetworkInfo(TYPE_WIFI)` returned null and killed the process.**
      Play Console, 1.5.1 (versionCode 124), Pixel 8 Pro on Android 17 **Beta**: NPE in
      `AVRApplication$1.onReceive` → `RuntimeException` out of `LoadedApk$ReceiverDispatcher`, which
      takes the process with it. The lines were unchanged since the initial commit, so 1.6.0 was
      affected identically. **The null itself did not reproduce** on a Pixel 8 with the finished
      Android 17 (SDK 37) — not across three Wi-Fi off/on cycles, not in airplane mode, and not with
      Data Saver on a metered Wi-Fi with the app in the background; the deprecated call returned an
      object every time. So the triggering condition is still unknown: either a state those runs did
      not hit, or beta behaviour that did not ship. Fixed anyway — the API is documented to be
      allowed to return null, and nothing here may dereference it blindly.
      Both call sites now go through `WiFiInfo.isWiFiConnected(ConnectivityManager)`, which asks
      `NetworkCapabilities.hasTransport(TRANSPORT_WIFI)` — about the active network first, then
      about every network. The question is unchanged from the old API; only the answer can no
      longer be null. Checking *only* the active network was the first attempt and is wrong: a
      Wi-Fi without internet (dead WAN, captive portal, an isolated AV network) stays connected
      alongside cellular without being the default, and `AVRScanner.java:117` refuses the whole
      scan on a `false`. `getAllNetworks()` is itself deprecated (API 31), which is why it is the
      fallback rather than the whole answer — it goes when the `NetworkCallback` item below lands.

## Next platform deadline: targetSdk 37

- [ ] **Local Network Protection becomes mandatory** for apps targeting Android 17. It affects the
      core of this app: the subnet sweep in `scan/AVRScanner` and the raw sockets to the receiver.
      Needs the new local-network runtime permission and a rationale UI. Background:
      [CONNECTION.md](CONNECTION.md).
- [ ] Same area, do it in one pass: `scan/WiFiInfo` still takes netmask and local IP for the sweep
      from `WifiManager.getDhcpInfo()`, deprecated since API 31 → `LinkProperties.getLinkAddresses()`
      and `getRoutes()`, via `ConnectivityManager.getLinkProperties(Network)`. The
      `getNetworkInfo(TYPE_WIFI)` half of this item is done, ahead of the deadline and not by choice
      — it was crashing in the field, see *Broken today*. Two things there are still legacy and want
      the same pass: the trigger is a `WifiManager` broadcast where a `NetworkCallback` belongs, and
      the fallback in `isWiFiConnected` uses `getAllNetworks()`, deprecated since API 31 as well. A
      callback holding the current Wi-Fi `Network` would replace all three at once — and would hand
      the scan and the sockets a `Network` to bind to, which is what the Local Network Protection
      item above needs anyway.

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
      and the connection dies with it. Note that the one "app does not reconnect after standby"
      report we have a log for was *not* this — the process had survived; it was the stale-teardown
      race in the item below. How the loop works today: [CONNECTION.md](CONNECTION.md).
- [x] `ActiveHandler.contextResumed()` → `forceReconnect()` → `ResilentConnector.stopConnector()`
      runs on the UI thread and waited there for the old reconnect thread — a full second whenever
      that thread sat in `checkAddress()`, because neither `InetAddress.isReachable()` nor the
      `testPort()` connects react to `interrupt()`. The wait bought nothing either: it timed out and
      the code carried on with the thread still alive. `ThreadHandler.stop()` now interrupts and
      returns. What the `join` *did* cover by accident was a detached thread that came back inside
      that second: its stale writes then landed before `closeCurrentConnection()`, which cleaned up
      after them. So the same change had to make `Reconnector.run()` consult `generation` before
      every write to shared state, and `publishConnector()` makes that check atomic against the
      cleanup — a check-then-assign only narrows the window. `core/ThreadHandlerTest` pins the
      timing (the case fails at ~1000 ms against the old implementation) but reaches none of that
      second half. Flagged in PR #13 review, predates that PR.
- [x] Teardown deferred by Doze stops the connection right after resume, leaving no reconnect loop
      at all until the app is killed and restarted. The `ACTION_SCREEN_OFF` receiver in
      `AVRApplication` is gone — `ActiveHandler` is now the only owner of the disconnect policy, so
      the connection drops after the user's auto-disconnect timeout instead of immediately on
      screen-off. On the timer path `StopConnectorTask.run()` skips the stop when an activity is
      active again (`cancelCurrentTask()` only wins that race sometimes) and, because that check is
      not atomic against a resume landing right after it, reconnects itself if one did.
- [ ] **`ResilentConnector.connectionConfig` is a plain field read across threads.** Written on the
      UI thread in `reconfigure()` and `forceReconnect()`, read by every reconnect thread — the
      `checkAddress()` calls and half the log lines in `Reconnector.run()`. Without `volatile` there
      is no guarantee a running thread ever sees a new address, so after an IP change the old thread
      can keep dialling the old receiver until it exits on its own. Never observed; the threads are
      short-lived and `stopConnector()` interrupts them anyway, which is probably why it has never
      surfaced. One keyword to fix. Left alone in the PR that removed the `join`, because that PR
      had no business touching it — but note the same PR made concurrent readers *certain* rather
      than occasional, so the odds moved.
- [ ] **`EnableManager.setStatus()` is an unsynchronised read-modify-write.** It copies
      `connectionStatus`, mutates it through the deliberate `switch` fallthrough, compares and fires
      the listeners (`EnableManager.java:109`). Callers now include the UI thread, the
      `StopConnector-Timer` thread and one or more reconnect threads at the same time. Two
      overlapping calls can lose a flag or fire listeners with a half-built status, which surfaces
      as buttons that stay greyed out until the next status change repairs them. Cheaper to fix than
      it looks: `fireListener()` only copies the status and `Handler.post()`s it, so a `synchronized`
      on `setStatus()` would cover a few field writes and a post, never the UI fanout itself.
- [ ] **`EnableManager.setStatus()` forgets `Zone4` when it clears.** The `Power` case of the
      remove-fallthrough resets `Zone1`, `Zone2` and `Zone3` and stops there
      (`EnableManager.java:131-136`), but `Zone4` is a real flag with a real zone behind it
      (`core/Zone.java:25`). On a four-zone receiver its controls therefore stay enabled after the
      connection drops, while zones 1–3 grey out — so it is visibly inconsistent, not just
      theoretical. One line, but check first whether any four-zone model is actually reachable in
      `@array/modelNames` before calling it user-visible.
- [ ] `ResilentConnector.java:159` logs `Reconnector:Reconnector:connection to [...] closed` — the
      prefix is doubled. Cosmetic, but anyone grepping a log against
      [CONNECTION.md](CONNECTION.md) trips over it.
- [ ] **`AVRTargetTester.PING_TIMEOUT` is 250 ms, which a phone waking from standby cannot meet.**
      Wi-Fi power save puts the receiver out of reach for the first moments after the user picks the
      phone up, so `checkAddress()` reports "not reachable" for a device that is plainly there — the
      measurement and what it costs the user is in [CONNECTION.md](CONNECTION.md) → *What Doze does*.
      This may well be part of what users report as "does not reconnect after standby". Worth
      measuring before changing: `scan/AVRScanner` uses the same constant for its subnet sweep, and
      raising it there costs scan time, so the two uses probably want separate values.

## Time bomb, no fuse length known

- [x] **Apache HTTP** in `http/AVRHTTPClient`, `http/Series08Reader` and `core/display/NetDisplay`
      (26 `org.apache.http` imports across the three). Replaced by `http/HTTPSupport` on
      `HttpURLConnection`; `useLibrary` and the manifest `<uses-library>` are gone, so the app no
      longer depends on Google keeping the optional platform library around.

## Housekeeping

- [x] **Gradle 10 syntax**: `./gradlew --warning-mode all` flags exactly two spots —
      `namespace "…"` and `abortOnError false` need `=` assignment. Two characters.
- [x] `versionCode` and `versionName` sat in the manifest unchanged since Nov 2020 and had to be
      raised before any release. Now 125 / 1.6.0, with a matching block in `assets/whatsnew.html` —
      without the `versionCode` bump the release notes never open, `AVRSettings.isShowChangeLog()`
      compares it against the `AVRLastVersionCode` preference. The whole procedure is written down
      in [RELEASE.md](RELEASE.md) now.
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
- [x] **Play-Store icon still showed the old 2010 raster.** The 512×512 export now exists as
      `misc/play-store-icon-512.png`, rendered from `misc/play-store-icon.svg` — the same artwork as
      `assets/icon.svg` plus a black `<rect>` covering the full viewBox, so the corners the round
      chassis leaves open are filled rather than transparent. The claim that nothing in-tree can
      rasterize was half right: `magick`, `rsvg-convert` and `inkscape` are all absent, but macOS
      ships `qlmanage`, and `qlmanage -t -s 512` renders the SVG faithfully (command recorded in a
      comment at the top of the SVG). The artwork now has **four** copies that have to move together
      — `res/drawable/ic_launcher_foreground.xml` (plus its API 24/25 twin in `mipmap-anydpi/`),
      `assets/icon.svg`, `docs/avr-icon.svg` (byte-identical to the latter, feeds the GitHub Pages
      site) and `misc/play-store-icon.svg`. Deduplicating them would be worth more than the fourth
      copy costs, but nothing in the build can generate one from another.
      **Still open: uploading it in the Play Console**, which cannot be automated — it is tracked as
      a checkbox in [RELEASE.md](RELEASE.md) instead. `res/drawable/icon_small.png` (32×32) is the
      last leftover of the old icon and is referenced nowhere.
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
- [x] Dead version check in `StatusbarManager.updateNotification()`: a `SDK_INT >= JELLY_BEAN`
      gate, always true at minSdk 24 (lint: `ObsoleteSdkInt`). Removed, and worth recording why it
      was more than clutter — the `if` had no `else`, so had it ever been false the method would
      have posted no notification at all and said nothing. The inner `>= O` check stays; API 26 is
      above minSdk, so the notification channel really is conditional. Lint's remaining
      `ObsoleteSdkInt` is `res/values-v14`, a folder qualifier rather than a version check, and is
      the item above.
- [ ] Two things noticed in `StatusbarManager` while doing that, both pre-existing and both left
      alone: the field `private Notification notification;` is never read or written — the only
      notification in play is the local in `updateNotification()` — and `createNotificationChannel`
      runs on every call rather than once. The latter is harmless (creating an existing channel id
      only updates the name; importance can only be lowered and the sound is ignored after
      creation), but it belongs in `AVRApplication.onCreate`.
- [ ] **`ScreenInfo` measures the window, not the display.** The class builds its diagonal from
      `getDefaultDisplay().getMetrics()` (`ScreenInfo.java:28-33`), and every caller hands it an
      Activity — so in multi-window the numbers describe the activity's window, which is exactly why
      the API was deprecated in API 30 in favour of `WindowMetrics.getBounds()`. Nothing depends on
      the value any more: `isTablet()` and its 4.5-inch threshold are gone, and what is left only
      reaches the OSD log line and `FeedbackReporter.java:169-176`. So this is a diagnostics-quality
      item, not a behaviour one — but a feedback report from a split-screen session currently
      overstates nothing and understates the device, which is worth knowing before trusting one.
      For the record on that threshold: it was **not** unreachable, as the commit removing the
      orientation lock claimed. Small phones above minSdk 24 exist — the Unihertz Jelly 2 is 3.0",
      the Palm PVG100 3.3" — and on those the lock fired and `ScreenListAdapter`'s 12/15/21sp ladder
      ran. Moving the size to `@dimen/osd_row_text_size` therefore does change something there:
      those devices go to 22sp in a row whose height is fixed. Rare enough to accept, not rare
      enough to have called impossible.

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
- [x] `OnScreenDisplayActivity` was never exercised during the SDK 36 verification — reaching it
      needs receiver display data. Done in August 2026 on a Pixel 8 (Android 17) against a real
      receiver, with display lines actually populated: the list renders, the row text is the
      expected size, and the screen rotates cleanly now that the portrait lock is gone — including
      landscape, which falls back to the portrait layout because the OSD layouts have no
      `layout-land` variant. Still unexercised there: the transport buttons and the search paths
      (`btnPlay`/`btnPause`/`btnStop`, `screenMenu.doSearch()`), and the whole of `NetDisplay`'s
      parsing beyond what that one receiver happened to send.

## Answered

- [x] The README links to the **Google Play Store**, but the release workflow builds an APK for
      GitHub Releases. Answered: the App Bundle requirement applies to apps published **since
      August 2021**; apps that were already on Play may keep shipping APKs, so the workflow does not
      have to change. The Play upload was never automated in the first place — the `v*` tag only
      creates a GitHub Release, and the Console step is manual. Switching to an AAB would force
      enrolment in Play App Signing, which is irreversible, so it is worth doing only if the Console
      actually refuses the APK. Details, deadlines and the Console checklist: [RELEASE.md](RELEASE.md).
