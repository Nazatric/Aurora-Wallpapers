# Verification status

## Android build and device checks

- This sandbox has no `java`, `javac`, `JAVA_HOME`, Android SDK, `adb` or emulator. A local Gradle build and on-device UI inspection were not possible.
- Tag-triggered GitHub Actions run [37997656536](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/37997656536) passed `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` for `v1.1.0` at commit `de6df304aa7593e4a26ec19d9c254b04f813e6b6`. The published [v1.1.0 release](https://github.com/Nazatric/Aurora-Wallpapers/releases/tag/v1.1.0) contains one APK asset: [`auroro-wallpapers-1.1.0-debug.apk`](https://github.com/Nazatric/Aurora-Wallpapers/releases/download/v1.1.0/auroro-wallpapers-1.1.0-debug.apk) (70,663,807 bytes). Automated builds/tests/lint do not verify runtime behavior or screenshots on a physical device/emulator.
- **Signing blocker:** no production-signed release APK was published for v1.1.0; the only release asset is the debug APK. The workflow attaches a release APK only when all four GitHub Actions secrets are configured: `AURORO_KEYSTORE_BASE64`, `AURORO_KEYSTORE_PASSWORD`, `AURORO_KEY_ALIAS` and `AURORO_KEY_PASSWORD`. Secret-list access returned HTTP 403, so this session could not inspect which secret or credential is missing. No keystore or signing secret is committed.

## Provider research and live checks

- Openverse's current API reference says anonymous requests are supported and sufficient for most use; registered OAuth clients receive higher limits. Auroro sends no credentials, uses a persistent local request window, observes the provider's rate headers and pages incrementally. The official `GET /v1/images/` endpoint was also checked without an authorization header and returned an image-search response. References: <https://api.openverse.org/v1/> and <https://docs.openverse.org/api/reference/authentication_and_throttling.html>.
- Wallhaven's official API documents ratio tokens but does not specify their exact boundary tolerance. A current public `ratios=21x9` response included 3440×1440 (43:18, within Auroro's default 3% relative tolerance of 21:9); returned dimensions are still checked locally. Reference: <https://wallhaven.cc/help/api>.
- The optional `scripts/live_source_smoke.sh` has not been run from the shell in this sandbox. It calls only the public Wallhaven SFW endpoint and the Openverse endpoint anonymously; neither provider requires a committed key for these checks.

The automated tests use local fixtures and a mock HTTP server. Tag-triggered run 37997656536 is the passing verification for v1.1.0.
