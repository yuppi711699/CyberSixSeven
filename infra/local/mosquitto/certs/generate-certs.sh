#!/usr/bin/env bash
# Generate a local CA, Mosquitto broker cert, Paho service client cert, and
# per-device client certs. Private keys and certificates are gitignored.
#
# Arduino WiFiClientSecure always mbedtls_ssl_set_hostname(MQTT_BROKER_HOST).
# ESP32 Arduino's mbedTLS 2.28.7 only matches SAN dNSName — IP Address SANs
# are ignored, so OpenSSL can succeed while the board returns -9984. Put the
# LAN address in as DNS:<ip> (and IP:<ip> for desktop clients). Reissue
# without rotating the CA:
#
#     ISSUE_BROKER_ONLY=1 ./generate-certs.sh
#     docker compose restart mosquitto
set -euo pipefail

OUT="${1:-$(cd "$(dirname "$0")" && pwd)}"
DAYS="${CERT_DAYS:-3650}"
DEVICES="${DEVICES:-esp32-dev-001}"

mkdir -p "$OUT"
cd "$OUT"

broker_san() {
  local san="DNS:localhost,DNS:mosquitto,IP:127.0.0.1"
  local extra="${BROKER_SAN_IPS:-}"
  if [[ -z "$extra" ]] && command -v ipconfig >/dev/null 2>&1; then
    extra="$(ipconfig getifaddr en0 2>/dev/null || true)"
  fi
  local ip
  for ip in $extra; do
    san+=",IP:${ip},DNS:${ip}"
  done
  printf '%s\n' "$san"
}

issue_broker() {
  if [[ ! -f ca.crt || ! -f ca.key ]]; then
    echo "ca.crt/ca.key missing — run without ISSUE_BROKER_ONLY first" >&2
    exit 1
  fi
  local san
  san="$(broker_san)"
  echo "Broker SAN: $san"
  if [[ ! -f broker.key ]]; then
    openssl req -newkey rsa:2048 -nodes -keyout broker.key -out broker.csr \
      -subj "/CN=mosquitto"
  else
    openssl req -new -key broker.key -out broker.csr -subj "/CN=mosquitto"
  fi
  cat > broker.ext <<EOF
subjectAltName=${san}
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF
  openssl x509 -req -in broker.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out broker.crt -days "$DAYS" -sha256 -extfile broker.ext
  rm -f broker.csr broker.ext ca.srl
  chmod 644 broker.crt
  chmod 600 broker.key
}

if [[ "${ISSUE_BROKER_ONLY:-}" == 1 ]]; then
  issue_broker
  echo "Reissued broker.crt in $OUT (CA unchanged — do not re-paste secrets.h)"
  exit 0
fi

openssl req -x509 -newkey rsa:4096 -sha256 -days "$DAYS" -nodes \
  -keyout ca.key -out ca.crt \
  -subj "/CN=CyberSixSeven Local CA"

rm -f broker.key
issue_broker

issue_client() {
  local name="$1"
  openssl req -newkey rsa:2048 -nodes -keyout "${name}.rsa.key" -out "${name}.csr" \
    -subj "/CN=${name}"
  cat > "${name}.ext" <<EOF
extendedKeyUsage=clientAuth
keyUsage=digitalSignature,keyEncipherment
EOF
  openssl x509 -req -in "${name}.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out "${name}.crt" -days "$DAYS" -sha256 -extfile "${name}.ext"
  openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in "${name}.rsa.key" -out "${name}.key"
  rm -f "${name}.rsa.key" "${name}.csr" "${name}.ext"
}

issue_client paho
for device in $DEVICES; do
  issue_client "$device"
done

rm -f ca.srl
chmod 644 ca.crt broker.crt paho.crt
chmod 600 ca.key broker.key paho.key
for device in $DEVICES; do
  chmod 644 "${device}.crt"
  chmod 600 "${device}.key"
done

echo "Wrote local mTLS material to $OUT"
echo "CA:     $OUT/ca.crt"
echo "Broker: $OUT/broker.crt"
echo "Paho:   $OUT/paho.crt $OUT/paho.key"
echo "Devices: $DEVICES"
echo "Broker SAN must include MQTT_BROKER_HOST (see ISSUE_BROKER_ONLY above)."
