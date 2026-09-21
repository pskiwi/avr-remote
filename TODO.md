# TODO

Things worth doing, roughly in the order they will hurt if left alone.
Most of it was found while moving the build to SDK 36 in July 2026; none of it
blocks the current build, which is green.

Finished items are removed rather than ticked — the git history holds what was done and why, and
this file stayed readable only by not accumulating them. What a closed item established and a live
one still depends on has been folded into the live one.

## Next platform deadline: targetSdk 37

The code side is done — `scan/LocalNetwork`, the socket binding, the permission and SSDP discovery
are in, `compileSdk` is 37, and [CONNECTION.md](CONNECTION.md) describes the result. `targetSdk` is
still 36, so none of it is in force. What is left is the part no build can answer.

- [ ] **Raise `targetSdk` to 37, in a commit of its own.** Everything below has now been run on
      a Pixel 8 against an AVR-3310, including against a real `targetSdk 37` build, so what is left
      is the commit itself and a look at the Play Console rollout.

      Verified with a `targetSdk 37` build installed: permission granted → `reachable true` and the
      app behaves normally; revoked → `reachable false` and the ConfigurationAssistant explains why
      instead of blaming the address. The scan refuses with the same explanation rather than
      searching for ten seconds. SSDP found the receiver among five UPnP responders in 3.5 s
      (`found 1 receiver(s) via SSDP`), and Wi-Fi off/on drove `StatusFlag.WLAN` and restarted the
      reconnect (`WiFi lost` → `WLAN = false`, `WiFi available` → `WLAN = true` →
      `Reconnector:start new connector`).

      Two things that run through it are worth keeping in mind when reading a log from that
      session: the receiver allows exactly one telnet session and had it occupied for most of the
      day, so `ECONNREFUSED` on port 23 there says nothing about this code — `reachable` is the
      signal that tracks Local Network Protection. And `RESTRICT_LOCAL_NETWORK` via `am compat`
      does **not** reach the hints, which are gated on `targetSdkVersion`; only a real 37 build
      does.

- [ ] **The one case still untested: mobile data on beside a Wi-Fi without internet.** That is what
      the socket binding exists for, and it is the one scenario the test Wi-Fi here cannot produce.
      Until someone runs it, the binding is verified only in the sense that sockets demonstrably
      leave from the Wi-Fi address.

- [ ] Before that commit, know what else `targetSdk 37` switches on. All seventeen behaviour
      changes for apps targeting Android 17 were checked against this code in September 2026, and
      **only Local Network Protection applies**. The rest, so nobody has to re-derive it:

      | Change | Why it misses this app |
      |---|---|
      | RemoteViews memory limit | no `RemoteViews` anywhere; the app widget is long gone |
      | Lock-free `MessageQueue`, `static final` no longer writable | no `setAccessible`/`getDeclaredField` — the only reflection is `Class.forName` in `ModelConfigurator` |
      | ECH, Certificate Transparency | the app opens no TLS connection at all; the one `https://` outside the GPL headers is a URL handed to the browser (`menu/OptionsMenu.java:193`) |
      | Safer native DCL | no native libraries |
      | Background audio hardening | no `AudioManager`, no `MediaSession` — volume goes to the receiver over telnet |
      | Orientation/resizability on large screens | no `screenOrientation` in the manifest, so there is no constraint to ignore |
      | Passwords hidden on physical keyboards | no password fields |
      | CP2 PII columns, CP2 strict SQL, OTP SMS, `setContentCaptureEnabled`, accessibility IME, `BluetoothSocket.read()` | those APIs are not used |

      One is a judgement rather than a grep: **BAL hardening**. There is a single `PendingIntent`
      (`StatusbarManager.java:45`, notification → activity, `FLAG_IMMUTABLE`), launched by the user
      tapping the notification, and the two `startActivity` calls outside an Activity
      (`ScreenMenu.java:311`, `core/display/NetDisplay.java:1107`) go through an Activity reference
      from a foreground interaction. No background launch — but that is reasoning, not a test.

      A trial build with `targetSdk 37` is clean and removes exactly one lint entry,
      `OldTargetApi`. **That proves very little:** AGP 8.13.0 is tested only to compile SDK 36.1,
      so checks for 37's behaviour changes do not exist in this lint at all. Its silence is not a
      pass. Changes that apply to *all* apps on Android 17 regardless of target are a different
      matter — those are already in force, and the app ran normally on the Pixel 8 under Android 17.
