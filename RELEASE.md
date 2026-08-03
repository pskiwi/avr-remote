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
block is skipped entirely and the build produces `app-release-unsigned.apk` — silently, on a local
build. If a release APK cannot be installed, this is why. In CI the same situation is loud rather
than silent, but for an unhelpful reason: the *Rename APK* step moves `app-release.apk`, which does
not exist, and the job fails there instead of at signing.

| Source | Where |
| --- | --- |
| CI | Repository secrets `KEY_JKS` (base64 of the JKS), `KEY_PASSWORD`, `KEY_ALIAS`, `STORE_PASSWORD`. The workflow decodes `KEY_JKS` to `key.jks` and hardcodes `STORE_FILE=../key.jks`, relative to the `app` module. |
| Local | `~/keystore.properties` with `keyAlias` / `keyPassword` / `storeFile` / `storePassword`, or the same four values as `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` environment variables. |

The keystore itself is not in the repository and must never be. `misc/genkey.sh` is the script it
was presumably created with, and its alias is `pskiwi` — but that is the script's default, not proof:
the alias CI actually signs with comes from the `KEY_ALIAS` secret and cannot be read from here.
`apksigner verify --print-certs` on a released APK is the way to find out for real.

> **The keystore is a single point of failure.** Verify this in the Play Console rather than trusting
> it: as of the 1.6.0 release the app is *not* enrolled in Play App Signing, which means Google holds
> no copy. Lose the keystore and the app can never be updated again — only republished under a new
> package name, losing every install, rating and review. Keep an offline backup, or enrol in Play App
> Signing (uploading the existing key keeps everything else unchanged).

## Steps

1. **Raise the version.** `versionCode` and `versionName` live in
   `app/src/main/AndroidManifest.xml` and nowhere else — there is no `versionCode` in
   `app/build.gradle`. Both must be raised; Play rejects a `versionCode` that is not higher than the
   last one.
2. **Add release notes** to `app/src/main/assets/whatsnew.html`, as a new `<b>version</b>` block at
   the top of the list. `AboutActivity` renders this file, and `AVRRemote` opens it automatically on
   the first start after an update — but only once a receiver is configured, the check at
   `AVRRemote.java:118` is `getConnectionConfig().isDefined() && AVRSettings.isShowChangeLog(this)`.
   `isShowChangeLog()` compares `packageInfo.versionCode` against the stored `AVRLastVersionCode`
   preference, so **an unchanged `versionCode` means nobody ever sees the notes**.

   **Credit every entry** with the GitHub login of whoever made the change, in trailing parentheses:
   `(pskiwi)`, or `(#13/netmindz)` when there is a pull request or issue to point at. Get the names
   from `git log --format='%h %an <%ae> %s' <last-tag>..HEAD --no-merges` and map the addresses to
   logins with `gh pr view <n> --json author`. Do **not** take the login from the merge commit
   subject: PR #9 reads "Merge pull request #9 from EMATech/master", but `EMATech` is the
   organisation that owns the fork — the author is `rdoursenaud`.

   Write for users, not for the diff. Two kinds of entry are easy to leave out and both matter more
   than any feature:

   - **A raised `minSdk` strands devices.** 1.6.0 went from `minSdk 8` to 24, so everything below
     Android 7.0 stays on 1.5.1 forever. Say so in the first bullet.
   - **Rewrites of the receiver-facing code**, even when nothing is meant to change for the user.
     The 1.6.0 replacement of Apache HttpClient could only be tested against the hardware that was
     available; a user on an untested model needs to know that a missing input name is worth
     reporting.
3. **Tick the items** in [TODO.md](TODO.md) that this release closes.
4. **Commit, tag, push.** The tag convention is `v<versionName>`:

   ```sh
   git tag v1.6.0
   git push origin master --tags
   ```

5. **Watch the build**, then fetch the APK and verify the signature before handing it to anyone:

   ```sh
   gh run watch
   gh release download v1.6.0
   $ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs avr-remote-v1.6.0.apk
   ```

6. **Upload to the Play Console** by hand — see below.

## Play Console

### Every release

- [ ] Upload the APK from the GitHub Release to a production release.
- [ ] Paste the release notes (the `whatsnew.html` block, as plain text).
- [ ] Check that no declaration on the *App content* page has gone red since last time. An
      outstanding declaration blocks **all** changes, including store-listing edits.
- [ ] If `minSdk` was raised, note that the device catalogue shrinks accordingly and the affected
      users silently stop receiving updates.
- [ ] **Still outstanding from 1.6.0:** replace the store-listing icon with
      `misc/play-store-icon-512.png`. The listing still shows the raster icon from 2010; the adaptive
      icon shipped in the APK is not used for the listing. Regenerate the PNG from
      `misc/play-store-icon.svg` if the artwork changes — the command is a comment at the top of that
      file, and the artwork has **four** copies in the tree that have to move together
      (`res/drawable/ic_launcher_foreground.xml`, `app/src/main/assets/icon.svg`, `docs/avr-icon.svg`
      and the store SVG).

### Deadlines

Everything below was checked in August 2026 and none of it is under our control, so re-read the
sources rather than this list — Play policy moves and this file will not.

- **Target API level.** Play requires API 35 today and **API 36 from 31 August 2026** for new apps
  and updates. The app is at `targetSdk 36`, so it clears both. The next step, API 37, brings
  mandatory Local Network Protection, which affects the subnet scan and the receiver sockets — see
  [TODO.md](TODO.md) → *Next platform deadline: targetSdk 37*. Expect that deadline around
  August 2027; Google has not announced it yet.
  ([requirements](https://support.google.com/googleplay/android-developer/answer/11926878),
  [local network permission](https://developer.android.com/privacy-and-security/local-network-permission))
- **Package name registration — 30 September 2026.** Part of the Android developer verification
  programme. Google auto-registered nearly all existing Play apps in March 2026; confirm on the Play
  Console home page that `de.pskiwi.avrremote` is listed as registered. Unregistered apps face
  removal, starting regionally in late 2026.
  ([details](https://support.google.com/googleplay/android-developer/answer/16984799))
- **Data safety form.** Mandatory, must stay accurate. The app collects nothing and requests only
  `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `POST_NOTIFICATIONS`.
  ([details](https://support.google.com/googleplay/android-developer/answer/10787469))
- **Content rating (IARC).** Since the policy update of 15 July 2026 unrated apps are not allowed on
  Play. Fill in the questionnaire if it has lapsed.
  ([announcement](https://support.google.com/googleplay/android-developer/answer/17134731))

### APK or App Bundle?

Apps that were on Play before **August 2021** may keep shipping APKs; the App Bundle requirement
applies to apps published since then (and, since June 2023, to Android TV updates). AVR-Remote
predates the cutoff, which is why the workflow still produces an APK.

If the Console ever refuses the APK, switching is not a one-line change: an AAB requires enrolling
in **Play App Signing**, which is irreversible. Add `bundleRelease` to the workflow, publish
`app/build/outputs/bundle/release/app-release.aab`, and upload the existing keystore to Google so
that existing installs keep updating.
