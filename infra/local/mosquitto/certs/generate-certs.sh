#!/usr/bin/env bash
# Generate a local CA, Mosquitto broker cert (localhost/mosquitto SANs),
# Paho service client cert, and per-device client certs.
# Private keys and certificates are gitignored.
set -euo pipefail

OUT="${1:-$(cd "$(dirname "$0")" && pwd)}"
DAYS="${CERT_DAYS:-3650}"
DEVICES="${DEVICES:-esp32-dev-001}"

mkdir -p "$OUT"
cd "$OUT"

openssl req -x509 -newkey rsa:4096 -sha256 -days "$DAYS" -nodes \
  -keyout ca.key -out ca.crt \
  -subj "/CN=CyberSixSeven Local CA"

openssl req -newkey rsa:2048 -nodes -keyout broker.key -out broker.csr \
  -subj "/CN=mosquitto"
cat > broker.ext <<'EOF'
subjectAltName=DNS:localhost,DNS:mosquitto,IP:127.0.0.1
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF
openssl x509 -req -in broker.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out broker.crt -days "$DAYS" -sha256 -extfile broker.ext

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

rm -f broker.csr broker.ext ca.srl
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
