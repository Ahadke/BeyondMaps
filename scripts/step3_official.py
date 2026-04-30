# step3_official.py
# Downloads Florence official city data
# Output: dataset/processed/official_data.json

import requests, json, zipfile, io, os
from xml.etree import ElementTree as ET

print("Downloading Florence official data...")

official = {"hotels": [], "monuments": [], "events": []}

# --- Official Hotels ---
print("  Getting official hotels...")
try:
    r = requests.get("https://datigis.comune.fi.it/json/strutture_ricettive_od.json", timeout=30)
    data = r.json()
    for feature in data.get("features", []):
        props  = feature.get("properties", {})
        coords = feature.get("geometry", {}).get("coordinates", [None, None])
        official["hotels"].append({
            "category": "hotel",
            "name":     props.get("DENOMINAZIONE", ""),
            "type":     props.get("TIPO", ""),
            "address":  props.get("INDIRIZZO", ""),
            "stars":    props.get("STELLE", ""),
            "phone":    props.get("TELEFONO", ""),
            "lat":      coords[1],
            "lng":      coords[0],
            "source":   "florence_official"
        })
    print(f"  Hotels found: {len(official['hotels'])}")
except Exception as e:
    print(f"  Hotels failed: {e}")

# --- Official Monuments ---
print("  Getting official monuments...")
try:
    r = requests.get("https://datigis.comune.fi.it/kml/monumenti.kmz", timeout=30)
    with zipfile.ZipFile(io.BytesIO(r.content)) as z:
        kml_name = [n for n in z.namelist() if n.endswith(".kml")][0]
        kml_text = z.read(kml_name).decode("utf-8")
    root = ET.fromstring(kml_text)
    ns = {"kml": "http://www.opengis.net/kml/2.2"}
    for pm in root.findall(".//kml:Placemark", ns):
        name_el  = pm.find("kml:name", ns)
        desc_el  = pm.find("kml:description", ns)
        coord_el = pm.find(".//kml:coordinates", ns)
        if coord_el is not None:
            parts = coord_el.text.strip().split(",")
            official["monuments"].append({
                "category":    "attraction",
                "name":        name_el.text if name_el is not None else "",
                "description": desc_el.text if desc_el is not None else "",
                "lat":         float(parts[1]),
                "lng":         float(parts[0]),
                "source":      "feel_florence"
            })
    print(f"  Monuments found: {len(official['monuments'])}")
except Exception as e:
    print(f"  Monuments failed: {e}")

# --- Events ---
print("  Getting events...")
try:
    r = requests.get("https://wwwext.comune.fi.it/opendata/files/eventi.json", timeout=30)
    data = r.json()

    # Handle both list and dict formats
    if isinstance(data, list):
        items = data
    else:
        items = data.get("features", [])

    for item in items:
        if isinstance(item, dict):
            props  = item.get("properties", item)
            coords = item.get("geometry", {}).get("coordinates", [None, None]) if isinstance(item.get("geometry"), dict) else [None, None]
            official["events"].append({
                "category":    "event",
                "name":        props.get("TITOLO", "") or props.get("title", ""),
                "description": props.get("DESCRIZIONE", "") or props.get("description", ""),
                "date_start":  props.get("DATA_INIZIO", ""),
                "date_end":    props.get("DATA_FINE", ""),
                "location":    props.get("LUOGO", ""),
                "lat":         coords[1] if coords and len(coords) > 1 else None,
                "lng":         coords[0] if coords and len(coords) > 0 else None,
                "source":      "feel_florence"
            })
    print(f"  Events found: {len(official['events'])}")
except Exception as e:
    print(f"  Events failed: {e}")

os.makedirs("dataset/processed", exist_ok=True)
with open("dataset/processed/official_data.json", "w", encoding="utf-8") as f:
    json.dump(official, f, ensure_ascii=False, indent=2)

print(f"\n=== Done! ===")
print(f"Hotels:    {len(official['hotels'])}")
print(f"Monuments: {len(official['monuments'])}")
print(f"Events:    {len(official['events'])}")
print(f"\nSaved to: dataset/processed/official_data.json")