- [ ] **Select the model from `description.xml` instead of asking the user.** The description is
      now fetched and lands in the feedback report (`http/DeviceDescription`, see
      [CONNECTION.md](CONNECTION.md)); what is not done is acting on it. `<modelName>` is on an
      AVR-3310 the exact string `@array/modelNames` holds and `ModelConfigurator` resolves by
      reflection, dashes and all — `DeviceDescriptionTest.modelNameMatchesTheModelList()` pins that.
      `<manufacturer>` would separate Denon from Marantz. Today the user picks from a list of 60.

      **Wait for field reports before building it.** Whether `modelName` matches the list verbatim
      on other generations is unknown — only an AVR-3310 was available — and so is whether port 8080
      carries the description at all on older or newer models. Both now show up in every feedback
      report as the `UPnP` line, so the data arrives by itself. A report reading `not available`
      answers the port question; one whose `modelName` is absent from `@array/modelNames` answers
      the other.

      `<presentationURL>` is worth taking at the same time, because it settles a guess made
      elsewhere: `menu/OptionsMenu.java:240` opens the receiver's web UI by probing the configured
      page and treating **only** a 404 as proof that it is missing, then falling back to the bare
      base URL — a heuristic whose confirmation is still outstanding with the reporter of the
      AVR-1912. On the AVR-3310 `presentationURL` is exactly that bare base URL, which corroborates
      the fallback for this model. Fetching it would replace the probe entirely.
- [ ] **A failed fetch of `description.xml` erases what an earlier one found.**
      `StatusAreaManager.loadXMLStatus()` calls `setDeviceDescription(...)` with whatever
      `DeviceDescription.read()` returned, and that is null both on an `IOException` and on an
      unrecognised body; `ModelConfigurator.setDeviceDescription` (`:167`) overwrites either way.
      So an hourly read that succeeds while the receiver is on, followed by one that fails while it
      is in standby, leaves the feedback report saying `not available` for a device that was
      identified an hour earlier. Keeping the last good value is two lines — but only half the
      answer: the value must then be cleared when the user switches receivers, or the report will
      name the wrong device. Both halves or neither.
- [ ] **Started from the options menu, the permission detour ends in silence.**
      `menu/OptionsMenu.java:105`/`:112` hand `AVRScanner.scanIP` an empty `Runnable`, and since the
      scan stops as soon as it has opened the permission dialog (`scan/AVRScanner.java:344`), the
      user taps *Scan*, answers the system prompt and sees nothing at all — no progress dialog, no
      message, no hint that the search has to be started again. From the `ConfigurationAssistant`
      the path is sound, its `runFinished` clears `visible`. A toast would need a new string in both
      languages; re-triggering the scan from `onRequestPermissionsResult` is the better answer and
      the larger one, since `OnScreenDisplayActivity` — the other caller — does not override it.
- [ ] The `NOTIFY` branch is untested against a real device in another sense too: `SSDPDiscovery`
      only ever looks at replies to its own `M-SEARCH`. If a receiver turns out to answer nothing
      (no `MulticastLock` — see [CONNECTION.md](CONNECTION.md)), the sweep fallback hides it, and
      the only sign is `SSDP: 0 device(s) answered` in the log.
