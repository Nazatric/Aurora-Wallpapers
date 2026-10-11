#!/usr/bin/env python3
"""Extract one version's Keep-a-Changelog section for a tagged release."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="Version without the leading v, e.g. 0.1")
    parser.add_argument("changelog", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    heading = re.compile(rf"^## \[{re.escape(args.version)}\](?:\s|$)")
    lines = args.changelog.read_text(encoding="utf-8").splitlines()
    starts = [index for index, line in enumerate(lines) if heading.match(line)]
    if len(starts) != 1:
        raise SystemExit(
            f"Expected exactly one changelog section for {args.version}; found {len(starts)}"
        )

    start = starts[0] + 1
    end = next(
        (index for index in range(start, len(lines)) if lines[index].startswith("## ")),
        len(lines),
    )
    notes = "\n".join(lines[start:end]).strip()
    if not notes:
        raise SystemExit(f"Changelog section for {args.version} is empty")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(f"# Auroro Wallpapers {args.version}\n\n{notes}\n", encoding="utf-8")


if __name__ == "__main__":
    main()
