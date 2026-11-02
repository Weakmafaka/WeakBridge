# Power design and measurement

Default behavior is event-driven: no periodic heartbeat, polling, WorkManager loop, wake lock, exact alarm or continuous scan. Foreground service presence alone does not continuously run the CPU. Delays for retries and bounded recovery suspend coroutines; they are not exact wakeup guarantees and may be deferred during sleep.

User-initiated pairing advertising uses BALANCED only for its bounded two-minute window. Connection setup requests BALANCED; completed subscriptions request LOW_POWER by default. Users can choose balanced idle for responsiveness. No HIGH request is justified yet. Hints may be ignored or negotiated differently by the controller/phone; LOW_POWER is not proof of deep sleep. There is no per-command priority oscillation.

Unexpected losses use 10/20/40/80/160/300-second base delays with ±15% jitter. Six failed retries lead to one passive Android autoConnect registration without an app retry timer. Successful connections reset the attempt budget only after two minutes of stability, preventing fast flaps from resetting backoff. A failed passive registration requires manual reconnect or a Bluetooth restart. Validate Samsung background reconnection and radio effects before changing this policy.

There are bounded one-shot timers for direct connection (30s), pairing (90s), scan (20s), advertisement (120s), GATT operation (12s), ANCS response (10s), stability (120s), and AMS metadata recovery (5s, at most twice). No normal idle timers repeat.

## Diagnostics

Enable Settings → Diagnostics and use Read diagnostics to snapshot counters: connection duration/drops, reconnect attempts, scan sessions/duration, GATT operations/type/duration, received and transmitted characteristic payload bytes, ANCS/AMS events, notification publications, service creations/commands and callbacks. GATT active time excludes queue waiting; payload counters are not radio airtime and omit descriptor overhead. A bounded 32-entry journal records selected events. These are in-memory counters and survive only the current service process. Callback count approximates app activity, **not actual CPU wakeup count**. Android BatteryManager capacity and charge counter are sampled only on request; unsupported charge counter returns MIN_VALUE. Turning diagnostics off stops increments. In-flight operation durations crossing a reset or enable/disable boundary are discarded; scan/link timing resumes correctly when recording is enabled mid-session. No content/addresses are logged.

## Repeatable physical power test

Record watch model/firmware, iOS version, battery health, brightness, AOD, LTE/Wi-Fi, watch face, connected headphones, notification volume, sleep schedule and ambient temperature. Disable Wireless ADB during timed measurements; its radio can dominate this bridge's incremental usage. Keep all other conditions identical. Repeat baseline and bridge trials on different days with order alternated.

1. Charge consistently and allow post-charge stabilization. Run at least an 8-hour baseline with WeakBridge disconnected. Record start/end battery percentage and supported charge counter readings.
2. Repeat with bridge connected, screen mostly off, no music and the same sparse notification workload. Do not interact with diagnostic UI during the interval.
3. Repeat an active scenario with a fixed number of notifications and track transitions/control actions. Separately run out-of-range recovery and Bluetooth restart scenarios.
4. Reconnect ADB only after the timed run to collect reports. Compare discharge and estimated UID CPU/radio activity; report variance and charging/thermal confounders. Battery percentage is coarse; do not infer precision from it.

Example supported ADB inspection (run with explicit consent for `batterystats --reset`, which clears measurement history):

```sh
adb shell dumpsys batterystats --reset
adb shell dumpsys battery
adb shell dumpsys batterystats dev.weakbridge > weakbridge-batterystats.txt
adb shell dumpsys activity services dev.weakbridge > weakbridge-service.txt
adb shell dumpsys bluetooth_manager > weakbridge-bluetooth.txt
adb shell dumpsys media_session > weakbridge-media.txt
adb bugreport weakbridge-bugreport.zip
```

Bugreports/Bluetooth snoop logs may contain private data. Keep them local, inspect/redact before sharing. Use OS power profiling/Perfetto where supported for detailed wakeups; do not enable continuous production telemetry. Do not request Samsung battery exclusions preemptively. If a repeatable OEM termination is observed, record evidence and test a documented user-selected battery setting as a separate comparison. There are no measured battery results yet.

## 0.2.0 decisions after comparing implementations

The source comparison is recorded in [the Russian audit](docs/COMPETITOR_ANALYSIS_RU.md), with pinned revisions and separate code/documentation evidence. Changes here are independent implementations within the existing serialized transport.

| Reference or finding | Decision in WeakBridge | Evidence and limit |
|---|---|---|
| [WearBridge connection guard](https://github.com/k97/WearBridge/blob/c9ff912b9e87bb679c63c863c6f7df6e97d06e52/wearos-app/app/src/main/java/com/wearos/ancsbridge/ble/BleConnectionManager.kt) | Duplicate selection of the same live link is ignored; explicit reconnect still forces recovery. Ordinary settings apply locally. | Watch QA retained the same media session across low-power/auto-reconnect changes. No discharge percentage is inferred. |
| Our truncated AMS read channel could discard the newest value in a burst | Keep only the newest revision per valid entity/attribute; recheck validity at actual GATT submission. | Regression tests cover 17 rapid updates, expiry and withdrawn support while queued. |
| A removed/filtered notification could still trigger an optional app-name transaction | Recheck UID revision/category/app after content response, before name lookup. Skip subsequent reads for known blocked UIDs. | Three regressions cover removal, filter changes and 20 blocked updates without extra reads. An unknown app still needs its initial identifier read. |
| Repeated identical Android publications | Cache projected content, republish settings silently, retain dismissed-card deletion, and count actual notify calls. | Store/policy tests; real Samsung alert delivery still needs acceptance. |
| PixelWatch's 3-second metadata loop, Herold's periodic watchdog, ultra's unbounded advertising | Keep existing event-driven recovery, bounded discovery/advertising and backoff/passive autoConnect. | These mechanisms were already present; copying periodic checks is not an established improvement. Herold's Wi-Fi/wake locks run while charging, not continuously on battery. |

No perpetual polling, wake lock, exact alarm, high-priority connection oscillation or automatic large-MTU request was introduced. LOW_POWER versus BALANCED and MTU remain measured experiments. Android also [discourages periodic BLE scans](https://developer.android.com/develop/connectivity/bluetooth/ble/background).
