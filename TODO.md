# TODO

Things worth doing, roughly in the order they will hurt if left alone.
Most of it was found while moving the build to SDK 36 in July 2026; none of it
blocks the current build, which is green.

Finished items are removed rather than ticked — the git history holds what was done and why, and
this file stayed readable only by not accumulating them. What a closed item established and a live
one still depends on has been folded into the live one.

## Android 17: Local Network Protection

`targetSdk` is **37**, so it is in force: without `ACCESS_LOCAL_NETWORK` no socket reaches the local
network, silently and by timeout. The machinery is described in [CONNECTION.md](CONNECTION.md), and
the commit that raised the target carries the survey of all seventeen behaviour changes for apps
targeting Android 17 — only this one touches this app. 1.7.0 is the first release with it and is
out, so the field reports the items below wait for can now arrive; the Play Console side is in
[RELEASE.md](RELEASE.md).

Verified on a Pixel 8 against an AVR-3310 in September 2026, with the permission granted, revoked
and granted again: `reachable` follows it, the assistant explains the block instead of blaming the
address, the scan stops with the same explanation rather than searching for ten seconds, SSDP finds
the receiver among five UPnP responders in 3.7 s, and Wi-Fi off/on drives `StatusFlag.WLAN` and
restarts the reconnect. Two things to keep in mind when reading a log from such a session: the
receiver allows exactly one telnet session, so `ECONNREFUSED` on port 23 says nothing about this
code — `reachable` is the signal that tracks LNP — and `RESTRICT_LOCAL_NETWORK` via `am compat` does
not reach the hints, which are gated on `targetSdkVersion`.

The switch that stops the reconnect loop from dialling into the mobile network was verified the same
way on 2026-09-26, Wi-Fi off with mobile data on. What the log shows: the interfaces are
`wlan0:192.168.10.119/24 rmnet1:10.17.65.161/32`, so the cellular interface is a **/32** and no LAN
address can fall inside it; every round is then skipped with one line
(`… is in no local subnet [rmnet1:10.17.65.161/32] …`) instead of 2.5 s of connect timeout plus a
second of `checkAddress`; a cold start in that state still defines `Connected` through the
`Reachable=false` of the skipped round, so the assistant shows "WLAN ist nicht aktiviert" — that is
the load-bearing half of the guard and it was seen on the device; Wi-Fi returning drives
`triggerReconnect()` within a second rather than waiting out the 16 s backoff; and with the switch on
the old behaviour returns verbatim, `SocketTimeoutException … from /10.17.65.161 … after 2500ms` once
per round. One thing to know when repeating this: the receiver holds its single telnet session for
minutes after the Wi-Fi drops (measured below, in the AVR-3310 item), so the reconnect afterwards
runs into `ECONNREFUSED` and the control-port dialog, which says nothing about any of the above.

What is left is the part no build can answer.

- [ ] **The one case still untested: mobile data on beside a Wi-Fi without internet.** That is what
      the socket binding exists for, and it is the one scenario the test Wi-Fi here cannot produce.
      The September 2026 run had mobile data active beside the Wi-Fi, but that Wi-Fi had internet
      and stayed the default network, so it proves only that the binding does no harm there. Until
      someone runs the real case, the binding is verified in the sense that sockets demonstrably
      leave from the Wi-Fi address, and no further.

- [ ] **The `EPERM` fallback in `LocalNetwork.refusedByVpn()` is derived, not measured.** A
      binding refused by a VPN leaves the socket unbound — see [CONNECTION.md](CONNECTION.md). Two
      things to see on a device: that WireGuard with the LAN excluded connects (it failed on every
      attempt before the fallback), and that the log shows
      `binding refused with EPERM (VPN?) -> sockets stay unbound` once. If the refusal arrives with
      another errno, the fallback does not catch it and the old failure is back — the exception in
      the log then names the errno to test for.

      **1.7.0 ships with this as an accepted risk** (decided 2026-09-27): no VPN setup was available
      to test with. The worst case is a VPN user who reached the receiver with 1.6.1 and no longer
      does, on every attempt, because each bind throws. What that looks like in a report: a
      `SocketException` naming `Binding socket to network` in the log, and a `tun` interface on the
      `IPv4` line. It needs no device to fix once such a log exists — widen `refusedByVpn()` to the
      errno it shows.

