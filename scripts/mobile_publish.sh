#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

MESSAGE="${1:-Update VOD from Android}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Błąd: uruchom ten skrypt w katalogu repozytorium vod."
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [ -z "$(git status --porcelain)" ]; then
  echo "Brak zmian do wysłania."
  exit 0
fi

echo "Dodawanie zmian..."
git add .

echo "Commit:"
git commit -m "$MESSAGE"

echo "Wysyłanie do GitHub..."
git push origin main

echo
echo "Wysłano. GitHub Actions powinien automatycznie rozpocząć build APK."
