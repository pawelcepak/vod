# VOD USB Android

Aktualna wersja: **0.2.0**

Androidowa aplikacja do wybierania odcinków, pobierania przez yt-dlp/FFmpeg
i przenoszenia gotowych plików na pendrive wskazany przez Android Storage Access Framework.

## Cloud build

Każdy push do `main` uruchamia:

**Actions → Build Android APK**

Po udanym buildzie pobierz artefakt:

`MJakMiloscUSB-APK`

W środku:

`MJakMiloscUSB-debug.apk`

## Wersja 0.2

- yt-dlp + FFmpeg w aplikacji,
- lista linków `numer|URL`,
- kolejka pobierania,
- pasek postępu,
- zatrzymywanie,
- pobieranie jednego odcinka do katalogu roboczego telefonu,
- automatyczne kopiowanie gotowego pliku na USB,
- automatyczne czyszczenie pliku tymczasowego,
- rozpoznawanie już pobranych odcinków,
- odtwarzanie i usuwanie z USB.
