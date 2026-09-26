"""FastAPI application for model-gen-service.

HTTP surface is GET /health only. The SQS consumer is ``python -m app.consumer``.
"""

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from app.consumer import configure_tracing

app = FastAPI(title="model-gen-service", version="0.7.0")
if configure_tracing():
    FastAPIInstrumentor.instrument_app(app)


@app.get("/health")
def health() -> dict[str, str]:
    """Liveness probe. Returns ``{"status": "ok"}``."""
    return {"status": "ok"}
