# Contributing

Bug reports and small, focused pull requests are welcome. For compatibility reports, include the watch model and firmware, iPhone/iOS version, music app and reproduction steps. Say what you expected and what happened instead.

Use JDK 17 and Android SDK 35. Run `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` before submitting changes. Release signing is optional for local builds.

Keep Bluetooth operations in the serialized transport queue. Avoid periodic scanning, polling and permanent wake locks. Preserve cancellation and stale-callback handling. Add regression coverage when changing a protocol parser or connection lifecycle.

Explain behavior changes and device tests in your pull request. Do not include SDKs, build outputs, credentials, signing keys or private device logs.

Contributions are provided under the project's MIT license.

## Project website

GitHub Pages publishes static files from `main:/docs`. Keep the English and Russian pages, canonical URLs, language alternates and `sitemap.xml` consistent when editing the site. Update the software version in JSON-LD when preparing a new app release.

After changed pages are live, `./scripts/submit-indexnow.sh` can notify participating search engines. The matching text file in `docs/` is a public ownership-verification key, not an app signing key or account credential. Submit only changed project URLs, not repeated requests on a timer. A successful response confirms receipt, not indexing or search position. See [IndexNow documentation](https://www.indexnow.org/documentation).
