# step6_embeddings.py
# Converts all RAG chunks into vectors using sentence-transformers
# Later swap this model for EmbeddingGemma from LiteRT team
# Output: dataset/output/florence_vectors.npy
#         dataset/output/florence_chunk_index.json

import json
import numpy as np
import os
from sentence_transformers import SentenceTransformer
from tqdm import tqdm

print("Loading florence_bundle.json...")
with open("dataset/output/florence_bundle.json", encoding="utf-8") as f:
    bundle = json.load(f)

chunks = bundle["rag_chunks"]
print(f"Total chunks to embed: {len(chunks)}")

# Load embedding model
# NOTE FOR LITERT TEAM: Replace this with EmbeddingGemma.tflite
print("\nLoading embedding model...")
print("(First run downloads ~90MB model - please wait...)")
model = SentenceTransformer("all-MiniLM-L6-v2")
print("Model loaded!")

# Extract just the text from each chunk
texts = [chunk["text"] for chunk in chunks]

# Embed in batches of 64 - shows progress bar
print(f"\nEmbedding {len(texts)} chunks...")
print("This takes 5-10 minutes on CPU...")

vectors = model.encode(
    texts,
    batch_size=64,
    show_progress_bar=True,
    convert_to_numpy=True
)

print(f"\nVectors shape: {vectors.shape}")
# Should print: (17626, 384)
# 17626 chunks, 384 numbers per vector

# Save vectors
os.makedirs("dataset/output", exist_ok=True)
np.save("dataset/output/florence_vectors.npy", vectors)
print("Saved: dataset/output/florence_vectors.npy")

# Save chunk index - maps vector[i] to chunk[i]
chunk_index = []
for i, chunk in enumerate(chunks):
    chunk_index.append({
        "index":    i,
        "id":       chunk.get("id", ""),
        "title":    chunk.get("title", ""),
        "category": chunk.get("category", ""),
        "lat":      chunk.get("lat"),
        "lng":      chunk.get("lng"),
        "source":   chunk.get("source", "")
    })

with open("dataset/output/florence_chunk_index.json", "w", encoding="utf-8") as f:
    json.dump(chunk_index, f, ensure_ascii=False, indent=2)

print("Saved: dataset/output/florence_chunk_index.json")

print(f"\n{'='*40}")
print(f"EMBEDDING COMPLETE")
print(f"{'='*40}")
print(f"Chunks embedded:  {len(vectors)}")
print(f"Vector size:      {vectors.shape[1]} numbers per chunk")
print(f"\nFiles for LiteRT team:")
print(f"  florence_bundle.json")
print(f"  florence_vectors.npy")
print(f"  florence_chunk_index.json")
