VOD USB — stały link do APK
===========================

Po zaakceptowaniu wersji testowej i scaleniu jej do `main`,
GitHub Actions automatycznie:

1. buduje podpisany release APK,
2. zachowuje artefakt buildu,
3. publikuje / podmienia plik `VOD-USB.apk`
   w GitHub Release o tagu `latest`.

Stały link dla użytkownika końcowego:

https://github.com/pawelcepak/vod/releases/download/latest/VOD-USB.apk

Repozytorium musi pozostać publiczne, aby pobieranie nie wymagało logowania.

WAŻNE:
- gałęzie testowe NIE aktualizują publicznego APK,
- link aktualizuje się dopiero po wejściu kodu do `main`,
- podpis APK pozostaje ten sam dzięki istniejącym GitHub Secrets,
  więc kolejne pliki powinny instalować się przez "Aktualizuj".
