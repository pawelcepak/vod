VOD USB — Android 0.4.1
=======================

Ta wersja dodaje workflow rozwoju projektu bezpośrednio z telefonu.

Nowe skrypty:
- scripts/mobile_sync.sh
- scripts/mobile_apply_patch.sh
- scripts/mobile_publish.sh
- scripts/mobile_status.sh

Dokumentacja:
- MOBILE_WORKFLOW_ANDROID.md

Model pracy:
telefon / Termux
  -> Git
  -> GitHub
  -> GitHub Actions
  -> podpisane APK
  -> aktualizacja aplikacji na tym samym telefonie

Sama funkcjonalność VOD z 0.4.0 pozostaje bez zmian.
