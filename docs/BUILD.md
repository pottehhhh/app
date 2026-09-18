# Building WEIRDNET

## Debug build (for testing on your own device)

In Android Studio, click **Run ▶** with a device connected, or from a
terminal in the project folder:

```
./gradlew assembleDebug
```

The output APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Debug
builds are automatically signed with Android's shared debug key, which is
fine for installing on your own test devices but not for distribution.

## Creating a release build

A release build needs its own signing key so Android trusts future updates
came from the same source.

### 1. Create a keystore (one-time)

In a terminal:
```
keytool -genkeypair -v -keystore weirdnet-release.keystore -alias weirdnet -keyalg RSA -keysize 2048 -validity 10000
```
It will ask for a keystore password, your name/organization (cosmetic, can be
anything), and a key password. **Save these somewhere safe -- losing this file
means you can never publish an update to the same app listing again.**

### 2. Point Gradle at it

Set these environment variables before building (exact commands depend on
your OS/shell):

```
export WEIRDNET_KEYSTORE_PATH=/absolute/path/to/weirdnet-release.keystore
export WEIRDNET_KEYSTORE_PASSWORD=your_keystore_password
export WEIRDNET_KEY_ALIAS=weirdnet
export WEIRDNET_KEY_PASSWORD=your_key_password
```

(`app/build.gradle.kts` already reads these four variables automatically -- see
the `signingConfigs` block.)

### 3. Build

```
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. If you skip step 2,
this command still succeeds -- it falls back to debug signing so you always
get an installable APK, but you should not distribute a debug-signed release
build beyond your own testing.

### 4. Installing on a phone without Android Studio

Copy the APK to the phone, then either:
- Open it with a file manager and tap Install (you'll need to allow "install
  unknown apps" for that file manager once), or
- Use `adb install app-release.apk` from a computer with the phone connected
  via USB debugging.

## Per-ABI APKs (smaller download size)

By default the build produces one "universal" APK containing native code for
every supported CPU architecture. To instead produce smaller per-architecture
APKs, set `isEnable = true` in the `splits { abi { ... } }` block in
`app/build.gradle.kts`, then run `assembleRelease` again -- Gradle will output
one APK per ABI plus a universal fallback.
