<div align="center">

<img src="https://raw.githubusercontent.com/copilotfather/cmf-buds-pro-2-manager/main/app/src/main/res/drawable/cmf_case_dial.png" width="130" alt="CMF Smart Dial Logo" />

# 🔘 CMF Buds Pro 2 Manager

### *Next-Generation Studio Audio Controller & Hardware Utility for Nothing CMF Buds Pro 2*

<p align="center">
  <a href="https://github.com/copilotfather/cmf-buds-pro-2-manager/releases"><img src="https://img.shields.io/github/v/release/copilotfather/cmf-buds-pro-2-manager?color=FF3B30&label=RELEASE&style=for-the-badge&logo=android" alt="Latest Release" /></a>
  <a href="https://github.com/copilotfather/cmf-buds-pro-2-manager/releases"><img src="https://img.shields.io/badge/APK%20Size-11.0%20MB-34C759?style=for-the-badge&logo=googleplay" alt="APK Size" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-007AFF?style=for-the-badge" alt="License" /></a>
  <img src="https://img.shields.io/badge/Architecture-Kotlin%20%7C%20Compose-AF52DE?style=for-the-badge&logo=kotlin" alt="Tech Stack" />
  <img src="https://img.shields.io/badge/Design-Liquid%20Gray%20Glass-FF9500?style=for-the-badge" alt="Design System" />
</p>

<br/>

<p align="center">
  <b><a href="#-english-documentation">English Documentation</a></b> • 
  <b><a href="#-русская-документация">Русская Документация</a></b> • 
  <b><a href="https://github.com/copilotfather/cmf-buds-pro-2-manager/releases/latest">⚡ Download Latest APK</a></b>
</p>

<br/>

---

### 📸 Visual Showcase & Interface Gallery

<table>
  <tr>
    <td align="center" width="25%"><b>💎 Overview & Control</b></td>
    <td align="center" width="25%"><b>📊 Deep Analytics</b></td>
    <td align="center" width="25%"><b>🎛️ Studio Master EQ</b></td>
    <td align="center" width="25%"><b>🛠️ Hardware Tools</b></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/overview_preview.png" width="230" alt="Overview Stage" /></td>
    <td align="center"><img src="docs/screenshots/analytics_preview.png" width="230" alt="Analytics Dashboard" /></td>
    <td align="center"><img src="docs/screenshots/sound_preview.png" width="230" alt="Studio Master Equalizer" /></td>
    <td align="center"><img src="docs/screenshots/tools_preview.png" width="230" alt="Hardware Diagnostics & Tools" /></td>
  </tr>
</table>

---

</div>

<br/>

<a name="-english-documentation"></a>
## 🇬🇧 English Documentation

**CMF Buds Pro 2 Manager** is an independent, high-performance companion app engineered from the ground up for Nothing's flagship **CMF Buds Pro 2** earphones. By leveraging **direct low-level Bluetooth protocol packets (SPP / BES RFCOMM)**, it provides audiophile-tier control, hardware switching, and real-time telemetry not available in stock companion software.

Designed according to the proprietary **Liquid Gray Glass** design specification: smoked translucent acrylic textures, four-corner specular light refraction, smooth GPU transitions, and 120Hz micro-interactions.

---

### ✨ Core Capabilities

#### 🌫️ 1. Liquid Gray Glass Design System
* **Smoked Acrylic Geometry:** Dark smoked glass surfaces (`#1A1C20` base, 0.55 opacity) with dynamic backdrop blur.
* **Reflective Rim Lighting:** Multi-point gradient borders mimicking physical glass edges catching ambient light.
* **High-FPS Rendering Pipeline:** Eliminates recomposition thrashing for zero-stutter 60 / 120 FPS navigation.
* **Floating Island Navigation:** Minimalist glass pill dock with smooth glow indicators.

