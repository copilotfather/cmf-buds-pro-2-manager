# Changelog

## 2.4.0 — Companion & Watch Beta

### Sound and everyday controls
- Listening profiles with custom saved settings; EQ A/B, additional presets and persistent manual curves.
- Seven home screen widgets with theme, accent and control customization.
- Per-earbud battery history, imbalance warning, connection diagnostics and ANC readback.
- Left/right acoustic finder and persisted last known phone location.

### Watch Pro 2 beta
- Password-gated DEV section, model-specific BLE scan, standard GATT reads and limited reconnection retries.
- Pairing handshake, signature verification, encrypted session and bounded write/read buffers.
- Local health sync for activity, heart rate, SpO₂, stress and sleep.
- Opt-in, per-app notification forwarding with privacy filters and watch ACK status.
- Four unit tests cover authentication, fragmented replies, invalid signatures/headers and pre-auth send protection.

### Fixes
- Screen stacking, dialog scrolling, clipped chart labels and bottom system insets.
- Narrower FileProvider access, HTTPS download redirects and duplicate connection suppression.
- Device filtering now targets CMF Buds Pro 2 rather than generic earbuds.

### Validation and limits
- Local release build and protocol unit tests pass. Hardware testing is pending.
- Watch keys are session-only; authenticate after reconnecting. Workout parsing and Health Connect export are absent.
- ANC readback is implemented; other earbud command application and actual negotiated codec remain firmware/system dependent.

## 2.3.10 — Layout hotfix
- Corrected overlapping screens, page/dialog scrolling and chart label clipping.

## 2.3.9 — UI motion
- Animated page transitions, navigation feedback and card motion.

## 2.3.8 — Interface refresh
- Dark grouped surfaces and restrained system-blue navigation.

## 2.3.7 — English localization
- Added missing translations throughout screens and dialogs.
