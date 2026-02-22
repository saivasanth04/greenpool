from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
from ultralytics import YOLO
from transformers import pipeline
import numpy as np
import hnswlib
import logging
import threading
import time
from typing import List, Dict, Any
from PIL import Image
import io

app = FastAPI()
logger = logging.getLogger(__name__)

# ────────────────────────────────────────────────
# HNSW Index configuration — tuned for stability
# ────────────────────────────────────────────────

DIM = None                  # set from first vector
index = None
ride_id_to_idx = {}         # rideid → hnswlib internal id
idx_to_ride_id = {}         # reverse mapping
lock = threading.Lock()

MAX_ELEMENTS = 100000       # plenty of headroom
M = 96                      # ← go higher (64 → 96)
ef_construction = 800       # ← very strong index build
ef_search = 600           # aggressive search effort → almost eliminates knn_query failures

# ────────────────────────────────────────────────

class Rider(BaseModel):
    lat: float
    lon: float
    rideid: int
    trustscore: int
    route_features: List[float]

    def normalized_rideid(self) -> int:
        return self.rideid

    def normalized_trustscore(self) -> int:
        return self.trustscore

    def normalized_route_features(self) -> List[float]:
        return self.route_features

class ClusterRequest(BaseModel):
    riders: List[Rider]

class SentimentRequest(BaseModel):
    text: str

class SentimentResponse(BaseModel):
    score: float
    pPositive: float
    confidence: float
    label: str

# ────────────────────────────────────────────────

def initialize_or_resize_index(dim: int):
    global index, DIM
    with lock:
        if index is None or DIM != dim:
            logger.info(f"Initializing/resizing HNSW index. dim = {dim}")
            index = hnswlib.Index(space='l2', dim=dim)  # change to 'cosine' if you normalize vectors
            index.init_index(max_elements=MAX_ELEMENTS, ef_construction=ef_construction, M=M)
            index.set_ef(ef_search)
            DIM = dim

# ────────────────────────────────────────────────

model = YOLO("yolov8n.pt")
sentiment_pipeline = pipeline(
    "sentiment-analysis",
    model="distilbert-base-uncased-finetuned-sst-2-english"
)

@app.get("/")
async def root():
    return {"status": "ok"}

@app.post("/detect_person")
async def detect_person(file: UploadFile = File(...)):
    image_bytes = await file.read()
    image = Image.open(io.BytesIO(image_bytes))
    results = model(image)

    has_person = any(
        results[0].names[int(cls)] == "person" and float(conf) > 0.5
        for cls, conf in zip(results[0].boxes.cls, results[0].boxes.conf)
    )
    # Optional debug print (remove in production if not needed)
    # print(results[0].names[int(cls)] if results[0].boxes else "no detections")
    return {"has_person": has_person}

@app.post("/sentiment", response_model=SentimentResponse)
def sentiment(req: SentimentRequest) -> SentimentResponse:
    result = sentiment_pipeline(req.text, truncation=True)[0]
    label = str(result["label"]).upper()
    conf = float(result["score"])

    if "POSITIVE" in label:
        ppos = conf
    else:
        ppos = 1.0 - conf

    s = 2.0 * ppos - 1.0
    s = max(-1.0, min(1.0, s))

    return SentimentResponse(
        score=s,
        pPositive=ppos,
        confidence=conf,
        label=label
    )

@app.post("/cluster")
def cluster_riders(request: ClusterRequest):
    try:
        riders = request.riders
        if not riders:
            return []

        # The last rider is the new/current ride — prioritize it
        current_ride = riders[-1]
        current_rid = current_ride.normalized_rideid()
        logger.info(f"Clustering | {len(riders)} riders | current/new ride ID: {current_rid}")

        first_features = riders[0].normalized_route_features()
        dim = len(first_features)

        if DIM is None or DIM != dim:
            initialize_or_resize_index(dim)

        # Add missing rides
        added = 0
        with lock:
            for rider in riders:
                rid = rider.normalized_rideid()
                if rid in ride_id_to_idx:
                    continue
                vec = np.array(rider.normalized_route_features(), dtype=np.float32)
                if len(vec) != DIM:
                    logger.warning(f"Dim mismatch ride {rid}")
                    continue
                pos = index.get_current_count()
                index.add_items(vec.reshape(1, -1), ids=np.array([pos]))
                ride_id_to_idx[rid] = pos
                idx_to_ride_id[pos] = rid
                added += 1

        logger.info(f"Added {added} new vectors. Total indexed: {index.get_current_count()}")

        results = []

        with lock:
            # ── Query focused on current ride ─────────────────────────────
            vec = np.array(current_ride.normalized_route_features(), dtype=np.float32).reshape(1, -1)

            try:
                k = min(15, index.get_current_count())
                labels, _ = index.knn_query(vec, k=k)
                logger.info(f"knn_query OK for ride {current_rid} | k={k} | neighbors={len(labels[0])}")
            except RuntimeError as re:
                logger.warning(f"knn_query failed for ride {current_rid}: {re}")
                fake_cluster = current_rid % 100000
                results.append({
                    "ride_id": current_rid,
                    "lat": current_ride.lat,
                    "lon": current_ride.lon,
                    "trust_score": current_ride.normalized_trustscore(),
                    "cluster": int(fake_cluster)
                })
                # Minimal fallback for others
                for r in riders[:-1]:
                    rid = r.normalized_rideid()
                    results.append({
                        "ride_id": rid,
                        "lat": r.lat,
                        "lon": r.lon,
                        "trust_score": r.normalized_trustscore(),
                        "cluster": int(rid % 100000)
                    })
                return results

            # Neighbors (exclude self)
            neighbor_ids = [
                idx_to_ride_id.get(lab)
                for lab in labels[0]
                if lab in idx_to_ride_id and idx_to_ride_id.get(lab) != current_rid
            ]

            # Stable cluster ID = smallest ride ID in group
            group_ids = [current_rid] + [rid for rid in neighbor_ids if rid is not None]
            cluster_id = min(group_ids) if group_ids else current_rid

            logger.info(f"Assigned cluster {cluster_id} to current ride {current_rid} with {len(neighbor_ids)} neighbors")

            # Assign to current ride
            results.append({
                "ride_id": current_rid,
                "lat": current_ride.lat,
                "lon": current_ride.lon,
                "trust_score": current_ride.normalized_trustscore(),
                "cluster": int(cluster_id)
            })

            # Assign to other rides (only if they are in the same group)
            for rider in riders[:-1]:
                rid = rider.normalized_rideid()
                cluster = cluster_id if rid in neighbor_ids else (rid % 100000)
                results.append({
                    "ride_id": rid,
                    "lat": rider.lat,
                    "lon": rider.lon,
                    "trust_score": rider.normalized_trustscore(),
                    "cluster": int(cluster)
                })

        return results

    except Exception as e:
        logger.error("Clustering failed", exc_info=True)
        return [{"ride_id": 0, "lat": 0, "lon": 0, "trust_score": 0, "cluster": 0}]