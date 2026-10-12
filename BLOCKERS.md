# Verification and release status

**Current source status recorded: 2026-10-12.** CI run [38221005867](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38221005867) tested commit `76ca42b`; its build job passed and its smoke job uncovered an inaccurate bulk text-input result. The paced text-entry fix and this status update are newer local changes awaiting CI.

## Current source: Android build, tests, emulator, screenshots

- This workspace has no `java`, `javac`, or `adb` on `PATH`; `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` are unset. `./gradlew` cannot run locally. Local Android build/install and `apksigner` verification are unavailable.
- On commits `0e20d08` and `76ca42b`, CI job `build` passed unit tests, lint, debug and release assembly, and `apksigner` verification of the debug APK (latest pass: run [38221005867](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38221005867)). Production signing was not configured; no production-signed release APK was published. The current paced-text smoke fix is newer and awaits build/CI confirmation.
- In run `38219861388`, emulator smoke reached Home but timed out on a stale `Auroro Wallpapers` label. In run `38221005867`, after that assertion was fixed, the debug app reached Home, displayed For You cards, opened Search, and showed the Wallhaven source selector. It then timed out waiting for live Wallhaven results; the hierarchy showed the entered query as `oean` (missing the `c`) and an honest `No matching wallpapers` state. The script currently types one character per ADB call, with a short pause, and asserts the exact query text before submitting; this correction awaits rerun. The CI failure alone does not distinguish an emulator bulk-input race from an app-side input issue.
- Hosted query diagnostics again included Wallhaven usable rows; an Art Institute query returned rows, but the captured sample had `0x0` dimensions and zero size-usable records. Smoke stopped before completing the journey. This is partial API evidence, not manual screenshot review or a comprehensive source/rights audit. Artifact/log downloads returned EOF, so screenshots were not inspected.
- Static checks on this working tree passed after the smoke-script fix: Python syntax compilation for `scripts/*.py` and `.github/scripts/*.py`, `bash -n scripts/live_source_smoke.sh`, XML parsing of 11 Android resource files, and `git diff --check`. These checks do not compile Kotlin or execute the app.
- No supported physical Android device or 120 Hz panel was available. Startup, scrolling/navigation latency, frame pacing, image-load time, memory, battery, APK size, and prolonged-browsing measurements are not claimed. Macrobenchmark/Baseline Profile remain follow-up work.

## Previous revision evidence (historical; not proof for current source)

- Commit [`77f3055`](https://github.com/Nazatric/Aurora-Wallpapers/commit/77f3055f0a96de4c9e14e55429347143a6671191) passed build and hosted API 35 emulator smoke in [CI run 38147269114](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38147269114) on 2026-10-11. That run used the **previous Wallhaven/Openverse source set**; its UI assertions, provider results, and screenshots do not validate NASA/APOD, Art Institute, retired-source, or current UI flows.
- Earlier revisions passed tests, lint, debug/release builds, debug-signature verification, and hosted-emulator smoke in [CI run 38154259798](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38154259798), but they predate the approved-provider migration. They do not verify the current code. Production signing was not configured.
- Another earlier run ([38153473753](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38153473753)) built the then-current APK but its live-provider emulator smoke stopped at For You when Wallhaven was unreachable. This was not a physical-device test.
- Earlier emulator screenshots were uploaded as artifacts but could not be manually inspected in this sandbox; artifact downloads returned EOF. Automated UI assertions are not a substitute for visual review.

## Live sources and rights

- This workspace's outbound network is restricted and cannot directly reach provider API/media hosts. A hosted Android-emulator run did reach some provider search endpoints, but its UI smoke stopped early; that partial run does not validate every source, query, media URL, or rights path.
- Wallhaven uses its official API with source-side `purity=100`. The hosted emulator logs included Wallhaven result rows accepted by the source/dimension checks. The app has no global SFW/NSFW preference, hidden keyword/tag rejection, or category rejection. Broader query, image-fetch, and rate-limit behavior still needs validation.
- NASA APOD Basic parsing and live endpoint behavior were not established by the retrieved smoke diagnostics. APOD does not provide authoritative pixel dimensions in its response; rendition URL parameters are not treated as image measurements. Credit/copyright fields do not establish reuse rights: APOD remains browse-only, with download and wallpaper setting disabled. The [official APOD API README](https://github.com/nasa/apod-api) notes a planned archive/redirect change on **2026-12-01**; verify the current endpoint contract and migration before relying on post-cutover availability.
- Art Institute search was reached in the hosted run and returned records, but the captured sample had unknown `0x0` dimensions and zero size-usable items. Six source-specific query mappings are configured, with one museum query per For You mix; only records marked public domain with an image ID are accepted. IIIF metadata, media loads, rate behavior, dimensions, and availability still need validation.
- Reddit remains paused until authorized official OAuth access is configured; no scraping, client impersonation, or access-limit bypass is used. Pixabay permission/terms for wallpaper-file use remain unverified and it is not integrated. Lorem Picsum is omitted.
- Openverse is removed from active provider/query use. Saved legacy metadata and existing local files remain readable; new retired-source previews, downloads, and remote wallpaper preparation are blocked.

## Production release

- Source version is `versionName` **0.1**, `versionCode` **3**. CI produced the debug APK from commit `0e20d08`, verified its signature with `apksigner`, and installed/launched it in the hosted emulator; this does not constitute production signing or a completed functional smoke run.
- No production signing key or signed release APK has been verified, and no new tag or GitHub release is claimed. CI reported that production signing was not configured and did not publish a release APK. Never put signing material in Git or chat.
- Release signing requires `AURORO_KEYSTORE_BASE64`, `AURORO_KEYSTORE_PASSWORD`, `AURORO_KEY_ALIAS`, and `AURORO_KEY_PASSWORD`. The tag workflow checks for an existing release and refuses to overwrite it.

## Required follow-up

1. Commit and push the corrected smoke assertion and status docs; rerun CI and fix any actual compile, test, lint, or app-flow failures rather than weakening functional checks.
2. Confirm the debug APK signature with `apksigner`, rerun the full `scripts/emulator_ui_smoke.py` journey, and manually review resulting screenshots. Record provider skips/failures separately from app/UI verification; investigate artifact download errors if screenshots remain inaccessible.
3. Run `scripts/live_source_smoke.sh` from a network that can reach the approved public APIs; preserve each provider's actual response/error independently.
4. Reconfirm reuse rights per NASA item before enabling any reuse; obtain Reddit authorization and confirm Pixabay permission before enabling those sources.
5. Measure startup, image loading, scrolling/navigation, memory, and glass effects on named supported physical devices; record build variant, cache state, network, refresh rate, and tool. Do not infer universal 120 Hz performance.
6. Publish a production APK only after building, installing, functionally verifying, production-signing and `apksigner`-verifying that exact release artifact, without replacing any existing release.
