---
name: PocketCraft build notes
description: How builds work — no Android SDK in Replit, CI via GitHub Actions, local setup scripts.
---

# PocketCraft Build Notes

## No Android SDK in Replit
The Android SDK cannot be installed in Replit's default environment (disk space + container limits). **All APK builds run through GitHub Actions CI.**

## Primary build path: GitHub Actions
`.github/workflows/build.yml` triggers on push to `main` or `develop`:
1. Sets up JDK 17 (Temurin) for Gradle
2. Sets up Android SDK via `android-actions/setup-android@v3`
3. Downloads the aarch64 JRE asset dynamically via Adoptium API (fallback: pinned Azul Zulu URL)
4. Runs `./gradlew assembleDebug`
5. Uploads `PocketCraft-debug-N.apk` as a downloadable artifact (retained 30 days)

## Local build path: scripts/install-all.sh
For local/Replit builds when the Android SDK is needed:
- `bash scripts/install-all.sh` — installs everything + builds APK
- `bash scripts/install-all.sh --no-build` — setup only, skip Gradle
- `bash scripts/install-all.sh --ndk` — also installs NDK (~2 GB)

Requires Java 17+ on PATH. In Replit: `.replit → [nix] packages` must include `jdk21_headless`.

## JRE asset download strategy
The CI workflow and `install-all.sh` both:
1. Try Adoptium (Eclipse Temurin) JSON API: `https://api.adoptium.net/v3/assets/latest/21/jre?architecture=aarch64&image_type=jre&jvm_impl=hotspot&os=linux&vendor=eclipse`
2. Fall back to a pinned Azul Zulu URL: `https://cdn.azul.com/zulu/bin/zulu21.42.19-ca-jre21.0.7-linux_aarch64.tar.gz`

**Why:** Azul Zulu CDN URLs are version-specific and go stale when a new build is released. Adoptium API always returns the current LTS.

## Replit .replit Nix packages
Must include: `jdk21_headless`, `curl`, `unzip`, `python3`
- `jdk21_headless` provides Java 21 for Gradle (AGP 8.x requires Java 17+)
- `python3` is needed by the JRE URL discovery script in install-all.sh and build.yml
- After changing Nix packages, reopen the Shell for the new environment to load

## Gradle / AGP versions
- AGP: 8.6.1 (requires Gradle 8.7+)
- Gradle: 8.9 (set in `gradle/wrapper/gradle-wrapper.properties`)
- Kotlin: 2.0.21
- KSP: 2.0.21-1.0.27 (must match Kotlin version)
