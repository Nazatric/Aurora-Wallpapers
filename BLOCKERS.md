# Verification and release status

**Current source status recorded: 2026-10-12.** Results below are revision-scoped: an earlier successful build or emulator run does not verify the provider/UI migration in this working tree.

## Current source: Android build, tests, emulator, screenshots

- This workspace has no `java`, `javac`, or `adb` on `PATH`; `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset. `./gradlew --version` exits because Java is unavailable. No Android build, Gradle test suite, lint, APK generation/install, or `apksigner` verification has run for the current source.
- Static checks passed: `python3 -m py_compile scripts/*.py .github/scripts/*.py`, `bash -n scripts/live_source_smoke.sh`, and XML well-formedness parsing for `app/src/main/**/*.xml`. These do not run the app or contact live providers. The GitHub Actions workflow has not yet run against this provider/UI change.
- No current emulator screenshots were captured or manually inspected. The updated UI smoke journey remains unrun on this source revision.
- No supported physical Android device or 120 Hz panel was available. Startup, frame pacing/FPS, memory, battery, APK size, and prolonged-browsing measurements are not claimed. Macrobenchmark/Baseline Profile remain follow-up work.

## Previous revision evidence (historical; not proof for current source)

- Commit [`77f3055`](https://github.com/Nazatric/Aurora-Wallpapers/commit/77f3055f0a96de4c9e14e55429347143a6671191) passed build and hosted API 35 emulator smoke in [CI run 38147269114](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38147269114) on 2026-10-11. That run used the **previous Wallhaven/Openverse source set**; its UI assertions, provider results, and screenshots do not validate the NASA/APOD, Art Institute, retired-source, or current UI flows.
- A later changelog/workflow-only revision passed tests, lint, debug/release builds, and debug-signature verification in [CI run 38153473753](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38153473753). Its emulator smoke failed at initial For You discovery because Wallhaven was unreachable. Production signing was not configured.
- Earlier emulator screenshots were uploaded as artifacts but could not be manually inspected in this sandbox; artifact downloads returned EOF. Automated UI assertions are not a substitute for that visual review.

## Live sources and rights

- The sandbox allows outbound access only to selected development/package hosts, not the Wallhaven, NASA, or Art Institute production API/media hosts. The current provider implementations and optional `scripts/live_source_smoke.sh` remain live-unverified here.
- Wallhaven uses its official API with source-side `purity=100`. The app has no global SFW/NSFW preference, hidden keyword/tag rejection, or category rejection. Current API reachability and responses still need testing.
- NASA APOD Basic response parsing and live endpoint behavior need validation. Credit/copyright fields do not establish reuse rights: APOD remains browse-only, with download and wallpaper setting disabled.
- Art Institute query syntax, current API responses, IIIF image metadata and availability need live validation. Only records marked public domain with an image ID are accepted; dimensions remain unknown if the IIIF metadata cannot be obtained.
- Reddit remains paused until authorized official OAuth access is configured; no scraping or client impersonation is used. Pixabay permission/terms for this use are unverified and it is not integrated. Lorem Picsum is omitted.
- Openverse is removed from active provider/query use. Saved legacy metadata and existing local files remain readable; new retired-source previews, downloads, and remote wallpaper preparation are blocked.

## Production release

- Source version is `versionName` **0.1**, `versionCode` **3**. No APK from the current source has been produced or verified; source values are not evidence of an installable artifact.
- No production signing key or signed release APK has been verified, and no new tag or GitHub release is claimed. The current workspace has no complete signing configuration. Never put signing material in Git or chat.
- Release signing requires `AURORO_KEYSTORE_BASE64`, `AURORO_KEYSTORE_PASSWORD`, `AURORO_KEY_ALIAS`, and `AURORO_KEY_PASSWORD`. The tag workflow now checks for an existing release and refuses to overwrite it.

## Required follow-up

1. Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` with JDK 17 and Android SDK Platform 37/Build Tools 37.0.0; record the exact commit and CI result.
2. Verify the debug APK with `apksigner`, install that exact artifact, run `scripts/emulator_ui_smoke.py`, and manually review the resulting screenshots.
3. Run `scripts/live_source_smoke.sh` from a network that can reach all three approved public APIs; preserve each provider's actual response/error independently.
4. Reconfirm reuse rights per NASA item before enabling any reuse; obtain Reddit authorization and confirm Pixabay permission before enabling those sources.
5. Measure startup, image loading, frame pacing, memory, and long-session behavior on named supported physical devices; record build variant, cache state, network, refresh rate, and tool. Do not infer universal 120 Hz performance.
6. Publish a production APK only after signing and `apksigner` verification, without replacing any existing release.
