VOD USB — Android 0.3.1
=======================

Cel tej wersji:
- stały podpis APK,
- kolejne wersje instalowane jako normalna aktualizacja,
- brak utraty danych aplikacji przy kolejnych aktualizacjach.

WAŻNE
-----
0.3.1 wprowadza nowy stały klucz release.
Ponieważ aktualnie zainstalowane 0.3.0 było podpisane innym kluczem debug,
PRZEJŚCIE NA 0.3.1 BĘDZIE WYMAGAŁO OSTATNIEGO ODINSTALOWANIA 0.3.0.

Od 0.3.1 w górę zachowaj ten sam keystore i GitHub Secrets.
Wtedy 0.3.2, 0.4.0 itd. będą instalowane przez "Aktualizuj".

GitHub Secrets:
- VOD_KEYSTORE_BASE64
- VOD_KEYSTORE_PASSWORD
- VOD_KEY_ALIAS
- VOD_KEY_PASSWORD

Generowanie klucza na Xubuntu:
./scripts/create_release_keystore.sh

Domyślny alias sugerowany:
vod

Po utworzeniu vod-release.jks:
base64 -w 0 vod-release.jks > vod-keystore-base64.txt

Zawartość vod-keystore-base64.txt dodaj jako:
VOD_KEYSTORE_BASE64

NIE DODAWAJ:
- vod-release.jks
- vod-keystore-base64.txt
do repozytorium GitHub.

Po dodaniu 4 sekretów wykonaj git push lub ręcznie uruchom workflow.

Artefakt:
VOD-USB-APK
  -> VOD-USB-0.3.1.apk

Funkcjonalność:
- pobieranie w tle,
- powiadomienie z postępem,
- trwała kolejka,
- kopiowanie na USB,
- audio0-Polski dla TVP.
