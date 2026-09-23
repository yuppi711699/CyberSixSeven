# CyberSixSeven

Run one block per terminal, from the repo root. First-time setup (`.env`, certs, `pnpm install`, Python venv) is under [Run locally](#run-locally).

```bash
# infrastructure — Postgres, Redis, LocalStack, Mosquitto
docker compose up -d
```

```bash
# platform-api — :8080
# -f the module pom: spring-boot:run on services/pom.xml tries to launch the aggregator and dies
set -a && source .env && set +a
./services/mvnw -f services/platform-api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
# device-command-service — :8081 HTTP, :9090 gRPC
# spring-boot:run sets cwd to the module, so cert paths must be absolute
set -a && source .env && set +a
ROOT="$(pwd)"
export AWS_ENDPOINT="${AWS_ENDPOINT:-${AWS_ENDPOINT_URL:-http://localhost:4566}}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_REGION="${AWS_REGION:-us-west-2}"
export DEVICE_COMMANDS_QUEUE_URL="${DEVICE_COMMANDS_QUEUE_URL:-http://localhost:4566/000000000000/device-commands}"
export MQTT_BROKER_URL="${MQTT_BROKER_URL:-ssl://localhost:8883}"
export MQTT_CA_CERT_PATH="$ROOT/infra/local/mosquitto/certs/ca.crt"
export MQTT_CLIENT_CERT_PATH="$ROOT/infra/local/mosquitto/certs/paho.crt"
export MQTT_CLIENT_KEY_PATH="$ROOT/infra/local/mosquitto/certs/paho.key"
./services/mvnw -f services/pom.xml -pl contracts -DskipTests -q install
./services/mvnw -f services/device-command-service/pom.xml spring-boot:run
```

```bash
# model-gen-service — :8000
cd services/model-gen-service
source .venv/bin/activate
set -a && source ../../.env && set +a
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_REGION="${AWS_REGION:-us-west-2}"
export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-${AWS_ENDPOINT:-http://localhost:4566}}"
export MODEL_GEN_QUEUE_URL="${MODEL_GEN_QUEUE_URL:-http://localhost:4566/000000000000/model-gen-jobs}"
export ACCESSORY_BUCKET="${ACCESSORY_BUCKET:-cybersixseven-accessories}"
export PLATFORM_API_BASE_URL="${PLATFORM_API_BASE_URL:-http://localhost:8080}"
uvicorn app.main:app --reload --port 8000
```

```bash
# model-gen consumer — separate process, same venv and env
cd services/model-gen-service
source .venv/bin/activate
set -a && source ../../.env && set +a
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_REGION="${AWS_REGION:-us-west-2}"
export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-${AWS_ENDPOINT:-http://localhost:4566}}"
export MODEL_GEN_QUEUE_URL="${MODEL_GEN_QUEUE_URL:-http://localhost:4566/000000000000/model-gen-jobs}"
export ACCESSORY_BUCKET="${ACCESSORY_BUCKET:-cybersixseven-accessories}"
export PLATFORM_API_BASE_URL="${PLATFORM_API_BASE_URL:-http://localhost:8080}"
python -m app.consumer
```

```bash
# student-web — :3000
test -f apps/student-web/.env.local || printf '%s\n' 'NEXT_PUBLIC_API_BASE_URL=http://localhost:8080' 'NEXT_PUBLIC_PRODUCT_ENABLED=true' > apps/student-web/.env.local
pnpm --filter @cybersixseven/student-web dev
```

```bash
# admin-web — :3001
if [ ! -f apps/admin-web/.env.local ]; then
  if [ -f apps/student-web/.env.local ]; then
    cp apps/student-web/.env.local apps/admin-web/.env.local
  else
    printf '%s\n' 'NEXT_PUBLIC_API_BASE_URL=http://localhost:8080' 'NEXT_PUBLIC_PRODUCT_ENABLED=true' > apps/admin-web/.env.local
  fi
fi
pnpm --filter @cybersixseven/admin-web dev
```

```bash
# ESP32 firmware — default env is SSD1306. 1602: -e esp32dev-lcd1602
# Upload/wiring: firmware/esp32/README.md
pio run -d firmware/esp32
pio run -d firmware/esp32 -e esp32dev-lcd1602
```

Students answer questions in a web app. The platform scores the attempt, drives a small companion device (LED, buzzer, motor, LCD), and generates a downloadable 3D-printable accessory for the result. LCD type is a PlatformIO env (`C67_LCD_TYPE`): SSD1306 OLED or 1602 + I2C backpack today — see `firmware/esp32/README.md`.

The device is an ESP32 that talks MQTT over mutual TLS. Everything else — identity, questions, scoring, the event pipeline, and the accessory file — lives in this repo. There is no firmware flashing required to run the web and API stack.

## How a submission moves

```text
student-web  →  platform-api  →  SNS submission-events
                                      ├─ SQS device-commands → device-command-service → MQTT → ESP32
                                      └─ SQS model-gen-jobs  → model-gen-service → private S3 object
student-web  ←  short-lived download from platform-api
```

Scoring happens on the server against the stored correct answer. The browser never sends a score.

## Layout

| Path | What it is |
|---|---|
| `apps/student-web` | Next.js student app, port 3000 |
| `apps/admin-web` | Next.js teacher/admin app, port 3001 |
| `packages/ui`, `api-client`, `auth-client` | Shared UI, typed API client, in-memory session |
| `services/platform-api` | Spring Boot API, port 8080. Postgres, Redis, auth, questions, submissions |
| `services/device-command-service` | Spring Boot, HTTP 8081 / gRPC 9090. SQS consumer, MQTT publisher |
| `services/contracts` | Shared protobuf (`device_command.proto`) |
| `services/model-gen-service` | Python FastAPI + SQS consumer, port 8000. Writes an STL |
| `firmware/esp32` | PlatformIO firmware. LCD types in `firmware/esp32/README.md` |
| `infra/local` | LocalStack init script and Mosquitto config |
| `infra/terraform` | AWS stack (VPC, RDS, Redis, ECS, SNS/SQS, S3, IoT) |
| `postman/` | HTTP collection for the services |
| `e2e/` | Playwright specs |

`documents/` is local planning notes and is gitignored.

## Prerequisites

- Java 21
- Node.js 22.12 or newer (24 is what this repo targets) and [pnpm](https://pnpm.io) 11.26.0. `corepack enable` is enough; `.npmrc` switches pnpm to the pinned version.
- Docker with Compose v2
- Python 3.13 if you run accessory generation (3.12 also works; 3.10 does not — the CAD wheel has no `cp310` build)
- PlatformIO only if you flash a board

## Run locally

### 1. Environment

```bash
cp .env.example .env
```

Set real values before starting anything:

- `POSTGRES_PASSWORD` and `DB_PASSWORD` — same password. Compose refuses to start Postgres until `POSTGRES_PASSWORD` is set. `platform-api` reads `DB_PASSWORD`.
- `JWT_SECRET` — at least 32 bytes. Shorter and the API fails at startup.
- `INTERNAL_API_KEY` — shared by `platform-api`, `device-command-service`, and `model-gen-service`.
- `FIXTURE_TEACHER_PASSWORD` and `FIXTURE_ADMIN_PASSWORD` — used when `APP_FIXTURES_ENABLED=true` to seed a teacher and an admin. Students register themselves in the student app.
- `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET` — only required for “Continue with Google”. Password login works without a real Google client. Authorized redirect URI for local Google login: `http://localhost:8080/login/oauth2/code/google`.

`platform-api` loads the repo-root `.env` on its own. Compose does too. The Next apps and the other processes do not — those steps below export what they need.

Append the local AWS endpoints (LocalStack account `000000000000`; the init script prints the same values as `topic=` and `device-queue=`):

```bash
SNS_SUBMISSION_EVENTS_TOPIC_ARN=arn:aws:sns:us-west-2:000000000000:submission-events
DEVICE_COMMANDS_QUEUE_URL=http://localhost:4566/000000000000/device-commands
MQTT_BROKER_URL=ssl://localhost:8883
MQTT_CA_CERT_PATH=infra/local/mosquitto/certs/ca.crt
MQTT_CLIENT_CERT_PATH=infra/local/mosquitto/certs/paho.crt
MQTT_CLIENT_KEY_PATH=infra/local/mosquitto/certs/paho.key
```

Paths are relative to the repo root. Start `device-command-service` from the repo root, or use absolute paths.

### 2. Certificates and infrastructure

Mosquitto only accepts mutual TLS. Generate the local CA, broker cert, and client certs (gitignored) before Compose, or the broker container exits:

```bash
infra/local/mosquitto/certs/generate-certs.sh
docker compose up -d
```

That starts Postgres 17 (`5432`), Redis 7 (`6379`), LocalStack 4.7 (`4566`: SNS, SQS, DynamoDB, S3), and Mosquitto (`8883`). LocalStack’s init script creates the topic, both queues and their dead-letter queues, the `device_events` table, and the private `cybersixseven-accessories` bucket. `docker compose down -v && docker compose up -d` rebuilds all of that.

```bash
docker compose ps
docker compose logs localstack | tail -n 20
```

### 3. API

The `local` profile points CORS and OAuth redirects at `localhost:3000` and `localhost:3001` and allows a non-Secure refresh cookie. Run this from the repo root. `spring-boot:run` has to target the module pom; pointing it at `services/pom.xml` launches the aggregator, which has no main class.

```bash
set -a && source .env && set +a
./services/mvnw -f services/platform-api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies the schema on startup (`ddl-auto` is `none`). Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

Seeded questions are available at `GET /api/questions`. Register a student with `POST /api/auth/register` or the student app. With fixtures enabled, log into the admin app as the teacher or admin from `.env`.

### 4. Web apps

Next reads env files from each app directory, not the repo root.

```bash
printf '%s\n' \
  'NEXT_PUBLIC_API_BASE_URL=http://localhost:8080' \
  'NEXT_PUBLIC_PRODUCT_ENABLED=true' \
  > apps/student-web/.env.local
cp apps/student-web/.env.local apps/admin-web/.env.local

pnpm install
pnpm dev
```

- Student: [http://localhost:3000](http://localhost:3000)
- Admin: [http://localhost:3001](http://localhost:3001)

Access tokens stay in memory. The refresh token is an HttpOnly cookie set by `platform-api`. Sign-in is email and password, or a full-page redirect to Google.

`NEXT_PUBLIC_PRODUCT_ENABLED=false` leaves auth up and hides the question form. The admin app rejects a student session on screen.

### 5. Accessory generator

Needs the LocalStack bucket and `model-gen-jobs` queue from step 2, and a running `platform-api`.

```bash
cd services/model-gen-service
python3.13 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

set -a && source ../../.env && set +a
uvicorn app.main:app --reload --port 8000
```

In another shell, with the same venv and env:

```bash
python -m app.consumer
```

`GET /health` returns `{"status":"ok"}`. The consumer is a separate process on purpose. After a scored submission it writes `accessories/{submissionId}/reward.stl` and calls `platform-api`, which then serves a short-lived download.

Sanity check for the CAD toolchain, no queue required:

```bash
python scripts/test_generate.py && ls -la out/cube.stl
```

### 6. Device commands

Needs Mosquitto, the `device-commands` queue, and `platform-api`.

Same command as the device-command block at the top of this file. `contracts` has to be installed first; `spring-boot:run` on the aggregator pom does not start this service.

HTTP health: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health). gRPC listens on `9090`.

Without a board, this process still consumes the queue and publishes to Mosquitto. Nothing physical happens until firmware is connected. Flash and serial monitor steps are in [`firmware/esp32/README.md`](firmware/esp32/README.md).

### 7. Calling the API by hand

Import `postman/CyberSixSeven.postman_collection.json`. Fixed bases:

| Service | Base |
|---|---|
| platform-api | `http://localhost:8080` |
| device-command-service | `http://localhost:8081` |
| model-gen-service | `http://localhost:8000` |

Main platform routes: `/api/auth/*`, `/api/questions`, `/api/submissions`, `/api/robots`, `/api/csrf`. Internal routes (`/internal/**`) require `X-Internal-Api-Key`.

## Tests

```bash
# JVM (Postgres and Redis via Testcontainers — Docker must be running)
cd services && ./mvnw -q verify

# Frontends and shared packages
pnpm -w build && pnpm -w lint
pnpm test

# Accessory service
cd services/model-gen-service && source .venv/bin/activate && pytest -q

# Browser
pnpm exec playwright install --with-deps
pnpm e2e
```

Playwright starts both Next apps. Specs under `e2e/` stub the auth HTTP calls; they do not need the full backend.

## Terraform

`infra/terraform` describes the deployed stack: VPC, RDS Postgres 17, ElastiCache Redis, ECS services, SNS/SQS, the accessories bucket, and IoT Core. State backend settings are in `backend.hcl.example`. Copy `terraform.tfvars.example` to `terraform.tfvars` (gitignored) before a plan. Do not commit tfvars or state.

```bash
cd infra/terraform
terraform fmt -check
terraform init -backend-config=backend.hcl
terraform validate
```

## Ports

| Process | Port |
|---|---|
| platform-api | 8080 |
| device-command-service | 8081 HTTP, 9090 gRPC |
| model-gen-service | 8000 |
| student-web | 3000 |
| admin-web | 3001 |
| Postgres | 5432 |
| Redis | 6379 |
| LocalStack | 4566 |
| Mosquitto | 8883 |
