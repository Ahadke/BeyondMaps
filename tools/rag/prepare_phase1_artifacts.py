#!/usr/bin/env python3
"""
Phase-1 artifact builder for BeyondMaps RAG.

Outputs:
  1) florence_pack_clean.json
  2) florence_embeddings_<model>.json

Usage examples:
  python tools/rag/prepare_phase1_artifacts.py \
    --input florence_pack.json \
    --output-dir artifacts/rag \
    --regenerate-embeddings \
    --model all-MiniLM-L6-v2
"""

from __future__ import annotations

import argparse
import json
import re
import hashlib
from pathlib import Path
from typing import Any, Dict, List


CONTROL_CHARS_RE = re.compile(r"[\x00-\x08\x0B\x0C\x0E-\x1F]")


def decode_bytes(raw: bytes) -> str:
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        # Fallback for malformed source exports.
        return raw.decode("latin-1")


def sanitize_text(raw_text: str) -> str:
    # Preserve \t, \n, \r and remove remaining control chars.
    return CONTROL_CHARS_RE.sub(" ", raw_text)


def sanitize_obj(value: Any) -> Any:
    if isinstance(value, str):
        return sanitize_text(value)
    if isinstance(value, list):
        return [sanitize_obj(v) for v in value]
    if isinstance(value, dict):
        return {k: sanitize_obj(v) for k, v in value.items()}
    return value


def parse_json_file(input_path: Path) -> Dict[str, Any]:
    raw = input_path.read_bytes()
    decoded = decode_bytes(raw)
    sanitized = sanitize_text(decoded)
    try:
        data = json.loads(sanitized)
    except json.JSONDecodeError:
        # Fallback for heavily malformed exports (unescaped quotes, stray bytes, etc.).
        try:
            from json_repair import repair_json
        except ImportError as exc:
            raise RuntimeError(
                "JSON parsing failed. Install json-repair for tolerant parsing: pip install json-repair"
            ) from exc
        repaired = repair_json(sanitized, return_objects=True)
        if not isinstance(repaired, dict):
            raise ValueError("json-repair could not recover a JSON object root.")
        data = repaired
    return sanitize_obj(data)


def parse_vector(raw_vector: Any) -> List[float]:
    if isinstance(raw_vector, list):
        return [float(v) for v in raw_vector]
    if isinstance(raw_vector, str):
        s = raw_vector.strip().removeprefix("[").removesuffix("]")
        if not s:
            return []
        return [float(part.strip()) for part in s.split(",")]
    raise TypeError(f"Unsupported vector type: {type(raw_vector)}")


def compute_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def regenerate_embeddings(chunks: List[Dict[str, Any]], model_name: str) -> List[Dict[str, Any]]:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise RuntimeError(
            "sentence-transformers is required for --regenerate-embeddings. "
            "Install with: pip install sentence-transformers"
        ) from exc

    model = SentenceTransformer(model_name)
    texts = [chunk.get("text", "") for chunk in chunks]
    vectors = model.encode(
        texts,
        batch_size=64,
        show_progress_bar=True,
        normalize_embeddings=False,
    )
    out: List[Dict[str, Any]] = []
    for i, chunk in enumerate(chunks):
        out.append(
            {
                "id": f"emb_{i}",
                "chunkId": chunk["id"],
                "modelName": model_name,
                "vector": [float(v) for v in vectors[i].tolist()],
            }
        )
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Path to original pack JSON")
    parser.add_argument("--output-dir", required=True, help="Output directory")
    parser.add_argument(
        "--regenerate-embeddings",
        action="store_true",
        help="Regenerate embeddings from chunk text instead of reusing existing vectors",
    )
    parser.add_argument(
        "--model",
        default="all-MiniLM-L6-v2",
        help="Embedding model name for regenerated vectors",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    data = parse_json_file(input_path)
    destination = data["destination"]
    chunks = data["knowledgeChunks"]
    phrasebook = data.get("phrasebook", [])
    meta = data.get("meta", {})

    if args.regenerate_embeddings:
        embeddings = regenerate_embeddings(chunks, args.model)
        vector_size = len(embeddings[0]["vector"]) if embeddings else int(meta.get("vectorSize", 0))
        model_name = args.model
    else:
        if "embeddings" not in data:
            raise ValueError(
                "Input JSON did not contain a recoverable `embeddings` section. "
                "Use --regenerate-embeddings to build vectors from chunk text."
            )
        raw_embeddings = data["embeddings"]
        embeddings = []
        for i, item in enumerate(raw_embeddings):
            vec = parse_vector(item["vector"])
            embeddings.append(
                {
                    "id": item.get("id", f"emb_{i}"),
                    "chunkId": item["chunkId"],
                    "modelName": item.get("modelName", meta.get("modelName", "unknown")),
                    "vector": vec,
                }
            )
        vector_size = len(embeddings[0]["vector"]) if embeddings else int(meta.get("vectorSize", 0))
        model_name = embeddings[0]["modelName"] if embeddings else meta.get("modelName", "unknown")

    if len(chunks) != len(embeddings):
        raise ValueError(f"Mismatch: chunks={len(chunks)} embeddings={len(embeddings)}")

    chunk_ids = {chunk["id"] for chunk in chunks}
    for emb in embeddings:
        if emb["chunkId"] not in chunk_ids:
            raise ValueError(f"Missing chunkId reference: {emb['chunkId']}")
        if len(emb["vector"]) != vector_size:
            raise ValueError(f"Vector dimension mismatch for {emb['chunkId']}")

    clean_pack = {
        "destination": destination,
        "knowledgeChunks": chunks,
        "phrasebook": phrasebook,
        "meta": {
            **meta,
            "totalChunks": len(chunks),
            "totalEmbeddings": len(embeddings),
            "vectorSize": vector_size,
            "modelName": model_name,
        },
    }
    embedding_pack = {
        "destinationId": destination["id"],
        "modelName": model_name,
        "vectorSize": vector_size,
        "embeddings": embeddings,
    }

    clean_output = output_dir / "florence_pack_clean.json"
    embeddings_output = output_dir / f"florence_embeddings_{model_name}.json"
    clean_output.write_text(json.dumps(clean_pack, ensure_ascii=False), encoding="utf-8")
    embeddings_output.write_text(json.dumps(embedding_pack, ensure_ascii=False), encoding="utf-8")

    print("Wrote:", clean_output)
    print("Wrote:", embeddings_output)
    print("clean_sha256:", compute_sha256(clean_output))
    print("emb_sha256:", compute_sha256(embeddings_output))
    print("chunks:", len(chunks), "embeddings:", len(embeddings), "vectorSize:", vector_size)


if __name__ == "__main__":
    main()
