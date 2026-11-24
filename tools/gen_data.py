#!/usr/bin/env python3
"""Generates app/src/main/assets/{recipes,ingredients}.json from the Python data files."""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from data_ingredients import ING, ALIASES, SUBS, GLASS
from data_recipes import RECIPES

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")


def ename(key):
    en = ING[key][0]
    first = en.split()[0]
    # keep brand / proper names capitalised
    proper = {"Cointreau", "Campari", "Aperol", "Lillet", "Bénédictine", "Galliano", "Fernet-Branca", "Amaro",
              "Cognac", "Calvados", "Champagne", "Prosecco",
              "Angostura", "Peychaud's", "Irish", "Islay", "Blended", "Cola", "Falernum", "Amaretto",
              "Limoncello", "Orgeat", "Grenadine", "Worcestershire", "Green", "Yellow"}
    if first in proper:
        return en
    return en[0].lower() + en[1:]


def nm(key, lang):
    return ING[key][1] if lang == "de" else ename(key)


def join(items, lang):
    items = list(items)
    if not items:
        return ""
    if len(items) == 1:
        return items[0]
    conj = " und " if lang == "de" else " and "
    return ", ".join(items[:-1]) + conj + items[-1]


GL = lambda g, lang: GLASS[g][1 if lang == "de" else 0]

T = {
    "chill": ("Chill the {glass} with ice water or in the freezer.", "Glas ({glass}) mit Eiswasser oder im Gefrierfach vorkühlen."),
    "rinse": ("Rinse the glass with {x} and discard the excess.", "Das Glas mit {x} ausschwenken, den Rest wegkippen."),
    "to_shaker": ("Add {x} to a shaker.", "{X} in einen Shaker geben."),
    "shake_ice": ("Fill with ice and shake hard for 10–15 seconds.", "Mit Eis füllen und 10–15 Sekunden kräftig shaken."),
    "dstrain_up": ("Double-strain into the chilled {glass}.", "Doppelt ins vorgekühlte Glas abseihen (Fein- und Barsieb)."),
    "strain_rocks": ("Strain into a {glass} over fresh ice.", "Auf frisches Eis ins Glas ({glass}) abseihen."),
    "dry": ("Shake without ice for 10 seconds to build foam.", "Ohne Eis 10 Sekunden shaken, um Schaum aufzubauen (Dry Shake)."),
    "dry2": ("Add ice and shake hard again.", "Eis dazugeben und nochmal kräftig shaken."),
    "to_mixing": ("Add {x} to a mixing glass filled with ice.", "{X} in ein mit Eis gefülltes Rührglas geben."),
    "stir": ("Stir for about 30 seconds until well chilled.", "Etwa 30 Sekunden rühren, bis alles gut gekühlt ist."),
    "strain_up": ("Strain into the chilled {glass}.", "Ins vorgekühlte Glas abseihen."),
    "strain_big": ("Strain into a {glass} over one large ice cube.", "Auf einen großen Eiswürfel ins Glas ({glass}) abseihen."),
    "fill_ice": ("Fill a {glass} with ice.", "Glas ({glass}) mit Eis füllen."),
    "pour": ("Pour in {x}.", "{X} hineingießen."),
    "top": ("Top up with {x} and stir gently.", "Mit {x} auffüllen und vorsichtig umrühren."),
    "float": ("Gently float {x} on top.", "{X} vorsichtig obenauf gießen (floaten)."),
    "pour_noice": ("Pour {x} into a {glass}.", "{X} ins Glas ({glass}) gießen."),
    "muddle": ("Gently muddle {x} in the {glass}.", "{X} im Glas sanft andrücken (muddeln)."),
    "add_crushed": ("Add {x} and fill the glass with crushed ice.", "{X} dazugeben und das Glas mit Crushed Ice füllen."),
    "churn": ("Stir from the bottom up until the glass frosts.", "Von unten nach oben rühren, bis das Glas beschlägt."),
    "crushed_top": ("Top with more crushed ice.", "Mit weiterem Crushed Ice abschließen."),
    "shake_short": ("Add a handful of crushed ice and shake briefly.", "Eine Handvoll Crushed Ice dazugeben und kurz shaken."),
    "pour_all": ("Pour everything into a {glass} and top with crushed ice.", "Alles ins Glas ({glass}) gießen und mit Crushed Ice auffüllen."),
    "crushed_glass": ("Strain into a {glass} filled with crushed ice.", "In ein mit Crushed Ice gefülltes Glas ({glass}) abseihen."),
    "blender_ice": ("Add {x} to a blender with a cup (about 150 g) of ice.", "{X} mit einer Tasse (ca. 150 g) Eis in einen Mixer geben."),
    "blender": ("Add {x} to a blender.", "{X} in einen Mixer geben."),
    "blend": ("Blend for 30–60 seconds until smooth.", "30–60 Sekunden mixen, bis alles glatt ist."),
    "pour_glass": ("Pour into a {glass}.", "Ins Glas ({glass}) gießen."),
    "layer1": ("Pour {x} into a {glass}.", "{X} ins Glas ({glass}) gießen."),
    "layerN": ("Slowly layer {x} over the back of a bar spoon.", "{X} langsam über einen Barlöffelrücken darüberschichten."),
    "warm": ("Warm the {glass} with hot water, then discard the water.", "Glas ({glass}) mit heißem Wasser vorwärmen, dann ausleeren."),
    "add_hot": ("Add {x} and stir well.", "{X} hineingeben und gut umrühren."),
    "pot": ("Put {x} in a pot.", "{X} in einen Topf geben."),
    "orange_slice": ("Slice the orange and add it.", "Die Orange in Scheiben schneiden und dazugeben."),
    "heat": ("Heat gently for 15–20 minutes – do not boil.", "15–20 Minuten sanft erhitzen – nicht kochen lassen."),
    "ladle": ("Ladle into glasses ({glass}).", "In Gläser schöpfen ({glass})."),
    "bowl": ("Combine {x} in a {glass}.", "{X} in einer {glass} vermengen."),
    "chill_hour": ("Stir and chill for at least 1 hour.", "Umrühren und mindestens 1 Stunde kalt stellen."),
    "big_ice": ("Just before serving, add a large block of ice{top}.", "Kurz vor dem Servieren einen großen Eisblock hineingeben{top}."),
    "roll": ("Add {x} to a shaker with ice and gently roll it back and forth between two tins (don't shake).",
             "{X} mit Eis in einen Shaker geben und vorsichtig zwischen zwei Behältern hin und her gießen (nicht shaken)."),
    "garnish": ("Garnish: {x}.", "Garnitur: {x}."),
}


