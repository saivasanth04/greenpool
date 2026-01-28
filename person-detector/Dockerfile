# Dockerfile for FastAPI + YOLOv8 (CPU) - Optimized, multi-stage, headless OpenCV
# Fixes libxcb.so.1 + python-multipart + all debug issues
# Pre-downloads model, uses venv, ~150MB final image

# ==============================================================================
# Stage 1: Builder (full deps + model download)
# ==============================================================================
FROM python:3.11 AS builder

WORKDIR /app
COPY app.py .

# Build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Venv + ALL deps (python-multipart added)
RUN python -m venv /venv
ENV PATH="/venv/bin:$PATH"
RUN pip install --upgrade pip
RUN pip install \
    fastapi \
    transformers \
    uvicorn[standard] \
    python-multipart \          
    ultralytics \
    scikit-learn \
    pillow \
    pydantic

# Headless OpenCV (ultralytics pulls full → replace)
RUN pip uninstall -y opencv-python opencv-python-headless || true && \
    pip install opencv-python-headless

# Pre-download YOLOv8n.pt
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"

# ==============================================================================
# Stage 2: Runtime (slim + EXACT debug libs)
# ==============================================================================
FROM python:3.11-slim-bookworm

WORKDIR /app

# EXACT libs from your debug session (fixes libxcb.so.1)
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    libsm6 \
    libxext6 \
    libxrender1 \
    libxcb1 \
    libgomp1 \
    libice6 \
    libx11-6 \
    libx11-data \
    libxau6 \
    libxdmcp6 \
    x11-common \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy venv + files
COPY --from=builder /venv /venv
COPY --from=builder /app/app.py .
COPY --from=builder /app/yolov8n.pt .

# Activate venv
ENV PATH="/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/ || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
