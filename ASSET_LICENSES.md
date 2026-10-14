# Artwork and font licences

## Runtime wallpaper imagery

No wallpaper photographs are bundled. Wallhaven and NASA APOD media are requested at runtime by the data layer. Wallhaven media remains subject to its original creator's rights; NASA APOD media is **view-only in Auroro until reuse rights are confirmed for that individual item**. Art Institute of Chicago records (source retired in 1.3.0) remain readable only where the API marked them public domain, with museum attribution. Reddit is disabled pending authorized API access. See [`README.md`](README.md) for source-specific behavior and [`PRIVACY.md`](PRIVACY.md) for network use.

## Launcher and notification icons

The launcher foreground/monochrome layers (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`) and the download-notification glyph (`ic_stat_auroro.xml`) use Google's **Material Icons `wallpaper`** icon, under Apache License 2.0. The 24 px upstream SVG is preserved at [`licenses/MaterialWallpaper.svg`](licenses/MaterialWallpaper.svg); the Android vector is a scale-adjusted copy of its path. Source: <https://github.com/google/material-design-icons/blob/master/src/device/wallpaper/materialicons/24px.svg>. Licence: <https://github.com/google/material-design-icons/blob/master/LICENSE>.

## Bundled interface icons

The navigation, favorite, refresh, empty-state and error-state glyphs are unmodified path data from Google's Material Icons (Apache-2.0), bundled as vector drawables with runtime tinting; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Removed in 1.4.0

The bundled Open Sans typefaces (SIL Open Font License 1.1) and the previous interface artwork were deleted with the 1.4.0 UI removal and have not been re-added.

## Slice-2/3 UI icons (`ic_arrow_back`, `ic_info_outlined`, `ic_download`, `ic_open_in_new`, `ic_close`)

Bundled from [`google/material-design-icons`](https://github.com/google/material-design-icons) (Apache License 2.0) via the `android/` resource layout (`navigation/arrow_back`, `action/info` outlined theme, `file/download`, `action/open_in_new`, `navigation/close`; 24 dp baseline/outline vectors). Fills were normalized to white and runtime tints stripped; they are tinted at runtime like the slice-1 icons. Licence: <https://github.com/google/material-design-icons/blob/master/LICENSE>.

The detail screen's Set-wallpaper action reuses the existing Material `wallpaper` glyph (`ic_stat_auroro`), attributed above.
