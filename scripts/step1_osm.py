# step1_osm.py
# Reads florence.osm.pbf and extracts all places
# Output: dataset/processed/osm_places.json

import json
import os
from collections import Counter

import osmium

print("Starting OSM processing...")
print("Reading florence.osm.pbf...")


class FlorenceHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.places = []

    def add_place(self, tags, lat, lon, category):
        name = tags.get("name:en") or tags.get("name", "")
        if not name:
            return

        self.places.append(
            {
                "category": category,
                "name": name,
                "name_it": tags.get("name", ""),
                "lat": lat,
                "lng": lon,
                "address": (
                    tags.get("addr:street", "")
                    + " "
                    + tags.get("addr:housenumber", "")
                ).strip(),
                "hours": tags.get("opening_hours", ""),
                "phone": tags.get("phone", ""),
                "website": tags.get("website", ""),
                "cuisine": tags.get("cuisine", ""),
                "stars": tags.get("stars", ""),
                "source": "openstreetmap",
            }
        )

    def node(self, n):
        tags = {t.k: t.v for t in n.tags}
        amenity = tags.get("amenity", "")
        tourism = tags.get("tourism", "")

        if amenity in ["restaurant", "cafe", "bar", "fast_food", "bakery", "ice_cream"]:
            self.add_place(tags, n.location.lat, n.location.lon, "restaurant")
        elif tourism in ["hotel", "guest_house", "hostel", "motel"]:
            self.add_place(tags, n.location.lat, n.location.lon, "hotel")
        elif (
            tourism in ["museum", "attraction", "gallery", "viewpoint"]
            or amenity == "place_of_worship"
        ):
            self.add_place(tags, n.location.lat, n.location.lon, "attraction")
        elif tags.get("highway") == "bus_stop" or amenity == "bus_station":
            self.add_place(tags, n.location.lat, n.location.lon, "transit")


# Run it
handler = FlorenceHandler()
handler.apply_file("dataset/raw/florence.osm.pbf")

# Make sure output folder exists
os.makedirs("dataset/processed", exist_ok=True)

# Save result
with open("dataset/processed/osm_places.json", "w", encoding="utf-8") as f:
    json.dump(handler.places, f, ensure_ascii=False, indent=2)

print("\nDone!")
print(f"Total places found: {len(handler.places)}")

# Show breakdown
cats = Counter(p["category"] for p in handler.places)
for cat, count in cats.most_common():
    print(f"  {cat}: {count}")

print("\nSaved to: dataset/processed/osm_places.json")
