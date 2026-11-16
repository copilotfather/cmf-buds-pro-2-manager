# Watch Pro 2 beta

[Back to README](../README.md)

## Pair and authorize

1. Open Settings → DEV; password: `betatestdev`.
2. Enable beta support and grant Bluetooth permissions. Android 11 and earlier also require location permission and location enabled for scanning.
3. Scan for `CMF Watch Pro 2` or `CMF Watch Pro 2-XXXX` and connect.
4. Close other watch companion apps. Select Authenticate, review the pairing notice and confirm on the watch if prompted.
5. GATT connected and authenticated are separate states. Health and notifications require authentication.

The current implementation negotiates a session through the documented shell/command channels, verifies the pairing signature and encrypts command traffic. It has protocol unit tests but has not been tested with a physical watch. Reconnection restores BLE only; authenticate again to restore health/notification access. Session keys are not persisted.

## Health

Press Sync health. Today's screen shows steps, distance, kcal, latest heart rate, SpO₂, stress and sleep duration. Per-device records are deduplicated, bounded to 12,000 entries and retained for up to 31 days. Partial data survives interrupted syncs; only a watch completion message marks a sync complete. Delete local health data removes this device's stored history.

No cloud uploads or Health Connect export are implemented. Health preferences are excluded from Android backup/transfer rules. Readings are device measurements, not medical advice. Workout-summary parsing is not implemented.

## Notifications

Enable forwarding, open Notification access and grant access to this app, then select WhatsApp, Telegram, Gmail, Instagram or enter another Android package name. Nothing is selected by default.

Only new, selected notifications are considered. Ongoing notifications, group summaries, secret notifications and notifications posted while the phone is locked are skipped. Duplicate content is filtered. The transport is rate limited to at most one notification per second and does not queue missed messages. Title/body limits are 20/128 UTF-8 bytes. BLE queue count and watch ACK are shown separately; neither proves the user saw a notification.

Notification text is not stored. The allowlist is excluded from Android backup. Disable forwarding to stop future messages; disconnect or lock DEV to drop the watch session.

## Troubleshooting

- **No devices:** check permission, Bluetooth, proximity and competing companion apps.
- **GATT without data:** standard battery/device information may be unavailable before authorization.
- **Authentication timeout:** close other clients, reconnect and retry. Do not reset/unbind the watch merely to retry this beta.
- **No notifications:** verify Android notification access, allowlist, unlocked phone and authenticated status. Existing notifications are not replayed.
- **Empty health screen:** manually sync; missing readings may not be recorded by the watch. Include firmware and error text in a bug report, never secret keys or notification contents.
