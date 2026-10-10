#!/usr/bin/env python3
"""Live Android emulator smoke test for the installed APK.

The test intentionally uses only the app's real Wallhaven/Openverse providers. It does not inject
wallpaper fixtures into the running app. Screenshots and a small UI report are kept under
artifacts/ui-smoke/ for CI inspection.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable

ARTIFACTS = Path("artifacts/ui-smoke")
REMOTE_HIERARCHY = "/sdcard/auroro-window.xml"
REPORT: dict[str, object] = {"screenshots": [], "checks": []}
EMPTY_STATES = ("No matches on this page", "No wallpapers matched", "Couldn't load wallpapers")


def run(command: list[str], *, timeout: int = 30, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, text=True, capture_output=True, timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(command)}\n"
            f"stdout: {result.stdout[-2000:]}\nstderr: {result.stderr[-2000:]}"
        )
    return result


def adb(*args: str, timeout: int = 30, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["adb", *args], timeout=timeout, check=check)


def wait_until(predicate: Callable[[ET.Element], bool], label: str, timeout: int = 60) -> ET.Element:
    deadline = time.monotonic() + timeout
    last_root: ET.Element | None = None
    while time.monotonic() < deadline:
        last_root = dump_hierarchy()
        if predicate(last_root):
            REPORT["checks"].append({"name": label, "result": "passed"})
            return last_root
        time.sleep(2)
    visible = [node_text(node) for node in last_root.iter("node")] if last_root is not None else []
    raise AssertionError(f"Timed out waiting for {label}; visible UI nodes: {visible[:120]}")


def dump_hierarchy() -> ET.Element:
    """Capture the accessibility tree, retrying transient Android UIAutomator startup errors."""
    last_error = "UIAutomator did not produce a hierarchy"
    for attempt in range(5):
        adb("shell", "rm", "-f", REMOTE_HIERARCHY, timeout=10, check=False)
        try:
            dump = adb("shell", "uiautomator", "dump", REMOTE_HIERARCHY, timeout=20, check=False)
            hierarchy = adb("exec-out", "cat", REMOTE_HIERARCHY, timeout=10, check=False)
        except subprocess.TimeoutExpired as error:
            last_error = f"attempt {attempt + 1}: {error}"
            time.sleep(1)
            continue

        if dump.returncode != 0 or hierarchy.returncode != 0 or not hierarchy.stdout.strip():
            last_error = (
                f"attempt {attempt + 1}: uiautomator exit={dump.returncode}, "
                f"stdout={dump.stdout[-800:]!r}, stderr={dump.stderr[-800:]!r}; "
                f"cat exit={hierarchy.returncode}, stderr={hierarchy.stderr[-800:]!r}"
            )
            time.sleep(1)
            continue

        try:
            return ET.fromstring(hierarchy.stdout)
        except ET.ParseError as error:
            last_error = f"attempt {attempt + 1}: invalid XML: {hierarchy.stdout[:1200]!r} ({error})"
            time.sleep(1)

    raise RuntimeError(f"Could not capture Android UI hierarchy after 5 attempts; {last_error}")


def node_text(node: ET.Element) -> str:
    return node.attrib.get("text", "") or node.attrib.get("content-desc", "")


def find_nodes(root: ET.Element, *, text: str | None = None, description: str | None = None, contains: bool = False) -> list[ET.Element]:
    matches = []
    for node in root.iter("node"):
        values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if text is not None and any((text in value if contains else text == value) for value in values):
            matches.append(node)
        elif description is not None and description == node.attrib.get("content-desc", ""):
            matches.append(node)
    return matches


def bounds_center(node: ET.Element) -> tuple[int, int]:
    bounds = node.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        raise AssertionError(f"UI element has no usable bounds: {node.attrib}")
    left, top, right, bottom = map(int, match.groups())
    return (left + right) // 2, (top + bottom) // 2


def clickable_target(root: ET.Element, node: ET.Element) -> ET.Element:
    """Resolve an image's merged Compose semantics to its nearest clickable card ancestor."""
    parents = {child: parent for parent in root.iter() for child in parent}
    current: ET.Element | None = node
    while current is not None:
        if current.attrib.get("clickable") == "true":
            return current
        current = parents.get(current)
    return node


def tap_node(node: ET.Element) -> None:
    x, y = bounds_center(node)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1)