def tr(k, lang, **kw):
    s = T[k][1 if lang == "de" else 0]
    for a, b in kw.items():
        s = s.replace("{" + a + "}", b).replace("{" + a.capitalize() + "}", b[:1].upper() + b[1:])
    return s


def steps_for(r, lang):
    g = GL(r["glass"], lang)
    ings = r["ings"]
    flag = lambda i: i[3] if len(i) > 3 else ""
    main = [i for i in ings if flag(i) == ""]
    tops = [i for i in ings if flag(i) == "t"]
    floats = [i for i in ings if flag(i) == "f"]
    muddles = [i for i in ings if flag(i) == "m"]
    rinses = [i for i in ings if flag(i) == "r"]
    L = lambda items: join([nm(i[0], lang) for i in items], lang)
    m = r["method"]
    s = []
    if r.get("extra") and m in ("muddle",) and r["id"] == "caipirinha":
        s.append(r["extra"][1 if lang == "de" else 0])
    if m in ("shake_up", "stir_up", "dry_shake") and r["glass"] not in ("rocks",) or (m == "stir_up"):
        s.append(tr("chill", lang, glass=g))
    if rinses:
        s.append(tr("rinse", lang, x=L(rinses)))
    if m == "shake_up":
        s += [tr("to_shaker", lang, x=L(main)), tr("shake_ice", lang), tr("dstrain_up", lang, glass=g)]
    elif m == "shake_rocks":
        s += [tr("to_shaker", lang, x=L(main)), tr("shake_ice", lang), tr("strain_rocks", lang, glass=g)]
    elif m == "dry_shake":
        s += [tr("to_shaker", lang, x=L(main)), tr("dry", lang), tr("dry2", lang),
              tr("strain_rocks", lang, glass=g) if r["glass"] == "rocks" else tr("dstrain_up", lang, glass=g)]
    elif m == "stir_up":
        s += [tr("to_mixing", lang, x=L(main)), tr("stir", lang), tr("strain_up", lang, glass=g)]
    elif m == "stir_rocks":
        s += [tr("to_mixing", lang, x=L(main)), tr("stir", lang), tr("strain_big", lang, glass=g)]
    elif m == "shake_top":
        s += [tr("to_shaker", lang, x=L(main)), tr("shake_ice", lang),
              tr("strain_rocks", lang, glass=g) if r["glass"] != "flute" else tr("strain_up", lang, glass=g)]
    elif m in ("build", "build_noice"):
        if m == "build":
            s.append(tr("fill_ice", lang, glass=g))
            s.append(tr("pour", lang, x=L(main)))
        else:
            s.append(tr("pour_noice", lang, x=L(main), glass=g))
    elif m == "muddle":
        rest = main
        s.append(tr("muddle", lang, x=L(muddles), glass=g))
        s.append(tr("add_crushed", lang, x=L(rest)))
        s.append(tr("churn", lang))
    elif m == "crushed":
        s += [tr("to_shaker", lang, x=L(main)), tr("shake_ice", lang), tr("crushed_glass", lang, glass=g)]
    elif m == "shake_crushed":
        s += [tr("to_shaker", lang, x=L(main)), tr("shake_short", lang), tr("pour_all", lang, glass=g)]
    elif m == "blend":
        s += [tr("blender_ice", lang, x=L(main)), tr("blend", lang), tr("pour_glass", lang, glass=g)]
    elif m == "blend_noice":
        s += [tr("blender", lang, x=L(main)), tr("blend", lang), tr("pour_glass", lang, glass=g)]
    elif m == "layer":
        s.append(tr("layer1", lang, x=nm(main[0][0], lang), glass=g))
        for i in main[1:]:
            s.append(tr("layerN", lang, x=nm(i[0], lang)))
    elif m == "hot":
        s += [tr("warm", lang, glass=g), tr("add_hot", lang, x=L(main))]
    elif m == "simmer":
        s.append(tr("pot", lang, x=L([i for i in main if i[0] != "orange"])))
        if any(i[0] == "orange" for i in main):
            s.append(tr("orange_slice", lang))
        s += [tr("heat", lang), tr("ladle", lang, glass=g)]
    elif m == "punch":
        s += [tr("bowl", lang, x=L(main), glass=g), tr("chill_hour", lang)]
        topx = (" und mit " + L(tops) + " auffüllen") if lang == "de" else (" and top up with " + L(tops))
        s.append(tr("big_ice", lang, top=topx if tops else ""))
        tops = []
    elif m == "roll":
        s += [tr("roll", lang, x=L(main)), tr("strain_rocks", lang, glass=g)]
    else:
        raise ValueError(m)
    if tops:
        s.append(tr("top", lang, x=L(tops)))
    if floats:
        s.append(tr("float", lang, x=L(floats)))
    if r.get("extra") and r["id"] != "caipirinha":
        s.append(r["extra"][1 if lang == "de" else 0])
    if r["garnish"]:
        s.append(tr("garnish", lang, x=r["garnish"][1 if lang == "de" else 0]))
    return s


