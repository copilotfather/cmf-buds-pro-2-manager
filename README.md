# CMF Buds Pro 2 Manager (Android)

Open-source, lightweight and fully customizable Android controller & audio suite for **CMF Buds Pro 2** by Nothing.

---

## Highlights

- **Hi-Fi Studio & Bento Architecture**: Clean, dark titanium obsidian interface with fluid spring physics.
- **Deep Usage Analytics**:
  - Detailed listening volume distribution (Low 0–30%, Mid 31–70%, High 71–100%).
  - Weekly activity histogram with peak day detection and favorite listening hours.
  - Case open counter (daily and all-time tracking).
  - Average battery duration estimation (hours on full 100% charge) and battery health tracking.
- **High-Res Bluetooth Codec Engine**:
  - Direct hardware switching on the earbuds (Sony LDAC, LHDC V5, Qualcomm aptX HD / Adaptive, AAC, SBC).
  - 24-bit / 96 kHz Hi-Res Audio Wireless support.
- **Master Equalizer**:
  - Smooth Bezier response curve on Canvas with real-time feedback.
  - 5 discrete frequency sliders (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz).
  - Studio presets (*Reference, Basshead 808, Vocal Clarity, Electronic, Rock & Metal, Acoustic*).
- **Active Noise Cancellation (ANC)**:
  - Continuous touch slider (*Off / Transparency / Noise Cancellation*).
  - 4 depth levels: Low, Mid, High (up to 50 dB), and Adaptive Auto-ANC based on ambient mic sensing.
- **Smart Dial Customization**:
  - Rotate action: Volume step, ANC modes, Ultra Bass level (0–5).
  - Click action: Play/Pause, Low-Latency Game Mode, Mute.
- **Tools & Safety**:
  - Sonic Water Eject (140–180 Hz kinetic resonant wave).
  - WHO Hearing Safe dose tracking.
  - Acoustic Sonar Chirp & GPS disconnect location.
  - Voice Whisper battery announcements.
  - Ultra Low-Latency PC Audio Relay Bridge over Wi-Fi / USB.

---

## Building from Source

```bash
git clone https://github.com/your-username/cmf-buds-pro-2-manager.git
cd cmf-buds-pro-2-manager
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License
MIT License.
