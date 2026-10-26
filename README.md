# DRIVE Launcher

A lightweight native Android home launcher with a landscape cockpit layout, clock, app shortcuts and music controls. Version 0.1.1 uses a 1280 × 720 design canvas and supports Android 6.0+ (API 23).

**Known issue:** live Android Auto song information is not working yet. Metadata parser and UI fixtures pass, but live projection-app event delivery and payload compatibility remain unresolved.

## Features

- Native home screen with a live clock/date and a silver SUV illustration.
- Assignable Navigation, Music, Radio, Phone and Android Auto shortcuts and an app drawer.
- Saved destination searches through compatible map apps.
- Standard Android media-session metadata and playback controls where supported.
- Experimental CarLetter ID3 and playback-status event adapter.
- Media-stream volume controls and saved day/night appearance.

No WebView, Internet permission, analytics, advertising or third-party runtime dependency is required.

## Build

Requirements: Python 3, JDK 17 (`java`, `javac` and `keytool` on `PATH`), Android SDK platform 34 and build-tools 34.0.0.

Set `ANDROID_HOME` to the SDK path. If unset, the build script uses `~/Library/Android/sdk`.

```sh
python3 build.py
```

Output: `build/drive-launcher.apk`.

The build uses SDK tools directly, without Gradle or downloads. A local development signing key is created under `.signing/`; preserve it for future updates and keep it out of Git. `DRIVE_KEYSTORE` selects an existing compatible key. Independently built APKs cannot update an installed copy signed with a different key. This is development signing, not a production release setup.

## Install and configure

```sh
adb -s DEVICE install --no-incremental -r build/drive-launcher.apk
adb -s DEVICE shell am start -n com.rahul.drive/.MainActivity
```

Test DRIVE as an ordinary app before selecting it as the default Home app. Keep the stock launcher installed. Assign shortcuts in DRIVE Settings or long-press a main shortcut. Available apps and hardware integrations differ across head units. Saved addresses use `geo:` searches.

DRIVE provides the Home UI; the existing projection app continues to provide Android Auto. It yields to other activities and does not force itself into the foreground when a phone connects. Phone auto-connect, camera return, steering-wheel controls, volume and sleep/wake behavior need device-specific testing while parked.

## Music access

Standard Android media-session discovery uses optional notification access. Android grants a broad capability, so enable it only with the device owner's consent. DRIVE uses it for media sessions and implements no notification-content processing or storage.

The experimental CarLetter adapter uses its vendor ID3 and playback-status broadcasts and does not itself require notification access. It accepts bounded JSON metadata and holds recent track data in memory, expiring it after ten minutes without updates. It starts no foreground service or network polling loop. Native apps and projection apps may expose no usable metadata. Android media-stream volume may differ from hardware amplifier or radio volume.

## Tests

Build the app first, then build and run the instrumentation fixtures using the same local signing key:

```sh
python3 tests/build.py
adb -s DEVICE install --no-incremental -t -r tests/build/drive-tests.apk
adb -s DEVICE shell am instrument -w com.rahul.drive.tests/com.rahul.drive.tests.MediaTests
adb -s DEVICE uninstall com.rahul.drive.tests
```

Fixtures cover title/artist/source UI, elapsed seconds, malformed JSON and nested/case-variant metadata. They invoke the adapter directly, clear their synthetic data, and do not send protected vendor broadcasts. Passing fixtures do not verify live Android Auto delivery.

## Startup preview

The startup screen in Settings is a visual concept only. This app does not change firmware, boot partitions or boot animations.

Signing material, generated APKs, vendor packages, device logs, pairing details and backups are excluded from this repository.
