# Roadmap

[Overview](README.md) · [Watch beta](docs/WATCH_BETA.md)

## Implemented in code

- [x] Five-band EQ, presets, JSON sharing and A/B comparison.
- [x] Listening profiles, seven widgets and appearance settings.
- [x] Battery history, connection diagnostics and ANC readback.
- [x] Independent left/right finder with safety confirmation and persisted phone location.
- [x] Watch Pro 2 BLE beta, session authorization, local health sync and opt-in notifications.
- [x] Watch protocol tests and release workflow.

## Next: validate on devices

- [ ] Test Buds Pro 2 commands and readback on real firmware; record supported versions.
- [ ] Test Watch Pro 2 pairing, reconnect, sync and notification ACK against physical hardware.
- [ ] Validate widgets, large fonts, small screens and system insets on Nothing Launcher.
- [ ] Check EQ behavior across players, phones and audio sessions.
- [ ] Add robust automated coverage for sync parsing and lifecycle recovery.

## Reliability and security

- [ ] Authenticate LAN audio-relay clients and review automation-receiver access.
- [ ] Review release signing and move private production credentials outside the source tree.
- [ ] Add long-lived background-session handling with explicit user controls.
- [ ] Persist watch authorization securely after hardware validation.

## Later

- [ ] Workout summary parsing and optional Health Connect export.
- [ ] Per-widget-instance configuration and more accessibility controls.
- [ ] Watch alarms, weather and notification controls after protocol/device validation.

Implementation checkboxes describe code availability, not hardware certification.
