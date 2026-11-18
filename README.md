<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Verve Browser" width="92" height="92">

<br><br>

# Verve Browser

**A tactile, thumb-first Android browser crafted with Material 3 Expressive—built out of a passion for thoughtful design, fluid motion, and effortless one-handed usability.**

<br>

<p align="center">
  <a href="https://github.com/afsanifti/Verve-Browser/releases/latest/download/Verve-Browser.apk"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-download.svg"><img src="docs/readme/light/btn-download.svg" alt="Download APK" height="54"></picture></a>
  <a href="https://github.com/afsanifti/Verve-Browser/releases"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-releases.svg"><img src="docs/readme/light/btn-releases.svg" alt="All Releases" height="54"></picture></a>
  <a href="https://github.com/afsanifti/Verve-Browser"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-source.svg"><img src="docs/readme/light/btn-source.svg" alt="Source Code" height="54"></picture></a>
  <a href="#capabilities"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-features.svg"><img src="docs/readme/light/btn-features.svg" alt="Explore Features" height="54"></picture></a>
  <a href="https://github.com/afsanifti/Verve-Browser/issues/new"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-issue.svg"><img src="docs/readme/light/btn-issue.svg" alt="Report an Issue" height="54"></picture></a>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-m3e.svg"><img src="docs/readme/light/badge-m3e.svg" alt="Material 3 Expressive" height="30"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-kotlin.svg"><img src="docs/readme/light/badge-kotlin.svg" alt="Kotlin + Compose" height="30"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-rust.svg"><img src="docs/readme/light/badge-rust.svg" alt="Rust Adblock" height="30"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-android.svg"><img src="docs/readme/light/badge-android.svg" alt="Android 12+" height="30"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-ota.svg"><img src="docs/readme/light/badge-ota.svg" alt="In-App Updater" height="30"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-oss.svg"><img src="docs/readme/light/badge-oss.svg" alt="Open Source" height="30"></picture>
</p>

<br>

[Overview](#overview) · [Interface](#interface) · [Capabilities](#capabilities) · [Stack](#stack) · [Build](#build)

</div>

<br>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-1.svg" height="26" align="top"> Overview

I built **Verve** out of a deep interest in mobile UI design and everyday usability. Most modern browsers push core controls to the top of tall screens and treat navigation as static, rigid buttons. Verve rethinks that experience around **Material 3 Expressive**—keeping everything within natural thumb reach inside a responsive bottom search pill driven by spring physics and subtle haptics.

- **Theme-Chameleon Bottom Bar:** On the homescreen and menus, Verve harmonizes with your system's Material You wallpaper colors. The moment you start browsing, the bottom bar dynamically adapts to match the active webpage's theme color—blending the browser chrome seamlessly into the site you are reading.
- **Tactile, Gesture-First Ergonomics:** Swipe horizontally across the bottom search pill to glide between open tabs with spring-compressed side buttons, or scroll down to watch the toolbar tuck smoothly out of the way.
- **Native Rust Performance:** Ad and tracker filtering runs inside a compiled **Rust** JNI engine (`native_adblock`) rather than sluggish script injection, keeping page loads fast and light on battery.

> **🧪 Alpha Stage — Join as an Alpha Tester:**
> Verve is currently in active **Alpha** (`v1.0.0-alpha.11`). Because features and animations are evolving rapidly, **there will be plenty of bugs and rough edges**. If you love testing early software, feel free to install the APK, report issues you spot, and get **daily updates** delivered straight inside the app via **About Verve**.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-2.svg" height="26" align="top"> Interface

<div align="center">
  <table>
    <tr>
      <td align="center" width="25%">
        <img src="docs/screenshots/verve-1.png" width="100%" alt="Minimal Homescreen"><br>
        <sub><b>Minimal Homescreen</b></sub>
      </td>
      <td align="center" width="25%">
        <img src="docs/screenshots/verve-2.png" width="100%" alt="Search Bar"><br>
        <sub><b>Search Bar</b></sub>
      </td>
      <td align="center" width="25%">
        <img src="docs/screenshots/verve-3.png" width="100%" alt="Browsing View"><br>
        <sub><b>Browsing View</b></sub>
      </td>
      <td align="center" width="25%">
        <img src="docs/screenshots/verve-4.png" width="100%" alt="Collapsed Bottom Bar"><br>
        <sub><b>Collapsed Bottom Bar</b></sub>
      </td>
    </tr>
  </table>
</div>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-3.svg" height="26" align="top"> Capabilities

### <img src="docs/readme/shape-4.svg" height="18" align="top"> Context-Aware Design & Theming
- **System + Webpage Adaptive Colors:** Follows your device's system light/dark theme and dynamic wallpaper palette at rest, then shifts the bottom toolbar's tonal colors to match the webpage's theme color while browsing.
- **Material 3 Expressive Motion:** Built with Jetpack Compose and `MaterialKolor` using expressive container shapes, spring-driven transitions, and edge-to-edge system bar styling.

### <img src="docs/readme/shape-5.svg" height="18" align="top"> Gesture-Driven Usability
- **Scroll-Aware Search Pill:** Automatically collapses into a compact bar as you read down a page and expands smoothly when scrolling up or tapping.
- **Horizontal Pill Tab Swiping:** Drag left or right directly on the bottom bar to switch between open tabs with organic button compression, edge rubber-banding, and haptic detents.
- **One-Tap Incognito Mode:** Toggle an isolated private session straight from the search bar with dedicated dark tonal styling.

### <img src="docs/readme/shape-6.svg" height="18" align="top"> Native Speed & Daily Alpha Updates
- **Native Rust Ad-Blocker:** Powered by `native_adblock` compiled via `cargo-ndk` (`arm64-v8a` & `x86_64`), blocking ads and trackers at the native JNI layer.
- **Pooled WebViews & Live Previews:** `TabWebViewPool` keeps tab switching responsive while `TabThumbnailCache` captures clean, toolbar-free page snapshots across RAM and disk.
- **Built-In Daily Updater:** Fetches new alpha releases directly from GitHub's CDN, verifies APK SHA-256 checksums before installing, and displays formatted Markdown release notes in **About Verve**.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-4.svg" height="26" align="top"> Stack

| Component | Technology |
| :--- | :--- |
| **App & UI** | Kotlin · Jetpack Compose · Material 3 Expressive · `MaterialKolor` |
| **Ad-Blocking Core** | Rust · JNI · `cargo-ndk` (`arm64-v8a`, `x86_64`) |
| **Build System** | Gradle Kotlin DSL · JDK 21 · Android SDK (API 31+) |
| **Release Pipeline** | GitHub Actions · `apksigner` · Automated SHA-256 `update.json` |

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-6.svg" height="26" align="top"> Build

Ensure you have **JDK 21**, the **Android SDK**, and a **Rust toolchain** with [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk) installed:

```bash
# 1. Clone the repository
git clone [https://github.com/afsanifti/Verve-Browser.git](https://github.com/afsanifti/Verve-Browser.git)
cd Verve-Browser

# 2. Add Rust Android targets and install cargo-ndk
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk

# 3. Compile native ad-blocker libraries
cd native_adblock
cargo ndk -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release
cd ..

# 4. Build the Android APK
./gradlew assembleDebug
```

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-1.svg" height="26" align="top"> License

Verve Browser is released under the terms in [`LICENSE`](LICENSE).