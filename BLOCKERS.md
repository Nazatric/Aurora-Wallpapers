# Verification status

## Android build and device checks

- This sandbox has no `java`, `javac`, `JAVA_HOME`, Android SDK, `adb` or emulator; a local Gradle build cannot run here, and on-device UI inspection was not possible.
- GitHub Actions run [37989599230](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/37989599230) passed `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` for commit `4fb840d48a1a5dd8801e362f42a7365d75e2004d`. It uploaded the `auroro-wallpapers-apk` artifact. This verifies compilation, automated tests, lint and APK assembly, not runtime behavior on a physical device.
- Release signing requires private signing secrets in GitHub Actions or a local keystore; signing material is not stored in this repository. The debug APK is debug-signed and installable.

## Provider research and live checks

- Openverse's current API reference says anonymous requests are supported and sufficient for most use; registered OAuth clients receive higher limits. Auroro sends no credentials, uses a persistent local request window, observes the provider's rate headers and pages incrementally. The official `GET /v1/images/` endpoint was also checked without an authorization header and returned an image-search response. References: <https://api.openverse.org/v1/> and <https://docs.openverse.org/api/reference/authentication_and_throttling.html>.
- Wallhaven's official API documents ratio tokens but does not specify their exact boundary tolerance. A current public `ratios=21x9` response included 3440×1440 (43:18, within Auroro's default 3% relative tolerance of 21:9); returned dimensions are still checked locally. Reference: <https://wallhaven.cc/help/api>.
- The optional `scripts/live_source_smoke.sh` has not been run from the shell in this sandbox. It calls only the public Wallhaven SFW endpoint and the Openverse endpoint anonymously; neither provider requires a committed key for these checks.

The automated tests use local fixtures and a mock HTTP server; run 37989599230 above is the passing result for the source revision named there.
