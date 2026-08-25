#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Błąd: uruchom skrypt w repozytorium vod."
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

BRANCH="${1:-}"

if [ -z "$BRANCH" ] && [ -f "$ROOT/.last_test_branch" ]; then
  BRANCH="$(cat "$ROOT/.last_test_branch")"
fi

if [ -z "$BRANCH" ]; then
  echo "Nie znam gałęzi testowej."
  echo "Podaj ją ręcznie:"
  echo "  ./scripts/mobile_accept_test.sh test/mobile-..."
  exit 1
fi

echo "Akceptujesz przetestowaną wersję:"
echo "  $BRANCH"
echo

git fetch origin

if ! git show-ref --verify --quiet "refs/remotes/origin/$BRANCH"; then
  echo "Błąd: gałąź origin/$BRANCH nie istnieje."
  exit 1
fi

git checkout main
git pull --ff-only origin main

echo "Scalanie do main..."
git merge --no-ff "origin/$BRANCH" -m "Accept tested mobile update"

echo "Wysyłanie main..."
git push origin main

echo "Usuwanie zdalnej gałęzi testowej..."
git push origin --delete "$BRANCH" || true

git branch -D "$BRANCH" 2>/dev/null || true
rm -f "$ROOT/.last_test_branch"

echo
echo "Zaakceptowane."
echo "Zmiana jest teraz w main."
echo "GitHub Actions zbuduje finalny APK z main."