- [ ] **The binding follows the most recent Wi-Fi, not the receiver's.** `onAvailable` binds every
      newly available Wi-Fi, and when the bound one is lost while another stays up,
      `LocalNetwork.onLost` binds the most recently available of the rest.
      If that is not the receiver's network (STA+STA, another app's local-only Wi-Fi), `mayConnect()`
      says yes because a Wi-Fi is bound, `bind()` leaves the socket unbound because the receiver is
      outside its prefix, and every round spends the connect timeout on the default network — where
      before the fallback `StatusFlag.WLAN` went false and the assistant pointed at the Wi-Fi.
      Preferring the network whose prefix holds the configured receiver would settle it, but
      `LocalNetwork` does not know the receiver today. The `onAvailable` half was seen on a Pixel 8
      on 2026-10-10: after a Wi-Fi off/on the phone joined another saved network (192.168.178.x)
      first, the binding moved there, the connect attempts left from the mobile address, and the
      assistant offered the IP dialog — the Wi-Fi the receiver is in had not come back at all, so
      there was no better choice to make. The fallback half is not in any log yet:
      `lost, falling back to another` followed by timeouts would be the case.
- [ ] **Auto-revoke with a stale asked marker.** The marker is only cleared when
      `requestLocalNetworkPermission()` sees the permission granted. Refused once, granted later in
      the settings while the app keeps running, then left unused until the revoke — every process
      that reaches `AVRRemote` asks again in its `onCreate`, so the marker is only stale if no such start
      fell between the grant and the revoke. If that revoke also resets
      the rationale flag, the app takes it for a final refusal and never asks. The hint's button
      still leads out. Which flags the disuse revoke sets is unmeasured.
- [ ] **The idle probe `PW?` is only measured on an AVR-3310.** A model that leaves it unanswered
      in some state would see a healthy connection torn down and rebuilt about every 70 s — churn,
      not a hang. A log full of `no answer to the probe` from an otherwise working setup is the sign.

- [ ] **The model suggestion is only confirmed on an AVR-3310.** Setup now scans first and then
      proposes the model the receiver names in its UPnP description (`models/ModelSuggestion`,
      called from `scan/AVRScanner`, confirmed by the user in `ConfigurationAssistant`). The match
      ignores dashes, case, spaces and the `(experimental)` remark — nothing else, on purpose.
      Whether other generations report `<modelName>` the way the list spells it is unknown: a log
      line `no list entry for reported model [...]` from a supported receiver is the case to fold
      into the matcher, not to guess at beforehand. The models from `avr-remote-specs` bring
      `aliases` (`AVR-X2700H DAB`, `AVC-X3700H`) that belong in the match; the models themselves are
      generated now (the classes marked "Originally generated" in `models/`), their aliases not yet.
      An existing configuration is never checked against the description — no hint when the
      hourly fetch in `StatusAreaManager` reports a different model than the one selected.

      `<presentationURL>` is worth taking as well, because it settles a guess made elsewhere:
      `menu/OptionsMenu.java:249` opens the receiver's web UI by probing the configured page and
      treating **only** a 404 as proof that it is missing, then falling back to the bare base URL —
      a heuristic whose confirmation is still outstanding with the reporter of the AVR-1912. On the
      AVR-3310 `presentationURL` is exactly that bare base URL, which corroborates the fallback for
      this model. Fetching it would replace the probe entirely.
- [ ] **Point a user once to a better-fitting model.** Until the generated models arrived, an owner
      of, say, an AVR-X2700H could only pick a neighbour (`AVR-X2000`) or `AVR-Generic`, and that
      choice is still stored. When the receiver's UPnP description (the `ModelSuggestion` match
      above, or the hourly fetch in `StatusAreaManager`) names a list entry other than the selected
      one, say so once and offer to switch — with the `(experimental)` remark visible, since the
      generated classes are unconfirmed. Remember a dismissal per pair (selected, suggested), so
      the hint neither nags nor stays silent after a later update adds yet another match. Depends
      on the matcher above being right for the newer generations; until it is, the hint simply
      never fires for them.
