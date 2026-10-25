#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required." >&2; exit 1; }
mkdir -p secrets
chmod 700 secrets

if [ ! -f secrets/jwt-private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/jwt-private.pem
fi
chmod 0444 secrets/jwt-private.pem

if [ ! -f secrets/accounts.json ]; then
  if [ ! -x .venv/bin/python ]; then
    python3 -m venv .venv
  fi
  .venv/bin/python -m pip install --disable-pip-version-check -r requirements-dev.txt
  .venv/bin/python scripts/create-account.py \
    --audience component-a --audience component-b \
    --scope component-a.read --scope component-b.read
fi
chmod 0444 secrets/accounts.json

echo "Local development secrets are ready in the ignored secrets/ directory."
echo "Start the stack with: docker compose up --build"
