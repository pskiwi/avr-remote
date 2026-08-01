# ![Alt text](app/src/main/assets/icon.png "AVR-Remote") AVR-Remote 

Android remote control for Denon and Marantz receivers.
[Google Play Store Link](https://play.google.com/store/apps/details?id=de.pskiwi.avrremote)


This application is not affiliated with Denon or Marantz. 
Denon and Marantz are registered trademarks of D&M Holdings, Inc. 

![Check build](https://github.com/pskiwi/avr-remote/workflows/Check%20build/badge.svg)

Runs on Android 7.0 (API 24) and newer.

## Supported models

Picked in the app under *Settings → Model Settings → Model*. The choice decides which features are
offered (number of zones, quick-select, level controls, DAB, …), so it is worth setting even when a
similar model appears to work.

**Denon (36)**

AVC-A1HDA, AVP-A1HDCI, AVR-100, AVR-990, AVR-991, AVR-1613, AVR-1713, AVR-1912, AVR-1913, AVR-2112,
AVR-2113, AVR-2312, AVR-2313, AVR-3310, AVR-3311, AVR-3312, AVR-3313, AVR-3805, AVR-3806, AVR-3808,
AVR-4306, AVR-4308, AVR-4310, AVR-4311, AVR-4520, AVR-4806, AVR-4810, AVR-5308, AVR-5805, AVR-E300,
AVR-E400, AVR-X1000, AVR-X2000, AVR-X3000, AVR-X4000, DN-500AV

**Marantz (17)**

AV-7005, AV-7701, AV-8801, NR-1504, NR-1602, NR-1603, NR-1604, SR-5006, SR-5007, SR-5008, SR-6005,
SR-6006, SR-6007, SR-6008, SR-7005, SR-7007, SR-7008

**Network and media players (6)**

ASD-51 *(experimental)*, DNP-720AE, M-CR603 *(experimental)*, M-ER803 *(experimental)*,
NA-7004 *(experimental)*, RCD-N7 *(experimental)*

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

## Receiver specs
Search for

_denon 3312 DENON AVR control protocol_

## Licenses

### App
GPL V3