- [ ] The `NOTIFY` branch is untested against a real device in another sense too: `SSDPDiscovery`
      only ever looks at replies to its own `M-SEARCH`. If a receiver turns out to answer nothing
      (no `MulticastLock` — see [CONNECTION.md](CONNECTION.md)), the sweep fallback hides it, and
      the only sign is `SSDP: 0 device(s) answered` in the log.
- [ ] **`Connector.Receiver.run()` logs a read error and tries again, without closing anything**
      (`core/Connector.java:168`). Predates the read watchdog, which only covers the timeout path.
      Only the `IOException` arm is meant here — the `catch (Exception)` beside it is new and does its
      job, keeping an unchecked throw from taking the receive path down.
      An `IOException` that persists — a `Software caused connection abort` on a socket whose
      `isClosed()` is still false — would be thrown again immediately by the next `read()`, so the
      thread spins at full speed, `closeSignal` never counts down, and `ResilentConnector` stays
      parked in `waitUntilClosed()` with no reconnect behind it. Not observed: in the Doze log in
      [CONNECTION.md](CONNECTION.md) the next `read()` returned end-of-stream 3 ms later and the
      thread left through the normal path. Closing on any `IOException` is the obvious fix and is
      exactly what the surrounding comments decided against, so it needs a case first — a captured
      log with the same error repeating would be one.
      Seen once more on a Pixel 8 on 2026-10-10, and again harmless: Wi-Fi switched off with the app
      in the background, Android aborted the Wi-Fi-bound socket about 100 ms after `WiFi lost`
      (`read failed … Software caused connection abort`), the next read returned end-of-stream and
      the thread left through the normal path. The same run settles a side question: a lost Wi-Fi
      needs no explicit reconnect from `wifiChanged()`, the system kills the bound socket itself.

- [ ] **Two cases behind the "Use mobile network" switch that no hardware here can produce.** The
      switch and the address test behind it (`LocalNetwork.mayConnect(String)`, the guard at the top
      of `ResilentConnector.Reconnector.run()`) were verified on a Pixel 8 against an AVR-3310 on
      2026-09-26 — see the paragraph above. What that run could not touch: a receiver reachable only
      from outside, over a VPN or a forwarded port, which is what the switch exists for and which
      nobody has ever reported using; and a device whose local network arrives over **Ethernet** or
      its own hotspot.

      That second case has to clear **two** gates, and both are unverified. The address test lets it
      through because the receiver is in a subnet the device is attached to, and `bind()` leaves the
      socket unbound because the target is not in the Wi-Fi's own prefix — the latter was the whole
      point of that fix, since binding to the Wi-Fi would have pinned such a socket to the wrong
      routing table. Both answer themselves in the field: every feedback report carries the `IPv4`
      line, every log the startup line `LocalNetwork: interfaces …`. A report showing `eth0:…`
      settles whether such devices are out there at all.
- [ ] **The AVR-3310 holds its telnet session far longer than assumed.** Measured on 2026-10-10:
      after the process was killed by a permission revoke (the phone's side closed, nothing on port
      23 in `ss -tn`), port 23 answered `ECONNREFUSED` for more than 4.5 minutes; after a Wi-Fi
      off/on, where the phone also came back with a new address, again for minutes. The app's
      standby/on reset freed it once and failed the second time; only unplugging helps reliably, as
      the assistant's dialog says. Nothing in the app can fix the receiver, but it bounds what a
      test of any reconnect path can show on this model: every scenario that ends a session without
      a clean `close()` from a live process is followed by minutes of a busy control port.

## Structural

