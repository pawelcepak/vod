#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PATCH="${1:-}"
MESSAGE="${2:-Test VOD update}"

if [ -z "$PATCH" ]; then
  echo "Użycie:"
  echo '  ./scripts/mobile_test_patch.sh "/storage/emulated/0/Download/PATCH.zip" "Opis testu"'
  exit 1
fi

if [ ! -f "$PATCH" ]; then
  echo "Błąd: nie znaleziono pliku:"
  echo "$PATCH"
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Błąd: uruchom skrypt w repozytorium vod."
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [ -n "$(git status --porcelain)" ]; then
  echo "Błąd: repozytorium ma niezapisane zmiany."
  echo "Najpierw je zaakceptuj, odrzuć albo zapisz."
  git status --short
  exit 1
fi

echo "Synchronizacja main..."
git fetch origin
git checkout main
git pull --ff-only origin main

STAMP="$(date +%Y%m%d-%H%M%S)"
BRANCH="test/mobile-$STAMP"

echo "Tworzenie gałęzi testowej: $BRANCH"
git checkout -b "$BRANCH"

echo "Nakładanie patcha..."
unzip -o "$PATCH" -d "$ROOT"

echo
echo "Zmiany:"
git status --short

if [ -z "$(git status --porcelain)" ]; then
  echo "Patch nie wprowadził żadnych zmian."
  git checkout main
  git branch -D "$BRANCH"
  exit 1
fi

git add .
git commit -m "$MESSAGE"

echo "Wysyłanie WYŁĄCZNIE gałęzi testowej..."
git push -u origin "$BRANCH"

printf '%s' "$BRANCH" > "$ROOT/.last_test_branch"

echo
echo "Gotowe."
echo "Main NIE został zmieniony."
echo "GitHub Actions zbuduje APK z:"
echo "  $BRANCH"
echo
echo "Po przetestowaniu APK:"
echo "  ./scripts/mobile_accept_test.sh"
echo
echo "Jeśli test nie przejdzie:"
echo "  ./scripts/mobile_reject_test.sh"
