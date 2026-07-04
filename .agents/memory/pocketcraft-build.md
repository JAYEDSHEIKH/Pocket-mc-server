---
name: PocketCraft build notes
description: How to build PocketCraft, CI workflow, JRE bundling, and local dev limitations.
---

## No Android SDK in Replit
- Replit cannot compile the APK. All build output is from GitHub Actions.
- Code changes are committed to GitHub; CI builds and uploads the APK as an artifact.

## GitHub Actions CI (`.github/workflows/build.yml`)
- Triggers on push to main/develop/master and on workflow_dispatch
- Steps:
  1. Set up JDK 17 (for Gradle — not the bundled server JRE)
  2. Cache Gradle packages
  3. Download OpenJDK 21 aarch64 JRE from Adoptium API (`/v3/assets/latest/21/jdk?architecture=aarch64&image_type=jre&os=linux&vendor=eclipse`)
  4. Copy tarball to `app/src/main/assets/jre/aarch64-jdk21.tar.gz`
  5. `./gradlew assembleDebug`
  6. Upload APK artifact named `PocketCraft-debug-{run_number}.apk` (retention 30 days)
- The Adoptium API URL is stable — returns the latest JDK 21 release, filtered to linux/aarch64/jre/tarball

## Getting a debug APK
1. Push any change to main
2. Go to repo Actions → latest run → Artifacts section
3. Download `PocketCraft-debug-N.apk`
4. Enable "Install from unknown sources" on your Android device and install

## Local setup (if you had Android Studio)
- Run `scripts/install-all.sh` to set up local toolchain
- The JRE asset must be manually downloaded and placed at `app/src/main/assets/jre/aarch64-jdk21.tar.gz`
- Use Adoptium for the aarch64 JRE (Azul Zulu URLs go stale)

## Distribution
- Direct APK / GitHub Releases (not Play Store — app executes downloaded native code + JVM at runtime)
- minSdk 26, arm64-v8a ABI only