#### 📡 2. High-Res Audio Codec Engine
* **Sony LDAC 990 kbps (24-bit / 96 kHz):** Audiophile bitrates unlocked through direct Android A2DP / Bluetooth codec profile integration.
* **Hardware Switching via SPP:** Emits direct `0xF05A` vendor opcode sequences to configure the internal BES chipset.
* **Full Codec Suite:** Native support for LDAC, LHDC V5, Qualcomm aptX HD / Adaptive, AAC, and SBC.

#### 📊 3. Deep Listening & Battery Analytics
* **SPL Volume Tiers:** Automatic logging of hours spent across *Low (0–30%)*, *Mid (31–70%)*, and *High (71–100%)* decibel levels to protect hearing health.
* **Weekly Histogram:** Day-by-day playback visualization identifying peak listening days and favorite listening hours.
* **Case Cycle Counter:** Independent tracking of daily and cumulative case lid open/close events.
* **Battery Degradation Monitor:** Historical discharge analysis calculating true full-charge endurance and estimated cell health.

#### 🎛️ 4. Studio Master Equalizer & Soundstage
* **Live Bezier Spline:** Real-time hardware Canvas frequency response curve reflecting discrete gain shifts.
* **5-Band Precision Sliders:** 60 Hz (Sub), 230 Hz (Bass), 910 Hz (Mids), 3.6 kHz (Presence), 14 kHz (Air) with ±1 dB steps.
* **Engineered Presets:** `Reference Studio`, `Basshead 808`, `Vocal Clarity`, `Electronic`, `Rock & Metal`, `Acoustic / Jazz`.

#### 🛡️ 5. Extended Hardware Suite
* **Continuous ANC Engine:** Stepless control between *Off*, *Transparency*, and *Active Noise Cancellation* (up to 50 dB depth with auto-adaptive ambient microphones).
* **Ultra Bass Stepper:** Direct DSP multi-tier bass boost (Levels 0 through 5).
* **Smart Dial Customization:** Rebind the physical case dial rotary actions and single/double clicks.
* **Sonic Water Eject:** Dual-frequency 140–180 Hz resonant acoustic sweeps to expel moisture from driver meshes.
* **Sonar Locator & GPS Log:** Directional ultrasonic buzzer + GPS location memory of the last Bluetooth disconnect.
* **Low-Latency PC Audio Relay:** Wi-Fi / USB audio bridge for ultra-low latency desktop gaming and video playback.

---

### 📥 Installation & Setup

