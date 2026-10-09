#!/usr/bin/env python3
"""Turn the interesting parts of a Gradle log into GitHub check-run annotations.

Annotations are readable through the GitHub REST API, which makes failures
diagnosable without downloading raw job logs.
"""
import re
import sys

path = sys.argv[1]
lines = open(path, errors="replace").read().splitlines()

def esc(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")

def emit(title: str, body: list[str], limit: int = 45000):
    text = "\n".join(body)
    if len(text) > limit:
        text = text[:limit] + "\n...[truncated]"
    print(f"::error title={title}::{esc(text)}")

kotlin = [l for l in lines if l.startswith("e: ") or re.search(r"error: ", l)]
if kotlin:
    emit("Kotlin/Java compile errors", kotlin[:120])

wrong = []
for i, l in enumerate(lines):
    if l.startswith("* What went wrong"):
        wrong.extend(lines[i : i + 14])
        wrong.append("-----")
if wrong:
    emit("Gradle: what went wrong", wrong[:120])

failed = []
for i, l in enumerate(lines):
    if re.search(r"> .* FAILED$", l) or re.search(r"^\s+(java|kotlin|org|androidx)[\w.]*(Exception|Error)", l):
        failed.append(l)
if failed:
    emit("Failed tests", failed[:120])

emit("Log tail", lines[-70:])