def tap_text(label: str, *, prefer_bottom: bool = False, timeout: int = 15) -> ET.Element:
    root = wait_until(lambda ui: bool(find_nodes(ui, text=label)), f"visible text '{label}'", timeout)
    candidates = find_nodes(root, text=label)
    node = max(candidates, key=lambda item: bounds_center(item)[1]) if prefer_bottom else candidates[0]
    tap_node(node)
    return node


def screenshot(name: str) -> None:
    path = ARTIFACTS / name
    path.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True, timeout=30).stdout
    path.write_bytes(data)
    REPORT["screenshots"].append(str(path))


def wallpaper_descriptions(root: ET.Element) -> list[str]:
    return [
        node.attrib.get("content-desc", "")
        for node in root.iter("node")
        if node.attrib.get("content-desc", "").startswith("Wallpaper")
    ]


def parse_dimensions(description: str) -> tuple[int, int] | None:
    match = re.search(r"(\d{2,6})\s+by\s+(\d{2,6})", description)
    return (int(match.group(1)), int(match.group(2))) if match else None


def wait_for_feed_content(label: str, timeout: int = 90, max_empty_pages: int = 4) -> ET.Element:
    """Wait for result cards; explicitly page through an empty-but-not-terminal provider page."""
    for attempt in range(max_empty_pages + 1):
        root = wait_until(
            lambda ui: bool(wallpaper_descriptions(ui)) or any(find_nodes(ui, text=state) for state in EMPTY_STATES),
            f"{label} results or honest empty/error state (page {attempt + 1})",
            timeout,
        )
        if wallpaper_descriptions(root) or any(find_nodes(root, text=state) for state in EMPTY_STATES[1:]):
            return root
        further = find_nodes(root, text="Look further")
        if not further or attempt == max_empty_pages:
            return root
        tap_node(further[0])
    raise AssertionError(f"No feed state became visible for {label}")


