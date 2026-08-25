#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Błąd: uruchom ten skrypt w katalogu repozytorium vod."
  exit 1
fi

echo "Synchronizacja repozytorium..."
git fetch origin
git pull --ff-only origin main
echo "Repozytorium jest aktualne."
