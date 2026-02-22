# Use specific Debian version (bookworm) instead of latest (trixie)
FROM python:3.11-slim-bookworm as builder

WORKDIR /app

# Install build dependencies with correct package names for Debian bookworm
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir --root-user-action=ignore -r requirements.txt

# Final stage
FROM python:3.11-slim-bookworm

WORKDIR /app

# Install runtime dependencies with correct package names
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy installed packages from builder
COPY --from=builder /usr/local/lib/python3.11/site-packages/ /usr/local/lib/python3.11/site-packages/
COPY --from=builder /usr/local/bin/ /usr/local/bin/

# Copy application code
COPY app.py .

# Create a download script for models
RUN echo 'import sys\n\
try:\n\
    from ultralytics import YOLO\n\
    print("Downloading YOLOv8 model...")\n\
    YOLO("yolov8n.pt")\n\
    print("YOLO model downloaded successfully")\n\
except Exception as e:\n\
    print(f"Warning: YOLO model download failed: {e}", file=sys.stderr)\n\
    sys.exit(0)\n' > download_model.py

# Download model during build
RUN python download_model.py

# Create non-root user
RUN useradd -m -u 1000 fastapi && \
    chown -R fastapi:fastapi /app

USER fastapi

EXPOSE 8000

# Health check with longer timeout
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8000/ || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]