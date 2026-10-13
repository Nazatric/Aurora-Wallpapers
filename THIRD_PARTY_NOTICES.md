# Third-party notices

Auroro Wallpapers is licensed under Apache License 2.0; third-party components remain under their own licenses. Versions below are pinned in `gradle/libs.versions.toml`. The application UI (and with it every UI-only dependency) was removed in 1.4.0; the notices below cover the remaining data/domain layer.

| Component | Version | Source | License / attribution |
|---|---:|---|---|
| Kotlin | 2.4.21 | <https://github.com/JetBrains/kotlin> | Apache-2.0 |
| Android Gradle Plugin | 9.3.1 | <https://developer.android.com/build> | Apache-2.0 |
| Gradle Wrapper | 9.7.1 | <https://github.com/gradle/gradle> | Apache-2.0 |
| AndroidX Core | 1.17.0 | <https://github.com/androidx/androidx> | Apache-2.0 |
| AndroidX Room | 2.8.4 | <https://developer.android.com/jetpack/androidx/releases/room> | Apache-2.0 |
| AndroidX SQLite Framework | 2.6.2 | <https://developer.android.com/jetpack/androidx/releases/sqlite> | Apache-2.0; used by the Room migration test |
| AndroidX DataStore | 1.2.0 | <https://developer.android.com/topic/libraries/architecture/datastore> | Apache-2.0 |
| AndroidX WorkManager | 2.11.0 | <https://developer.android.com/topic/libraries/architecture/workmanager> | Apache-2.0 |
| Retrofit | 3.0.0 | <https://github.com/square/retrofit> | Apache-2.0 |
| OkHttp | 5.3.0 | <https://github.com/square/okhttp> | Apache-2.0 |
| Kotlinx Coroutines | 1.11.0 | <https://github.com/Kotlin/kotlinx.coroutines> | Apache-2.0 |
| Kotlinx Serialization | 1.11.0 | <https://github.com/Kotlin/kotlinx.serialization> | Apache-2.0 |
| KSP | 2.3.12 | <https://github.com/google/ksp> | Apache-2.0 |
| JUnit | 4.13.2 | <https://github.com/junit-team/junit4> | EPL-1.0 |
| Robolectric | 4.17 | <https://github.com/robolectric/robolectric> | MIT |
| MockWebServer | 5.3.0 | <https://github.com/square/okhttp/tree/master/mockwebserver> | Apache-2.0 |
| Turbine | 1.2.1 | <https://github.com/cashapp/turbine> | Apache-2.0 |
| Material Icons `wallpaper` notification glyph | Current upstream source preserved at [`licenses/MaterialWallpaper.svg`](licenses/MaterialWallpaper.svg) | <https://github.com/google/material-design-icons/blob/master/src/device/wallpaper/materialicons/24px.svg> | Apache-2.0; Google Material Icons; path adapted to the Android notification vector |

Dependencies are used as software components only. Auroro does not redistribute wallpaper photographs. Source wallpapers are loaded at runtime from the upstream provider, and remain subject to their original owners' rights and provider terms.

All application UI artwork was removed in 1.4.0; the only remaining bundled drawable is the attributed notification glyph. No provider logo or unlicensed wallpaper image is bundled.
