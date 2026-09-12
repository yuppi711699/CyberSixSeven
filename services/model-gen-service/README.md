# model-gen-service

Python service whose only job is generating `.stl` files.

**v0.1 scope:** a FastAPI app with `GET /health`, plus a standalone script that
proves the CAD toolchain works. No SQS consumer, no S3 upload, no
OpenTelemetry — those arrive in v0.4 and v0.7.

---

## Python version — 3.13 (3.12 acceptable). Never 3.10.

This is a hard constraint, not a preference.

`build123d` pulls in `cadquery-ocp-novtk` transitively. That package is a
binary wheel wrapping OpenCascade, it requires **Python ≥ 3.11**, and it
publishes **no `cp310` wheel** — native arm64 wheels exist for cp311–cp314
only. On Python 3.10 the install fails outright, *despite build123d's own
metadata claiming 3.10 support*. The failure happens at `pip install` time, so
you find out immediately, but the error points at OCP rather than at your
Python version and is easy to misread.

**3.13 is the target. 3.12 is fine. 3.11 works. 3.10 does not.**

No conda, no mamba, no Docker workaround is needed — plain `pip` in a venv
installs cleanly on Apple Silicon. The historical OpenCascade fragility is
resolved; if you find yourself reaching for conda, you are solving a problem
that no longer exists.

## Setup

```bash
cd services/model-gen-service
python3.13 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Prove the CAD toolchain works

Do this **before** writing any real modelling code. It builds a 10 mm cube and
exports it, exercising build123d → cadquery-ocp-novtk → OpenCascade end to end:

```bash
python scripts/test_generate.py && ls -la out/cube.stl
```

`out/` and `*.stl` are both gitignored — the artifact is disposable.

## Run the API

```bash
uvicorn app.main:app --reload --port 8000
curl -s localhost:8000/health    # expect {"status":"ok"}
```

Port **8000** is this service's fixed slot in the project-wide port map. Do not
vary it.

## Layout

```
app/main.py            FastAPI app — GET /health
scripts/test_generate.py   CAD toolchain smoke test -> out/cube.stl
requirements.txt       every dependency pinned with ==
```

## Dependencies

Pinned exactly; do not float them.

| Package | Version |
|---|---|
| build123d | 0.11.1 |
| fastapi | 0.141.1 |
| uvicorn | 0.42.0 |

`cadquery-ocp-novtk` 7.9.3.1.1 is pulled in transitively by build123d and is
not pinned here directly.
