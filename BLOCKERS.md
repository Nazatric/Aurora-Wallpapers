# Verification status

## Android build and device checks

- This sandbox has no `java`, `javac`, `JAVA_HOME`, Android SDK, `adb` or emulator, so it cannot run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` or inspect the UI on a device.
- GitHub Actions run [37920685262](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/37920685262) passed `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` for parent commit `741fdefb69af752d9cb6bab748835d48093788ad`. Its APK artifact does **not** include the changes currently being prepared.
- Kotlin compilation, unit tests, lint, APK generation and on-device UI checks for the current changes therefore remain unverified until a fresh CI run completes. The repository workflow builds and uploads a debug APK; release signing requires configured private signing secrets.

## Provider research and live checks

- Openverse's current API reference says anonymous requests are supported and sufficient for most use; registered OAuth clients receive higher limits. Auroro sends no credentials, uses a persistent local request window, observes the provider's rate headers and pages incrementally. The official `GET /v1/images/` endpoint was also checked without an authorization header and returned an image-search response. References: <https://api.openverse.org/v1/> and <https://docs.openverse.org/api/reference/authentication_and_throttling.html>.
- Wallhaven's official API documents ratio tokens but does not specify their exact boundary tolerance. A current public `ratios=21x9` response included 3440×1440 (43:18, within Auroro's default 3% relative tolerance of 21:9); returned dimensions are still checked locally. Reference: <https://wallhaven.cc/help/api>.
- The optional `scripts/live_source_smoke.sh` has not been run from the shell in this sandbox. It calls only the public Wallhaven SFW endpoint and the Openverse endpoint anonymously; neither provider requires a committed key for these checks.

The source-independent unit tests use fixtures and a local mock HTTP server. Their existence is not evidence that they passed; check the fresh CI run above for results applicable to the latest commit.
