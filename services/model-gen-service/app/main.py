"""FastAPI application for model-gen-service.

HTTP surface is GET /health only. The SQS consumer is ``python -m app.consumer``.
"""

from fastapi import FastAPI

app = FastAPI(title="model-gen-service", version="0.4.0")


@app.get("/health")
def health() -> dict[str, str]:
    """Liveness probe. Returns ``{"status": "ok"}``."""
    return {"status": "ok"}
