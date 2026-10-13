# Artwork and font licences

## Runtime wallpaper imagery

No wallpaper photographs are bundled. Wallhaven and NASA APOD media are requested at runtime by the data layer. Wallhaven media remains subject to its original creator's rights; NASA APOD media is **view-only in Auroro until reuse rights are confirmed for that individual item**. Art Institute of Chicago records (source retired in 1.3.0) remain readable only where the API marked them public domain, with museum attribution. Reddit is disabled pending authorized API access. See [`README.md`](README.md) for source-specific behavior and [`PRIVACY.md`](PRIVACY.md) for network use.

## Notification icon

The download-notification glyph (`app/src/main/res/drawable/ic_stat_auroro.xml`) uses Google's **Material Icons `wallpaper`** icon, under Apache License 2.0. The 24 px upstream SVG is preserved at [`licenses/MaterialWallpaper.svg`](licenses/MaterialWallpaper.svg); the Android vector is a scale-adjusted copy of its path. Source: <https://github.com/google/material-design-icons/blob/master/src/device/wallpaper/materialicons/24px.svg>. Licence: <https://github.com/google/material-design-icons/blob/master/LICENSE>.

## Removed in 1.4.0

The application UI was removed completely in 1.4.0. The launcher/adaptive/monochrome icon resources, the bundled Open Sans typefaces (SIL Open Font License 1.1), all interface artwork, and the Open Sans licence file were deleted along with it. The only remaining bundled artwork is the notification glyph attributed above.
