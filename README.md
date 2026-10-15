# <img src="app/src/main/assets/icon.svg" width="64" height="64" alt="" align="absmiddle"> AVR-Remote 

Android remote control for Denon and Marantz receivers.
[Google Play Store Link](https://play.google.com/store/apps/details?id=de.pskiwi.avrremote)


This application is not affiliated with Denon or Marantz. 
Denon and Marantz are trademarks of their respective owners. 

![Check build](https://github.com/pskiwi/avr-remote/workflows/Check%20build/badge.svg)

Runs on Android 7.0 (API 24) and newer.

## Supported models

Picked in the app under *Settings → Model Settings → Model*. The choice decides which features are
offered (number of zones, quick-select, level controls, DAB, …), so it is worth setting even when a
similar model appears to work.

**Denon (76)**

AVC-A1HDA, AVP-A1HDCI, AVR-100, AVR-990, AVR-991, AVR-1613, AVR-1713, AVR-1912, AVR-1913, AVR-2112,
AVR-2113, AVR-2312, AVR-2313, AVR-3310, AVR-3311, AVR-3312, AVR-3313, AVR-3805, AVR-3806, AVR-3808,
AVR-4306, AVR-4308, AVR-4310, AVR-4311, AVR-4520, AVR-4806, AVR-4810, AVR-5308, AVR-5805, AVR-E300,
AVR-E400, AVR-X1000, AVR-X2000, AVR-X3000, AVR-X4000, DN-500AV

Generated from Denon's protocol documents, all *(experimental)* until a receiver confirms them:
AVC-3310, AVR-A110, AVR-A1H, AVR-S640H, AVR-S650H, AVR-S740H, AVR-S750H, AVR-S940H, AVR-S950H,
AVR-S960H, AVR-S970H, AVR-X1100W, AVR-X1200W, AVR-X1500H, AVR-X1600H, AVR-X2100W, AVR-X2200W,
AVR-X2500H, AVR-X2600H, AVR-X2700H, AVR-X2800H, AVR-X3100W, AVR-X3200W, AVR-X3500H, AVR-X3600H,
AVR-X3700H, AVR-X3800H, AVR-X4100W, AVR-X4200W, AVR-X4700H, AVR-X4800H, AVR-X5200W, AVR-X6700H,
AVR-X7200W, DRA-800H, S700W, S900W.
DAB is not supported on these yet. AVR-3310-G and AVR-990-G are generated the same way, as an
alternative to the hand-written AVR-3310 and AVR-990.

Network and media players: ASD-51 *(experimental)*, DNP-720AE, RCD-N7 *(experimental)*

**Marantz (32)**

AV-7005, AV-7701, AV-8801, NR-1504, NR-1602, NR-1603, NR-1604, SR-5006, SR-5007, SR-5008, SR-6005,
SR-6006, SR-6007, SR-6008, SR-7005, SR-7007, SR-7008

Generated from Marantz's protocol document, all *(experimental)* until a receiver confirms them:
AV7702, AV7702mkII, AV8802, NR1506, NR1605, NR1606, SR5009, SR5010, SR6009, SR6010, SR7009, SR7010.
All of the above except AV-7005, SR-6005 and SR-7005 are generated the same way as well, as an
alternative with `-G` after the name (SR6008-G next to SR-6008).

Network and media players: M-CR603 *(experimental)*, M-ER803 *(experimental)*,
NA-7004 *(experimental)*

Not listed? **AVR-Generic** is the fallback and speaks the common subset of the protocol — usually
enough for power, volume, mute and input selection.

## Required Tools

* JDK 17
* Android SDK Platform 36
* Android Studio (optional, the Gradle wrapper is enough)

## Build

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew build
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` is not checked in; the SDK location is taken from `ANDROID_HOME`.

Release builds are signed only if `~/keystore.properties` exists or the `KEY_ALIAS`,
`KEY_PASSWORD`, `STORE_FILE` and `STORE_PASSWORD` environment variables are set.
Without those, `./gradlew build` produces an unsigned release APK.

## Release

Tagging `v<version>` builds a signed APK and publishes it as a GitHub Release; the Play Store upload
is manual. The whole procedure, including the Play Console checklist, is in
[RELEASE.md](RELEASE.md).

## Receiver specs
Search for

_denon 3312 DENON AVR control protocol_

## Licenses

### App
GPL V3
