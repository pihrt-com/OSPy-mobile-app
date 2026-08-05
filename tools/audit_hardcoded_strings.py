#!/usr/bin/env python3
"""Find likely user-visible Java strings that bypass Android resources."""

from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "app/src/main/java"

PATTERNS = [
    re.compile(r"\.(?:setText|setTitle|setSubtitle|setMessage|setContentDescription)\(\s*\"([^\"]+)\""),
    re.compile(r"\b(?:text|button|compactButton|heading|message|toast|input|statusCard)\(\s*\"([^\"]+)\""),
]

# Symbols, protocol defaults and product names are intentionally not translated.
ALLOWED = {
    "×",
    "—",
    "OSPy",
    "https://",
    "24",
}

findings: list[tuple[Path, int, str]] = []
for path in sorted(JAVA_ROOT.rglob("*.java")):
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        for pattern in PATTERNS:
            for match in pattern.finditer(line):
                literal = match.group(1)
                if literal in ALLOWED:
                    continue
                if literal.startswith(("http://", "https://")):
                    continue
                if not any(character.isalpha() for character in literal):
                    continue
                findings.append((path.relative_to(ROOT), number, literal))

if findings:
    print("Possible user-visible hard-coded strings:")
    for path, number, literal in findings:
        print(f"{path}:{number}: {literal}")
    raise SystemExit(1)

print("Hard-coded UI string audit passed.")
