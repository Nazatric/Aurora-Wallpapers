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
    last_capture_error: str | None = None
    while time.monotonic() < deadline:
        try:
            last_root = dump_hierarchy()
            last_capture_error = None
        except RuntimeError as error:
            # UIAutomator can briefly lose its output file during emulator startup or app transitions.
            # Retry within the requested wait instead of failing an otherwise recoverable UI check.
            last_capture_error = str(error)
            time.sleep(2)
            continue
        if predicate(last_root):
            REPORT["checks"].append({"name": label, "result": "passed"})
            return last_root
        time.sleep(2)
    visible = [node_text(node) for node in last_root.iter("node")] if last_root is not None else []
    raise AssertionError(f"Timed out waiting for {label}; visible UI nodes: {visible[:120]}; last UIAutomator error: {last_capture_error}")


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


def set_airplane_mode(enabled: bool, settle_seconds: float = 3.0) -> None:
    action = "enable" if enabled else "disable"
    command = adb("shell", "cmd", "connectivity", "airplane-mode", action, check=False)
    state = adb("shell", "settings", "get", "global", "airplane_mode_on", check=False).stdout.strip()
    if command.returncode != 0 or state != ("1" if enabled else "0"):
        adb("shell", "settings", "put", "global", "airplane_mode_on", "1" if enabled else "0")
        adb(
            "shell", "am", "broadcast", "-a", "android.intent.action.AIRPLANE_MODE",
            "--ez", "state", "true" if enabled else "false", check=False,
        )
        state = adb("shell", "settings", "get", "global", "airplane_mode_on", check=False).stdout.strip()
    if state != ("1" if enabled else "0"):
        raise AssertionError(f"Could not set emulator airplane mode to {enabled}; settings value was {state!r}")
    REPORT.setdefault("connectivity", []).append({"airplane_mode": enabled, "settings_value": state})
    time.sleep(settle_seconds)


def zoom_value(root: ET.Element) -> float | None:
    for node in root.iter("node"):
        match = re.fullmatch(r"([0-9]+(?:[.,][0-9]+)?)×", node_text(node))
        if match:
            return float(match.group(1).replace(",", "."))
    return None


def selected_state(root: ET.Element, label: str) -> bool:
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in find_nodes(root, text=label):
        current: ET.Element | None = node
        while current is not None:
            # Compose RadioButton/selectable semantics can map to either Android selected or checked.
            if current.attrib.get("selected") == "true" or current.attrib.get("checked") == "true":
                return True
            current = parents.get(current)
    return False


def switch_node(root: ET.Element, label: str) -> ET.Element:
    labels = find_nodes(root, text=label)
    if not labels:
        raise AssertionError(f"Settings label {label!r} is not visible")
    target_y = bounds_center(labels[0])[1]
    matches = [
        node for node in root.iter("node")
        if "switch" in node.attrib.get("class", "").lower()
        and node.attrib.get("bounds")
        and abs(bounds_center(node)[1] - target_y) < 80
    ]
    if not matches:
        raise AssertionError(f"No accessible switch appeared beside {label!r}")
    return matches[0]


def move_zoom_slider(root: ET.Element, proportion: float) -> None:
    sliders = [node for node in root.iter("node") if "seekbar" in node.attrib.get("class", "").lower()]
    if not sliders:
        exposed = [
            {key: node.attrib.get(key, "") for key in ("text", "content-desc", "class", "bounds", "clickable", "enabled")}
            for node in root.iter("node")
            if node.attrib.get("class", "").lower().endswith(("seekbar", "slider"))
        ]
        raise AssertionError(f"Compose did not expose an accessible crop-zoom slider; candidates={exposed}")
    slider = sliders[0]
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", slider.attrib.get("bounds", ""))
    if not match:
        raise AssertionError(f"Crop zoom slider has no usable bounds: {slider.attrib}")
    left, top, right, bottom = map(int, match.groups())
    x = round(left + (right - left) * min(max(proportion, 0.05), 0.95))
    y = (top + bottom) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1)


