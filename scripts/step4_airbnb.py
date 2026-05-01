# step4_airbnb.py
# Reads Airbnb listings and reviews
# Output: dataset/processed/airbnb_data.json

import pandas as pd
import json
import os

print("Processing Airbnb data...")

# --- Load Listings ---
print("  Reading listings.csv...")
listings = pd.read_csv("dataset/raw/listings.csv", low_memory=False)
print(f"  Total listings: {len(listings)}")

# Keep useful columns only
useful_cols = [
    "id", "name", "description", "neighborhood_overview",
    "latitude", "longitude", "neighbourhood_cleansed",
    "room_type", "price", "review_scores_rating",
    "review_scores_cleanliness", "review_scores_location",
    "review_scores_value", "number_of_reviews"
]
# Only keep columns that actually exist in the file
useful_cols = [c for c in useful_cols if c in listings.columns]
listings = listings[useful_cols]

# Rename to cleaner names
listings = listings.rename(columns={
    "id":                        "airbnb_id",
    "neighbourhood_cleansed":    "neighborhood",
    "review_scores_rating":      "rating",
    "review_scores_cleanliness": "rating_cleanliness",
    "review_scores_location":    "rating_location",
    "review_scores_value":       "rating_value",
    "number_of_reviews":         "review_count"
})

listings["category"] = "accommodation"
listings["source"]   = "airbnb"

# --- Load Reviews ---
print("  Reading reviews.csv...")
reviews = pd.read_csv("dataset/raw/reviews.csv", low_memory=False)
print(f"  Total reviews: {len(reviews)}")

# Get 3 most recent reviews per listing
reviews_sorted = reviews.sort_values("date", ascending=False)
recent = reviews_sorted.groupby("listing_id").head(3)

# Group reviews by listing id
reviews_by_listing = {}
for _, row in recent.iterrows():
    lid = str(row["listing_id"])
    if lid not in reviews_by_listing:
        reviews_by_listing[lid] = []
    comment = str(row.get("comments", ""))[:300]
    if comment and comment != "nan":
        reviews_by_listing[lid].append({
            "date":    str(row.get("date", "")),
            "comment": comment
        })

# Attach reviews to listings
listings["recent_reviews"] = listings["airbnb_id"].astype(str).map(reviews_by_listing)

# Convert to list of dicts
airbnb_data = listings.to_dict(orient="records")

# Save
os.makedirs("dataset/processed", exist_ok=True)
with open("dataset/processed/airbnb_data.json", "w", encoding="utf-8") as f:
    json.dump(airbnb_data, f, ensure_ascii=False, indent=2)

print(f"\n=== Done! ===")
print(f"Listings saved:  {len(airbnb_data)}")
print(f"With reviews:    {sum(1 for x in airbnb_data if x.get('recent_reviews'))}")
print(f"\nSaved to: dataset/processed/airbnb_data.json")