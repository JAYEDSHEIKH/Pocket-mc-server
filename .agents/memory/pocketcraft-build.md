---
name: PocketCraft Build Notes
description: How to build PocketCraft — CI-only Gradle setup, JDK asset bundling, and wrapper JAR provenance.
---

## No local Android SDK
The Replit environment has no Android SDK or Gradle build capability. All APKs are produced exclusively by GitHub Actions.

**Get a debug APK:** push to `main` or `develop` → Actions → latest run → Artifacts.

## Gradle wrapper JAR
`gradle/wrapper/gradle-wrapper.jar` was downloaded from `https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar` (43 KB, verified Zip/JAR). The CI workflow also uses `gradle/actions/setup-gradle@v4` which can bootstrap it automatically.

**Why not committed as base64 or via script:** binary must exist on disk before `./gradlew` can run. The direct download approach is the cleanest.

## aarch64 JDK 21 asset
The build workflow downloads Azul Zulu JRE 21.0.5 (headless, aarch64 Linux) from `cdn.azul.com` and places it at `app/src/main/assets/jre/aarch64-jdk21.tar.gz` before Gradle runs. The asset is ~80 MB and NOT committed to the repo. JreInstaller.kt extracts it to `filesDir/jre/` on first app launch using the system `tar` command.

## Paper Fill API base URL
`https://fill.papermc.io/v3/` — the old `api.papermc.io/v2` is retired. Do not use the v2 endpoint.
