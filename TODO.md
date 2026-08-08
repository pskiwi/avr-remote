# TODO

Things worth doing, roughly in the order they will hurt if left alone.
Most of it was found while moving the build to SDK 36 in July 2026; none of it
blocks the current build, which is green.

Finished items are removed rather than ticked — the git history holds what was done and why, and
this file stayed readable only by not accumulating them. What a closed item established and a live
one still depends on has been folded into the live one.

## Next platform deadline: targetSdk 37

- [ ] **Local Network Protection becomes mandatory** for apps targeting Android 17. It affects the
      core of this app: the subnet sweep in `scan/AVRScanner` and the raw sockets to the receiver.
      Needs the new local-network runtime permission and a rationale UI. Background:
      [CONNECTION.md](CONNECTION.md).
- [ ] Same area, do it in one pass: everything network-facing in `scan/WiFiInfo` and
      `AVRApplication` is still on legacy API.
      - `WifiManager.getDhcpInfo()` gives netmask and local IP for the sweep. Deprecated since API
        31 → `LinkProperties.getLinkAddresses()` and `getRoutes()`, via
        `ConnectivityManager.getLinkProperties(Network)`.
      - The trigger is a `WifiManager` broadcast where a `NetworkCallback` belongs.
      - `WiFiInfo.isWiFiConnected()` asks `NetworkCapabilities.hasTransport(TRANSPORT_WIFI)` about
        the active network first and then about every network, via `getAllNetworks()` — itself
        deprecated since API 31. The two-step is deliberate and any rewrite has to keep the
        property: a Wi-Fi without internet (dead WAN, captive portal, an isolated AV network) stays
        connected alongside cellular without being the default network, and `AVRScanner.java:117`
        refuses the whole scan on a `false`. Checking only the active network was tried and is
        wrong for exactly that case.

      A callback holding the current Wi-Fi `Network` replaces all three at once — and hands the
      scan and the sockets a `Network` to bind to, which is what Local Network Protection needs
      anyway. That is why this is one item and not three.
- [ ] Watch for another NPE in `AVRApplication$1.onReceive`. The one that came in from the field
      (1.5.1, Pixel 8 Pro, Android 17 **Beta**) was `getNetworkInfo(TYPE_WIFI)` returning null, and
      it is fixed; but the null could not be reproduced on a Pixel 8 with the finished Android 17 —
      not across three Wi-Fi off/on cycles, not in airplane mode, not with Data Saver on a metered
      Wi-Fi with the app backgrounded. So the trigger is still unknown, and the working theory is
      that it was beta-only. A second report would disprove that and mean something else is wrong.

## Structural

- [ ] **Test coverage is eight JVM classes and no instrumentation tests.** The cheapest places to add
      more are `models/` (pure capability logic, no Android types) and `core/ZoneState.java`
      (1237 lines). `core/display/` is now half done: `NetDisplayTest` covers the line reader, but
      `TunerDisplay` (1059 lines) and `BDDisplay` have nothing. Both *do* construct on a JVM —
      `TunerDisplay.createFM(null, null)` and `new BDDisplay(null)` were tried and work — so the
      obstacle is only that nobody has written the cases. `TunerDisplay.TunerFrequency` is the
      obvious start: `convertFrequency` splits FM from AM at 50000, which the 2007 protocol paper
      words as "(>050000 is AM.)" — note it puts exactly 050000 on the AM side where the paper reads
      as FM, an edge the tuner never actually sits on.
- [ ] **`http/AVRXMLInfoParser` only works on Android and cannot be unit-tested.** `startElement` and
      `endElement` read `localName`, which a standard `SAXParserFactory` leaves empty because it is
      not namespace-aware by default — on a JVM the parser silently collects nothing. Android's
      Expat-based SAX fills `localName` regardless, which is the only reason the scraping path works.
      Falling back to `qName` when `localName` is empty would make the parser portable and testable.
      `core/RenameService.java:109` has the same pattern and would need the same fix; the
      `Series08*Parser` classes are unaffected, they read with `BufferedReader` and regexes.
      Whoever writes that test will also trip over the XXE hardening in the same parser: on a plain
      JVM it now rejects **any** XML carrying a DOCTYPE, because `disallow-doctype-decl` applies
      there and on Android it does not (see CLAUDE.md). Receivers never send one, so nothing breaks
      in the app.
- [ ] **The 2008-series path is only half covered by real data.** Two pages of an AVR-3808 are now
      captured under `app/src/test/resources/de/pskiwi/avrremote/http/`, and `Series08ParserTest`
      parses them verbatim — that settles `Series08InputParser` (including its empty-page guard) and
      `Series08ZoneRenameParser`. What is left has no capture and no test:
      `http/Series08QuickSelectParser`, whose `d_option1.asp` was never recorded and which is still
      pinned only by cases derived from the code, and `http/Series08Reader` — the cookie store it
      clears per run, and the `r_option1.asp` → `d_option1.asp` sequence that depends on shared
      session state, neither reachable without HTTP. One run against a 2008-series receiver settles
      both: check that quick-select names appear at all. A capture of `d_option1.asp` would settle
      the parser half on its own.
