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
  exit 1
fi

git fetch origin
git checkout main
git reset --hard origin/main

git push origin --delete "$BRANCH" || true
git branch -D "$BRANCH" 2>/dev/null || true
rm -f "$ROOT/.last_test_branch"

echo "Test odrzucony. Main pozostał bez zmian."
