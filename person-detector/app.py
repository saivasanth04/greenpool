from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
from ultralytics import YOLO
from transformers import pipeline
import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import MinMaxScaler
from PIL import Image
import io
import logging

app = FastAPI()
logger = logging.getLogger(__name__)

# Load model (auto-downloads yolov8n.pt if not present) - ENV fixes PyTorch 2.6
model = YOLO("yolov8n.pt")
sentiment_pipeline = pipeline(
    "sentiment-analysis",
    model="distilbert-base-uncased-finetuned-sst-2-english"
)
class SentimentRequest(BaseModel):
    text: str

class SentimentResponse(BaseModel):
    score: float  # [-1, 1]
class Rider(BaseModel):
    lat: float
    lon: float
    trust_score: int   # kept for compatibility, NOT used in clustering
    ride_id: int       # used to track rides

class ClusterRequest(BaseModel):
    riders: list[Rider]

@app.get("/")
async def root():
    return {"status": "ok"}

@app.post("/detect_person")
async def detect_person(file: UploadFile = File(...)):
    image_bytes = await file.read()
    image = Image.open(io.BytesIO(image_bytes))
    results = model(image)

    # Check for 'person' class (class ID 0 in COCO) with confidence > 0.5
    has_person = any(
        results[0].names[int(cls)] == "person" and float(conf) > 0.5
        for cls, conf in zip(results[0].boxes.cls, results[0].boxes.conf)
    )

    return {"has_person": has_person}
@app.post("/sentiment", response_model=SentimentResponse)
def sentiment(req: SentimentRequest) -> SentimentResponse:
    """
    Return a continuous sentiment score in [-1, 1].

    -1  = very negative
     0  = neutral
    +1  = very positive
    """

    logger.info(
        "SENTIMENT START: text_len=%d, text_preview=%r",
        len(req.text) if req.text else 0,
        (req.text[:80] + "...") if req.text and len(req.text) > 80 else req.text,
    )

    try:
        # Call Hugging Face pipeline
        result = sentiment_pipeline(req.text, truncation=True)[0]
        logger.info("Pipeline raw result: %s", result)

        label = str(result["label"]).upper()   # "POSITIVE" or "NEGATIVE"
        conf = float(result["score"])          # probability of predicted label in [0,1]
        logger.info("Parsed label=%s, conf=%.4f", label, conf)

        if "POSITIVE" in label:
            p_pos = conf
        else:
            p_pos = 1.0 - conf

        # Map P(positive) ∈ [0,1] to [-1,1]
        s = 2.0 * p_pos - 1.0

        # Clamp for safety
        if s < -1.0:
            logger.warning("Score below -1.0 (%.4f), clamping to -1.0", s)
            s = -1.0
        if s > 1.0:
            logger.warning("Score above 1.0 (%.4f), clamping to 1.0", s)
            s = 1.0

        logger.info("SENTIMENT SUCCESS: final_score=%.4f", s)
        return SentimentResponse(score=s)

    except Exception as e:
        logger.exception("SENTIMENT FAILED: error while processing text")
        # Surface a clearly invalid sentinel value so Java side will throw
        

@app.post("/cluster")
def cluster_riders(request: ClusterRequest):
    try:
        riders = request.riders
        if not riders:
            return {"error": "No riders provided"}

        # Single rider: trivial cluster 0
        if len(riders) == 1:
            r = riders[0]
            return [{
                "ride_id": r.ride_id,
                "lat": r.lat,
                "lon": r.lon,
                "trust_score": r.trust_score,
                "cluster": 0
            }]

        # Feature matrix: ONLY lat, lon (trust_score ignored for clustering)
        data = np.array([[r.lat, r.lon] for r in riders], dtype=float)

        scaler = MinMaxScaler()
        scaled_data = scaler.fit_transform(data)

        # Small-N behavior: avoid over-splitting
        n_riders = len(riders)
        if n_riders <= 3:
            n_clusters = 1
        elif n_riders <= 6:
            n_clusters = 2
        else:
            n_clusters = min(3, n_riders)

        kmeans = KMeans(
            n_clusters=n_clusters,
            n_init=10,
            random_state=42
        )
        labels = kmeans.fit_predict(scaled_data)

        clusters = []
        for i, rider in enumerate(riders):
            clusters.append({
                "ride_id": rider.ride_id,
                "lat": rider.lat,
                "lon": rider.lon,
                "trust_score": rider.trust_score,  # echoed back only
                "cluster": int(labels[i])
            })

        logger.info(
            "Clustered %d riders into %d clusters",
            len(riders),
            len(set(labels))
        )
        return clusters

    except Exception as e:
        logger.error("Clustering failed: %s", str(e))
        return {"error": str(e)}
