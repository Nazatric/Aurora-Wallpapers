# Auroro Wallpapers privacy notice

**Last updated: 2026-10-10**

Auroro has no account, app server, advertising, analytics or telemetry. Searches and local wallpaper data are not sent to Auroro.

## Network requests

When you browse, search, open a source page or download an image, your device connects directly to the selected provider or the image host:

- **Wallhaven:** the public SFW API and Wallhaven's published image hosts. Search terms, filters, pagination and image requests are visible to Wallhaven from your network address under its policies.
- **Openverse:** `api.openverse.org` for anonymous image search, followed by the third-party media host and original source page named in a result. Auroro sends no Openverse API key, OAuth secret or bearer token. Openverse and image/source hosts may receive standard request data such as your IP address, requested URL, time and user agent. Their own privacy policies and terms apply.

Openverse indexes works hosted by third parties. It does not verify every work's licence claim; inspect the original source page and licence before reuse. Auroro retains and displays available creator, source and licence metadata, but does not guarantee its completeness or accuracy.

Image responses may be held in a bounded temporary HTTP/image cache in app cache storage. That cache is disposable and is not a saved download. You can clear it in Settings. Original files you save are separate.

## Data on your device

Room stores wallpaper identifiers, image/source URLs, available metadata and licence information, favorites, collections, viewing history, and download status plus local file references. DataStore stores settings such as appearance, aspect tolerance, enabled providers, cache size and download preferences, plus up to eight recent search terms. Search history is local, can be cleared from Search, and is not synced to Auroro. Provider query pacing is also stored locally so it can continue across process restarts.

By default, downloads are saved through Android MediaStore to `Pictures/Auroro Wallpapers` and remain in Gallery until you delete them. You can instead save privately in app storage; Android removes those files if Auroro is uninstalled. App backup is disabled. Clearing the temporary image cache does not delete saved downloads.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE` support provider requests and network status.
- `SET_WALLPAPER` is used when you choose to set a wallpaper, or if you explicitly enable Set after download.
- `POST_NOTIFICATIONS` is requested if you enable download notifications; downloads can continue when notifications are denied.
- Foreground-service permissions support a user-started background download.

Auroro does not request location, camera, microphone, contacts or broad shared-storage permissions.

## Deleting data

Favorites, collections, viewing history and recent search terms can be managed in the app; recent searches can be cleared from Search. Completed downloads can be deleted from Downloads; Android may prevent deletion of a file the app no longer controls. Uninstalling removes app-private files, database and preferences. MediaStore downloads in Pictures remain until deleted in Gallery or Android storage settings.

## Contact and provider policies

For a privacy question or correction, open an issue in the project repository: <https://github.com/Nazatric/Aurora-Wallpapers/issues>.

- Wallhaven privacy policy: <https://wallhaven.cc/privacy-policy>
- Openverse API documentation and terms: <https://api.openverse.org/v1/> · <https://docs.openverse.org/terms_of_service.html>
