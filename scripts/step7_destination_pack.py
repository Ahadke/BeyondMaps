# step7_destination_pack.py
# Converts our files into destination pack format
# for Android Room DB
# Output: dataset/output/florence_pack.json

import json
import numpy as np
from datetime import datetime

print("Building Florence destination pack...")

# Load our 3 files
print("  Loading florence_bundle.json...")
with open("dataset/output/florence_bundle.json", encoding="utf-8") as f:
    bundle = json.load(f)

print("  Loading florence_vectors.npy...")
vectors = np.load("dataset/output/florence_vectors.npy", allow_pickle=True)

print("  Loading florence_chunk_index.json...")
with open("dataset/output/florence_chunk_index.json", encoding="utf-8") as f:
    chunk_index = json.load(f)

chunks = bundle["rag_chunks"]

print(f"  Chunks: {len(chunks)}")
print(f"  Vectors: {vectors.shape}")

# ─── Build DestinationEntity ───
destination = {
    "id":           "florence_italy",
    "city":         "Florence",
    "country":      "Italy",
    "version":      "1.0.0",
    "downloadedAt": int(datetime.now().timestamp() * 1000)
}

# ─── Build KnowledgeChunkEntity list ───
print("\n  Building knowledge chunks...")
knowledge_chunks = []
for chunk in chunks:
    name = chunk.get("id", "")
    if not name:
        continue
    knowledge_chunks.append({
        "id":            chunk.get("id", ""),
        "destinationId": "florence_italy",
        "category":      chunk.get("category", "general"),
        "title":         chunk.get("title", ""),
        "text":          chunk.get("text", ""),
        "source":        chunk.get("source", "")
    })

print(f"  Knowledge chunks built: {len(knowledge_chunks)}")

# ─── Build EmbeddingEntity list ───
print("  Building embeddings...")
print("  Converting vectors to JSON strings (this takes a moment)...")
embeddings = []
for i, (chunk, vector) in enumerate(zip(chunks, vectors)):
    chunk_id = chunk.get("id", f"chunk_{i}")
    
    # Convert numpy array to JSON string
    # Round to 6 decimal places to keep file size reasonable
    vector_list = [round(float(v), 6) for v in vector]
    vector_str = json.dumps(vector_list)
    
    embeddings.append({
        "id":        f"emb_{i}",
        "chunkId":   chunk_id,
        "modelName": "all-MiniLM-L6-v2",
        "vector":    vector_str
    })

print(f"  Embeddings built: {len(embeddings)}")

# ─── Build final pack ───
pack = {
    "destination":       destination,
    "knowledgeChunks":   knowledge_chunks,
    "embeddings":        embeddings,
    "phrasebook":        bundle["phrasebook"],
    "meta": {
        "totalChunks":     len(knowledge_chunks),
        "totalEmbeddings": len(embeddings),
        "vectorSize":      int(vectors.shape[1]),
        "modelName":       "all-MiniLM-L6-v2",
        "createdAt":       datetime.now().isoformat()
    }
}

# Save
print("\n  Saving florence_pack.json...")
with open("dataset/output/florence_pack.json", "w", encoding="utf-8") as f:
    json.dump(pack, f, ensure_ascii=False)

print(f"\n{'='*40}")
print(f"DESTINATION PACK COMPLETE")
print(f"{'='*40}")
print(f"Destination:      {destination['city']}, {destination['country']}")
print(f"Knowledge chunks: {len(knowledge_chunks)}")
print(f"Embeddings:       {len(embeddings)}")
print(f"Vector size:      {vectors.shape[1]}")
print(f"Phrasebook:       {len(bundle['phrasebook'])} phrases")
print(f"\nSaved to: dataset/output/florence_pack.json")
print(f"\nGive florence_pack.json to your Android teammate.")
print(f"They load it → parse → insert into Room DB.")