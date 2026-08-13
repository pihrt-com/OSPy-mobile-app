#!/usr/bin/env python3
"""Generate complete Android string resources while preserving format tokens."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import time
import xml.etree.ElementTree as ET

from deep_translator import GoogleTranslator


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "values/strings.xml"
CACHE_FILE = ROOT / "app/build/translation_cache_v2.json"
LANGUAGES = {
    "cs": ("cs", "values-cs"),
    "de": ("de", "values-de"),
    "pl": ("pl", "values-pl"),
    "sk": ("sk", "values-sk"),
    "es": ("es", "values-es"),
    "fr": ("fr", "values-fr"),
    "it": ("it", "values-it"),
    "sr": ("sr", "values-sr"),
    "ru": ("ru", "values-ru"),
    "da": ("da", "values-da"),
    "zh-CN": ("zh-CN", "values-zh-rCN"),
    "ja": ("ja", "values-ja"),
    "ko": ("ko", "values-ko"),
}
FORMAT_TOKEN = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%|\\[nrt]|https?://\S+|"
    r"\b(?:OSPy|Android|GitHub|Google Play|API|FCM|HTTP|HTTPS|DNS|IP|Wi-Fi|LAN|2FA)\b"
)


def protect(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def replace(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"8888{len(tokens) - 1:04d}8888"

    return FORMAT_TOKEN.sub(replace, text), tokens


def restore(text: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        text = re.sub(
            f"8888{index:04d}8888", lambda unused, value=token: value, text,
        )
    return text.replace("'", r"\'")


def translate_text(translator: GoogleTranslator, text: str, cache: dict[str, str], language: str) -> str:
    cache_key = f"{language}\0{text}"
    if cache_key in cache:
        return cache[cache_key]
    protected, tokens = protect(text)
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            translated = translator.translate(protected)
            result = restore(translated, tokens)
            cache[cache_key] = result
            CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
            CACHE_FILE.write_text(
                json.dumps(cache, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            return result
        except Exception as error:  # network service can transiently throttle
            last_error = error
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Translation failed for {language}: {text}") from last_error


def fill_cache(translator: GoogleTranslator, texts: list[str], cache: dict[str, str], language: str) -> None:
    missing = list(dict.fromkeys(
        text for text in texts if f"{language}\0{text}" not in cache
    ))
    while missing:
        batch: list[str] = []
        length = 0
        while missing and len(batch) < 20:
            candidate = missing[0]
            if batch and length + len(candidate) > 3500:
                break
            batch.append(missing.pop(0))
            length += len(candidate)
        protected_items: list[str] = []
        item_tokens: list[list[str]] = []
        for text in batch:
            protected, tokens = protect(text)
            protected_items.append(protected)
            item_tokens.append(tokens)
        separators = [f"9999{index:04d}9999" for index in range(len(batch) - 1)]
        joined_parts: list[str] = []
        for index, protected in enumerate(protected_items):
            joined_parts.append(protected)
            if index < len(separators):
                joined_parts.append(separators[index])
        joined = "\n".join(joined_parts)
        try:
            translated = translator.translate(joined)
            pattern = "|".join(re.escape(value) for value in separators)
            parts = re.split(pattern, translated, flags=re.IGNORECASE) if pattern else [translated]
            if len(parts) != len(batch):
                raise ValueError("translation service changed a batch separator")
            for source, value, tokens in zip(batch, parts, item_tokens):
                cache[f"{language}\0{source}"] = restore(value.strip(), tokens)
            CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
            CACHE_FILE.write_text(
                json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8",
            )
            print(f"{language}: {len(texts) - len(missing)}/{len(texts)}", flush=True)
        except Exception:
            for source in batch:
                translate_text(translator, source, cache, language)


def indent(element: ET.Element, level: int = 0) -> None:
    spacing = "\n" + "    " * level
    child_spacing = "\n" + "    " * (level + 1)
    if len(element):
        if not element.text or not element.text.strip():
            element.text = child_spacing
        for child in element:
            indent(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = child_spacing
        element[-1].tail = spacing
    elif level and (not element.tail or not element.tail.strip()):
        element.tail = spacing


def generate(language: str, folder: str, cache: dict[str, str]) -> None:
    source_root = ET.parse(SOURCE).getroot()
    target_root = ET.Element("resources")
    translator = GoogleTranslator(source="en", target=language)
    destination = RES / folder / "strings.xml"
    existing: dict[tuple[str, str], str] = {}
    if destination.exists():
        for resource in ET.parse(destination).getroot():
            name = resource.attrib.get("name", "")
            if resource.tag == "string":
                existing[(name, "")] = resource.text or ""
            elif resource.tag in {"plurals", "string-array"}:
                for item in resource:
                    discriminator = item.attrib.get("quantity", str(len(existing)))
                    existing[(name, discriminator)] = item.text or ""
    source_texts: list[str] = []
    for source in source_root:
        if source.attrib.get("translatable") == "false":
            continue
        name = source.attrib.get("name", "")
        if source.tag == "string" and (name, "") not in existing:
            source_texts.append(source.text or "")
        elif source.tag in {"plurals", "string-array"}:
            for index, item in enumerate(source):
                discriminator = item.attrib.get("quantity", str(index))
                if (name, discriminator) not in existing:
                    source_texts.append(item.text or "")
    fill_cache(translator, source_texts, cache, language)
    total = len(source_root)
    for index, source in enumerate(source_root, start=1):
        target = ET.SubElement(target_root, source.tag, dict(source.attrib))
        if source.attrib.get("translatable") == "false":
            target.text = source.text
        elif source.tag == "string":
            target.text = existing.get((source.attrib.get("name", ""), ""))
            if target.text is None:
                target.text = translate_text(
                    translator, source.text or "", cache, language,
                )
        elif source.tag in {"plurals", "string-array"}:
            source_discriminators: set[str] = set()
            for item_index, source_item in enumerate(source):
                target_item = ET.SubElement(
                    target, source_item.tag, dict(source_item.attrib),
                )
                discriminator = source_item.attrib.get("quantity", str(item_index))
                source_discriminators.add(discriminator)
                target_item.text = existing.get(
                    (source.attrib.get("name", ""), discriminator))
                if target_item.text is None:
                    target_item.text = translate_text(
                        translator, source_item.text or "", cache, language,
                    )
            if source.tag == "plurals":
                name = source.attrib.get("name", "")
                for (existing_name, quantity), value in existing.items():
                    if existing_name != name or quantity in source_discriminators:
                        continue
                    extra_item = ET.SubElement(target, "item", {"quantity": quantity})
                    extra_item.text = value
        if index % 50 == 0:
            print(f"{language}: {index}/{total}", flush=True)
    indent(target_root)
    destination.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(target_root).write(
        destination, encoding="utf-8", xml_declaration=True,
    )
    print(f"Created {destination}", flush=True)


def main() -> int:
    cache = (
        json.loads(CACHE_FILE.read_text(encoding="utf-8"))
        if CACHE_FILE.exists() else {}
    )
    selected = sys.argv[1:] or list(LANGUAGES)
    for locale in selected:
        if locale not in LANGUAGES:
            raise SystemExit(f"Unknown locale: {locale}")
        language, folder = LANGUAGES[locale]
        generate(language, folder, cache)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
