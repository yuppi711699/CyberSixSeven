# model-gen-service

Python service whose only job is generating `.stl` files, uploading them to a
private bucket, and telling `platform-api` the deterministic object key.

**v0.4 scope:** parametric accessory geometry, SQS consumer for `model-gen-jobs`,
private S3 upload, authenticated internal PATCH. No OpenTelemetry yet (v0.7).

---

## Python version — 3.13 (3.12 acceptable). Never 3.10.

This is a hard constraint, not a preference.

`build123d` pulls in `cadquery-ocp-novtk` transitively. That package is a
binary wheel wrapping OpenCascade, it requires **Python ≥ 3.11**, and it
publishes **no `cp310` wheel**.

## Setup

```bash
cd services/model-gen-service
python3.13 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Prove the CAD toolchain works

```bash
python scripts/test_generate.py && ls -la out/cube.stl
pytest -q
```

## Run

```bash
# health (port 8000 is this service's fixed slot)
uvicorn app.main:app --reload --port 8000

# consumer — separate process, never a FastAPI lifespan hook
python -m app.consumer
```

The consumer long-polls `MODEL_GEN_QUEUE_URL`, writes
`accessories/{submissionId}/reward.stl` to `cybersixseven-accessories`, then
`PATCH /internal/submissions/{id}/accessory` with `X-Internal-Api-Key`.
The SQS message is deleted only after that PATCH succeeds. Visibility timeout
on `model-gen-jobs` is 300s; after 5 receives SQS redrives to `model-gen-jobs-dlq`.

## Layout

```
app/main.py        FastAPI — GET /health
app/geometry.py    parametric accessory (pure)
app/io.py          S3 HeadObject/PutObject + internal PATCH
app/consumer.py    SQS poller (`python -m app.consumer`)
```

## Dependencies

Pinned exactly; do not float them.

| Package | Version |
|---|---|
| build123d | 0.11.1 |
| fastapi | 0.141.1 |
| uvicorn | 0.42.0 |
| boto3 | 1.43.93 |
| moto | 5.2.3 |
| pytest | 9.0.2 |