def wallpaper_id_state() -> tuple[tuple[tuple[str, int, int], ...], str]:
    result = adb("shell", "dumpsys", "wallpaper", timeout=60, check=False)
    dump = result.stdout + ("\n" + result.stderr if result.stderr else "")
    if not dump.strip():
        dump = f"dumpsys wallpaper returned no output (exit={result.returncode})"
    section = "other"
    states: list[tuple[str, int, int]] = []
    for line in dump.splitlines():
        lowered = line.lower()
        if "system wallpaper state" in lowered:
            section = "home"
        elif "lock wallpaper state" in lowered:
            section = "lock"
        elif "fallback wallpaper state" in lowered:
            section = "fallback"
        match = re.search(r"\bid=(\d+)\s*:\s*mWhich=(\d+)", line, flags=re.IGNORECASE)
        if match:
            states.append((section, int(match.group(2)), int(match.group(1))))
    return tuple(states), dump


def wait_for_wallpaper_id_change(
    before: tuple[tuple[str, int, int], ...], label: str, timeout: int = 90,
) -> tuple[tuple[str, int, int], ...]:
    deadline = time.monotonic() + timeout
    last_states: tuple[tuple[str, int, int], ...] = ()
    last_dump = ""
    while time.monotonic() < deadline:
        last_states, last_dump = wallpaper_id_state()
        expected_which = 1 if label.lower() == "home" else 2
        def target_state(states: tuple[tuple[str, int, int], ...]) -> tuple[tuple[str, int], ...]:
            return tuple((section, wallpaper_id) for section, which, wallpaper_id in states
                         if which == expected_which and section != "fallback")
        previous_target = target_state(before)
        current_target = target_state(last_states)
        if current_target and current_target != previous_target:
            REPORT.setdefault("wallpaper_service", []).append({
                "target": label,
                "states": [{"section": section, "which": which, "id": wallpaper_id} for section, which, wallpaper_id in last_states],
            })
            REPORT["checks"].append({"name": f"Android wallpaper service changed for {label}", "result": "passed"})
            return last_states
        time.sleep(2)
    diagnostics = last_dump[-1800:].replace("\n", " | ")
    raise AssertionError(f"Android wallpaper ID did not change for {label}; before={before}, after={last_states}, dumpsys tail={diagnostics}")


def screenshot(name: str) -> None:
    path = ARTIFACTS / name
    path.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True, timeout=30).stdout
    path.write_bytes(data)
    REPORT["screenshots"].append(str(path))


def wallpaper_nodes(root: ET.Element) -> list[ET.Element]:
    return [node for node in root.iter("node") if node_text(node).startswith("Wallpaper")]


