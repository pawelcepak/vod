#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "=== Repozytorium ==="
git status --short --branch

echo
echo "=== Ostatni commit ==="
git log -1 --oneline

echo
echo "=== Remote ==="
git remote -v
