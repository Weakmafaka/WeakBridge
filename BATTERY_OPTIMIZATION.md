# Power design and measurement

Default behavior is event-driven: no periodic heartbeat, polling, WorkManager loop, wake lock, exact alarm or continuous scan. Foreground service presence alone does not continuously run the CPU. Delays for retries and bounded recovery suspend coroutines; they are not exact wakeup guarantees and may be deferred during sleep.

User-initiated pairing advertising uses BALANCED only for its bounded two-minute window. Connection setup requests BALANCED; completed subscriptions request LOW_POWER by default. Users can choose balanced idle for responsiveness. No HIGH request is justified yet. Hints may be ignored or negotiated differently by the controller/phone; LOW_POWER is not proof of deep sleep. There is no per-command priority oscillation.

Unexpected losses use 10/20/40/80/160/300-second base delays with ±15% jitter. Six failed retries lead to one passive Android autoConnect registration without an app retry timer. Successful connections reset the attempt budget only after two minutes of stability, preventing fast flaps from resetting backoff. A failed passive registration requires manual reconnect or a Bluetooth restart. Validate Samsung background reconnection and radio effects before changing this policy.

There are bounded one-shot timers for direct connection (30s), pairing (90s), scan (20s), advertisement (120s), GATT operation (12s), ANCS response (10s), stability (120s), and AMS metadata recovery (5s, at most twice). No normal idle timers repeat.

## Diagnostics

Enable Settings → Diagnostics and use Read diagnostics to snapshot counters: connection duration/drops, reconnect attempts, scan sessions/duration, GATT operations, ANCS/AMS events, service starts and callbacks. These are in-memory counters and survive only the current service process. Callback count approximates app activity, **not actual CPU wakeup count**. Android BatteryManager capacity and charge counter are sampled only on request; unsupported charge counter returns MIN_VALUE. Turning diagnostics off stops increments. No content/addresses are logged.

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
