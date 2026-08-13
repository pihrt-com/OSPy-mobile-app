#!/usr/bin/env python3
"""Validate Android translation resources used by OSPy Mobile."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
BASE_FILE = RES / "values/strings.xml"
LOCALE_CONFIG = RES / "xml/locales_config.xml"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
REPLACEMENT_CHARACTER = "\ufffd"


def placeholders(text: str) -> list[str]:
    return sorted(PLACEHOLDER.findall(text or ""))


def parse_resources(path: Path) -> dict[str, tuple[str, object]]:
    root = ET.parse(path).getroot()
    result: dict[str, tuple[str, object]] = {}
    duplicates: list[str] = []
    for element in root:
        if element.tag not in {"string", "plurals", "string-array"}:
            continue
        name = element.attrib.get("name")
        if not name or element.attrib.get("translatable") == "false":
            continue
        if name in result:
            duplicates.append(name)
        if element.tag == "string":
            value: object = "".join(element.itertext())
        else:
            value = {
                child.attrib.get("quantity", str(index)): "".join(child.itertext())
                for index, child in enumerate(element)
            }
        result[name] = (element.tag, value)
    if duplicates:
        raise ValueError(f"duplicate resources: {', '.join(sorted(duplicates))}")
    return result


def compare_file(base: dict[str, tuple[str, object]], path: Path) -> list[str]:
    errors: list[str] = []
    try:
        translated = parse_resources(path)
    except Exception as error:
        return [f"{path}: {error}"]

    missing = sorted(set(base) - set(translated))
    extra = sorted(set(translated) - set(base))
    if missing:
        errors.append(f"{path}: missing keys: {', '.join(missing)}")
    if extra:
        errors.append(f"{path}: unknown keys: {', '.join(extra)}")

    for key in sorted(set(base) & set(translated)):
        base_type, base_value = base[key]
        translated_type, translated_value = translated[key]
        if base_type != translated_type:
            errors.append(
                f"{path}: {key}: resource type {translated_type} != {base_type}"
            )
            continue

        if base_type == "string":
            if REPLACEMENT_CHARACTER in str(translated_value):
                errors.append(f"{path}: {key}: contains Unicode replacement character")
            if placeholders(str(base_value)) != placeholders(str(translated_value)):
                errors.append(
                    f"{path}: {key}: placeholders {placeholders(str(translated_value))} "
                    f"!= {placeholders(str(base_value))}"
                )
        else:
            base_items = dict(base_value)  # type: ignore[arg-type]
            translated_items = dict(translated_value)  # type: ignore[arg-type]
            if not translated_items:
                errors.append(f"{path}: {key}: resource has no items")
                continue
            reference = base_items.get("other", next(iter(base_items.values()), ""))
            expected = placeholders(reference)
            for quantity, value in translated_items.items():
                if REPLACEMENT_CHARACTER in value:
                    errors.append(
                        f"{path}: {key}/{quantity}: contains Unicode replacement character"
                    )
                if placeholders(value) != expected:
                    errors.append(
                        f"{path}: {key}/{quantity}: placeholders "
                        f"{placeholders(value)} != {expected}"
                    )
    return errors


def main() -> int:
    errors: list[str] = []
    try:
        base = parse_resources(BASE_FILE)
    except Exception as error:
        print(f"Cannot parse base resources: {error}", file=sys.stderr)
        return 1

    for key, (kind, value) in base.items():
        if kind == "string":
            if REPLACEMENT_CHARACTER in str(value):
                errors.append(f"{BASE_FILE}: {key}: contains Unicode replacement character")
        else:
            for quantity, item_value in dict(value).items():  # type: ignore[arg-type]
                if REPLACEMENT_CHARACTER in item_value:
                    errors.append(
                        f"{BASE_FILE}: {key}/{quantity}: contains Unicode replacement character"
                    )

    translated_files = sorted(RES.glob("values-*/strings.xml"))
    if not translated_files:
        errors.append("No translated values-*/strings.xml files were found")

    for path in translated_files:
        errors.extend(compare_file(base, path))

    try:
        locale_root = ET.parse(LOCALE_CONFIG).getroot()
        configured = [item.attrib.get("{http://schemas.android.com/apk/res/android}name")
                      for item in locale_root.findall("locale")]
        configured = [item for item in configured if item]
        expected = ["en"] + [
            path.parent.name.removeprefix("values-").replace("zh-rCN", "zh-CN")
            for path in translated_files
        ]
        if Counter(configured) != Counter(expected):
            errors.append(
                f"{LOCALE_CONFIG}: configured locales {configured} != {expected}"
            )
    except Exception as error:
        errors.append(f"{LOCALE_CONFIG}: {error}")

    java_text = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((ROOT / "app/src/main/java").rglob("*.java"))
    )
    referenced_strings = set(re.findall(r"(?<![A-Za-z0-9_.])R\.string\.([A-Za-z0-9_]+)", java_text))
    referenced_plurals = set(re.findall(r"(?<![A-Za-z0-9_.])R\.plurals\.([A-Za-z0-9_]+)", java_text))
    base_strings = {name for name, (kind, _) in base.items() if kind == "string"}
    base_plurals = {name for name, (kind, _) in base.items() if kind == "plurals"}
    missing_string_refs = sorted(referenced_strings - base_strings)
    missing_plural_refs = sorted(referenced_plurals - base_plurals)
    if missing_string_refs:
        errors.append(f"Java references missing strings: {', '.join(missing_string_refs)}")
    if missing_plural_refs:
        errors.append(f"Java references missing plurals: {', '.join(missing_plural_refs)}")

    if errors:
        print("Translation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Translation validation passed: {len(base)} resources, "
        f"{len(translated_files)} translated languages."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
