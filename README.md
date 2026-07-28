# ![Alt text](app/src/main/assets/icon.png "AVR-Remote") AVR-Remote 

Android remote control for Denon and Marantz receivers.
[Google Play Store Link](https://play.google.com/store/apps/details?id=de.pskiwi.avrremote)


This application is not affiliated with Denon or Marantz. 
Denon and Marantz are registered trademarks of D&M Holdings, Inc. 

![Check build](https://github.com/pskiwi/avr-remote/workflows/Check%20build/badge.svg)

Runs on Android 7.0 (API 24) and newer.

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
