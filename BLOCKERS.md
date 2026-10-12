# Verification and release status

**Current source status recorded: 2026-10-12.** Results are revision-scoped: successful builds or emulator runs on earlier revisions do not verify this multi-source discovery change.

## Current source: Android build, tests, emulator, screenshots

- This workspace has no `java`, `javac`, or `adb` on `PATH`; `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` are unset. `./gradlew` cannot run locally. No local Android build, Gradle test suite, lint, APK generation/install, or `apksigner` verification has run for this working tree.
- Static checks on the current working tree passed: Python syntax compilation for `scripts/*.py` and `.github/scripts/*.py`, `bash -n scripts/live_source_smoke.sh`, XML parsing of 12 Android resource files, and `git diff --check`. These checks do not compile Kotlin or execute the app.
- The latest CI run before these local edits, [38214793120](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38214793120), failed in the build/test step on commit `9d12af2`. Its compiler annotations reported unresolved `semantics`/`selected` references in `HomeScreen.kt` and `SearchScreen.kt`. Imports were corrected in the current working tree, but those fixes and the new coordinator/tests have not yet been compiled by CI.
- No current emulator screenshots were captured or manually inspected. The updated multi-source smoke journey has not run against this source revision.
- No supported physical Android device or 120 Hz panel was available. Startup, scrolling/navigation latency, frame pacing, image-load time, memory, battery, APK size, and prolonged-browsing measurements are not claimed. Macrobenchmark/Baseline Profile remain follow-up work.

## Previous revision evidence (historical; not proof for current source)

- Commit [`77f3055`](https://github.com/Nazatric/Aurora-Wallpapers/commit/77f3055f0a96de4c9e14e55429347143a6671191) passed build and hosted API 35 emulator smoke in [CI run 38147269114](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38147269114) on 2026-10-11. That run used the **previous Wallhaven/Openverse source set**; its UI assertions, provider results, and screenshots do not validate NASA/APOD, Art Institute, retired-source, or current UI flows.
- Earlier revisions passed tests, lint, debug/release builds, debug-signature verification, and hosted-emulator smoke in [CI run 38154259798](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38154259798), but they predate the approved-provider migration. They do not verify the current code. Production signing was not configured.
- Another earlier run ([38153473753](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38153473753)) built the then-current APK but its live-provider emulator smoke stopped at For You when Wallhaven was unreachable. This was not a physical-device test.
- Earlier emulator screenshots were uploaded as artifacts but could not be manually inspected in this sandbox; artifact downloads returned EOF. Automated UI assertions are not a substitute for visual review.

## Live sources and rights

- The sandbox allows outbound access only to selected development/package hosts, not the Wallhaven, NASA, or Art Institute production API/media hosts. Current source implementations, query syntax, API availability, image results, and media hosts remain live-unverified here.
- Wallhaven uses its official API with source-side `purity=100`. The app has no global SFW/NSFW preference, hidden keyword/tag rejection, or category rejection. Current API reachability and responses still need testing.
- NASA APOD Basic response parsing and live endpoint behavior need validation. APOD does not provide authoritative pixel dimensions in its response; rendition URL parameters are not treated as image measurements. Credit/copyright fields do not establish reuse rights: APOD remains browse-only, with download and wallpaper setting disabled. The [official APOD API README](https://github.com/nasa/apod-api) notes a planned archive/redirect change on **2026-12-01**; verify the current endpoint contract and migration before relying on post-cutover availability.
- Art Institute query syntax, live search results, IIIF image metadata, rate behavior, and availability need validation. Six source-specific query mappings are configured, with one museum query per For You mix; only records marked public domain with an image ID are accepted. Dimensions remain unknown if IIIF metadata cannot be obtained.
- Reddit remains paused until authorized official OAuth access is configured; no scraping, client impersonation, or access-limit bypass is used. Pixabay permission/terms for wallpaper-file use remain unverified and it is not integrated. Lorem Picsum is omitted.
- Openverse is removed from active provider/query use. Saved legacy metadata and existing local files remain readable; new retired-source previews, downloads, and remote wallpaper preparation are blocked.

## Production release

- Source version is `versionName` **0.1**, `versionCode` **3**. No APK from the current source has been produced or verified; source values are not evidence of an installable artifact.
- No production signing key or signed release APK has been verified, and no new tag or GitHub release is claimed. The current workspace has no complete signing configuration. Never put signing material in Git or chat.
- Release signing requires `AURORO_KEYSTORE_BASE64`, `AURORO_KEYSTORE_PASSWORD`, `AURORO_KEY_ALIAS`, and `AURORO_KEY_PASSWORD`. The tag workflow checks for an existing release and refuses to overwrite it.

## Required follow-up

1. Commit and push the current Kotlin/test changes; use the new CI result to fix every compile, test, or lint failure before claiming completion.
2. Verify the debug APK with `apksigner`, install that exact artifact, run `scripts/emulator_ui_smoke.py`, and manually review the resulting screenshots. Record any live-source skips/failures separately from app/UI verification.
3. Run `scripts/live_source_smoke.sh` from a network that can reach the approved public APIs; preserve each provider's actual response/error independently.
4. Reconfirm reuse rights per NASA item before enabling any reuse; obtain Reddit authorization and confirm Pixabay permission before enabling those sources.
5. Measure startup, image loading, scrolling/navigation, memory, and glass effects on named supported physical devices; record build variant, cache state, network, refresh rate, and tool. Do not infer universal 120 Hz performance.
6. Publish a production APK only after building, installing, functionally verifying, production-signing and `apksigner`-verifying that exact release artifact, without replacing any existing release.
