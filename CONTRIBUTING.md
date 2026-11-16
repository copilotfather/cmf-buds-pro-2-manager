# Contributing

Small, focused changes are welcome. For device issues include model, firmware, Android version, app version and repeatable steps. State whether a behavior was tested on hardware or only in a simulator.

## Development

1. Fork and clone the repository.
2. Install JDK 17 and Android SDK 35.
3. Run `./gradlew testDebugUnitTest assembleRelease` (`gradlew.bat` on Windows).
4. Keep English and Russian UI strings in sync.
5. Explain the problem, the resulting behavior and validation in your pull request.

Use existing dependencies and architecture where practical. New vendor commands need a primary protocol/source reference and clear compatibility notes. Never advertise a feature as hardware-verified without a device test.

Do not include signing keys, auth secrets, full notification contents, location history or health exports in public issues/logs. See [SECURITY.md](SECURITY.md).
