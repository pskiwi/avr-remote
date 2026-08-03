# Releasing

How a version of AVR-Remote gets from `master` to users. Written down after the 1.6.0 release,
because none of it was recorded anywhere and most of it is not guessable from the repository.

## What the automation does — and what it does not

`.github/workflows/releasebuild.yml` triggers on a tag matching `v*`. It builds with JDK 17, signs
the release APK from the repository secrets, renames it to `avr-remote-<tag>.apk` and attaches it to
a **GitHub Release**. A tag containing `rc` produces a pre-release.

**There is no Play Store automation.** No fastlane, no gradle-play-publisher, no service account —
the Play upload has always been done by hand in the Play Console, and still has to be. The GitHub
Release is where the APK comes from; the Console is where it goes.

## Signing

The `signingConfigs` block in `app/build.gradle` is **conditional**. It is only created when either
`~/keystore.properties` is readable or `KEY_PASSWORD` is set in the environment. With neither, the
block is skipped entirely and the build produces `app-release-unsigned.apk` — silently. If a release
APK cannot be installed, this is why.

| Source | Where |
| --- | --- |
| CI | Repository secrets `KEY_JKS` (base64 of the JKS), `KEY_PASSWORD`, `KEY_ALIAS`, `STORE_PASSWORD`. The workflow decodes `KEY_JKS` to `key.jks` and hardcodes `STORE_FILE=../key.jks`, relative to the `app` module. |
| Local | `~/keystore.properties` with `keyAlias` / `keyPassword` / `storeFile` / `storePassword`, or the same four values as `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` environment variables. |

The keystore was created with `misc/genkey.sh`; the key alias is `pskiwi`. The keystore itself is
not in the repository and must never be.

> **The keystore is a single point of failure.** It is not enrolled in Play App Signing, so Google
> holds no copy. Lose it and the app can never be updated again — only republished under a new
> package name, losing every install, rating and review. Keep an offline backup, or enrol in Play
> App Signing (uploading the existing key keeps everything else unchanged).

## Steps

1. **Raise the version.** `versionCode` and `versionName` live in
   `app/src/main/AndroidManifest.xml` and nowhere else — there is no `versionCode` in
   `app/build.gradle`. Both must be raised; Play rejects a `versionCode` that is not higher than the
   last one.
2. **Add release notes** to `app/src/main/assets/whatsnew.html`, as a new `<b>version</b>` block at
   the top of the list. `AboutActivity` renders this file, and `AVRRemote` opens it automatically on
   the first start after an update. The trigger is `AVRSettings.isShowChangeLog()`, which compares
   `packageInfo.versionCode` against the stored `AVRLastVersionCode` preference — so **an unchanged
   `versionCode` means nobody ever sees the notes**.
3. **Tick the items** in [TODO.md](TODO.md) that this release closes.
4. **Commit, tag, push.** The tag convention is `v<versionName>`:

   ```sh
   git tag v1.6.0
   git push origin master --tags
   ```

5. **Watch the build**: `gh run watch`, then `gh release view v1.6.0`. Verify the signature before
   handing the APK to anyone:

   ```sh
   $ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs avr-remote-v1.6.0.apk
   ```

6. **Upload to the Play Console** by hand — see below.

## Play Console

### Every release

- [ ] Upload the APK from the GitHub Release to a production release.
- [ ] Paste the release notes (the `whatsnew.html` block, as plain text).
- [ ] Check that no declaration on the *App content* page has gone red since last time. An
      outstanding declaration blocks **all** changes, including store-listing edits.

### Deadlines and one-off items

- **Target API level.** Play requires API 35 today and **API 36 from 31 August 2026** for new apps
  and updates. The app is at `targetSdk 36`, so it clears both. The next step, API 37, brings
  mandatory Local Network Protection, which affects the subnet scan and the receiver sockets — see
  [TODO.md](TODO.md) → *Next platform deadline: targetSdk 37*. Expect that deadline around
  August 2027; Google has not announced it yet.
- **Package name registration — 30 September 2026.** Part of the Android developer verification
  programme. Google auto-registered nearly all existing Play apps in March 2026; confirm on the Play
  Console home page that `de.pskiwi.avrremote` is listed as registered. Unregistered apps face
  removal, starting regionally in late 2026.
- **Data safety form.** Mandatory, must stay accurate. The app collects nothing and requests only
  `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `POST_NOTIFICATIONS`.
- **Content rating (IARC).** Since the policy update of 15 July 2026 unrated apps are not allowed on
  Play. Fill in the questionnaire if it has lapsed.
- **Store listing icon.** `misc/play-store-icon-512.png`, regenerated from
  `misc/play-store-icon.svg` (the command is in a comment at the top of that file). It has to be
  uploaded manually; the adaptive icon in the APK is not used for the listing.

### APK or App Bundle?

Apps that were on Play before **August 2021** may keep shipping APKs; the App Bundle requirement
applies to apps published since then (and, since June 2023, to Android TV updates). AVR-Remote
predates the cutoff, which is why the workflow still produces an APK.

If the Console ever refuses the APK, switching is not a one-line change: an AAB requires enrolling
in **Play App Signing**, which is irreversible. Add `bundleRelease` to the workflow, publish
`app/build/outputs/bundle/release/app-release.aab`, and upload the existing keystore to Google so
that existing installs keep updating.
