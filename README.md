<p align="center">
  <img width="120" src="app/src/main/res/mipmap/ic_launcher.png" />
  <h1 align="center">EteSync - Secure Data Sync</h1>
</p>

Secure, end-to-end encrypted, and privacy respecting sync for your contacts, calendars and tasks (Android client).

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play" 
      height="80" />](https://play.google.com/store/apps/details?id=com.etesync.syncadapter)
[<img src="https://www.etesync.com/static/img/fdroid-badge.fe865d4c8f63.png"
      alt="Get it on F-Droid"
      height="80" />](https://f-droid.org/app/com.etesync.syncadapter)

![GitHub tag](https://img.shields.io/github/tag/nikossvnk/etesync-android.svg)
[![Chat with us](https://img.shields.io/badge/chat-IRC%20|%20Matrix%20|%20Web-blue.svg)](https://www.etebase.com/community-chat/)

# Overview

Please see the [EteSync website](https://www.etesync.com) for more information.

EteSync is licensed under the [GPLv3 License](LICENSE).

# Upgrading to 3.0

This fork supports Etebase accounts only and requires Android 6.0 (API 23) or newer.
EteSync 1.0 accounts can no longer sync. Before upgrading, export legacy contacts,
calendars, and tasks with the previous client. Existing legacy databases are not
deleted automatically. Removing an Android account can remove its local synced
data; export it first. Re-adding an account does not migrate EteSync 1.0 data.

Etebase accounts and their encrypted caches remain compatible. Installing over an
existing app requires the same signing key; a fork signed with a different key
cannot update the official app in place.

# Building

Use JDK 25 and the Gradle wrapper. The build uses Gradle 9.8.2, Android Gradle
Plugin 9.5.0, Kotlin 2.4.21, and Java 17 bytecode. Install Android SDK Platform
37.0, Build Tools 37.0.0, NDK 30.0.16248370, Rust/rustup, and a native C toolchain
(`build-essential` and `pkg-config` on Ubuntu). Set `ANDROID_HOME` to your SDK.
The app targets Android 16 (API 36).

```sh
git clone --recurse-submodules https://github.com/nikossvnk/etesync-android.git
cd etesync-android
sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "ndk;30.0.16248370"
./gradlew assembleDebug
./gradlew build compileDebugAndroidTestKotlin :etebase-client:compileDebugAndroidTestJavaWithJavac
```

The four source submodules are maintained under `github.com/nikossvnk`:
`ical4android`, `vcard4android`, `cert4android`, and `etebase-java`. Native Etebase
bindings build automatically from the locked Rust dependencies for all four Android
ABIs, with 16 KB ELF alignment. The first build downloads the repository-selected
Rust toolchain and target libraries.

Release builds generate a narrow R8 library-API rule for SDK-hidden
`ZoneRulesProvider`. Core-library desugaring supplies its runtime implementation;
the build does not package raw JDK classes or suppress missing-class diagnostics.

For an existing checkout, run `git submodule update --init --recursive` after
pulling. Do not replace submodule revisions with arbitrary branch heads.

`build` assembles debug and unsigned release APKs, runs JVM tests, and runs Android
Lint. Instrumented tests require a device or emulator:

```sh
./gradlew connectedCheck
```

Calendar task-provider tests require Tasks.org or OpenTasks. Etebase's standalone
instrumented service tests also require a disposable test server; see
[`etebase-java/README.md`](etebase-java/README.md). There is no coverage threshold.

## Releases

Set `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, and
`releaseKeyPassword` as Gradle properties to sign a local release. Keep keystores
and passwords outside the repository.

The Check workflow builds, lints, and tests changes and runs weekly. Dependabot
checks Gradle dependencies, Git submodules, and GitHub Actions weekly. The Release
workflow publishes a signed APK and checksum for `vX.Y.Z` tags matching
`versionName`. It requires `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` repository secrets. It rejects
missing signing credentials and debug-signed APKs.

# Translating

Translators are highly welcome! 
To help with translation please go to [our Weblate page](https://hosted.weblate.org/projects/etesync/) and translate it from there.


Third Party Code
================

EteSync's source code was originally based on [DAVdroid](https://www.davx5.com) but the codebases has since diverged quite significantly.

This project relies on many great third party libraries. Please take a look at the
app's about menu for more information about them and their licenses.
