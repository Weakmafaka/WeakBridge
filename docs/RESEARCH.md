# References

WeakBridge implements Apple's notification and media services over Bluetooth LE.
These references are useful when working on pairing, protocol parsing and media controls.

## Authoritative references

- [ANCS introduction](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html), [protocol](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html), and [appendix](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Appendix/Appendix.html): UUIDs, authorization, little endian fields, UTF-8, fragmentation, categories/actions and Service Changed.
- [AMS protocol](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Specification/Specification.html) and [appendix](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Appendix/Appendix.html): entity IDs, attribute/command IDs, relative volume and playback elapsed-time format. AMS is metadata/commands, not audio.
- [Android media sessions](https://developer.android.com/media/media3/session/control-playback): system control surface and Player/session relationship. The implementation selects MediaSessionCompat for direct remote state rather than adding a fake ExoPlayer.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): connectedDevice type and Bluetooth permission prerequisite.
- [AGP 8.9 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes): API 35, Gradle 8.11.1, JDK 17, build tools 35.0.0.

## Related projects

- [GooglePixelWatch-for-iPhone](https://github.com/toastmod/GooglePixelWatch-for-iPhone): ANCS/AMS on Wear OS and metadata recovery.
- [WearBridge](https://github.com/k97/WearBridge): notification lifecycle and background service patterns (MIT).
- [Herold](https://github.com/chackrahunter/herold): Samsung watch pairing and ANCS service solicitation (MIT).
- [wear-ios-bridge](https://github.com/hnkabraham/wear-ios-bridge): an alternative design using a companion app and a custom protocol (MIT).

These projects were consulted during development; their application source is not bundled in WeakBridge. Their device-support claims do not establish compatibility for this app.

## Licenses

WeakBridge uses MIT. The Gradle wrapper is Apache-2.0; its license is included at `gradle/LICENSE`. Runtime dependencies retain their upstream licenses; see [Third-party notices](../THIRD_PARTY_NOTICES.md).
