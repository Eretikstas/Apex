#!/usr/bin/env python3
"""
Remove from strings_ru_overlay.xml the Russian strings that AIMI now translates by itself.

The overlay files (app/ and wear/src/main/res/values-ru-rRU/strings_ru_overlay.xml) hold Russian
strings that the AIMI translation files do not have. They are separate files, so an update of AIMI
never conflicts with them. After an update, AIMI may translate some of these strings itself; a
string defined twice in the same module stops the build, and in the other modules the overlay would
hide the newer AIMI translation. Run this after every update of AIMI:

    python3 tools/ru_overlay_prune.py          # remove them
    python3 tools/ru_overlay_prune.py --check  # only list them, exit 1 if there are any

It also removes strings whose English text no longer has the same placeholders. Strings whose
English text changed otherwise are only listed ("English changed, check the translation"): the
translation stays, as it is mostly still right, but it should be looked at. The English text each
translation was made from is in tools/ru_overlay_source.json.
"""
import glob
import json
import os
import re
import sys

SOURCE = "tools/ru_overlay_source.json"
LITERAL_PERCENT = "tools/ru_overlay_literal_percent.txt"


def literal_percent_keys():
    if not os.path.exists(LITERAL_PERCENT):
        return set()
    return {l.strip() for l in open(LITERAL_PERCENT, encoding="utf-8") if l.strip() and not l.startswith("#")}

R = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)
PH = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXcbeEgGoh%]')


def placeholders(s):
    return sorted(p for p in PH.findall(s) if p != "%%")


def main():
    check = "--check" in sys.argv
    found = 0
    source = json.load(open(SOURCE, encoding="utf-8")) if os.path.exists(SOURCE) else {}
    changed = []
    literal = literal_percent_keys()
    for overlay in glob.glob("*/src/main/res/values-ru-rRU/strings_ru_overlay.xml"):
        wear = overlay.startswith("wear/")
        others, english = set(), {}
        for f in glob.glob("**/src/main/res/values*/strings*.xml", recursive=True):
            if "/build/" in f or f == overlay or f.startswith("wear/") != wear:
                continue
            names = {m.group(1): m.group(2) for m in R.finditer(open(f, encoding="utf-8").read())}
            if "/values-ru" in f:
                others |= set(names)
            elif "/values/" in f:
                for k, v in names.items():
                    english.setdefault(k, v)
        text = open(overlay, encoding="utf-8").read()
        drop = []
        for m in R.finditer(text):
            k, v = m.group(1), m.group(2)
            if k in others:
                drop.append((k, "AIMI translates it now"))
            elif k not in english:
                drop.append((k, "no longer in the English strings"))
            elif k in literal and english[k].count("%") != v.count("%"):
                drop.append((k, "percent signs changed in English"))
            elif k not in literal and (placeholders(english[k]) != placeholders(v) or english[k].count("%%") != v.count("%%")):
                drop.append((k, "placeholders changed in English"))
            elif k in source and source[k] != english[k]:
                changed.append(k)
        for k, why in drop:
            print(f"{overlay}: {k}: {why}")
        found += len(drop)
        if drop and not check:
            names = {k for k, _ in drop}
            text = re.sub(r'\n\s*<string\s+name="([^"]+)"[^>]*>.*?</string>',
                          lambda m: "" if m.group(1) in names else m.group(0), text, flags=re.S)
            open(overlay, "w", encoding="utf-8").write(text)
        for k in changed:
            print(f"{overlay}: {k}: English changed, check the translation")
            print(f"    was: {source[k]}")
            print(f"    now: {english[k]}")
        if drop and not check:
            for k, _ in drop:
                source.pop(k, None)
            json.dump(dict(sorted(source.items())), open(SOURCE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        changed = []
    print(f"{found} string(s) {'to remove' if check else 'removed'}")
    return 1 if check and found else 0


if __name__ == "__main__":
    sys.exit(main())
