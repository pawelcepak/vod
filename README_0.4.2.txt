VOD USB — Android 0.4.2

Nowy workflow developerski z telefonu:
- patch nie trafia od razu do main,
- tworzona jest automatyczna gałąź test/mobile-...,
- GitHub Actions buduje APK z gałęzi testowej,
- po teście na telefonie użytkownik akceptuje lub odrzuca zmianę,
- dopiero akceptacja scala kod do main.

Nowe skrypty:
- scripts/mobile_test_patch.sh
- scripts/mobile_accept_test.sh
- scripts/mobile_reject_test.sh

Główna funkcjonalność aplikacji VOD pozostaje bez zmian.