def main():
    os.makedirs(OUT, exist_ok=True)
    ids = set()
    out = []
    for r in RECIPES:
        assert r["id"] not in ids, r["id"]
        ids.add(r["id"])
        for i in r["ings"]:
            assert i[0] in ING, (r["id"], i[0])
        assert r["glass"] in GLASS, r["glass"]
        out.append({
            "id": r["id"], "name": r["name"], "nameDe": r["name_de"], "type": r["type"], "styles": r["styles"],
            "glass": r["glass"], "method": r["method"], "color": r["color"], "flavors": r["flavors"],
            "season": r["season"], "servings": r["servings"], "difficulty": r["difficulty"],
            "ingredients": [{"key": i[0], "amount": i[1], "unit": i[2], "flag": (i[3] if len(i) > 3 else "")} for i in r["ings"]],
            "garnish": {"en": r["garnish"][0], "de": r["garnish"][1]} if r["garnish"] else None,
            "about": {"en": r["about"][0], "de": r["about"][1]} if r["about"] else None,
            "steps": {"en": steps_for(r, "en"), "de": steps_for(r, "de")},
        })
    with open(os.path.join(OUT, "recipes.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=0)
    ing = {
        "ingredients": [{"key": k, "en": v[0], "de": v[1], "category": v[2]} for k, v in ING.items()],
        "aliases": ALIASES,
        "substitutions": {k: [{"key": s[0], "en": s[1], "de": s[2]} for s in v] for k, v in SUBS.items() if v and k in ING},
        "glasses": {k: {"en": v[0], "de": v[1]} for k, v in GLASS.items()},
    }
    for k, v in SUBS.items():
        for s in v:
            assert s[0] in ING, (k, s[0])
    for a, k in ALIASES.items():
        assert k in ING, (a, k)
    with open(os.path.join(OUT, "ingredients.json"), "w", encoding="utf-8") as f:
        json.dump(ing, f, ensure_ascii=False, indent=0)
    print(len(out), "recipes,", len(ING), "ingredients")


if __name__ == "__main__":
    main()
