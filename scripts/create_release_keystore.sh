#!/usr/bin/env bash
set -euo pipefail

KEYSTORE="${1:-vod-release.jks}"
ALIAS="${2:-vod}"

echo "Tworzenie stałego klucza podpisu VOD."
echo "Zapisz hasła — będą potrzebne jako GitHub Secrets."
echo

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

echo
echo "Gotowe: $KEYSTORE"
echo
echo "Aby utworzyć wartość VOD_KEYSTORE_BASE64:"
echo "base64 -w 0 \"$KEYSTORE\" > vod-keystore-base64.txt"
