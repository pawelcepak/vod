VOD USB — Android 0.5.0
=======================

Cel wersji:
maksymalnie uproszczona obsługa dla osoby, która ma umieć:
- podłączyć pendrive,
- wybrać odcinki,
- pobrać je,
- odtworzyć plik,
- zaznaczyć obejrzane,
- usunąć obejrzane z pendrive.

Najważniejsze zmiany:
- duży i czytelny stan pendrive,
- ekran podzielony na kroki 1 i 2,
- pole "pierwszy odcinek" + tylko 20 kolejnych pozycji zamiast tysięcy wierszy,
- po podłączeniu USB aplikacja sugeruje numer po najwyższym odcinku znajdującym się na pendrive,
- duży przycisk pobierania,
- czytelniejsza lista plików na USB,
- proste potwierdzenie usuwania,
- komunikat po zakończeniu: można odłączyć pendrive i podłączyć go do telewizora,
- ustawienia techniczne schowane pod "Ustawienia zaawansowane",
- miniatury odcinków nie są wyświetlane, pobierane ani zapisywane w cache aplikacji,
- usunięto zależność Coil, która służyła do ładowania miniaturek.

Workflow:
wersja przeznaczona do testu z telefonu przez scripts/mobile_test_patch.sh.
Po udanym teście dopiero scripts/mobile_accept_test.sh scala ją do main.
