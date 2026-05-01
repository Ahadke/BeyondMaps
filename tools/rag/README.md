# BeyondMaps RAG Phase 1

This folder contains the Phase-1 data preparation script for a split artifact workflow:

- `florence_pack_clean.json` (content-only pack)
- `florence_embeddings_<model>.json` (embedding-only pack)

## 1) Build artifacts on laptop

Reuse existing vectors:

```bash
python tools/rag/prepare_phase1_artifacts.py \
  --input florence_pack.json \
  --output-dir artifacts/rag
```

Regenerate vectors with `all-MiniLM-L6-v2`:

```bash
pip install json-repair
pip install sentence-transformers
python tools/rag/prepare_phase1_artifacts.py \
  --input florence_pack.json \
  --output-dir artifacts/rag \
  --regenerate-embeddings \
  --model all-MiniLM-L6-v2
```

## 2) Push artifacts to Android app external files dir

```bash
adb push artifacts/rag/florence_pack_clean.json /sdcard/Android/data/com.beyondmaps/files/
adb push artifacts/rag/florence_embeddings_all-MiniLM-L6-v2.json /sdcard/Android/data/com.beyondmaps/files/
```

If the original JSON has malformed text and the script cannot recover the original `embeddings` block, use `--regenerate-embeddings`.

## 3) App import behavior

At startup, the app imports these files into Room tables:

- `destinations`
- `knowledge_chunks`
- `embeddings`
- `phrases`
- `pack_import_state`

The import is idempotent: if file hashes match a previous import, it is skipped.
