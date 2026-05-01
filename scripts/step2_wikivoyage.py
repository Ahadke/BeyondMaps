# step2_wikivoyage.py
import json, os
import xml.etree.ElementTree as ET
import mwparserfromhell
from tqdm import tqdm

print("Starting Wikivoyage processing...")
print("This takes 2-3 minutes...")

TARGET_ARTICLES = [
    "Florence",
    "Florence/Oltrarno",
    "Florence/Santa Croce",
    "Florence/San Lorenzo",
    "Florence/Santa Maria Novella",
    "Italian phrasebook",
    "Italy",
    "Tuscany"
]

LISTING_TYPES = ["eat", "sleep", "see", "do", "drink", "buy", "listing"]

output = {
    "text_chunks": [],
    "listings":    [],
    "phrasebook":  []
}

found_articles = []

# Open as plain text XML
with open("dataset/raw/enwikivoyage-latest-pages-articles.xml.bz2", "r", encoding="utf-8") as f:
    context = ET.iterparse(f, events=("end",))

    for event, elem in tqdm(context, desc="Scanning articles"):
        if not elem.tag.endswith("page"):
            continue

        title_el = elem.find(".//{*}title")
        text_el  = elem.find(".//{*}text")

        if title_el is None or text_el is None:
            elem.clear()
            continue

        title = title_el.text or ""
        text  = text_el.text or ""

        title_lower = title.lower()
        is_target = (
            title in TARGET_ARTICLES
            or "florence" in title_lower
            or "firenze" in title_lower
        )
        if not is_target:
            elem.clear()
            continue

        print(f"\n  Found article: {title}")
        found_articles.append(title)
        wikicode = mwparserfromhell.parse(text)

        # Extract listings
        for template in wikicode.filter_templates():
            tname = template.name.strip().lower()
            if tname in LISTING_TYPES:
                listing = {"article": title, "type": tname}
                for param in template.params:
                    key = str(param.name).strip()
                    val = str(param.value).strip()
                    if val:
                        listing[key] = val
                output["listings"].append(listing)

        # Extract text sections
        plain_text = wikicode.strip_code()
        current_section = "overview"
        current_text = []

        for line in plain_text.split("\n"):
            line = line.strip()
            if not line:
                continue
            if line.startswith("==") and not line.startswith("==="):
                if current_text:
                    full_text = " ".join(current_text)
                    if len(full_text) > 100:
                        output["text_chunks"].append({
                            "id":     f"{title}_{current_section}".replace("/","_").replace(" ","_"),
                            "title":  f"{title} — {current_section}",
                            "text":   full_text,
                            "source": "wikivoyage"
                        })
                current_section = line.strip("= ").lower()
                current_text = []
            else:
                current_text.append(line)

        if current_text:
            output["text_chunks"].append({
                "id":     f"{title}_{current_section}".replace("/","_").replace(" ","_"),
                "title":  f"{title} — {current_section}",
                "text":   " ".join(current_text),
                "source": "wikivoyage"
            })

        # Extract phrasebook
        if "phrasebook" in title.lower():
            for template in wikicode.filter_templates():
                try:
                    params = template.params
                    if len(params) >= 2:
                        english = str(params[0].value).strip()
                        italian = str(params[1].value).strip()
                        phonetic = str(params[2].value).strip() if len(params) > 2 else ""
                        if english and italian:
                            output["phrasebook"].append({
                                "english":  english,
                                "italian":  italian,
                                "phonetic": phonetic
                            })
                except:
                    pass

        elem.clear()

os.makedirs("dataset/processed", exist_ok=True)
with open("dataset/processed/wikivoyage_data.json", "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print(f"\n=== Done! ===")
print(f"Articles found:     {len(found_articles)}")
print(f"Text chunks:        {len(output['text_chunks'])}")
print(f"Listings:           {len(output['listings'])}")
print(f"Phrasebook entries: {len(output['phrasebook'])}")
print(f"\nSaved to: dataset/processed/wikivoyage_data.json")