- [ ] The NPE in `AVRApplication$1.onReceive` (1.5.1, Pixel 8 Pro, Android 17 **Beta**) can no
      longer happen: the `BroadcastReceiver` is gone with the `WifiManager` broadcast, and so is the
      crash site. The cause was never found — it was `getNetworkInfo(TYPE_WIFI)` returning null, and
      that null was not reproducible on a Pixel 8 with the finished Android 17 (three Wi-Fi off/on
      cycles, airplane mode, Data Saver on a metered Wi-Fi in the background). Closed by removal, not
      by understanding; worth knowing if something similar turns up in `LocalNetwork`.

## Structural

- [ ] **Test coverage is eleven JVM classes and no instrumentation tests.** The cheapest places to add
      more are `models/` (pure capability logic, no Android types) and `core/ZoneState.java`
      (1237 lines). `core/display/` is now part done: `NetDisplayTest` covers the line reader and
      `TunerDisplayTest` the frequency conversion, but the rest of `TunerDisplay` (presets, HD Radio,
      DAB, the status lines) and all of `BDDisplay` have nothing. Both construct on a JVM
      (`TunerDisplay.createFM(null, null)`, `new BDDisplay(null)`), but only `TunerDisplay` can be
      driven from a test as it stands: its `TunerFrequency` is a public inner class, so
      `outer.new TunerFrequency()` works. **`BDDisplay` cannot** — `BDDisplayStatus` is
      `private final static` (`BDDisplay.java:38`) and `BDState` is `private final`
      (`BDDisplay.java:192`), and `getState()` hands back only the `IAVRState` interface. Testing it
      needs the same widening as `ResilentConnector.ThreadHandler` and
      `ModelConfigurator.createModel(String)`: make the nested class package-private, and say in a
      comment that the test is why.
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
      measuring before changing: the same constant serves the subnet sweep, and raising it there
      costs scan time, so the two uses probably want separate values. The sweep matters less than it
      did — it only runs when SSDP found nothing — but the constant is also on the path of every
      SSDP candidate, since those go through `testAddress()` too. Separately: that ping is the one
      socket operation that cannot be bound to the Wi-Fi at all, so it may be measuring the wrong
      interface; see [CONNECTION.md](CONNECTION.md).

## Housekeeping

- [ ] Lint reports 47 unused resources and 30 missing German translations. Four of them are gone:
      `disconnected`, `disconnectedReachable`, `connected` and `poweron` were removed once it turned
      out nothing referenced them — the status bar draws from drawables, see the entry below. One of the unused ones is
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
      `$ANDROID_HOME/platforms/android-37.2/data/api-versions.xml`, which carries a `deprecated=`
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
- [ ] **The status bar paints itself from three drawables, not from `colors.xml`.**
      `StatusAreaManager` switches between `connected_power.xml` (`#30a030`),
      `connected_poweroff.xml` (`#a03030`) and `connection_problem.xml` (`#a0a0a0`), each a
      gradient with the colour written into it. So the states of the one element every user watches
      are the only colours in the app that are not named anywhere — worth pulling into `colors.xml`
      next time that area is touched, together with the greens: `#30a030` there has nothing to do
      with the green of the pre-Holo checkmark next to it in `LevelActivity`.
- [ ] Three smaller leftovers from the same pass, none of them broken, all of them arguable:
      the checkbox in `LevelActivity` is still the green pre-Holo drawable while the radio buttons
      in dialogs are tinted with the accent — `android:buttonTint` in a `checkboxStyle` would align
      them, at the price of the established "green = on"; the dialog title carries the same red as
      the dialog's text buttons and is separated from them only by size and position, where
      `textStyle bold` in `AVRDialogTitle` would help; and `drawable/edit_text_avr.xml` has no
      `state_enabled="false"` variant, which the platform nine-patch it replaced did have.
- [ ] **`LevelActivity.java:210` adds a divider that is zero pixels high.** It is an empty `View`
      with `WRAP_CONTENT` and a background colour, so it measures to nothing and has never been
      visible — not in the light theme it was written for and not in the dark one the screen uses
      now. Either give it a height (`1dp`) and a colour from `colors.xml`, or drop it and the
      `0xFFCCCCCC` with it.
