#!/usr/bin/env python3
"""
Add Russian strings to strings_ru_overlay.xml, checked against the English text.

    python3 tools/ru_overlay_add.py translations.json

translations.json is {"key": "Russian text", ...}. A string goes to wear/ when its English is in
the wear module, else to app/. It is refused when the placeholders (%1$s, %d ...), %% or \\n differ
from the English text, when it has an unescaped apostrophe or is not valid XML, or when AIMI
already translates the key. The English text is recorded in tools/ru_overlay_source.json, so that
tools/ru_overlay_prune.py can tell when AIMI changes the English text later.
"""
import glob, json, os, re, sys
from xml.dom import minidom

R = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PH = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXcbeEgGoh%]')
SOURCE = "tools/ru_overlay_source.json"
CYRILLIC = re.compile(r"[\u0400-\u04FF]")


def placeholders(s):
    return sorted(p for p in PH.findall(s) if p != "%%")


def load(f):
    return {m.group(1): (m.group(3), m.group(2)) for m in R.finditer(open(f, encoding="utf-8").read())}


def check(en, ru, force_percent=False):
    if not ru.strip():
        return "empty"
    if not force_percent and placeholders(en) != placeholders(ru):
        return "placeholders differ"
    if en.count("%") != ru.count("%") if force_percent else en.count("%%") != ru.count("%%"):
        return "percent signs differ"
    if en.count("\\n") != ru.count("\\n"):
        return "line breaks differ"
    if re.search(r"(?<!\\)'", ru) and not (ru.startswith('"') and ru.endswith('"')):
        return "unescaped apostrophe"
    try:
        minidom.parseString(("<r>%s</r>" % ru).encode("utf-8"))
    except Exception as e:
        return "not valid XML: %s" % e
    return None


def main():
    new = json.load(open(sys.argv[1], encoding="utf-8"))
    # literal "%" before a letter in English ("50% or") looks like a format; list such keys here
    literal_file = "tools/ru_overlay_literal_percent.txt"
    literal_percent = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else set()
    if os.path.exists(literal_file):
        literal_percent |= {l.strip() for l in open(literal_file, encoding="utf-8") if l.strip() and not l.startswith("#")}
    source = json.load(open(SOURCE, encoding="utf-8")) if os.path.exists(SOURCE) else {}
    english, module_of, translated = {}, {}, set()
    for f in glob.glob("**/src/main/res/values*/strings*.xml", recursive=True):
        if "/build/" in f or "strings_ru_overlay" in f:
            continue
        wear = f.startswith("wear/")
        for k, (v, a) in load(f).items():
            if "/values/" in f and 'translatable="false"' not in a:
                if wear:
                    english.setdefault("wear:" + k, v)
                else:
                    english.setdefault(k, v)
                module_of.setdefault(k, "app") if not wear else module_of.setdefault("wear:" + k, "wear")
            elif "/values-ru" in f:
                # An AIMI "translation" still in English (no Cyrillic) in a module other than app/
                # can be overridden by the overlay: the app module wins when resources are merged.
                if f.startswith("app/") or f.startswith("wear/") or CYRILLIC.search(v) or not re.search(r"[A-Za-z]", v):
                    translated.add(k)
    added, refused = {"app": {}, "wear": {}}, 0
    for k, ru in new.items():
        if k.startswith("wear:"):
            pass
        elif k not in english and "wear:" + k in english:
            k = "wear:" + k
        if k not in english:
            print("refused", k, ": no such English string"); refused += 1; continue
        if k in translated:
            print("refused", k, ": AIMI translates it already"); refused += 1; continue
        why = check(english[k], ru, k in literal_percent)
        if why:
            print("refused", k, ":", why); refused += 1; continue
        added[module_of[k]][k.replace("wear:", "")] = ru
        if module_of[k] == "app":
            source[k] = english[k]
    for part, items in added.items():
        if not items:
            continue
        path = part + "/src/main/res/values-ru-rRU/strings_ru_overlay.xml"
        text = open(path, encoding="utf-8").read()
        have = load(text) if False else {m.group(1): m.group(3) for m in R.finditer(text)}
        have.update(items)
        head = text[: text.index("<resources>") + len("<resources>")] + "\n"
        body = "".join(f'    <string name="{k}">{have[k]}</string>\n' for k in sorted(have))
        out = head + body + "</resources>\n"
        minidom.parseString(out.encode("utf-8"))
        open(path, "w", encoding="utf-8").write(out)
    json.dump(dict(sorted(source.items())), open(SOURCE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("added:", sum(len(v) for v in added.values()), "refused:", refused)


if __name__ == "__main__":
    main()
