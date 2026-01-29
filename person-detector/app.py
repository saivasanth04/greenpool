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
    # Accept exactly what Java sends:
    lat: float
    lon: float
    rideid: int
    trustscore: int
    route_features: list[float]
    def normalized_rideid(self) -> int:
        return self.rideid if self.rideid is not None else int(self.ride_id)

    def normalized_trustscore(self) -> int:
        return self.trustscore if self.trustscore is not None else int(self.trust_score)

    def normalized_route_features(self) -> list[float]:
        if self.route_features is not None:
            return self.route_features
        return self.routefeatures or []

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
            return []

        # Single rider: trivial cluster 0 (unchanged behavior)
        if len(riders) == 1:
            r = riders[0]
            return [{
                "ride_id": r.normalized_rideid(),
                "lat": r.lat,
                "lon": r.lon,
                "trust_score": r.normalized_trustscore(),
                "cluster": 0
            }]

        # Use precomputed route_features only for clustering (unchanged behavior)
        feature_rows = [r.normalized_route_features() for r in riders]
        data = np.array(feature_rows, dtype=float)

        scaler = MinMaxScaler()
        scaled_data = scaler.fit_transform(data)

        # Small-N behavior (unchanged behavior)
        n_riders = len(riders)
        if n_riders <= 3:
            n_clusters = 1
        elif n_riders <= 6:
            n_clusters = 2
        else:
            n_clusters = min(3, n_riders)

        kmeans = KMeans(n_clusters=n_clusters, n_init=10, random_state=42)
        labels = kmeans.fit_predict(scaled_data)

        clusters = []
        for i, r in enumerate(riders):
            clusters.append({
                "ride_id": r.normalized_rideid(),
                "lat": r.lat,
                "lon": r.lon,
                "trust_score": r.normalized_trustscore(),
                "cluster": int(labels[i])
            })

        logger.info(
            "Clustered %d riders into %d clusters (route_features)",
            len(riders),
            len(set(labels))
        )
        return clusters

    except Exception as e:
        logger.error("Clustering failed: %s", str(e))
        return {"error": str(e)}
    



    