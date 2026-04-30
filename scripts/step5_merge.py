# step5_merge.py
# Merges all 4 processed files into one final bundle
# Output: dataset/output/florence_bundle.json

import json
import os
from collections import Counter

print("Merging all datasets...")

# Load all 4 processed files
print("  Loading osm_places.json...")
with open("dataset/processed/osm_places.json", encoding="utf-8") as f:
    osm = json.load(f)

print("  Loading wikivoyage_data.json...")
with open("dataset/processed/wikivoyage_data.json", encoding="utf-8") as f:
    wiki = json.load(f)

print("  Loading official_data.json...")
with open("dataset/processed/official_data.json", encoding="utf-8") as f:
    official = json.load(f)

print("  Loading airbnb_data.json...")
with open("dataset/processed/airbnb_data.json", encoding="utf-8") as f:
    airbnb = json.load(f)

print("  Loading phrasebook.json...")
with open("dataset/phrasebook.json", encoding="utf-8") as f:
    phrasebook_raw = json.load(f)

# Flatten all categories into one list
phrasebook_data = []
for category, phrases in phrasebook_raw["categories"].items():
    for phrase in phrases:
        phrase["category"] = category
        phrasebook_data.append(phrase)

print(f"  Phrasebook phrases: {len(phrasebook_data)}")

# Combine all places into one list
all_places = (
    osm +
    official["hotels"] +
    official["monuments"] +
    official["events"] +
    wiki["listings"] +
    airbnb
)

print(f"\n  Total places combined: {len(all_places)}")

# Build RAG chunks
# Each chunk = one piece of text EmbeddingGemma will embed
print("  Building RAG chunks...")
rag_chunks = []

# 1. Wikivoyage text sections
for chunk in wiki["text_chunks"]:
    rag_chunks.append(chunk)

# 2. Convert every place into a text chunk
for place in all_places:
    name = place.get("name", "")
    if not name or str(name) == "nan":
        continue

    # Build natural language description
    text = f"{place.get('category','place').upper()}: {name}. "

    if place.get("address"):
        text += f"Address: {str(place['address']).strip()}. "
    if place.get("neighborhood"):
        text += f"Neighborhood: {place['neighborhood']}. "
    if place.get("hours"):
        text += f"Hours: {place['hours']}. "
    if place.get("cuisine"):
        text += f"Cuisine: {place['cuisine']}. "
    if place.get("stars"):
        text += f"Stars: {place['stars']}. "
    if place.get("rating") and str(place.get("rating")) != "nan":
        text += f"Rating: {place['rating']}/5. "
    if place.get("description") and str(place.get("description")) != "nan":
        text += str(place["description"])[:300] + " "
    if place.get("content"):
        text += str(place["content"])[:300] + " "

    # Add review snippets
    reviews = place.get("recent_reviews") or []
    if isinstance(reviews, list):
        for r in reviews[:2]:
            if isinstance(r, dict):
                comment = r.get("comment", "")
                if comment and comment != "nan":
                    text += f'Guest said: "{comment[:150]}" '

    rag_chunks.append({
        "id":       f"place_{str(name)[:40].replace(' ','_')}",
        "title":    str(name),
        "text":     text.strip(),
        "lat":      place.get("lat") or place.get("latitude"),
        "lng":      place.get("lng") or place.get("longitude"),
        "category": place.get("category", ""),
        "source":   place.get("source", "")
    })

print(f"  Total RAG chunks: {len(rag_chunks)}")

# Build final bundle
bundle = {
    "city":       "Florence",
    "country":    "Italy",
    "language":   "Italian",
    "places":     all_places,
    "rag_chunks": rag_chunks,
    "phrasebook": phrasebook_data
}

# Save to output folder
os.makedirs("dataset/output", exist_ok=True)
with open("dataset/output/florence_bundle.json", "w", encoding="utf-8") as f:
    json.dump(bundle, f, ensure_ascii=False, indent=2)

# Final summary
print(f"\n{'='*40}")
print(f"FLORENCE BUNDLE COMPLETE")
print(f"{'='*40}")
print(f"Total places:    {len(all_places)}")
print(f"RAG chunks:      {len(rag_chunks)}")
print(f"Phrasebook:      {len(bundle['phrasebook'])} phrases")
print(f"\nBreakdown by source:")
sources = Counter(p.get("source","unknown") for p in all_places)
for source, count in sources.most_common():
    print(f"  {source:<25} {count}")

print(f"\nSaved to: dataset/output/florence_bundle.json")