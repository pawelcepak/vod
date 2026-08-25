VOD USB — Android 0.3.0
=======================

NAJWAŻNIEJSZE ZMIANY

1. Pobieranie działa w Foreground Service.
   Możesz uruchomić kolejkę, wyjść z aplikacji i korzystać z telefonu.

2. Powiadomienie pokazuje:
   - aktualny odcinek,
   - procent,
   - ETA podczas pobierania,
   - postęp kopiowania na USB,
   - przycisk "Zatrzymaj".

3. Kolejka jest zapisywana w SharedPreferences.
   Jeśli aplikacja zostanie zamknięta, usługa nadal ma kolejkę.
   Jeśli proces zostanie odtworzony przez Androida, usługa próbuje wznowić
   zapisaną kolejkę.

4. TVP:
   preferowane audio:
   audio0-Polski
   zamiast Audiodeskrypcji.

5. Nadal:
   - jeden odcinek jest pobierany/scalany w katalogu roboczym telefonu,
   - po pobraniu jest kopiowany na pendrive,
   - plik roboczy jest usuwany,
   - aplikacja wymaga około 2,5 GB wolnego miejsca na telefonie.

6. Android 13+:
   aplikacja prosi o zgodę na powiadomienia.

PODPIS APK

Workflow nadal używa tego samego cache:
vod-android-debug-keystore-v1

Jeżeli 0.2.1 była już zbudowana po dodaniu tego cache, 0.3.0 powinna
zainstalować się jako zwykła aktualizacja. Jeśli nie, ta instalacja może
jeszcze wymagać jednorazowego odinstalowania; kolejne buildy z tym samym
cache powinny zachować podpis.

TEST 0.3.0

1. Podłącz pendrive.
2. Uruchom aplikację.
3. Wybierz folder USB.
4. Wklej linki.
5. Zaznacz najlepiej jeden odcinek na pierwszy test.
6. Uruchom pobieranie.
7. Gdy pojawi się powiadomienie, przejdź do YouTube/Chrome.
8. Po kilku minutach sprawdź powiadomienie.
9. Po zakończeniu wróć do aplikacji.
10. Sprawdź, czy odcinek jest na pendrive i czy audio TVP jest poprawne.
