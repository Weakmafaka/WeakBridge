# Testing

## Automated checks

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The suite currently contains 35 JVM tests. It covers ANCS fragmentation and notification lifecycle, GATT serialization and cancellation, reconnect policy, pairing advertising layout, service status text, AMS metadata recovery and track transitions. Fake transports and coroutine virtual time make failures reproducible.

Tests do not emulate Samsung's Bluetooth stack, notification rendering or gesture recognition. Reports are written to `app/build/reports/`.

## Device coverage

Galaxy Watch6 (SM-R930) with iPhone 13 / iOS 26.0:

- First discovery and pairing confirmed after adding Android's discoverability prompt.
- Music controls confirmed by the user; an active remote media session was also observed through ADB.
- Track-change session reset fixed in 0.1.3 and covered by regression tests. Longer playback tests are still needed.
- Notification actions, all media apps, reboot recovery and long-term battery consumption are not comprehensively validated.

## Before a release

- Run the automated checks and verify the APK signature, package ID and version.
- Install on a configured watch, pair an iPhone and check a fresh notification.
- Start music, change tracks repeatedly, pause/resume, return to the watch face.
- Test disconnect/reconnect and confirm stale actions cannot control the new session.
- Check screen-off behavior separately from foreground operation.
- Compare battery consumption using [BATTERY_OPTIMIZATION.md](BATTERY_OPTIMIZATION.md).

When reporting a result, include model, firmware, iOS version and the music app used. Never attach unredacted message content, Bluetooth addresses or full bugreports to a public issue.
