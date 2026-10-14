# AGENTS.md — repository conventions for AI agents and contributors

## Current state

Work in progress. The entire old application UI was removed in October 2026 (git history
around `versionName 1.4.0`); all GitHub releases and tags were deleted at the same time.
A new UI is being rebuilt slice by slice on top of the validated data/domain layer.
Slice 1 (Home feed + shell pill navigation) is implemented under
`app/src/main/java/com/auroro/wallpapers/ui/`; later slices add Search, detail/preview,
Library and Settings. Keep slices small and land each with green CI.

## Layout

- `app/src/main/java/com/auroro/wallpapers/core/data/` — providers (Wallhaven, NASA APOD),
  discovery engines and session planning, quality validation, settings, favorites/collections/
  history repositories, download pipeline (`download/`)
- `app/src/main/java/com/auroro/wallpapers/core/database/` — Room database and migrations
- `app/src/main/java/com/auroro/wallpapers/core/model/` — domain types (no framework deps
  beyond harmless annotations)
- `app/src/main/java/com/auroro/wallpapers/core/network/` — OkHttp/Retrofit clients, URL policy
- `app/src/main/java/com/auroro/wallpapers/app/` — `Application` class, `AppContainer`
  (manual dependency injection), `MainActivity`, `AuroroShell`
- `app/src/main/java/com/auroro/wallpapers/ui/` — Compose UI: `theme/`, `components/`,
  `home/`, placeholder screens; icons are Material Icons vectors in `res/drawable`
- `app/src/test/` — JVM unit tests (JUnit 4, Robolectric, MockWebServer, Turbine)
- `scripts/live_source_smoke.sh` — curl-based live provider checks (no app or UI involved)
- `.github/workflows/android.yml` — CI: unit tests, Android lint, debug/release assembly,
  `apksigner` verification, tag-triggered release artifacts
- `.github/workflows/security.yml` — CodeQL analysis (build-less Kotlin) on pull requests, weekly and on demand; the schedule/dispatch triggers only fire once the workflow is on the default branch

## Hard rules

1. **CI is the only build vehicle.** Agent sandboxes here have no JVM or Android SDK, and
   Gradle's download hosts are blocked; `./gradlew` cannot run locally. Never claim tests,
   lint, or a build pass without a green GitHub Actions run for that exact commit.
2. **Do not weaken the data layer to make CI green.** Provider validation gates, rights
   handling (APOD view-only, retired-source attribution), Room migrations, DataStore keys,
   and their tests are deliberate. Fix real failures; never delete assertions to hide them.
3. **Backward compatibility.** Room migrations and DataStore preference keys must keep
   working for existing installs (`MigrationsTest` guards this).
4. **No secrets in Git or chat.** Release signing reads `AURORO_KEYSTORE_*`
   environment/secrets only; no key material is ever committed.
5. **Licence hygiene.** Keep `THIRD_PARTY_NOTICES.md` and `ASSET_LICENSES.md` in sync with
   the actual dependencies and bundled assets. Verify licences before copying code; GPL
   code must not be copied into this Apache-2.0 project.
6. **Formatting.** `.editorconfig` is authoritative: UTF-8, LF, final newline, no trailing
   whitespace, 4-space Kotlin (ktlint `android_studio` conventions), 2-space YAML/shell.

## Useful local checks (no JVM required)

- `bash -n scripts/live_source_smoke.sh` — shell syntax
- `python3 -m compileall .github/scripts` — Python syntax
- `ruff check .github/scripts` / `ruff format --check .github/scripts` — Python quality
- `npx prettier --check "*.md" ".github/**/*.yml"` — Markdown/YAML consistency