def main() -> None:
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    release_apks = sorted(Path("dist").glob("*-release.apk"))
    debug_apks = sorted(Path("dist").glob("*-debug.apk"))
    apk = release_apks[0] if release_apks else (debug_apks[0] if debug_apks else None)
    if apk is None:
        raise FileNotFoundError("CI did not provide an APK under dist/")
    REPORT["installed_apk_variant"] = "release" if release_apks else "debug"
    run(["adb", "install", "-r", str(apk)], timeout=120)
    adb("shell", "am", "start", "-W", "-n", "com.auroro.wallpapers/.app.MainActivity", timeout=60)

    root = wait_until(lambda ui: bool(find_nodes(ui, text="Discover")), "initial Discover screen", timeout=60)
    screenshot("01-home.png")
    if find_nodes(root, text="Auroro Wallpapers"):
        raise AssertionError("The native app-name title bar is still visible above the Compose screen")
    REPORT["checks"].append({"name": "no duplicate native app title", "result": "passed"})

    tap_text("Search", prefer_bottom=True)
    root = wait_until(lambda ui: bool(find_nodes(ui, text="Try ocean, forest or a place")), "Search screen field", timeout=30)
    field = find_nodes(root, text="Try ocean, forest or a place")[0]
    tap_node(field)
    adb("shell", "input", "text", "ocean")
    adb("shell", "input", "keyevent", "66")
    time.sleep(3)  # allow the app's intentional 520 ms type-ahead debounce to fire
    tap_text("Wallhaven", timeout=20)
    root = wait_for_feed_content("real Wallhaven search", timeout=90)
    REPORT["wallhaven_result_count"] = len(wallpaper_descriptions(root))
    screenshot("02-wallhaven-search.png")
    if not wallpaper_descriptions(root):
        raise AssertionError("Wallhaven did not produce a visible result for the live 'ocean' search")

    # Verify the real-dimension orientation filter, then restore Any before the next provider.
    tap_text("Portrait", timeout=20)
    root = wait_for_feed_content("portrait filter", timeout=60)
    portraits = wallpaper_descriptions(root)
    known = [pair for item in portraits if (pair := parse_dimensions(item)) is not None]
    invalid_portraits = [item for item in portraits if (pair := parse_dimensions(item)) is not None and pair[0] >= pair[1] * 0.95]
    if invalid_portraits:
        raise AssertionError(f"Portrait filter displayed a non-portrait image: {invalid_portraits}")
    REPORT["checks"].append({
        "name": "orientation filter uses real dimensions",
        "result": "passed",
        "visible_results": portraits,
        "measured_portraits": len(known),
    })
    screenshot("03-portrait-filter.png")
    tap_text("Any", timeout=20)
    root = wait_for_feed_content("Wallhaven results after clearing orientation", timeout=60)
    if not wallpaper_descriptions(root):
        raise AssertionError("Wallhaven returned no visible results after clearing the orientation filter")

    # Favorite and queue an actual Wallhaven original before testing Openverse licensing, which can
    # legitimately disallow direct downloads for some individual works.
    tile_nodes = [
        node for node in root.iter("node")
        if node.attrib.get("content-desc", "").startswith("Wallpaper")
    ]
    if not tile_nodes:
        raise AssertionError("No accessible Wallhaven wallpaper card was available to open")
    tap_node(clickable_target(root, tile_nodes[0]))
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorite")), "Wallhaven detail actions", timeout=60)
    if not find_nodes(detail, text="Wallhaven"):
        raise AssertionError("Selecting Wallhaven did not open a Wallhaven wallpaper detail")
    screenshot("04-wallhaven-detail.png")
    tap_text("Set wallpaper", timeout=20)
    apply_dialog = wait_until(lambda ui: bool(find_nodes(ui, text="Home screen")) and bool(find_nodes(ui, text="Cancel")), "wallpaper target chooser", timeout=30)
    screenshot("05-wallpaper-target-dialog.png")
    tap_text("Cancel", timeout=20)
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorite")), "return from wallpaper target chooser", timeout=20)
    REPORT["checks"].append({"name": "wallpaper-setting targets and cancellation", "result": "passed"})
    tap_text("Favorite", timeout=20)
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorited")), "favorite saved in detail", timeout=30)
    REPORT["checks"].append({"name": "favorite action", "result": "passed"})

    download_nodes = find_nodes(detail, text="Download original")
    if not download_nodes:
        visible = [node_text(node) for node in detail.iter("node") if node_text(node)]
        raise AssertionError(f"No original-download action was present in Wallhaven detail; visible text: {visible[:120]}")
    download_target = clickable_target(detail, download_nodes[0])
    if download_target.attrib.get("enabled") == "false" or download_nodes[0].attrib.get("enabled") == "false":
        raise AssertionError(
            "The Wallhaven original-download action was visibly disabled: "
            f"label={download_nodes[0].attrib}, target={download_target.attrib}"
        )
    tap_node(download_target)
    download_state = wait_until(
        lambda ui: bool(find_nodes(ui, text="Downloading")) or bool(find_nodes(ui, text="Saved")) or bool(find_nodes(ui, text="Couldn't queue download", contains=True)),
        "download enqueued or surfaced an honest error", timeout=45,
    )
    if find_nodes(download_state, text="Couldn't queue download", contains=True):
        raise AssertionError("The UI surfaced a download queue failure")
    REPORT["checks"].append({"name": "original download entered a visible state", "result": "passed"})
    screenshot("06-download-action.png")

    # Back navigation retains Search; Offline then reads the actual persisted Room download record.
    adb("shell", "input", "keyevent", "4")
    restored = wait_until(lambda ui: bool(wallpaper_descriptions(ui)), "back to Wallhaven results", timeout=45)
    screenshot("07-restored-wallhaven-search.png")
    REPORT["checks"].append({"name": "detail-to-search navigation retains results", "result": "passed"})
    tap_text("Offline", prefer_bottom=True, timeout=20)
    offline = wait_until(
        lambda ui: bool(find_nodes(ui, text="Offline")) and any(
            find_nodes(ui, text=section)
            for section in ("In progress", "Saved wallpapers", "Needs attention")
        ),
        "persisted Offline download record",
        timeout=45,
    )
    if find_nodes(offline, text="No downloads yet"):
        raise AssertionError("The queued original did not appear in Offline")
    screenshot("08-offline-downloads.png")
    REPORT["checks"].append({"name": "queued original appears in Offline", "result": "passed"})
    tap_text("Search", prefer_bottom=True, timeout=20)
    wait_until(lambda ui: bool(find_nodes(ui, text="Try ocean, forest or a place")) and bool(wallpaper_descriptions(ui)), "restore Search from Offline", timeout=45)
    search_size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size").stdout)
    if search_size:
        width, height = map(int, search_size.groups())
        for _ in range(2):
            adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.24)), str(width // 2), str(int(height * 0.82)), "350")
            time.sleep(1)

    # Exercise Openverse itself and verify its provider attribution in the real detail page.
    tap_text("Openverse", timeout=20)
    openverse_root = wait_for_feed_content("Openverse search", timeout=90)
    REPORT["openverse_result_count"] = len(wallpaper_descriptions(openverse_root))
    screenshot("09-openverse-search.png")
    openverse_tiles = [
        node for node in openverse_root.iter("node")
        if node.attrib.get("content-desc", "").startswith("Wallpaper")
    ]
    if openverse_tiles:
        tap_node(clickable_target(openverse_root, openverse_tiles[0]))
        wait_until(lambda ui: bool(find_nodes(ui, text="Openverse")), "Openverse source attribution in detail", timeout=60)
        screenshot("10-openverse-detail.png")
        adb("shell", "input", "keyevent", "4")
    else:
        REPORT["openverse_note"] = "The app showed its explicit empty/error state; inspect the screenshot and provider message."

    # Finally exercise All Sources and return to Home, keeping any provider failures visible in the report.
    search_size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size").stdout)
    if search_size:
        width, height = map(int, search_size.groups())
        for _ in range(2):
            adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.24)), str(width // 2), str(int(height * 0.82)), "350")
            time.sleep(1)
    tap_text("All", timeout=20)
    combined = wait_for_feed_content("combined-source search", timeout=90)
    REPORT["combined_result_count"] = len(wallpaper_descriptions(combined))
    screenshot("11-combined-search.png")
    if not wallpaper_descriptions(combined):
        raise AssertionError("The combined search showed no results; inspect its explicit empty/error state")
    adb("shell", "input", "keyevent", "4")
    home = wait_until(
        lambda ui: bool(find_nodes(ui, text="Discover")) and bool(find_nodes(ui, text="Explore topics")) and not find_nodes(ui, text="Try ocean, forest or a place"),
        "system-back navigation to Home",
        timeout=45,
    )
    screenshot("12-home-restored.png")
    REPORT["checks"].append({"name": "combined-source search and system-back Home navigation", "result": "passed"})

    tap_text("Settings", timeout=20)
    settings = wait_until(
        lambda ui: bool(find_nodes(ui, text="Appearance")) and bool(find_nodes(ui, text="Reduce transparency")),
        "Settings appearance and transparency controls",
        timeout=45,
    )
    screenshot("13-settings-appearance.png")
    size_output = adb("shell", "wm", "size").stdout
    size = re.search(r"(\d+)x(\d+)", size_output)
    if size:
        width, height = map(int, size.groups())
        for _ in range(4):
            if find_nodes(settings, text="Wallpaper sources", contains=True):
                break
            adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.82)), str(width // 2), str(int(height * 0.28)), "450")
            time.sleep(1)
            settings = dump_hierarchy()
    source_action = find_nodes(settings, text="Wallpaper sources", contains=True)
    if not source_action:
        raise AssertionError("The Settings screen did not expose its real Wallpaper sources entry")
    tap_node(source_action[0])
    sources = wait_until(
        lambda ui: bool(find_nodes(ui, text="Two integrated wallpaper catalogues")) and bool(find_nodes(ui, text="Wallhaven")),
        "integrated provider settings",
        timeout=45,
    )
    screenshot("14-source-settings.png")
    REPORT["checks"].append({"name": "settings and provider configuration navigation", "result": "passed"})
    adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    wait_until(lambda ui: bool(find_nodes(ui, text="Discover")), "return from source settings to Home", timeout=45)

    (ARTIFACTS / "report.json").write_text(json.dumps(REPORT, indent=2) + "\n")
    print(json.dumps(REPORT, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # keep diagnostics for a failed live provider, screenshot or UI action
        ARTIFACTS.mkdir(parents=True, exist_ok=True)
        try:
            screenshot("failure.png")
        except Exception:
            pass
        REPORT["failure"] = f"{type(error).__name__}: {error}"
        (ARTIFACTS / "report.json").write_text(json.dumps(REPORT, indent=2) + "\n")
        print(json.dumps(REPORT, indent=2), file=sys.stderr)
        raise
