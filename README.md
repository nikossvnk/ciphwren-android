<p align="center">
  <img width="120" src="app/src/main/res/mipmap/ic_launcher.png" />
  <h1 align="center">Ciphwren</h1>
</p>

Secure, end-to-end encrypted, and privacy respecting sync for your contacts, calendars and tasks (Android client).

> [!NOTE]
> **This is an independent fork** of [EteSync for Android](https://github.com/etesync/android), the
> original project of the EteSync authors, and is not affiliated with or supported by EteSync. It's a
> separate app (`com.ciphwren.android`): an EteSync install doesn't update to it, and its accounts
> are added again in it. The APKs are on the [releases page](https://github.com/nikossvnk/ciphwren-android/releases).

# Overview

Please report bugs and questions on the [issue tracker](https://github.com/nikossvnk/ciphwren-android/issues).

The app has no default server: enter the URL of your Etebase server when you log
in or sign up. Links (including the server's account dashboard) open in your
browser.

Ciphwren is licensed under the [GPLv3 License](LICENSE).

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
git clone --recurse-submodules https://github.com/nikossvnk/ciphwren-android.git
cd ciphwren-android
sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "ndk;30.0.16248370"
./gradlew assembleDebug
./gradlew build compileDebugAndroidTestKotlin :etebase-client:compileDebugAndroidTestKotlin
```

The four source submodules are maintained alongside this repository:
`ical4android`, `vcard4android`, `cert4android`, and `etebase-java`. The Etebase
native library and its UniFFI-generated Kotlin bindings build automatically from the
locked Rust dependencies for all four Android ABIs, with 16 KB ELF alignment; the app
packages only those ABIs (JNA's `libjnidispatch.so` included). The first build
downloads the repository-selected Rust toolchain and target libraries.
`scripts/check-apk-native-libs.sh <apk>` checks the packaged ABIs and their 16 KB
alignment, as CI does.

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

Translators are highly welcome! Translations live in this repository, in
`app/src/main/res/values-<locale>/strings.xml`, next to the English source
strings in `app/src/main/res/values/strings.xml`. To add or improve a language,
edit (or create) the locale's `strings.xml` and open a pull request. Keep
placeholders such as `%s` and `%1$d` intact and skip strings marked
`translatable="false"`.

`scripts/translation-status.sh` shows, per locale, how many strings are
translated and which are missing. It runs offline and exits with an error when a
translation contains strings that no longer exist in the English file; delete
those. `-q` hides the names of the missing strings:

```sh
scripts/translation-status.sh -q
```


Third Party Code
================

EteSync's source code, which this is a fork of, was originally based on [DAVdroid](https://www.davx5.com) but the codebases has since diverged quite significantly.

This project relies on many great third party libraries. Please take a look at the
app's about menu for more information about them and their licenses.
