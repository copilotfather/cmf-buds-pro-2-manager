## v2.4.0 · Companion & Watch Beta

A companion update for CMF Buds Pro 2 with experimental Watch Pro 2 integration.

### What's new

- **Sound:** saved listening profiles, EQ A/B comparison, new presets and persistent manual curves.
- **Widgets:** seven home screen variants, light/dark styling, accents and configurable third control.
- **Buds:** model-specific filtering, connection diagnostics, ANC readback, battery history and left/right finder.
- **Interface:** fixes for overlapping screens, scrolling, chart labels and the bottom system area.
- **Watch beta:** DEV gate, BLE discovery, pairing/signature verification and encrypted command sessions.
- **Health:** local activity, heart rate, SpO₂, stress and sleep sync with deduplication and deletion.
- **Notifications:** explicit app allowlist, Android permission, unlocked-phone filtering and watch ACK status.

### Install

Download **CMF_Buds_Pro_2_Manager.apk** below and install it on Android 8.0+. Android may ask you to allow installation from this source. Existing users can install over their current copy if Android accepts the signing identity.

For Watch Pro 2: **Settings → DEV → `betatestdev` → enable beta → scan → connect → Authenticate**. Close other companion apps and confirm pairing on the watch. This may replace another companion binding. Authenticate again after reconnecting.

### Validation & compatibility

Local release build and **four watch protocol unit tests** passed. Physical-device validation is pending; vendor command behavior depends on firmware. Android chooses the actual codec/bitrate. Watch workout parsing, Health Connect export and persistent authorization are not included.

Health data stays local. Notification forwarding is off by default and missed notifications are not queued. Experimental LAN audio relay has no client authentication; use a trusted network.

[English guide](https://github.com/copilotfather/cmf-buds-pro-2-manager#readme) · [Русская инструкция](https://github.com/copilotfather/cmf-buds-pro-2-manager/blob/main/docs/README.ru.md) · [Watch beta guide](https://github.com/copilotfather/cmf-buds-pro-2-manager/blob/main/docs/WATCH_BETA.md) · [Report a bug](https://github.com/copilotfather/cmf-buds-pro-2-manager/issues/new/choose)

---

### По-русски

Профили, A/B эквалайзера, семь виджетов, история батареи и исправления интерфейса. Бета часов получила авторизацию, синхронизацию здоровья и уведомления выбранных приложений. На физических устройствах пока не проверено. APK и контрольная сумма доступны ниже.
