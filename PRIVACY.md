# Auroro Wallpapers privacy notice

**Last updated: 2026-10-12**

Auroro has no app server, account, ads, analytics, or telemetry. Searches and local wallpaper data are not sent to Auroro. Network requests go directly from the device to the selected provider or its media host.

## Network requests

- **Wallhaven:** the official public API and Wallhaven-hosted media. The API receives the search phrase, provider filters, pagination, and the device's ordinary network request data (such as IP address, time, and user agent). Requests use Wallhaven's own `purity=100` query parameter.
- **NASA APOD:** the official APOD Basic endpoint at `science.nasa.gov`, followed by a NASA-hosted image URL and the APOD source page. The feed's credit and copyright values are shown as metadata; Auroro does not treat them as reuse permission. APOD images are view-only in the app until rights are confirmed for the individual item.
- **Art Institute of Chicago:** its official API at `api.artic.edu`, IIIF image metadata, and image URLs on `www.artic.edu`. Requests are restricted to public-domain/Open Access records.
- **Reddit:** no Reddit request is made. The two proposed subreddits remain disabled until an authorized official API application is configured.
- **Retired Openverse records:** no new Openverse API, preview, download, or apply-cache request is made. Existing local records and files remain on the device. Opening a saved source page is an explicit user action that can contact that page's host.

Providers and media hosts may receive standard network information under their own terms and privacy policies. Auroro does not proxy provider traffic through a project server. Opening an external source or rights-holder page is user initiated and may send information to that site.

## Caches and on-device data

Coil keeps disposable image bytes in a bounded memory cache and app-cache disk cache (default disk limit: 250 MB; adjustable in Settings). A separate small HTTP response cache is also disposable. Provider page caches retain ordered wallpaper identifiers and metadata in Room, with bounded expiry. These caches are not gallery downloads and can be cleared in Settings.

Room stores wallpaper/source identifiers and available metadata, favorites, collections, viewing history, and download state/local file references. DataStore stores appearance and accessibility preferences, enabled provider choices, search/filter settings, discovery preferences, download options, and up to eight recent search phrases. Request pacing/cooldown timestamps are kept in local preferences. None of this is synced to Auroro.

By default, user-requested original downloads are saved with Android MediaStore under `Pictures/Auroro Wallpapers`; the optional private-storage choice uses app-private files. User-saved downloads are separate from temporary image/apply caches. The app declares backup disabled; uninstall removes app-private files and local database/preferences, while MediaStore files remain until the user deletes them through Gallery or Android storage settings.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE` support provider requests and network state.
- `SET_WALLPAPER` is used only after the user chooses a wallpaper target and confirms the apply flow.
- `POST_NOTIFICATIONS` is needed for optional notifications on Android 13+; downloads remain separate from notification display.
- Foreground-service permissions support a user-started background download.

Auroro does not request location, camera, microphone, contacts, or broad shared-storage permissions.

## Deleting data

Recent searches can be cleared from Search. Favorites, collections, history, app preferences, and cache controls are available in the app. Completed gallery downloads can be deleted from Downloads when Android permits; items outside the app's control may have to be removed in Gallery or system storage settings. Uninstalling removes the local app database, preferences, and private files. MediaStore downloads remain until separately deleted.

## Provider links

- Wallhaven: <https://wallhaven.cc/help/api> · <https://wallhaven.cc/privacy-policy>
- NASA APOD: <https://science.nasa.gov/apod/> · <https://science.nasa.gov/apod/apod-about/>
- Art Institute of Chicago: <https://api.artic.edu/docs/> · <https://www.artic.edu/open-access/open-access-images>
- Reddit Data API policy: <https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki>

For a privacy question or correction, open an issue in the project repository: <https://github.com/Nazatric/Aurora-Wallpapers/issues>.