- [ ] **Test coverage is fifteen JVM classes and no instrumentation tests.** The cheapest places to add
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
      A user asked in October 2026 for the connection to be kept while the app is in the
      background, so that switching back needs no reconnect. A Service is the only way to do that:
      on a Pixel 8 with Android 17 the system cuts a background app's network about six seconds
      after `onPause` (`blocked=APP_BACKGROUND`, measured 2026-10-10, see CONNECTION.md → *Who
      decides when to hang up*), so the disconnect-time setting cannot keep anything alive beyond
      that. Weigh it against the cost: a permanent notification, a foreground-service type Play has
      to accept, and the receiver's single telnet session blocked for every other app and device
      the whole time. The reconnect it would save took about 1 s in the same measurement.
- [ ] **`AVRTargetTester.PING_TIMEOUT` (250 ms) may be too short for a phone waking from standby.**
      Measured once in August 2026: right after waking, the ping failed entirely, so `checkAddress()`
      reported "not reachable" for a receiver that was plainly there — see
      [CONNECTION.md](CONNECTION.md) → *What Doze does*. A repeat on 2026-10-11 did **not** reproduce
      it (33 ms on battery right after unlocking), so the value stays until a report shows the
      failure. Every ping now logs its duration; `ping failed /… after 25x ms` lines right after an
      `activity resumed` in a report from a working setup are the case. If it comes: the scan and
      the connection path share the constant (SSDP candidates go through `testAddress()` too), so
      raise it only for `ConnectionConfiguration.checkAddress()`. Separately: that ping is the one
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
- [ ] **The icon artwork exists in four copies that have to move together**:
      `res/drawable/ic_launcher_foreground.xml` (plus its API 24/25 twin in `mipmap-anydpi/`),
      `assets/icon.svg`, `docs/avr-icon.svg` (byte-identical to the latter, feeds the GitHub Pages
      site) and `misc/play-store-icon.svg`. Deduplicating them would be worth more than the fourth
      copy costs, but nothing in the build can generate one from another — `magick`, `rsvg-convert`
      and `inkscape` are all absent; the 512×512 Play Store export was rendered with macOS's
      `qlmanage`, command recorded in a comment at the top of the SVG.
- [ ] `LocalNetwork.getNetwork()` has no callers. It predates the binding helpers — everything that
      needs the `Network` now goes through `bind()` or `openConnection()`, which hold it themselves.
      Left in place rather than deleted, like the rest of the dead code in this section.
- [ ] **`ScreenInfo` measures the window, not the display.** The class builds its diagonal from
      `getDefaultDisplay().getMetrics()` (`ScreenInfo.java:28-33`), and every caller hands it an
      Activity — so in multi-window the numbers describe the activity's window, which is exactly why
      the API was deprecated in API 30 in favour of `WindowMetrics.getBounds()`. Nothing depends on
      the value any more: `isTablet()` and its 4.5-inch threshold are gone, and what is left only
      reaches the OSD log line and `FeedbackReporter.java:209-217`. So this is a diagnostics-quality
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

## Generated models: what the documents offer and the app does not

The generated classes in `models/` (see CLAUDE.md, "Receiver models") cover what
`IAVRModel` can express. The protocol documents behind them describe more — features of the 2014,
2015 and FY20–FY23 generations the app has no code for at all. Counts are how many of the first
26 newly covered models list a feature (the 2015 AVR-X1200W–X4200W came later and are not
counted). None of it is tested on hardware; the documents are all there is.

- [ ] **Simple settings — the cheap ones.** Each is a switch or a short value list, the shape
      `ZoneState` already has for its options: an `OptionType` constant, an `AbstractManualSelect`
      subclass, and one line in the `OPTIONS` table of `generate_java.py` (avr-remote-specs) so the
      model files switch it on per model. Take the values from the documents rather than inventing
      them.

      | Setting | Command | Models |
      |---|---|---|
      | Graphic EQ | `PSGEQ` | 24 |
      | Loudness Management | `PSLOM` | 24 |
      | Subwoofer on/off | `PSSWR` | 24 |
      | Room Size | `PSRSZ` | 24 |
      | Video Processing Mode | `VSVPM` | 19 |
      | Headphone EQ | `PSHEQ` | 17 |
      | Neural:X | `PSNEURAL` | 16 |
      | Speaker Virtualizer | `PSSPV` | 16 |
      | Dialog Control | `PSDIC` | 16 |
      | Picture Mode | `PV` | 14 |
      | Audyssey LFC | `PSLFC`, `PSCNTAMT` | 10 |
      | Dialog Level | `PSCLV` / `PSDIL` | 10 / 7 |
      | Dialog Enhancer | `PSDEH` | 8 |
      | Center Spread | `PSCES` | 8 |
      | IMAX | `PSIMAX…` | 8 |
      | Auro-3D | `PSAUROMODE`, `PSAUROPR`, `PSAUROST` | 6 |
      | Bass Sync | `PSBSC` | 6 |
