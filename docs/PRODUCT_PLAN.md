# CMF Buds Pro 2 Manager — Product Plan

## Product direction

Make the app a dependable, polished companion for daily earbud use. Prioritize clear device state, controls that reflect confirmed device responses, and recovery paths when Bluetooth operations fail. Never label an operation as saved or applied until the earbuds confirm it.

## Experience and visual system

- **Overview:** one clear connection header, a compact earbud/case battery hero, ANC controls, and frequently used actions. Move diagnostics, long-form setup, and rarely used tools off the first screen.
- **Sound:** group ANC, earbud listening presets, bass, phone-side EQ, codec, and game mode into one sound workspace. Show device-backed values separately from phone-side audio processing.
- **Device:** firmware/model information, connection diagnostics, battery history, and per-earbud actions.
- **Tools:** finder, fit test, sleep timer, water eject, widgets, and automation. Keep safety confirmation directly beside actions that can produce loud sound.
- **Motion:** use short fade/slide transitions for navigation, spring only for direct manipulation, and animate values only when a confirmed state changes. Respect reduced-motion settings and avoid perpetual decorative animation.
- **Visual language:** dark neutral surfaces, one selected CMF accent, restrained gradients, consistent 20–24 dp card corners, aligned spacing, and minimum 48 dp touch targets. Check compact screens, large fonts, landscape, and accessibility labels.

## Useful feature priorities

1. **Device state truthfulness:** read back ANC, bass, latency and firmware; separate sent, acknowledged, timed-out, and unsupported states.
2. **Sound workspace:** verified persistent earbud settings, EQ only after protocol and device tests, and a clear split between earbud DSP and Android audio effects.
3. **Diagnostics:** transport health, last successful read, per-channel battery, firmware/model identity, and a shareable privacy-safe report.
4. **Convenience:** named listening profiles with explicit apply/read-back, configurable shortcuts/widgets, and battery/connection history.
5. **Firmware:** identify hardware revision, discover signed vendor update metadata, download only vendor-authenticated images, validate hashes/signatures, and implement resumable transfer plus recovery only after OTA protocol is established.

## Firmware update gate

Firmware update is a separate, high-risk protocol project. Current code has a vendor control channel and a firmware-version read command; neither proves an OTA transport. The public `debarkak/nothingx-pc` implementation reports device controls and firmware reads, while EQ/custom EQ and other areas remain under reverse engineering; it does not document CMF Buds Pro 2 OTA. No firmware binary source, signing validation, transfer protocol, recovery path, or physical-device test fixture is currently present.

Do not expose a flash button until all gates pass:

1. Capture official Nothing X OTA traffic for each supported hardware revision and firmware family.
2. Document service/characteristics, negotiation, chunking, checksums/signatures, acknowledgements, abort and resume behavior.
3. Establish image provenance and cryptographic verification; reject unknown revisions and downgrade attempts.
4. Test interrupted transfer, low battery, reconnect, and rollback/recovery on sacrificial hardware.
5. Add protocol fixtures and hardware test evidence; default to Nothing X handoff when any prerequisite is missing.

## Delivery sequence

- **A — UI foundation:** consolidate overview hierarchy, reduce competing cards, standardize tokens, motion, touch targets, and accessibility.
- **B — verified controls:** use one command/response state machine for read/write confirmation; add packet fixtures for known commands and validate against physical Buds Pro 2.
- **C — practical tools:** improve diagnostics, privacy-safe export, profiles, and per-widget configuration.
- **D — OTA research:** capture and document Nothing X's update flow and device/revision matrix. No flashing UI during research.
- **E — OTA implementation:** only after the firmware gates above are backed by evidence and recoverability tests.

## Current evidence and gaps

- Firmware version read and settings channel exist in the app; physical compatibility is still unverified.
- The model query (`0xC01C`) returns a 16-bit little-endian model code. B172-only controls and the advisory firmware catalog are enabled only after a B172 response; unknown model IDs stay read-only. B187 is not yet included in the verified controls matrix.
- A community Nothing X implementation documents Enhanced Bass GET/SET. The app now uses this command and requests readback, but persistence and response behavior still need physical validation.
- A second implementation documents B172 listening-preset GET `0xC050` and SET `0xF01D`, and is live-tested on CMF Buds Pro 2 B172 / firmware `1.0.1.74`. The app now reads and changes this device preset. Persistence across power-off still needs validation. The `0xF01D` command is distinct from ANC; do not alias the two.
- The same implementation documents custom EQ (`0xC044` / `0xF041`), latency mode (`0xC041` / `0xF040`) and in-ear detection (`0xC00E` / `0xF004`). The app exposes these with readback; physical checks and persistence after reconnect still need validation.
- The official-app-derived B172 model data documents six spatial modes (`0xC04F` / `0xF052`) and the 4-byte gesture map (`0xC018` / `0xF003`). The app now reads and edits left/right double tap, triple tap and hold actions, plus all six spatial modes. Device/case gesture mappings and action codes outside these verified menus remain hidden.
- B172 also exposes dual-connection enable/read (`0xF01A` / `0xC027`) and its remembered-device list (`0xC028`), with per-device connect state (`0xF01B`). The app now reads and manages the two active device slots; enabling/disabling the mode prompts for the required earbud reboot.
- The dual-connection list supports reading and connecting/disconnecting remembered devices. No verified command for deleting one stored device was found; the app provides the manufacturer's case-button reset steps for clearing device memory, which also resets earbud settings.
- A newly reviewed CMF Buds Pro 2 implementation explicitly excludes firmware updates and directs users to the official app; no OTA sequence was found.
- The current firmware catalog contains a community-posted minimum for one firmware family; it is advisory and is not an OTA feed.
- The previous signing key has been exposed in repository history; resolve the release-key rotation item before distributing a trusted APK.
