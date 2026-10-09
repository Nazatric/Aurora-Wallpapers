# Verification status

## Android build and device checks

- This sandbox has no `java`, `javac`, `JAVA_HOME`, Android SDK, `adb` or emulator. A local Gradle build and on-device UI inspection were not possible.
- GitHub Actions run [37996295484](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/37996295484) passed `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` for commit `db5ca865ec26a9f67238c4e6a47badb0a8563868`. It uploaded the `auroro-wallpapers-apk` artifact for version 1.1.0. CI verifies compilation, automated tests, lint and APK assembly—not runtime behavior or screenshots on a physical device/emulator.
- The prior public release `v1.0.0` contains only a debug APK. Production signing needs the private release keystore and passwords configured in GitHub Actions or locally; no keystore is committed. The GitHub secret-list request was denied with HTTP 403, so secret values or availability could not be inspected from this session. Do not describe a debug APK as production-signed.

## Provider research and live checks

- Openverse's current API reference says anonymous requests are supported and sufficient for most use; registered OAuth clients receive higher limits. Auroro sends no credentials, uses a persistent local request window, observes the provider's rate headers and pages incrementally. The official `GET /v1/images/` endpoint was also checked without an authorization header and returned an image-search response. References: <https://api.openverse.org/v1/> and <https://docs.openverse.org/api/reference/authentication_and_throttling.html>.
- Wallhaven's official API documents ratio tokens but does not specify their exact boundary tolerance. A current public `ratios=21x9` response included 3440×1440 (43:18, within Auroro's default 3% relative tolerance of 21:9); returned dimensions are still checked locally. Reference: <https://wallhaven.cc/help/api>.
- The optional `scripts/live_source_smoke.sh` has not been run from the shell in this sandbox. It calls only the public Wallhaven SFW endpoint and the Openverse endpoint anonymously; neither provider requires a committed key for these checks.

The automated tests use local fixtures and a mock HTTP server; run 37996295484 is the passing result for the redesigned source revision.
