# Security

This is an independent beta companion app. The latest published release is the focus for fixes; no support SLA is promised.

## Report privately

Use this repository's **Security → Report a vulnerability** to disclose sensitive findings. Include the affected version, impact and minimal reproduction. Do not post exploit details, watch secrets, health data, location records or notification text in public issues.

## Known boundaries

- The DEV password is a feature gate, not account authentication or a secure secret store.
- Watch authentication keys are held only for the current session.
- Health records are stored in app-private preferences and excluded from Android backup/transfer rules.
- Audio relay is experimental and its LAN listener does not yet authenticate clients. Use only on a trusted network.
- The exported automation receiver currently allows third-party control broadcasts.
- Source-tree signing material/configuration is not a model for private production signing; independent forks should supply their own private keys.

These limitations are tracked in [ROADMAP.md](ROADMAP.md).
