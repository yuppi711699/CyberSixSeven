#!/usr/bin/env bash
# First clone (macOS / Linux). Windows is not supported.
#
# Makes the repo ready to start. Does not start Spring, Next, uvicorn, or flash.
#
# Usage (from repo root):
#   ./setup/firstSetup.sh
#   ./setup/firstSetup.sh --force-certs
#
# You still type:
#   - WIFI_SSID and WIFI_PASSWORD in firmware/esp32/secrets.h
#   - Optional: replace .env change-me / replace-me values (example placeholders boot locally)
set -euo pipefail

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == CYGWIN* || "$(uname -s)" == MSYS* ]]; then
  echo "setup/firstSetup.sh does not support Windows. Use macOS or Linux." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FORCE_CERTS=0
usage() {
  cat <<'EOF'
Usage: ./setup/firstSetup.sh [--force-certs]

  --force-certs  Rotate the local CA, rewrite firmware PEMs, restart Mosquitto
                 if it is running. Then reflash the ESP32 and restart
                 device-command-service (this script does neither).

Idempotent otherwise: existing CA is left alone; secrets.h WIFI_* is left alone.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-certs) FORCE_CERTS=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing prerequisite: $1" >&2
    exit 1
  }
}

echo "==> checking prerequisites"
need docker
need openssl
need python3
need node
need java

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not running." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose)." >&2
  exit 1
fi

NODE_MAJOR="$(node -p "process.versions.node.split('.')[0]")"
NODE_MINOR="$(node -p "process.versions.node.split('.')[1]")"
if (( NODE_MAJOR < 22 || (NODE_MAJOR == 22 && NODE_MINOR < 12) )); then
  echo "Node.js >= 22.12 required (found $(node -v))." >&2
  exit 1
fi

JAVA_VER="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
if [[ -z "$JAVA_VER" || "$JAVA_VER" -lt 21 ]]; then
  echo "Java 21+ required (found $(java -version 2>&1 | head -n 1))." >&2
  exit 1
fi

PYTHON=""
for candidate in python3.13 python3.12 python3; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done
PY_MINOR="$("$PYTHON" -c 'import sys; print(sys.version_info.minor)')"
PY_MAJOR="$("$PYTHON" -c 'import sys; print(sys.version_info.major)')"
if [[ "$PY_MAJOR" != "3" || "$PY_MINOR" -lt 12 ]]; then
  echo "Python 3.12 or 3.13 required for model-gen-service (found $("$PYTHON" -V))." >&2
  exit 1
fi

lan_ip() {
  local ip=""
  if command -v ipconfig >/dev/null 2>&1; then
    ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
  fi
  if [[ -z "$ip" ]] && command -v ip >/dev/null 2>&1; then
    ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}')"
  fi
  printf '%s' "$ip"
}

env_set() {
  local key="$1"
  local value="$2"
  local file="$3"
  python3 - "$file" "$key" "$value" <<'PY'
from pathlib import Path
import sys

path, key, value = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
text = path.read_text() if path.exists() else ""
if not text.endswith("\n") and text:
    text += "\n"
prefix = f"{key}="
lines = text.splitlines(True)
found = False
out = []
for line in lines:
    if line.startswith(prefix):
        out.append(f"{key}={value}\n")
        found = True
    else:
        out.append(line)
if not found:
    if out and not out[-1].endswith("\n"):
        out[-1] += "\n"
    out.append(f"{key}={value}\n")
path.write_text("".join(out))
PY
}

