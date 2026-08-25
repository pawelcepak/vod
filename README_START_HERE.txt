M JAK MIŁOŚĆ USB — PROTOTYP 0.1
=================================

CO DZIAŁA W TEJ WERSJI
----------------------
- wybór folderu na pendrivie przez systemowy wybór Androida,
- zapamiętanie dostępu do wybranego folderu,
- ponowne wykrycie folderu po restarcie aplikacji,
- lista odcinków 1801–1807 do zaznaczania,
- orientacyjne podsumowanie ile danych zajmą zaznaczone odcinki,
- skanowanie pendrive i wykrywanie plików MP4/MKV/WEBM/M4V,
- pokazanie liczby pobranych odcinków i ich łącznego rozmiaru,
- próba pokazania wolnego/całkowitego miejsca na nośniku USB,
- rozpoznawanie numeru odcinka z nazwy pliku,
- odtwarzanie filmu w zewnętrznym odtwarzaczu Androida,
- zaznaczanie pobranych odcinków,
- potwierdzone usuwanie wybranych plików z pendrive.

CZEGO JESZCZE NIE MA
--------------------
- prawdziwego pobierania z yt-dlp,
- FFmpeg,
- pobierania bezpośrednio na USB,
- aktualizacji listy odcinków z internetu,
- ekranu administratora/PIN.

Po sprawdzeniu działania pendrive na Galaxy S25 Ultra dodamy downloader.

PENDRIVE
--------
Zalecany system plików: exFAT.

Pierwszy test:
1. Sformatuj pendrive jako exFAT.
2. Utwórz na nim folder np.:
   M jak milosc
3. Wrzuć do niego testowo 1–2 pliki MP4, najlepiej nazwane np.:
   odc. 1801.mp4
   odc. 1802.mp4
4. Podłącz pendrive do Galaxy S25 Ultra.
5. Uruchom aplikację.
6. Naciśnij "Wybierz pendrive".
7. W systemowym oknie Androida wskaż folder "M jak milosc".
8. Sprawdź:
   - czy pliki są widoczne,
   - czy pokazuje ich rozmiar,
   - czy ▶ uruchamia film,
   - czy usuwanie działa,
   - czy po zamknięciu i ponownym uruchomieniu aplikacji folder jest zapamiętany.

JAK OTWORZYĆ PROJEKT
--------------------
1. Zainstaluj Android Studio.
2. Rozpakuj ZIP.
3. W Android Studio wybierz Open i wskaż folder:
   MJakMiloscUSB
4. Poczekaj na Gradle Sync.
5. Podłącz telefon kablem USB i włącz Debugowanie USB
   albo uruchom aplikację na emulatorze.
6. Kliknij Run.

Jeśli Android Studio zaproponuje aktualizację wersji Gradle/AGP,
możesz zaakceptować automatyczną migrację projektu.

LISTA ODCINKÓW
--------------
Testowa lista jest w:
app/src/main/assets/episodes.json

W kolejnym etapie ten sam plik może zawierać gotowe URL-e przygotowane przez Ciebie.
