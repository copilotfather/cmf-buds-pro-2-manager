<div align="center">

# 🎧 CMF Buds Pro 2 Manager

**A studio-grade, open-source Android manager & audio suite for CMF Buds Pro 2 by Nothing.**

[![Release](https://img.shields.io/github/v/release/copilotfather/cmf-buds-pro-2-manager?color=FF5722&style=for-the-badge&logo=android)](https://github.com/copilotfather/cmf-buds-pro-2-manager/releases)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android)](https://www.android.com)
[![Architecture](https://img.shields.io/badge/UI-Liquid%20Gray%20Glass-708090?style=for-the-badge)](README.md)

<p align="center">
  <a href="#-english">English</a> •
  <a href="#-русский">Русский</a>
</p>

---

<p align="center">
  <img src="docs/screenshots/overview_preview.png" width="300" alt="Overview UI" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/screenshots/analytics_preview.png" width="300" alt="Analytics UI" />
</p>

</div>

---

<a name="-english"></a>
## 🇬🇧 English

### 🌟 Key Highlights

- **Liquid Gray Glass Aesthetics**: Smoked frosted glass cards (`#301E212A`), four-point specular rim gradients, floating navigation pill, and native 60/120 FPS hardware rendering.
- **Hi-Res Bluetooth Audio Codecs**:
  - Direct hardware protocol switching (`0xF05A` SPP packet) for Sony LDAC (up to 990 kbps, 24-bit / 96 kHz).
  - Support for LHDC V5 (Savitech), Qualcomm aptX HD / Adaptive, Apple AAC, and Standard SBC.
- **Deep Usage Analytics**:
  - **Volume Time Distribution**: Detailed tracking for Low (0–30%), Mid (31–70%), and High (71–100%) volume sessions.
  - **Weekly Activity Histogram**: Daily listening duration with peak day detection and favorite listening hours.
  - **Case Open Counter**: Real-time tracking of case lid opens (daily and cumulative).
  - **Average 100% Battery Duration**: Smart estimation of full-charge life span and battery health condition.
- **Studio Master Equalizer**:
  - Real-time Bezier response curve drawn on GPU Canvas with interactive frequency points.
  - 5 discrete studio frequency sliders (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz).
  - Audiophile sound presets (*Reference, Basshead 808, Vocal Clarity, Electronic, Rock & Metal, Acoustic*).
- **Comprehensive Hardware Controls**:
  - **Continuous ANC Slider**: Off, Transparency, Active Noise Cancellation (Low, Mid, High up to 50 dB, and Mic-calibrated Auto-ANC).
  - **Ultra Bass**: Multi-step hardware bass boost (Levels 0–5).
  - **Smart Dial Config**: Custom assignments for case dial rotation (volume, ANC, bass) and single/double clicks.
  - **Sonic Water Eject**: 140–180 Hz kinetic acoustic wave for driver mesh clearing.
  - **Acoustic Sonar Chirp & Find My Buds**: High-pitch dual-tone locating alarm + last-known GPS disconnect coordinates.

### 📥 Download & Installation

1. Go to the [Latest Releases](https://github.com/copilotfather/cmf-buds-pro-2-manager/releases) page.
2. Download `CMF_Buds_Pro_2_Manager.apk`.
3. Install it on your Android smartphone (Android 8.0+ supported).

### 🛠️ Building from Source

```bash
git clone https://github.com/copilotfather/cmf-buds-pro-2-manager.git
cd cmf-buds-pro-2-manager
./gradlew assembleRelease
```

---

<a name="-русский"></a>
## 🇷🇺 Русский

### 🌟 Основные возможности

- **Дизайн Liquid Gray Glass**: Премиальное матовое серое стекло (`#301E212A`) с четырёхточечными зеркальными бликами по контуру (specular reflection), парящим стеклянным островом навигации и плавным рендерингом 60/120 Гц без лагов.
- **Аудиокодеки высокого разрешения (Hi-Res Wireless)**:
  - Прямое аппаратное переключение DSP-чипа наушников (пакет `0xF05A`) на кодек Sony LDAC (до 990 кбит/с, 24 бит / 96 кГц).
  - Поддержка LHDC V5 (Savitech), Qualcomm aptX HD / Adaptive, Apple AAC и стандартного SBC.
- **Глубокая аналитика использования**:
  - **Распределение времени по громкости**: Подсчет минут прослушивания на Тихой (0–30%), Средней (31–70%) и Высокой (71–100%) громкости.
  - **Гистограмма активности по дням недели**: Замер ежедневного времени прослушивания, определение самого активного дня и любимого интервала времени суток.
  - **Счётчик открытий кейса**: Фиксация каждого открытия крышки (за сегодня и суммарно за всё время).
  - **Средняя автономность от 100%**: Расчёт реального времени работы на полном заряде и оценка здоровья аккумулятора.
- **Студийный интерактивный эквалайзер**:
  - Живая кривая Безье частотного отклика на GPU Canvas со светящимися контрольными точками.
  - 5 точных дискретных регуляторов частот (60 Гц, 230 Гц, 910 Гц, 3.6 кГц, 14 кГц) с шагом ±1 dB.
  - Пресеты студийного уровня (*Reference, Basshead 808, Vocal Clarity, Electronic, Rock & Metal, Acoustic*).
- **Полный спектр аппаратных инструментов**:
  - **Бесшовный слайдер шумоподавления**: Выкл, Прозрачность, Активный шумодав (до 50 дБ) + Адаптивный авто-ANC по микрофону смартфона.
  - **Ultra Bass**: 6 ступеней аппаратного усиления низких частот (уровни 0–5).
  - **Настройка Smart Dial**: Привязка вращения колесика кейса (громкость, ANC, бас) и кликов.
  - **Очистка от влаги и пыли (Sonic Water Eject)**: Резонансная кинетическая волна 140–180 Гц для выталкивания капель воды из сеток.
  - **Поиск наушников (Sonar)**: Громкий поисковый сигнал + автосохранение GPS-точки при разрыве связи.

### 📥 Скачивание и установка

1. Перейдите во вкладку [Релизы (Releases)](https://github.com/copilotfather/cmf-buds-pro-2-manager/releases).
2. Скачайте файл `CMF_Buds_Pro_2_Manager.apk`.
3. Установите его на ваш телефон (поддерживается Android 8.0 и новее).

### 🛠️ Сборка из исходников

```bash
git clone https://github.com/copilotfather/cmf-buds-pro-2-manager.git
cd cmf-buds-pro-2-manager
./gradlew assembleRelease
```

---

## 📄 License / Лицензия

Distributed under the **MIT License**. See `LICENSE` for more information.