1. Grab the latest standalone APK from the **[Releases](https://github.com/copilotfather/cmf-buds-pro-2-manager/releases)** section.
2. Install the APK on any device running **Android 8.0 (Oreo) or higher**.
3. Pair your **CMF Buds Pro 2** via standard Bluetooth settings.
4. Launch the app — it automatically binds to your earbuds and sets up the hardware link.

---

<br/>

<a name="-русская-документация"></a>
## 🇷🇺 Русская Документация

**CMF Buds Pro 2 Manager** — это ультимативный центр управления для флагманских TWS-наушников **CMF Buds Pro 2** от Nothing. Приложение взаимодействует с гарнитурой через **низкоуровневые сервисные команды Bluetooth (SPP / BES RFCOMM)**, предоставляя полный доступ к скрытым возможностям чипсета, студийному звуку и глубокой телеметрии.

Интерфейс спроектирован по эстетике **Liquid Gray Glass**: дымчатый матовый акрил, физическая рефракция света по граням, плавная физика без задержек и поддержка экранов 120 Гц.

---

### 🌟 Ключевой функционал

#### 🌫️ 1. Эстетика Liquid Gray Glass
* **Дымчатое серое стекло:** Матовые полупрозрачные карточки с благородным графитовым оттенком и динамическим размытием.
* **Зеркальная огранка (Specular Refraction):** Градиентные световые блики по контурам элементов, создающие эффект объёмного стекла.
* **Аппаратная оптимизация:** Исключены тяжёлые пружинные анимации — идеально ровные 60 / 120 кадров в секунду.
* **Парящий стеклянный остров:** Лаконичная нижняя панель навигации с деликатной подсветкой активного экрана.

#### 📡 2. Модуль Hi-Res Аудиокодеков
* **Sony LDAC 990 кбит/с (24 бит / 96 кГц):** Максимальное качество передачи аудио без потерь через прямое переключение A2DP профилей Android.
* **Аппаратное переключение через SPP:** Прямая посылка служебных пакетов `0xF05A` в аудиочип BES гарнитуры.
* **Полный спектр кодеков:** Поддержка LDAC, LHDC V5, Qualcomm aptX HD / Adaptive, AAC и SBC.

#### 📊 3. Аналитика прослушивания и батареи
* **Анализ громкости (SPL):** Подсчёт времени на тихой (0–30%), средней (31–70%) и высокой (71–100%) громкости для защиты слуха.
* **Гистограмма за неделю:** Детальный график активности по дням с выявлением пиковых дней и любимого времени суток.
* **Счётчик циклов кейса:** Независимый счётчик открытий крышки за текущий день и суммарно за всё время.
* **Ресурс аккумулятора:** Расчёт среднего времени автономной работы от 100% заряда и мониторинг износа аккумулятора.

#### 🎛️ 4. Студийный Мастер-Эквалайзер
* **Живая сплайн-кривая Безье:** Аппаратный рендеринг частотной характеристики в реальном времени.
* **5-полосная прецизионная настройка:** 60 Гц (саб), 230 Гц (бас), 910 Гц (середина), 3.6 кГц (презенс), 14 кГц (воздух) с шагом ±1 dB.
* **Аудиофильские пресеты:** `Reference Studio`, `Basshead 808`, `Vocal Clarity`, `Electronic`, `Rock & Metal`, `Acoustic / Jazz`.

#### 🛡️ 5. Аппаратные инструменты и безопасность
* **Бесступенчатый шумодав:** Выкл, Режим прозрачности, Активное шумоподавление (до 50 дБ с автоподстройкой под окружающий шум).
* **Усилитель баса Ultra Bass:** 6 уровней аппаратного алгоритма усиления низких частот (0–5).
* **Настройка Smart Dial:** Переназначение колесика на кейсе (вращение, одинарный, двойной клик).
* **Выталкивание воды (Sonic Eject):** Акустические колебания 140–180 Гц для безопасного удаления влаги из звуководов.
* **Поиск наушников (Sonar):** Ультразвуковой звуковой маяк + автоматическое сохранение координат GPS в точке разрыва связи.
* **Аудиомост с ПК:** Прямая трансляция звука с компьютера на наушники через Wi-Fi или USB с ультранизкой задержкой.

---

### 📦 Быстрая установка

1. Скачайте актуальный файл `.apk` со страницы **[Релизов (Releases)](https://github.com/copilotfather/cmf-buds-pro-2-manager/releases)**.
2. Установите приложение на телефон под управлением **Android 8.0 или новее**.
3. Подключите **CMF Buds Pro 2** в настройках Bluetooth.
4. Откройте приложение — статус подключения и все параметры синхронизируются мгновенно.

---

### 🛠️ Сборка из исходников

```bash
# Клонирование репозитория
git clone https://github.com/copilotfather/cmf-buds-pro-2-manager.git
cd cmf-buds-pro-2-manager

# Сборка Release APK
./gradlew assembleRelease
```

Собранный файл будет находиться по пути:  
`app/build/outputs/apk/release/app-release.apk`

---

### 📜 Лицензия / License

Распространяется по лицензии **MIT License**. Подробнее см. в файле [LICENSE](LICENSE).

<div align="center">
  <sub>Создано с любовью для сообщества Nothing & CMF • Maintainer: <b><a href="https://github.com/copilotfather">copilotfather</a></b></sub>
</div>