def wallpaper_descriptions(root: ET.Element) -> list[str]:
    return [node_text(node) for node in wallpaper_nodes(root)]


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
    any_count = len(wallpaper_descriptions(root))
    tap_text("Landscape", timeout=20)
    root = wait_for_feed_content("landscape filter", timeout=60)
    landscapes = wallpaper_descriptions(root)
    known_landscapes = [pair for item in landscapes if (pair := parse_dimensions(item)) is not None]
    invalid_landscapes = [item for item in landscapes if (pair := parse_dimensions(item)) is not None and pair[0] <= pair[1] * 1.05]
    if invalid_landscapes:
        raise AssertionError(f"Landscape filter displayed a non-landscape image: {invalid_landscapes}")
    if not landscapes or not known_landscapes:
        raise AssertionError("Wallhaven's real-dimension Landscape filter produced no measurable results")
    REPORT["orientation_result_counts"] = {"any": any_count, "portrait": len(portraits), "landscape": len(landscapes)}
    screenshot("04-landscape-filter.png")
    tap_text("Any", timeout=20)
    root = wait_for_feed_content("Wallhaven results after clearing orientation", timeout=60)

    # Favorite and queue an actual Wallhaven original before testing Openverse licensing, which can
    # legitimately disallow direct downloads for some individual works.
    tile_nodes = wallpaper_nodes(root)
    if not tile_nodes:
        raise AssertionError("No accessible Wallhaven wallpaper card was available to open")
    tap_node(clickable_target(root, tile_nodes[0]))
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorite")), "Wallhaven detail actions", timeout=60)
    if not find_nodes(detail, text="Wallhaven"):
        raise AssertionError("Selecting Wallhaven did not open a Wallhaven wallpaper detail")
    screenshot("04-wallhaven-detail.png")
    # Persist one live result in both Favorites and a newly created collection.
    tap_text("Collection", timeout=20)
    collection_dialog = wait_until(lambda ui: bool(find_nodes(ui, text="Add to collection")), "Add to collection dialog", timeout=30)
    screenshot("05-add-to-collection.png")
    tap_text("New collection", timeout=20)
    wait_until(lambda ui: bool(find_nodes(ui, text="Name")) and bool(find_nodes(ui, text="Create")), "new collection name dialog", timeout=20)
    collection_name = "AuroroSmokeCollection"
    tap_text("Name", timeout=15)
    adb("shell", "input", "text", collection_name)
    tap_text("Create", timeout=15)
    collection_dialog = wait_until(
        lambda ui: bool(find_nodes(ui, text=collection_name)) and bool(find_nodes(ui, text="Done")),
        "created collection with the selected wallpaper", timeout=45,
    )
    member_node = find_nodes(collection_dialog, text=collection_name)[0]
    member_y = bounds_center(member_node)[1]
    member_checked = any(
        node.attrib.get("checked") == "true" and abs(bounds_center(node)[1] - member_y) < 55
        for node in collection_dialog.iter("node")
        if node.attrib.get("bounds")
    )
    REPORT["collection_membership_checked"] = member_checked
    tap_text("Done", timeout=20)
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorite")), "return to Wallhaven detail", timeout=20)
    tap_text("Favorite", timeout=20)
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorited")), "favorite saved in detail", timeout=30)
    REPORT["checks"].append({"name": "favorite and collection actions", "result": "passed"})
    screenshot("06-wallhaven-favorited.png")

    # Keep WorkManager constrained in airplane mode so cancellation and retry are deterministic.
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
    set_airplane_mode(True)
    tap_node(download_target)
    download_state = wait_until(
        lambda ui: bool(find_nodes(ui, text="Downloading")) or bool(find_nodes(ui, text="Saved")) or bool(find_nodes(ui, text="Couldn't queue download", contains=True)),
        "download enqueued or surfaced an honest error", timeout=45,
    )
    if find_nodes(download_state, text="Couldn't queue download", contains=True):
        raise AssertionError("The UI surfaced a download queue failure")
    REPORT["checks"].append({"name": "original download queued with network disabled", "result": "passed"})
    screenshot("07-download-queued.png")
    adb("shell", "input", "keyevent", "4")
    wait_until(lambda ui: bool(find_nodes(ui, text="ocean")) and bool(wallpaper_descriptions(ui)), "return to Search before Offline", timeout=45)
    tap_text("Offline", prefer_bottom=True, timeout=20)
    offline = wait_until(
        lambda ui: bool(find_nodes(ui, text="Queued original")) or bool(find_nodes(ui, text="Saving original")),
        "WorkManager download visible in Offline", timeout=45,
    )
    cancel_nodes = find_nodes(offline, description="Cancel download")
    if not cancel_nodes:
        raise AssertionError("Queued original had no accessible Cancel download action")
    tap_node(cancel_nodes[0])
    canceled = wait_until(lambda ui: bool(find_nodes(ui, text="Download canceled")), "download cancellation persisted", timeout=45)
    screenshot("08-download-canceled.png")
    retry_nodes = find_nodes(canceled, description="Retry download")
    if not retry_nodes:
        raise AssertionError("Canceled original had no accessible Retry download action")
    tap_node(retry_nodes[0])
    retried = wait_until(lambda ui: bool(find_nodes(ui, text="Queued original")), "canceled download queued for retry", timeout=45)
    screenshot("09-download-retried.png")
    REPORT["checks"].append({"name": "download cancellation and retry actions", "result": "passed"})

    set_airplane_mode(False, settle_seconds=0.5)
    progress_state = wait_until(
        lambda ui: bool(find_nodes(ui, text="Saving original")) or bool(find_nodes(ui, text="Saved original")) or bool(find_nodes(ui, text="Download failed")),
        "retried original shows progress or reaches a terminal state", timeout=45,
    )
    REPORT["download_progress_visible"] = bool(find_nodes(progress_state, text="Saving original"))
    screenshot("10-download-progress.png")
    completed = wait_until(
        lambda ui: bool(find_nodes(ui, text="Saved original")) or bool(find_nodes(ui, text="Download failed")),
        "retried original completes or reports a failure", timeout=360,
    )
    if find_nodes(completed, text="Download failed"):
        visible = [node_text(node) for node in completed.iter("node") if node_text(node)]
        raise AssertionError(f"The retried Wallhaven original failed: {visible[:120]}")
    screenshot("10-download-completed.png")
    REPORT["checks"].append({"name": "retried original saved as a local file", "result": "passed"})

    # Reopen the actual saved result through Search, then exercise the immersive crop and both Android targets.
    tap_text("Search", prefer_bottom=True, timeout=20)
    search = wait_until(
        lambda ui: bool(find_nodes(ui, text="ocean")) and bool(wallpaper_descriptions(ui)),
        "restore Wallhaven search after Offline download", timeout=45,
    )
    tile_nodes = wallpaper_nodes(search)
    if not tile_nodes:
        raise AssertionError("No Wallhaven tile was available after restoring Search")
    tap_node(clickable_target(search, tile_nodes[0]))
    detail = wait_until(lambda ui: bool(find_nodes(ui, text="Favorited")) and bool(find_nodes(ui, text="Set wallpaper")), "saved Wallhaven detail", timeout=60)
    tap_text("Set wallpaper", timeout=20)
    apply_dialog = wait_until(
        lambda ui: bool(find_nodes(ui, text="Home screen")) and bool(find_nodes(ui, text="Cancel")),
        "wallpaper target chooser", timeout=30,
    )
    screenshot("11-wallpaper-target-dialog.png")
    tap_text("Home screen", timeout=20)
    crop = wait_until(lambda ui: bool(find_nodes(ui, text="Preview & crop")) and bool(find_nodes(ui, text="Reset")), "full-screen crop route", timeout=60)
    screenshot("12-full-screen-crop.png")
    move_zoom_slider(crop, 0.62)
    zoomed = wait_until(lambda ui: (zoom_value(ui) or 1.0) > 1.5, "crop zoom slider changes scale", timeout=25)
    size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size").stdout)
    if size:
        width, height = map(int, size.groups())
        adb("shell", "input", "swipe", str(int(width * 0.68)), str(int(height * 0.45)), str(int(width * 0.35)), str(int(height * 0.46)), "500")
    tap_text("Reset", timeout=15)
    reset = wait_until(lambda ui: zoom_value(ui) == 1.0, "crop reset restores 1.0x", timeout=20)
    move_zoom_slider(reset, 0.56)
    wait_until(lambda ui: (zoom_value(ui) or 1.0) > 1.2, "crop zoom restored before applying", timeout=20)
    home_button = wait_until(
        lambda ui: any(clickable_target(ui, node).attrib.get("enabled") == "true" for node in find_nodes(ui, text="Set Home screen")),
        "saved original enables Home screen setting", timeout=60,
    )
    screenshot("13-crop-home-ready.png")
    before_home_ids, _ = wallpaper_id_state()
    tap_text("Set Home screen", timeout=20)
    home_applied = wait_until(
        lambda ui: bool(find_nodes(ui, text="Favorited")) and bool(find_nodes(ui, text="Set wallpaper"))
        and any(node.attrib.get("content-desc", "").startswith("Full wallpaper preview") for node in ui.iter("node")),
        "return to wallpaper detail after Home setting", timeout=45,
    )
    after_home_ids = wait_for_wallpaper_id_change(before_home_ids, "Home", timeout=120)
    screenshot("14-home-wallpaper-applied.png")
    REPORT["checks"].append({"name": "immersive crop zoom, pan, reset and Home wallpaper setting", "result": "passed"})

    detail = home_applied
    tap_text("Set wallpaper", timeout=20)
    wait_until(lambda ui: bool(find_nodes(ui, text="Lock screen")) and bool(find_nodes(ui, text="Cancel")), "Lock screen target choice", timeout=30)
    tap_text("Lock screen", timeout=20)
    lock_crop = wait_until(lambda ui: bool(find_nodes(ui, text="Preview & crop")) and bool(find_nodes(ui, text="Set Lock screen")), "saved image crop for Lock screen", timeout=60)
    screenshot("15-crop-lock-ready.png")
    before_lock_ids, _ = wallpaper_id_state()
    tap_text("Set Lock screen", timeout=20)
    lock_applied = wait_until(
        lambda ui: bool(find_nodes(ui, text="Favorited")) and bool(find_nodes(ui, text="Set wallpaper"))
        and any(node.attrib.get("content-desc", "").startswith("Full wallpaper preview") for node in ui.iter("node")),
        "return to wallpaper detail after Lock setting", timeout=45,
    )
    after_lock_ids = wait_for_wallpaper_id_change(before_lock_ids, "Lock", timeout=120)
    screenshot("16-lock-wallpaper-applied.png")
    REPORT["checks"].append({"name": "Lock screen wallpaper setting", "result": "passed"})

    # Back navigation restores Search, while Offline shows the completed original record.
    back_nodes = find_nodes(lock_applied, description="Back to wallpapers")
    if not back_nodes:
        raise AssertionError("Wallpaper detail did not expose its accessible Back to wallpapers control")
    tap_node(back_nodes[0])
    restored = wait_until(lambda ui: bool(find_nodes(ui, text="ocean")) and bool(wallpaper_descriptions(ui)), "back to Wallhaven results", timeout=45)
    screenshot("17-restored-wallhaven-search.png")
    REPORT["checks"].append({"name": "detail-to-search navigation retains results", "result": "passed"})
    tap_text("Offline", prefer_bottom=True, timeout=20)
    offline = wait_until(
        lambda ui: bool(find_nodes(ui, text="Offline")) and bool(find_nodes(ui, text="Saved original")),
        "completed original in the persisted Offline screen", timeout=45,
    )
    if find_nodes(offline, text="No downloads yet"):
        raise AssertionError("The completed original has no persisted Offline record")
    screenshot("18-offline-saved-original.png")
    REPORT["checks"].append({"name": "completed original remains in Offline", "result": "passed"})

    # Favorites and collection membership must survive navigation and remain visible as real results.
    tap_text("Search", prefer_bottom=True, timeout=20)
    search = wait_until(lambda ui: bool(find_nodes(ui, text="ocean")) and bool(wallpaper_descriptions(ui)), "Search restored after Offline", timeout=45)
    menu_nodes = find_nodes(search, description="Open navigation menu")
    if not menu_nodes:
        raise AssertionError("Search did not expose its navigation menu")
    tap_node(menu_nodes[0])
    drawer = wait_until(lambda ui: bool(find_nodes(ui, text="Favorites")), "navigation drawer with Favorites", timeout=20)
    tap_text("Favorites", timeout=20)
    favorites = wait_until(
        lambda ui: bool(find_nodes(ui, text="Favorites")) and bool(find_nodes(ui, text="Saved wallpapers")) and bool(wallpaper_descriptions(ui)),
        "saved favorite wallpaper screen", timeout=45,
    )
    screenshot("19-favorites.png")
    REPORT["checks"].append({"name": "favorite persists in Favorites", "result": "passed"})
    tap_text("Collections", prefer_bottom=True, timeout=20)
    collections = wait_until(lambda ui: bool(find_nodes(ui, text=collection_name)), "saved collection appears in Collections", timeout=45)
    screenshot("20-collections.png")
    collection_card = find_nodes(collections, text=collection_name)[0]
    tap_node(clickable_target(collections, collection_card))
    collection_detail = wait_until(
        lambda ui: bool(find_nodes(ui, text=collection_name)) and bool(find_nodes(ui, text="1 saved wallpaper")) and bool(wallpaper_descriptions(ui)),
        "collection contains the selected wallpaper", timeout=45,
    )
    screenshot("21-collection-detail.png")
    REPORT["checks"].append({"name": "new collection retains its wallpaper membership", "result": "passed"})
    adb("shell", "input", "keyevent", "4")
    tap_text("Search", prefer_bottom=True, timeout=20)
    search = wait_until(lambda ui: bool(find_nodes(ui, text="ocean")) and bool(wallpaper_descriptions(ui)), "Search restored after collection navigation", timeout=45)
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
    openverse_tiles = wallpaper_nodes(openverse_root)
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
        "Settings appearance and transparency controls", timeout=45,
    )
    if not selected_state(settings, "System"):
        raise AssertionError("The System theme is not initially selected")
    tap_text("Dark ocean", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "Dark ocean"), "Dark ocean theme selection", timeout=20)
    screenshot("22-theme-dark.png")
    tap_text("Light sky", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "Light sky"), "Light sky theme selection", timeout=20)
    screenshot("23-theme-light.png")
    tap_text("System", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "System"), "restore System theme", timeout=20)
    tap_text("Emerald", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "Emerald"), "Emerald accent selection", timeout=20)
    tap_text("Aqua", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "Aqua"), "restore Aqua accent", timeout=20)
    transparency = switch_node(settings, "Reduce transparency")
    if transparency.attrib.get("checked") != "false":
        raise AssertionError(f"Expected fresh-install Reduce transparency=false, got {transparency.attrib}")
    tap_node(transparency)
    settings = wait_until(
        lambda ui: switch_node(ui, "Reduce transparency").attrib.get("checked") == "true",
        "Reduce transparency preference toggled on", timeout=20,
    )
    screenshot("24-settings-appearance.png")

    size_output = adb("shell", "wm", "size").stdout
    size = re.search(r"(\d+)x(\d+)", size_output)
    if size:
        width, height = map(int, size.groups())
        for _ in range(8):
            if find_nodes(settings, text="500 MB"):
                break
            adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.82)), str(width // 2), str(int(height * 0.27)), "450")
            time.sleep(1)
            settings = dump_hierarchy()
    if not find_nodes(settings, text="500 MB"):
        raise AssertionError("The Image cache section and its 500 MB setting were not reachable")
    tap_text("500 MB", timeout=20)
    settings = wait_until(lambda ui: selected_state(ui, "500 MB"), "500 MB image cache limit selected", timeout=20)
    screenshot("25-settings-cache.png")
    REPORT["checks"].append({"name": "theme, accent, transparency and image-cache settings", "result": "passed"})

    for _ in range(8):
        if find_nodes(settings, text="Wallpaper sources", contains=True):
            break
        adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.82)), str(width // 2), str(int(height * 0.27)), "450")
        time.sleep(1)
        settings = dump_hierarchy()
    source_action = find_nodes(settings, text="Wallpaper sources", contains=True)
    if not source_action:
        raise AssertionError("The Settings screen did not expose its real Wallpaper sources entry")
    tap_node(source_action[0])
    sources = wait_until(
        lambda ui: bool(find_nodes(ui, text="Two integrated wallpaper catalogues")) and bool(find_nodes(ui, text="Wallhaven")),
        "integrated provider settings", timeout=45,
    )
    screenshot("26-source-settings.png")
    REPORT["checks"].append({"name": "settings and provider configuration navigation", "result": "passed"})
    adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    wait_until(lambda ui: bool(find_nodes(ui, text="Discover")), "return from source settings to Home", timeout=45)

    # A process death must keep DataStore choices, favorites, collection membership and saved files.
    adb("shell", "am", "force-stop", "com.auroro.wallpapers")
    adb("shell", "am", "start", "-W", "-n", "com.auroro.wallpapers/.app.MainActivity", timeout=60)
    home = wait_until(lambda ui: bool(find_nodes(ui, text="Discover")), "Home after process recreation", timeout=60)
    tap_text("Settings", timeout=20)
    settings = wait_until(
        lambda ui: bool(find_nodes(ui, text="Appearance")) and bool(find_nodes(ui, text="Reduce transparency")),
        "restored Settings after process recreation", timeout=45,
    )
    if not selected_state(settings, "System") or not selected_state(settings, "Aqua"):
        raise AssertionError("Theme or accent selection did not survive process recreation")
    if switch_node(settings, "Reduce transparency").attrib.get("checked") != "true":
        raise AssertionError("Reduce transparency did not survive process recreation")
    for _ in range(8):
        if find_nodes(settings, text="500 MB"):
            break
        adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.82)), str(width // 2), str(int(height * 0.27)), "450")
        time.sleep(1)
        settings = dump_hierarchy()
    if not selected_state(settings, "500 MB"):
        raise AssertionError("The 500 MB cache preference did not survive process recreation")
    REPORT["checks"].append({"name": "settings persist across Android process recreation", "result": "passed"})
    adb("shell", "input", "keyevent", "4")

    home = wait_until(lambda ui: bool(find_nodes(ui, text="Discover")), "Home after Settings", timeout=45)
    menu_nodes = find_nodes(home, description="Open navigation menu")
    if not menu_nodes:
        raise AssertionError("Home did not expose its navigation drawer after process recreation")
    tap_node(menu_nodes[0])
    wait_until(lambda ui: bool(find_nodes(ui, text="Favorites")), "recreated navigation drawer", timeout=20)
    tap_text("Favorites", timeout=20)
    favorites = wait_until(
        lambda ui: bool(find_nodes(ui, text="Favorites")) and bool(wallpaper_descriptions(ui)),
        "Room favorite after process recreation", timeout=45,
    )
    tap_text("Collections", prefer_bottom=True, timeout=20)
    collections = wait_until(lambda ui: bool(find_nodes(ui, text=collection_name)), "Room collection after process recreation", timeout=45)
    tap_node(clickable_target(collections, find_nodes(collections, text=collection_name)[0]))
    collection_detail = wait_until(
        lambda ui: bool(find_nodes(ui, text="1 saved wallpaper")) and bool(wallpaper_descriptions(ui)),
        "Room collection membership after process recreation", timeout=45,
    )
    REPORT["checks"].append({"name": "favorites and collection survive process recreation", "result": "passed"})
    adb("shell", "input", "keyevent", "4")

    # Finally disable radios and open the locally saved image without any provider network access.
    set_airplane_mode(True)
    tap_text("Offline", prefer_bottom=True, timeout=20)
    offline = wait_until(lambda ui: bool(find_nodes(ui, text="Offline")) and bool(find_nodes(ui, text="Saved original")), "saved original in airplane mode", timeout=45)
    saved_tiles = [node for node in offline.iter("node") if node.attrib.get("content-desc", "").startswith("Saved wallpaper")]
    if not saved_tiles:
        raise AssertionError("Offline did not expose the saved wallpaper preview in airplane mode")
    tap_node(clickable_target(offline, saved_tiles[0]))
    offline_detail = wait_until(
        lambda ui: any(node.attrib.get("content-desc", "").startswith("Full wallpaper preview") for node in ui.iter("node"))
        and bool(find_nodes(ui, text="Favorited")),
        "local wallpaper detail available offline", timeout=60,
    )
    if find_nodes(offline_detail, text="Preview unavailable"):
        raise AssertionError("Offline wallpaper detail fell back to a broken network preview")
    screenshot("27-airplane-mode-offline-detail.png")
    REPORT["checks"].append({"name": "saved original opens from local storage in airplane mode", "result": "passed"})
    set_airplane_mode(False)
    adb("shell", "input", "keyevent", "4")
    wait_until(lambda ui: bool(find_nodes(ui, text="Discover")) or bool(find_nodes(ui, text="Offline")), "exit offline detail", timeout=45)

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
