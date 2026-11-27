## v2.4.6 · Sound Studio

### What's new

- Refined the home screen hierarchy, studio layout, card surfaces and navigation motion.
- Added B172 hardware identification before exposing revision-specific earbud controls.
- Added earbud preset and custom EQ controls, game mode, in-ear detection, spatial audio, touch gestures and dual-connection device management with readback.
- Added a revision-aware firmware advisory and official Nothing X update handoff. Direct earbud flashing is not supported.
- Added saved-device reset guidance based on the official CMF case-button procedure.

### Новое

- Переработаны иерархия главного экрана, студийная вкладка, карточки и анимации переходов.
- Перед показом расширенных настроек определяется аппаратная ревизия B172.
- Добавлены настройки пресетов и EQ наушников, игрового режима, автопаузы, пространственного звука, жестов и двойного подключения с чтением состояния.
- Проверка версии прошивки учитывает ревизию и ведёт к обновлению через Nothing X. Прямая перепрошивка не поддерживается.
- Добавлена официальная инструкция сброса памяти подключений кнопкой кейса.

Физическая проверка новых команд ещё не проводилась. Для расширенных настроек поддерживается только подтверждённая ревизия B172.

---

## v2.4.5 · Comfortable Controls

### What's new

- Added an optional Comfortable Controls setting with larger 56 dp touch targets for key audio and earbud controls.
- Saved the accessibility preference across app restarts.

### Новое

- Добавлен переключатель «Комфортное управление»: зоны нажатия основных аудио-настроек и поиска наушников увеличиваются до 56 dp.
- Выбор сохраняется после перезапуска приложения.

### Обновление

- Пользователи v2.4.3 должны удалить старую версию перед установкой из-за смены ключа подписи. С v2.4.4 обновление устанавливается обычно.

---

## v2.4.4 · Companion update

### Важно для пользователей v2.4.3

В релизе используется новый ключ подписи. Android не установит его поверх v2.4.3. В окне обновления нажмите **«Браузер»**, удалите старую версию, затем скачайте и установите новый APK. Заранее сохраните нужные данные приложения.

### Important for v2.4.3 users

This release uses a new signing key, so Android will not install it over v2.4.3. In the update dialog, tap **Browser**, uninstall the old version, then download and install the new APK. Back up any app data you need first.

### What's new

- Improved security and reliability across app services and local data handling.
- Added battery status to Quick Settings tiles.
- Added a live audio spectrum visualizer.
- Refined color themes, screen transitions, and touch feedback.
- Improved Watch health metric transitions.

---

## v2.4.3 · CMF Watch dashboard

### What's new

- Added a dedicated CMF Watch Pro 2 page with BLE discovery, connection status, battery and health summaries.
- Improved watch pairing by handling fragmented shell responses and BLE frames correctly.
- Improved updater cleanup for HTTP connections and interrupted downloads.

### По-русски

Добавлена отдельная страница CMF Watch Pro 2 с поиском часов, зарядом и показателями здоровья. Улучшена обработка авторизации и фрагментированных BLE-кадров; исправлено закрытие сетевых ресурсов при обновлении.

---

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
