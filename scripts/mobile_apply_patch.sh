#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PATCH="${1:-}"

if [ -z "$PATCH" ]; then
  echo "Użycie:"
  echo "  ./scripts/mobile_apply_patch.sh /storage/emulated/0/Download/NAZWA_PATCHA.zip"
  exit 1
fi

if [ ! -f "$PATCH" ]; then
  echo "Błąd: plik nie istnieje:"
  echo "$PATCH"
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Błąd: uruchom ten skrypt w katalogu repozytorium vod."
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"

echo "Nakładanie patcha:"
echo "$PATCH"
echo "na:"
echo "$ROOT"

unzip -o "$PATCH" -d "$ROOT"

echo
echo "Gotowe. Zmiany:"
git -C "$ROOT" status --short
