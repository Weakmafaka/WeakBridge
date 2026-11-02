<div align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="128" alt="WeakBridge">
  <h1>WeakBridge</h1>
  <p>iPhone notifications and music controls on your Galaxy Watch.</p>
  <p><a href="https://github.com/Weakmafaka/WeakBridge/releases/latest">Download APK</a> · <a href="docs/README.ru.md">Русский</a> · <a href="https://github.com/Weakmafaka/WeakBridge/issues">Report a bug</a></p>
  <p>
    <a href="https://github.com/Weakmafaka/WeakBridge/releases/latest"><img src="https://img.shields.io/github/v/release/Weakmafaka/WeakBridge" alt="Latest release"></a>
    <img src="https://img.shields.io/badge/platform-Wear%20OS-4285F4" alt="Wear OS">
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-38BDAE" alt="MIT license"></a>
  </p>
</div>

WeakBridge connects an already configured Wear OS watch to an iPhone over Bluetooth. Install one app on the watch: no iPhone companion, account or cloud service is required. Music stays on the iPhone or its selected audio output.

<p align="center">
  <img src="docs/images/watch6-home-0.2.0.png" width="220" alt="Connection and service status on Galaxy Watch6">
  <img src="docs/images/watch6-settings-0.2.0.png" width="220" alt="Connection settings">
  <img src="docs/images/watch6-quiet-hours-0.2.0.png" width="220" alt="Quiet hours settings">
</p>

Screenshots from Galaxy Watch6. The interface is available in English and Russian.

## Features

| | What you can do |
|---|---|
| Notifications | Receive new iPhone notifications, updates, removals and actions provided by iOS |
| Filters | Choose categories and individual apps after their first notification arrives |
| Alert controls | Group by app, hide message content, set quiet hours and limit repeat alerts |
| Music | See track information and control play/pause, next/previous and relative volume through the watch's system player |
| Connection | Reconnect to a saved iPhone, view separate notification/music status and keep the bridge running after closing the app |
| Diagnostics | Read, copy and clear local counters without logging message content |

Version 0.2.0 adds the new interface and notification controls, avoids unnecessary reconnects when changing ordinary settings, and skips obsolete Bluetooth work. See the [changelog](CHANGELOG.md).

## Compatibility and limits

**Beta, tested with Galaxy Watch6 (SM-R930) and iPhone 13 on iOS 26.0.** Pairing, music controls, the new interface and ordinary use have been confirmed on this combination. Other models, firmware and music apps need testing; long-term battery drain has not been measured.

Requires Android 13 / API 33 or later on an already configured watch. WeakBridge uses Apple's ANCS and AMS services. It does not set up a factory-reset watch, replace Galaxy Wearable, stream audio, sync health data or send arbitrary message replies. Notification actions depend on what iOS supplies. Gestures depend on watch firmware; double pinch in AOD and global fist-to-pause are not provided by this app.

## Install

1. Download `WeakBridge-0.2.0.apk` from [Releases](https://github.com/Weakmafaka/WeakBridge/releases/latest).
2. Enable Developer options and Wireless debugging on the watch. Connect the watch and computer to the same Wi-Fi network.
3. Use the addresses and ports displayed on the watch:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:DEBUG_PORT
adb install -r WeakBridge-0.2.0.apk
```

The pairing and connection ports differ. Turn Wireless debugging off after installation. APK checksums are attached to the release as `SHA256SUMS.txt`.

**Updating 0.1.4:** install with `adb install -r`; the release signing key is unchanged. Settings are retained.

**Coming from a private development build:** its signing key differs from the public APK. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, changing to the public version requires uninstalling the private app, which removes app settings. The Bluetooth bond can remain; select your iPhone again after installation.

## Connect

1. Open WeakBridge and allow Nearby devices and notifications.
2. Open **Connection → Connect iPhone** and approve the system request to make the watch discoverable for two minutes.
3. On iPhone, open **Settings → Bluetooth**, select the watch and confirm the matching codes on both devices.
4. Allow notification sharing on iPhone. Enable **Share System Notifications** in the watch's Bluetooth entry if shown.
5. In WeakBridge's **Connection** screen, open the saved-device list and select your iPhone. Use **Reconnect** for an already selected phone.
6. Send a new notification and start music to check both services.

The watch keeps its Bluetooth name. The optional compatible-device scan is a diagnostic tool; an empty result does not prove the iPhone is incompatible. [Russian setup and troubleshooting](docs/README.ru.md).

## Privacy and battery

Notification content and observed app names stay in memory and Android's notification system; there is no persistent message history. Only preferences, blocked app identifiers and the selected Bluetooth device are stored. There is no Internet permission, analytics or account system.

The bridge is event-driven, with no continuous scan, periodic keepalive or permanent wake lock. Recovery attempts are bounded. Version 0.2.0 reduces redundant work, but no battery-life percentage is claimed. See [power design and measurement](BATTERY_OPTIMIZATION.md).

## Build

Use **JDK 17**, **Android SDK 35** and **Build Tools 35.0.0**. Set `ANDROID_HOME` or your SDK path in `local.properties`.

```sh
./gradlew :app:assembleWear :app:testDebugUnitTest :app:lintWear
```

The optimized local build is `app/build/outputs/apk/wear/app-wear.apk`, signed with your development certificate. Use it when evaluating performance; an unoptimized debug build is not representative.

For a signed release, set `WEAKBRIDGE_KEYSTORE`, `WEAKBRIDGE_KEY_ALIAS`, `WEAKBRIDGE_STORE_PASSWORD` and `WEAKBRIDGE_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`. Without those variables, release output is unsigned. Signing keys are not distributed.

## Contribute

[Report a bug](https://github.com/Weakmafaka/WeakBridge/issues/new/choose) with your device models, OS versions and reproduction steps. Remove private messages and device addresses before attaching logs or screenshots.

See [CONTRIBUTING.md](CONTRIBUTING.md), [architecture](ARCHITECTURE.md), [testing](TESTING.md) and the [watch-only roadmap](docs/WATCH_ONLY_PLAN_RU.md). If the app is useful to you, a GitHub star helps other watch owners find it.

## License

[MIT](LICENSE). Dependency licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Related interoperability work is linked in [References](docs/RESEARCH.md).
