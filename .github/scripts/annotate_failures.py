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


def emit_chunks(title: str, body: list[str], limit: int = 3200):
    """Keep check annotations small enough that GitHub's API does not truncate them."""
    chunks: list[list[str]] = []
    current: list[str] = []
    size = 0
    for line in body:
        extra = len(line) + (1 if current else 0)
        if current and size + extra > limit:
            chunks.append(current)
            current = []
            size = 0
            extra = len(line)
        current.append(line)
        size += extra
    if current:
        chunks.append(current)
    for index, chunk in enumerate(chunks, start=1):
        suffix = f" ({index}/{len(chunks)})" if len(chunks) > 1 else ""
        emit(title + suffix, chunk, limit)

kotlin = [l for l in lines if l.startswith("e: ") or re.search(r"error: ", l)]
if kotlin:
    emit_chunks("Kotlin/Java compile errors", kotlin[:120])

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
