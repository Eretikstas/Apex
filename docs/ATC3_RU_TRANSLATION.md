# Russian translation in this branch

## Where it is

- AIMI's own translation files (`*/src/main/res/values-ru-rRU/strings*.xml`) are kept **exactly as
  in AIMI**. They are never edited here, so updates of AIMI do not conflict with this branch.
- The Russian strings AIMI does not have yet are in two files of our own, which AIMI does not know:
  - `app/src/main/res/values-ru-rRU/strings_ru_overlay.xml` (the phone app). Strings of the app
    module override the library modules, so one file covers the whole app.
  - `wear/src/main/res/values-ru-rRU/strings_ru_overlay.xml` (the watch app).
- The ATC3 driver has its translation in its own module: `pump/atc3/src/main/res/values-ru-rRU/`.

## Where the strings come from

In this order, and only where the English text is exactly the same as ours:

1. the Russian translation of the AAPS build already in use (the custom APK),
2. AAPS `dev` and AAPS 3.x (community translations from Crowdin),
3. strings translated for this branch.

Every string is checked: the same placeholders (`%1$s`, `%d`, ...), the same `%%` and `\n` as the
English text, valid XML, and no unescaped apostrophe.

## After an update of AIMI

AIMI may now translate some strings that are in the overlay. A string defined twice in the app
module stops the build, and elsewhere the overlay would hide the newer AIMI text. So run:

```
python3 tools/ru_overlay_prune.py --check   # list them
python3 tools/ru_overlay_prune.py           # remove them
```

and commit the result. CI runs the same step before every build, so a build never fails on this,
but the cleaned file should still be committed.