- [ ] **System settings, same mechanism — but do they belong in a remote?** ECO mode (`ECO`),
      dimmer (`DIM`), auto standby (`STBY`), panel and remote lock (`SYPANEL`, `SYREMOTE`), the menu
      keys OPTION/INFO/channel level (`MNOPT`, `MNINF`, `MNCHL`) and the forced audio input mode
      (`SD`) — all 25; All Zone Stereo (`MNZST`, 24), speaker presets (`SPPR`, 10), Bluetooth
      transmitter (`BTTX`, 10). Decide per item: a lock the remote can switch on but the user cannot
      find again is worse than none.
- [ ] **Option values are hard-coded, their availability no longer is.** The generated classes
      decide *whether* an option shows (`createSupportedOptions`), but its values still come from
      `ZoneState` — `DRCSetting` offers `AUTO LOW MID HI OFF` for every model. Where a document
      lists other values, the menu offers ones the receiver may reject. Taking the values from the
      model files as well is a change to `ZoneState`'s option classes, not to the models.
- [ ] **Channel levels beyond `ZoneState.LevelType`.** It knows 13 channels; 22 more ids in the
      documents are dropped by the generator — height, top, Dolby-enabled and Atmos channels
      (`TFL`/`TFR`/`TML`/`TMR` in 19 models, `RHL`, `TRL`, `FDL`, `SDL`, `BDL`, `SHL`, `TS`, `CH`, …)
      and the extra subwoofers `SW2`–`SW4`. The AVR-A110 shows 13 of its 32. `LevelType` also
      carries one range per kind for all models, while the files state each model's own. Both need
      `LevelType` and `LevelActivity` to stop being a fixed enum.
- [ ] **Per-zone settings.** Zones 2 and 3 get power, volume, mute, input and quick select through
      `Zone.getCommandPrefix`. Not: sleep (`Z2SLP`, 23), auto standby (`Z2STBY`, 23), high-pass
      filter (`Z2HPF`, 11), stereo/mono (`Z2CS`, 11), HDMI audio (`Z2HDA`, 11).
- [ ] **DAB on the FY20+ receivers** (X2600H, X2700H, X2800H, DRA-800H). Not a missing command but a
      second dialect — `TMANDAB`, `TPAN`/`TFAN` for stations, `DAODR` for the order, `DA ?` for the
      status — against the `TMDA` one `TunerDisplay` speaks for the AVR-4308 and the Marantz
      players. Laid out in `APP_MAPPING.md` (avr-remote-specs). The generated classes leave
      `supportsDAB()` at `false`; it needs a receiver to test against.
- [ ] **No network display on the H models.** From FY20 on the documents list no `NS` display
      commands (HEOS took over); only the 2014 W/S models have them. The generated H classes say
      `hasPgUpDown() == false`, but nothing stops the app from opening the network display on `NET`
      and waiting for lines that never come. Check on a device what it shows before deciding
      whether `NETWORK` needs a model switch of its own.
- [ ] **RDS station name** (`TFANNAME?`, 16) is not queried; the tuner display shows the frequency
      only.
- [ ] **DRA-800H: bass and treble without tone control.** Its document lists `PSBAS`/`PSTRE` but
      no `PSTONE CTRL`, so `DRA800H` offers the two levels without the `ToneControl` option. On
      the receivers that have it, bass and treble only act while tone control is on; whether the
      DRA-800H needs it at all is unknown. Check on a device before adding or removing anything.
