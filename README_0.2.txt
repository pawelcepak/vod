VOD USB — ANDROID 0.2
=====================

CO NOWEGO
---------
Ta wersja ma prawdziwy silnik yt-dlp + FFmpeg.

Schemat:
internet
  -> yt-dlp/FFmpeg na telefonie
  -> tymczasowy gotowy odcinek w katalogu roboczym aplikacji
  -> kopiowanie na wybrany folder pendrive
  -> usunięcie pliku tymczasowego z telefonu
  -> następny odcinek

Dzięki temu telefon NIE przechowuje całej kolejki.
Potrzebuje jednak wolnego miejsca na jeden aktualnie pobierany odcinek.
Aplikacja wymaga minimum ok. 2,5 GB wolnego miejsca roboczego.

LINKI
-----
Kliknij:
"Ustaw / edytuj linki odcinków"

Format jednej linii:

1801|https://vod.tvp.pl/...
1802|https://vod.tvp.pl/...
1803|https://vod.tvp.pl/...

Linki zapisują się w aplikacji.

OBSŁUGA
-------
1. Podłącz pendrive exFAT.
2. Uruchom aplikację.
3. Wybierz folder na pendrive.
4. Poczekaj aż u góry będzie:
   "Silnik pobierania: gotowy"
5. Ustaw linki.
6. Zaznacz odcinki.
7. Kliknij:
   "Pobierz zaznaczone na pendrive"
8. Nie zamykaj aplikacji podczas pobierania w tej wersji.
9. Ekran jest utrzymywany aktywny podczas kolejki.
10. Po każdym odcinku gotowy film trafia na USB, a plik tymczasowy jest kasowany.

Pobrane odcinki są automatycznie wykrywane.
Jeśli np. "odc. 1801.mp4" już znajduje się na USB,
1801 dostanie status "Pobrany ✓" i nie można go zaznaczyć ponownie.

USUWANIE
--------
Na dole ekranu:
- zaznacz pobrane odcinki,
- kliknij "Usuń zaznaczone z pendrive".

Po usunięciu odcinek ponownie będzie dostępny do zaznaczenia i pobrania.

WAŻNE
-----
Wersja 0.2 jest pierwszym testem yt-dlp na Galaxy S25 Ultra.
Jeżeli konkretny link nie zadziała, zanotuj dokładny komunikat aplikacji.

Projekt korzysta z:
io.github.junkfood02.youtubedl-android 0.18.1

oraz dołączonego modułu FFmpeg.

GITHUB
------
Workflow .github/workflows/build-apk.yml pozostaje w projekcie.
Po pushu GitHub Actions zbuduje nowe APK automatycznie.
