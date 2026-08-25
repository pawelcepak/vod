# VOD USB — test na telefonie przed zatwierdzeniem do main

## Cel

Nowa wersja NIE trafia od razu do `main`.

Przebieg:

patch z ChatGPT
→ lokalne repo w Termux
→ automatyczna gałąź `test/mobile-...`
→ push tylko tej gałęzi
→ GitHub Actions buduje testowe APK
→ instalacja i test na telefonie
→ AKCEPTUJ albo ODRZUĆ
→ dopiero po akceptacji merge do `main`

## Test nowego patcha

```bash
cd ~/vod
./scripts/mobile_test_patch.sh \
"/storage/emulated/0/Download/VOD_USB_Android_X.Y.Z_patch.zip" \
"Test VOD X.Y.Z"
```

GitHub zbuduje APK z gałęzi `test/mobile-...`.
`main` pozostaje bez zmian.

## Po udanym teście

```bash
cd ~/vod
./scripts/mobile_accept_test.sh
```

Skrypt:
- pobierze aktualny main,
- scali przetestowaną gałąź,
- wyśle main,
- usunie gałąź testową.

## Jeśli aplikacja nie działa

```bash
cd ~/vod
./scripts/mobile_reject_test.sh
```

Main pozostanie bez zmian.