- [ ] **Front height and wide levels stop at +12 dB — confirm on a device.** Since #62 the level
      dialog, presets and replayed macros limit `CVFHL`/`FHR`/`FWL`/`FWR` to 38–62 like every other
      channel (they went to 99 before). Unit-tested only; check on a receiver with height or wide
      speakers that the slider ends at +12 dB and that a subwoofer switched off (`CVSW 00`) stays
      off after Cancel in the level dialog.
- [ ] **Smart Select on the Marantz receivers.** Marantz calls quick select Smart Select and its
      document has only that: `MSSMART1`–`5`, `… MEMORY`, `Z2SMART`/`Z3SMART`, on the 2013–2015
      models (12 of 29); the older ones have neither. `QuickMenu` always sends
      `<zone>QUICK<n>`, so the generated Marantz classes say `hasQuick() == false` — while the
      hand-written ones keep the `AbstractModel` default `true` and send a command their
      document does not list. Needs the quick prefix as a model method (`QUICK` / `SMART`), then
      `hasQuick` for the twelve and `false` for the older hand-written ones, checked on a device.
- [ ] **At the next release: the seven new models.** AVR-X7200W and the 2018 AVR-X3500H, X2500H,
      X1500H, S940H, S740H, S640H (#61) go into `ReleasedModelNamesTest` with the release that
      ships them (RELEASE.md step 3), and into the release notes together with the level limit
      (#62). The same for the 29 Marantz names: AV7702, AV7702mkII, AV8802, NR1506, NR1605,
      NR1606, SR5009, SR5010, SR6009, SR6010, SR7009, SR7010 and the seventeen `-G` variants.

## Large, no deadline

- [ ] **UI modernisation**: AndroidX/AppCompat, `TabActivity` / `ListActivity` /
      `ExpandableListActivity` / `PreferenceActivity` → Fragments and `androidx.preference`,
      `AsyncTask` (5 files) → executors, `ProgressDialog` (4 files) → inline progress,
      `startActivityForResult` → Activity Result API, 12× `new Handler()` → `Handler(Looper)`.
      All deprecated, all still compiles. Worth doing only when the look is to change — at which
      point it is unavoidable, because the pre-Holo platform themes do not allow modern styling.
- [ ] **A model list one can find a receiver in.** `@array/modelNames` is one flat
      `ListPreference` (`res/xml/settings.xml:17`) of 128 entries — Denon, Marantz and the network
      players interleaved, hand-written and generated mixed, `(experimental)` as plain text. Ideas:
      grouped by brand (and perhaps by generation); a filter field that narrows the list with
      every keystroke to the matching models only (ignoring dashes and case, as the suggestion
      match does, so "x27" finds the AVR-X2700H); the model the scan suggests on top;
      `(experimental)` as a marker rather than part of the name. The stored value must stay
      the list string it is today: `ModelConfigurator.update()` rewrites it to the current
      spelling and `ReleasedModelNamesTest` holds every released name, so only the presentation
      changes. Possible as a custom dialog now; easier once preferences move to
      `androidx.preference` (item above).
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
      | 16 | `Configuration.ORIENTATION_SQUARE` | 1 | `AVRRemote.java:429` |
      | 22 | `Resources.getDrawable()` | 8 | `AVRTheme`, `IconManager`, `OnScreenDisplayActivity` |
      | 23 | `AlertDialog.Builder.setInverseBackgroundForced()` | 6 | 4 files |
      | 29/30 | `android.preference.*` (31× `PreferenceManager`), `AsyncTask`, `TabHost`, `ListActivity`, `ExpandableListActivity`, 12× `new Handler()` | 61 | this item |

      `TabActivity` is the oldest thing in the app by a wide margin — deprecated in Android 3.2, so
      **15 years**. It cannot be picked off on its own: it and the API 29/30 block are the same
      AppCompat migration, which is why the age does not translate into urgency.
- [ ] The one entry in that table that is a genuine decision rather than a rename:
      `Configuration.ORIENTATION_SQUARE` (`AVRRemote.java:429`) is **not** dead code. The app
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