- [ ] Same area, cookie lifetime: the store is cleared per Series08 read
      (`Series08Reader.readSeries08Info`), whereas the old `DefaultHttpClient` was per
      `AVRHTTPClient` instance, so it also covered the multi-zone path. Exact parity would clear it
      in the `AVRHTTPClient` constructor. Every receiver tested sends no `Set-Cookie` at all, so
      this only matters if a 2008-series device turns out to use sessions.
- [ ] **`NetDisplay.doHTTPMove()` and `doHTTPSeries08Move()` are unreachable.**
      `AbstractModel:135` returns `DisplayMoveMode.Classic` and not one of the 60 model classes
      overrides `getDisplayMoveMode()`, so the `switch` in `ScreenMover` always takes the `default`
      branch. Both methods (and `DisplayMoveMode`'s other two constants) are dead. Left in place
      during the Apache removal — decide whether the feature was meant to be wired up or should go.
- [ ] **The receiver connection lives on a daemon thread owned by `AVRApplication`, not a Service.**
      A design decision from 2010. Under modern background restrictions the process can be reclaimed
      and the connection dies with it. Note that the one "app does not reconnect after standby"
      report we have a log for was *not* this — the process had survived; it was a stale-teardown
      race between `ActiveHandler.contextResumed()` and the reconnect thread, fixed since. How the
      loop works today: [CONNECTION.md](CONNECTION.md).
- [ ] **`EnableManager.setStatus()` is an unsynchronised read-modify-write.** It copies
      `connectionStatus`, mutates it through the deliberate `switch` fallthrough, compares and fires
      the listeners (`EnableManager.java:109`). Callers include the UI thread, the
      `StopConnector-Timer` thread and one or more reconnect threads at the same time. Two
      overlapping calls can lose a flag or fire listeners with a half-built status, which surfaces
      as buttons that stay greyed out until the next status change repairs them. Cheaper to fix than
      it looks: `fireListener()` only copies the status and `Handler.post()`s it, so a `synchronized`
      on `setStatus()` would cover a few field writes and a post, never the UI fanout itself.
- [ ] **`AVRTargetTester.PING_TIMEOUT` is 250 ms, which a phone waking from standby cannot meet.**
      Wi-Fi power save puts the receiver out of reach for the first moments after the user picks the
      phone up, so `checkAddress()` reports "not reachable" for a device that is plainly there — the
      measurement and what it costs the user is in [CONNECTION.md](CONNECTION.md) → *What Doze does*.
      This may well be part of what users report as "does not reconnect after standby". Worth
      measuring before changing: `scan/AVRScanner` uses the same constant for its subnet sweep, and
      raising it there costs scan time, so the two uses probably want separate values.

## Housekeeping

- [ ] Lint reports 47 unused resources and 30 missing German translations. One of the unused ones is
      `widget_button_width` in `res/values/dimension.xml`, left over from the app widget that no
      longer exists — its neighbour `widget_margin` and the whole `res/values-v14/` override are
      already gone.
- [ ] `allowBackup="true"` without `dataExtractionRules`. Not a bug — the API 31 default backs
      everything up, including receiver IPs in the SharedPreferences — but an explicit rule would be
      cleaner. Note this got wider when the log moved to `getExternalFilesDir(null)`: that directory
      is inside the default full-backup scope, the old shared-external `AVRRemote/` was not, so with
      logging enabled the log files and `avrremote.zip` — which contain the same receiver IPs, via
      `AVRSettings.getAll()` — now travel with the backup too.
- [ ] `StatusbarManager.createNotificationChannel` runs on every call rather than once. Harmless
      (creating an existing channel id only updates the name; importance can only be lowered and the
      sound is ignored after creation), but it belongs in `AVRApplication.onCreate`.
- [ ] **The icon artwork exists in four copies that have to move together**:
      `res/drawable/ic_launcher_foreground.xml` (plus its API 24/25 twin in `mipmap-anydpi/`),
      `assets/icon.svg`, `docs/avr-icon.svg` (byte-identical to the latter, feeds the GitHub Pages
      site) and `misc/play-store-icon.svg`. Deduplicating them would be worth more than the fourth
      copy costs, but nothing in the build can generate one from another — `magick`, `rsvg-convert`
      and `inkscape` are all absent; the 512×512 Play Store export was rendered with macOS's
      `qlmanage`, command recorded in a comment at the top of the SVG.
      `res/drawable/icon_small.png` (32×32) is the last leftover of the old 2010 icon and is
      referenced nowhere.
- [ ] `misc/file-copyright.txt` is referenced by nothing since `misc/add-copyright.sh` was deleted.
- [ ] **`ScreenInfo` measures the window, not the display.** The class builds its diagonal from
      `getDefaultDisplay().getMetrics()` (`ScreenInfo.java:28-33`), and every caller hands it an
      Activity — so in multi-window the numbers describe the activity's window, which is exactly why
      the API was deprecated in API 30 in favour of `WindowMetrics.getBounds()`. Nothing depends on
      the value any more: `isTablet()` and its 4.5-inch threshold are gone, and what is left only
      reaches the OSD log line and `FeedbackReporter.java:169-176`. So this is a diagnostics-quality
      item, not a behaviour one — but a feedback report from a split-screen session understates the
      device, which is worth knowing before trusting one.
      For the record on that threshold: it was **not** unreachable, as the commit removing the
      orientation lock claimed. Small phones above minSdk 24 exist — the Unihertz Jelly 2 is 3.0",
      the Palm PVG100 3.3" — and on those the lock fired and `ScreenListAdapter`'s 12/15/21sp ladder
      ran. Moving the size to `@dimen/osd_row_text_size` therefore does change something there:
      those devices go to 22sp in a row whose height is fixed. Rare enough to accept, not rare
      enough to have called impossible.
- [ ] Still unexercised in `OnScreenDisplayActivity`, which was otherwise verified in August 2026 on
      a Pixel 8 against a real receiver: the transport buttons and the search paths
      (`btnPlay`/`btnPause`/`btnStop`, `screenMenu.doSearch()`), and the whole of `NetDisplay`'s
      parsing beyond what that one receiver happened to send.

## Large, no deadline

- [ ] **UI modernisation**: AndroidX/AppCompat, `TabActivity` / `ListActivity` /
      `ExpandableListActivity` / `PreferenceActivity` → Fragments and `androidx.preference`,
      `AsyncTask` (5 files) → executors, `ProgressDialog` (4 files) → inline progress,
      `startActivityForResult` → Activity Result API, 12× `new Handler()` → `Handler(Looper)`.
      All deprecated, all still compiles. Worth doing only when the look is to change — at which
      point it is unavoidable, because the pre-Holo platform themes do not allow modern styling.
- [ ] **What is actually deprecated, by age.** Measured rather than guessed, in August 2026:
      `./gradlew compileDebugJavaWithJavac --rerun-tasks` with `-Xlint:deprecation` **and
      `-Xmaxwarns 5000`** — javac stops at 100 by default, which silently hides the tail (that is
      how the `scan/WiFiInfo` entries went missing on the first run). Feed the warnings through
      `$ANDROID_HOME/platforms/android-36/data/api-versions.xml`, which carries a `deprecated=`
      attribute per class, method and field, to get the level each one died in. Constructors are
      `<init>` there, and a warning naming an inherited method resolves against the declaring class.

      | API | Symbol | Uses | Where |
      |---|---|---|---|
      | 13 (2011) | `TabActivity` + its base `ActivityGroup` | 28 | `AVRRemote`, `AboutActivity` |
      | 15 | `PreferenceActivity.findPreference()` / `getPreferenceScreen()` / `addPreferencesFromResource()` | 7 | `AVRSettings`, `PreferenceSummaryUpdater` |
      | 15 | `Display.getWidth()` / `getHeight()` | 4 | `AVRRemote` |
      | 15 | `LayoutParams.FILL_PARENT` | 3 | `LevelActivity`, `ScreenMenu` |
      | 16 | `Configuration.ORIENTATION_SQUARE` | 1 | `AVRRemote.java:357` |
      | 22 | `Resources.getDrawable()` | 8 | `AVRTheme`, `IconManager`, `OnScreenDisplayActivity` |
      | 23 | `AlertDialog.Builder.setInverseBackgroundForced()` | 6 | 4 files |
      | 29/30 | `android.preference.*` (31× `PreferenceManager`), `AsyncTask`, `TabHost`, `ListActivity`, `ExpandableListActivity`, 12× `new Handler()` | 61 | this item |
      | 31 | `WifiManager.getDhcpInfo()`, `ConnectivityManager.getAllNetworks()` | 4 | `scan/WiFiInfo` — the targetSdk 37 item |

      `TabActivity` is the oldest thing in the app by a wide margin — deprecated in Android 3.2, so
      **15 years**. It cannot be picked off on its own: it and the API 29/30 block are the same
      AppCompat migration, which is why the age does not translate into urgency.
- [ ] The one entry in that table that is a genuine decision rather than a rename:
      `Configuration.ORIENTATION_SQUARE` (`AVRRemote.java:357`) is **not** dead code. The app
      computes the orientation itself from display width and height and returns that constant, so
      the branch can still fire on a square window. It is the *constant* that is obsolete — the
      platform has not reported it since Android 4.1 — so replacing it means deciding what a square
      window should mean here.
- [ ] The custom-background picker stores the picked URI (`AVRSettings.java:92`, listener registered
      at `:62`) without `takePersistableUriPermission()`, so it does not survive a restart. Note the
      fix is not just an added call: the picker uses `ACTION_PICK`, and persistable permissions need
      `ACTION_OPEN_DOCUMENT`. Part of the same Activity-Result-API rewrite.