wait_container() {
  local name="$1"
  local i st has_health
  for i in $(seq 1 60); do
    has_health="$(docker inspect -f '{{if .State.Health}}yes{{else}}no{{end}}' "$name" 2>/dev/null || echo missing)"
    if [[ "$has_health" == "missing" ]]; then
      sleep 2
      continue
    fi
    if [[ "$has_health" == "yes" ]]; then
      st="$(docker inspect -f '{{.State.Health.Status}}' "$name")"
      if [[ "$st" == "healthy" ]]; then
        return 0
      fi
    else
      st="$(docker inspect -f '{{.State.Status}}' "$name")"
      if [[ "$st" == "running" ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "timed out waiting for $name" >&2
  docker compose logs --tail 40 "$name" >&2 || true
  exit 1
}

echo "==> .env"
if [[ ! -f "$ROOT/.env" ]]; then
  if [[ ! -f "$ROOT/.env.example" ]]; then
    echo ".env.example missing" >&2
    exit 1
  fi
  cp "$ROOT/.env.example" "$ROOT/.env"
  echo "created .env from .env.example (placeholders boot locally; change them if you share this machine)"
else
  echo "keeping existing .env (passwords untouched)"
fi

# JVM services read AWS_ENDPOINT. Python reads AWS_ENDPOINT_URL. Set both.
env_set AWS_ACCESS_KEY_ID test "$ROOT/.env"
env_set AWS_SECRET_ACCESS_KEY test "$ROOT/.env"
env_set AWS_REGION us-west-2 "$ROOT/.env"
env_set AWS_ENDPOINT http://localhost:4566 "$ROOT/.env"
env_set AWS_ENDPOINT_URL http://localhost:4566 "$ROOT/.env"
env_set SNS_SUBMISSION_EVENTS_TOPIC_ARN arn:aws:sns:us-west-2:000000000000:submission-events "$ROOT/.env"
env_set DEVICE_COMMANDS_QUEUE_URL http://localhost:4566/000000000000/device-commands "$ROOT/.env"
env_set MODEL_GEN_QUEUE_URL http://localhost:4566/000000000000/model-gen-jobs "$ROOT/.env"
env_set ACCESSORY_BUCKET cybersixseven-accessories "$ROOT/.env"
env_set MQTT_BROKER_URL ssl://localhost:8883 "$ROOT/.env"
env_set MQTT_CA_CERT_PATH "$ROOT/infra/local/mosquitto/certs/ca.crt" "$ROOT/.env"
env_set MQTT_CLIENT_CERT_PATH "$ROOT/infra/local/mosquitto/certs/paho.crt" "$ROOT/.env"
env_set MQTT_CLIENT_KEY_PATH "$ROOT/infra/local/mosquitto/certs/paho.key" "$ROOT/.env"
env_set REWARD_SERVICE_BASE_URL http://127.0.0.1:8082 "$ROOT/.env"
env_set PLATFORM_API_BASE_URL http://localhost:8080 "$ROOT/.env"

CERTS="$ROOT/infra/local/mosquitto/certs"
echo "==> mTLS certificates"
if [[ "$FORCE_CERTS" -eq 1 || ! -f "$CERTS/ca.crt" || ! -f "$CERTS/esp32-dev-001.crt" || ! -f "$CERTS/paho.crt" ]]; then
  if [[ "$FORCE_CERTS" -eq 1 ]]; then
    echo "rotating CA (--force-certs)"
  else
    echo "generating local CA + broker + paho + esp32-dev-001"
  fi
  bash "$CERTS/generate-certs.sh"
else
  echo "keeping existing CA in $CERTS (pass --force-certs to rotate)"
fi

HOST_IP="$(lan_ip)"
echo "==> firmware/esp32/secrets.h"
if [[ ! -f "$ROOT/firmware/esp32/secrets.h" ]]; then
  cp "$ROOT/firmware/esp32/secrets.h.example" "$ROOT/firmware/esp32/secrets.h"
  echo "created secrets.h from example — set WIFI_SSID and WIFI_PASSWORD yourself"
else
  echo "keeping existing secrets.h WIFI_* lines"
fi

python3 - "$ROOT/firmware/esp32/secrets.h" "$CERTS" "$HOST_IP" <<'PY'
from pathlib import Path
import re
import sys

secrets_path = Path(sys.argv[1])
certs = Path(sys.argv[2])
host = sys.argv[3]
text = secrets_path.read_text()

def pem(name: str) -> str:
    return (certs / name).read_text().strip() + "\n"

def put(src: str, var: str, body: str) -> str:
    pat = rf'(constexpr char {var}\[\] = R"EOF\(\n)(.*?)(\n\)EOF";)'
    ntext, n = re.subn(
        pat,
        lambda m: m.group(1) + body + m.group(3).lstrip("\n"),
        src,
        count=1,
        flags=re.S,
    )
    if n != 1:
        raise SystemExit(f"could not replace {var} in secrets.h (n={n})")
    return ntext

text = put(text, "CA_CERT", pem("ca.crt"))
text = put(text, "CLIENT_CERT", pem("esp32-dev-001.crt"))
text = put(text, "CLIENT_KEY", pem("esp32-dev-001.key"))
if host:
    text, n = re.subn(
        r'constexpr char MQTT_BROKER_HOST\[\] = "[^"]*";',
        f'constexpr char MQTT_BROKER_HOST[] = "{host}";',
        text,
        count=1,
    )
    if n != 1:
        raise SystemExit("could not set MQTT_BROKER_HOST")
secrets_path.write_text(text)
if host:
    print(f"injected PEMs + MQTT_BROKER_HOST={host}")
else:
    print("injected PEMs; could not detect LAN IP — set MQTT_BROKER_HOST yourself")
PY

echo "==> Next .env.local"
write_next_env() {
  local dest="$1"
  if [[ -f "$dest" ]]; then
    echo "keeping $dest"
    return
  fi
  printf '%s\n' \
    'NEXT_PUBLIC_API_BASE_URL=http://localhost:8080' \
    'NEXT_PUBLIC_PRODUCT_ENABLED=true' \
    > "$dest"
  echo "wrote $dest"
}
write_next_env "$ROOT/apps/student-web/.env.local"
write_next_env "$ROOT/apps/admin-web/.env.local"

echo "==> docker compose up -d"
docker compose up -d
if [[ "$FORCE_CERTS" -eq 1 ]]; then
  docker compose restart mosquitto
  echo "restarted mosquitto to load the new broker cert"
fi
wait_container cybersixseven-postgres
wait_container cybersixseven-redis
wait_container cybersixseven-localstack
wait_container cybersixseven-mosquitto
echo "waiting for LocalStack init (queues/topic/table/bucket)"
READY=0
for i in $(seq 1 60); do
  if docker logs cybersixseven-localstack 2>&1 | grep -q "localstack v0.5 ready"; then
    READY=1
    break
  fi
  sleep 2
done
if [[ "$READY" -ne 1 ]]; then
  echo "LocalStack init did not print 'localstack v0.5 ready'" >&2
  docker compose logs --tail 40 localstack >&2
  exit 1
fi
docker compose ps

echo "==> pnpm install"
if command -v corepack >/dev/null 2>&1; then
  corepack enable >/dev/null 2>&1 || true
  corepack prepare pnpm@11.26.0 --activate
fi
if ! command -v pnpm >/dev/null 2>&1; then
  echo "pnpm missing. Run: corepack enable && corepack prepare pnpm@11.26.0 --activate" >&2
  exit 1
fi
pnpm install

echo "==> Playwright Chromium"
if [[ "$(uname -s)" == Linux ]]; then
  pnpm exec playwright install --with-deps chromium
else
  pnpm exec playwright install chromium
fi

echo "==> Maven (all JVM modules)"
./services/mvnw -f services/pom.xml -DskipTests -q install

echo "==> model-gen-service venv ($PYTHON)"
(
  cd "$ROOT/services/model-gen-service"
  if [[ ! -d .venv ]]; then
    "$PYTHON" -m venv .venv
  fi
  .venv/bin/pip install -q -r requirements.txt
)

if command -v pio >/dev/null 2>&1; then
  echo "==> PlatformIO libs (no upload)"
  if ! pio run -d firmware/esp32 -e esp32dev -e esp32dev-lcd1602; then
    echo "pio build failed — firmware is optional; set WIFI in secrets.h and retry" >&2
  fi
else
  echo "==> PlatformIO not on PATH (skip). Web/API stack does not need it."
fi

echo
echo "firstSetup done. Processes are not running yet."
echo
echo "Still manual:"
echo "  - firmware/esp32/secrets.h  WIFI_SSID / WIFI_PASSWORD  (board only)"
echo "  - .env passwords if you do not want the .env.example placeholders"
if [[ "$FORCE_CERTS" -eq 1 ]]; then
  echo "  - reflash ESP32 and restart device-command-service (CA rotated)"
fi
echo
echo "Start from the repo root (one terminal each) — see README.md"
if [[ -z "$HOST_IP" ]]; then
  echo "WARNING: no en0 IPv4 — MQTT_BROKER_HOST was not auto-set." >&2
fi
