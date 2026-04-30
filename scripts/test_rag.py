# test_rag.py
# Tests if RAG search is working correctly
# Simulates what the app does at runtime

import json
import numpy as np
from sentence_transformers import SentenceTransformer

print("Loading files...")

# Load all 3 files
with open("dataset/output/florence_bundle.json", encoding="utf-8") as f:
    bundle = json.load(f)

vectors = np.load("dataset/output/florence_vectors.npy")

with open("dataset/output/florence_chunk_index.json", encoding="utf-8") as f:
    chunk_index = json.load(f)

chunks = bundle["rag_chunks"]

# Load same model used for embedding
model = SentenceTransformer("all-MiniLM-L6-v2")

print("Ready! Type a question about Florence.")
print("Type 'quit' to exit\n")

def search(question, top_k=3):
    # Step 1 — convert question to vector
    question_vector = model.encode([question])[0]

    # Step 2 — cosine similarity with all vectors
    # Dot product of normalized vectors = cosine similarity
    norms = np.linalg.norm(vectors, axis=1)
    normalized = vectors / norms[:, np.newaxis]
    q_norm = question_vector / np.linalg.norm(question_vector)
    scores = normalized.dot(q_norm)

    # Step 3 — get top matches
    top_indices = scores.argsort()[-top_k:][::-1]

    # Step 4 — return matching chunks
    results = []
    for idx in top_indices:
        results.append({
            "score": round(float(scores[idx]), 3),
            "title": chunks[idx]["title"],
            "text":  chunks[idx]["text"][:300]
        })
    return results

# Interactive loop
while True:
    question = input("Your question: ").strip()
    if question.lower() == "quit":
        break
    if not question:
        continue

    print(f"\nSearching for: '{question}'")
    print("-" * 40)

    results = search(question)
    for i, r in enumerate(results, 1):
        print(f"\nResult {i} (score: {r['score']})")
        print(f"Title: {r['title']}")
        print(f"Text:  {r['text']}")
        print()

    print("-" * 40)
    