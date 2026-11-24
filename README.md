# 🍸 Barkeep

**Deine Hausbar in der Hosentasche – kostenlos & Open Source.**
*Your home bar in your pocket – free & open source.* ([English below](#english))

<p>
<img src="docs/screenshots/de/1_discover.png" width="200">
<img src="docs/screenshots/de/2_detail.png" width="200">
<img src="docs/screenshots/de/3_bar_bottles.png" width="200">
<img src="docs/screenshots/de/4_what_can_i_make.png" width="200">
</p>

## Funktionen

- **Meine Bar** – Flaschen per **Barcode-Scanner** (EAN/UPC, Produktdaten von Open Food Facts), per Suche oder per KI-Foto erfassen, mit Füllstand. Dazu frische Zutaten zum Abhaken.
- **Was kann ich mixen?** – sofort mixbar, eine/zwei Zutaten fehlen, mit Ersatz-Zutat möglich, plus „Lohnt sich zu kaufen“: welche Flasche die meisten neuen Drinks freischaltet.
- **Rezepte** – eigene zweisprachige Sammlung (DE/EN) aus Klassikern, Modern Classics, Tiki, Spritz, Mocktails, Bowlen, Punsch, Smoothies und Shots. Optional zuschaltbar:
  - **TheCocktailDB** komplett offline (~600 Rezepte mit Fotos)
  - **Community-Sammlung** (~7.000 Rezepte, nur für den privaten Gebrauch – siehe unten)
- **KI-Fotoerkennung** mit eigenem API-Schlüssel (Google Gemini, Claude oder OpenAI): Cocktail fotografieren → Name, Geschichte, Rezept; Zutaten fotografieren → ab in die Bar.
- **Einsteiger & Profis** – Schritt-für-Schritt-Modus, Technik-Tipps, ml/cl/oz-Umrechnung, Skalierung nach Portionen oder Zielmenge (Batching), Ersatz-Zutaten mit Geschmackshinweis.
- **Eigene Rezepte** – neu anlegen oder bestehende anpassen.
- **Favoriten in Ordnern**, **Einkaufsliste** (teilen, „gekauft → in meine Bar“), **Zufalls-Drink**, **Drink des Tages**, **Saisonales**, **ähnliche Drinks**, **Food-Pairing**.
- Deutsch & Englisch, Sprache in der App umschaltbar. Keine Werbung, kein Konto, keine Tracker.

## Installation

APK aus den [Releases](https://github.com/Schnubbirunkel/Barkeep/releases) laden und installieren (Android 8+).
Der Barcode-Scanner nutzt die Google-Play-Dienste (keine Kamera-Berechtigung nötig).

## Datenquellen & Lizenzen

| Quelle | Inhalt | Hinweis |
|---|---|---|
| Barkeep-Sammlung (`tools/`) | eigene Rezepte DE/EN | eigene Texte, CC0 |
| [TheCocktailDB](https://www.thecocktaildb.com) | Online-Suche, Offline-Download | Testschlüssel „1“; eigener Schlüssel für Unterstützer |
| [Open Food Facts](https://world.openfoodfacts.org) | Barcode-Produktdaten | ODbL |
| Hugging Face `erwanlc/cocktails_recipe_no_brand` | Community-Sammlung | **unklare Lizenz** – wird nicht mitgeliefert, sondern erst nach Aktivierung direkt aufs Gerät geladen; nur privater Gebrauch |

Schriften: [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans) & [Inter](https://rsms.me/inter/) (SIL Open Font License).

Rezeptdaten neu erzeugen: `python3 tools/gen_data.py` · Übersetzungen: `python3 tools/gen_strings.py`

## Bauen

```bash
gradle :app:assembleRelease          # APK
gradle :app:recordRoborazziDebug     # Screenshots nach docs/screenshots
```
GitHub Actions baut bei jedem Push; ein Tag `v*` erzeugt ein Release.

---

## English

<p>
<img src="docs/screenshots/en/1_discover.png" width="200">
<img src="docs/screenshots/en/2_detail.png" width="200">
<img src="docs/screenshots/en/4_what_can_i_make.png" width="200">
<img src="docs/screenshots/en/5_search.png" width="200">
</p>

Barkeep is a free cocktail app: manage your home bar (barcode scanner via Open Food Facts, fill levels, fresh ingredients), see what you can make right now, browse a bilingual built-in recipe collection plus optional TheCocktailDB (offline) and a community collection (private use), identify drinks or ingredients from a photo using your own Gemini/Claude/OpenAI key, scale and batch recipes, get substitutes, food pairings, folders, a shopping list and a step-by-step mode. German & English UI.
