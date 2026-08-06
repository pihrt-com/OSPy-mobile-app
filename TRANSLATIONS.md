# OSPy Mobile translations

OSPy Mobile uses native Android string resources. English is the source language
and Czech, German, Polish and Slovak are included.

## Resource files

- `app/src/main/res/values/strings.xml` — English source
- `app/src/main/res/values-cs/strings.xml` — Czech
- `app/src/main/res/values-de/strings.xml` — German
- `app/src/main/res/values-pl/strings.xml` — Polish
- `app/src/main/res/values-sk/strings.xml` — Slovak
- `app/src/main/res/xml/locales_config.xml` — languages offered by Android

To add another language, copy the English file to a locale-specific directory,
for example `values-fr/strings.xml`, translate only the text values, and add the
locale to `locales_config.xml`.

## Crowdin, POEditor or Weblate

Use the Android XML format directly. Keep `values/strings.xml` as the source and
export each language to its matching `values-<android-code>/strings.xml` path.
Do not convert through gettext unless the translation platform requires it.

Preserve placeholders such as `%1$s`, `%1$d` and `%2$s` exactly. Android plural
resources must remain `<plurals>` resources with the appropriate quantities for
the target language.

Suggested Weblate file mask:

```text
app/src/main/res/values-*/strings.xml
```

Source language file:

```text
app/src/main/res/values/strings.xml
```

## Validation

Run before committing:

```bash
python3 tools/check_translations.py
python3 tools/audit_hardcoded_strings.py
./gradlew --no-daemon lintDebug assembleDebug
```

GitHub Actions runs the same checks and uploads the complete Lint reports.
