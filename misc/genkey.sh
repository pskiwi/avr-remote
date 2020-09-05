#!/bin/sh
keytool -genkey -v -keystore ../my-release-key.keystore -alias pskiwi -keyalg RSA -keysize 2048 -validity 10000
