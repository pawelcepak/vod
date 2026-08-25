# VOD USB — rozwój projektu z telefonu

Od wersji 0.4.1 projekt można aktualizować z Androida bez PC i laptopa.
Telefon nie kompiluje APK lokalnie. Telefon edytuje/synchronizuje repo,
a ciężką kompilację wykonuje GitHub Actions.

## Jednorazowa konfiguracja Termux

W Termux:

```bash
pkg update
pkg install git unzip gh
termux-setup-storage
gh auth login
gh auth setup-git
```

Następnie:

```bash
cd ~
git clone https://github.com/TWOJ_LOGIN/vod.git
cd vod
chmod +x scripts/mobile_*.sh
```

Jeżeli repo jest już sklonowane, nie klonuj go drugi raz.

## Zwykła aktualizacja projektu z patcha

1. Pobierz patch ZIP z ChatGPT do folderu Pobrane/Download telefonu.
2. W Termux:

```bash
cd ~/vod
./scripts/mobile_sync.sh
./scripts/mobile_apply_patch.sh "/storage/emulated/0/Download/NAZWA_PATCHA.zip"
./scripts/mobile_publish.sh "Update VOD to x.x.x"
```

3. GitHub Actions rozpocznie build automatycznie.
4. Po zielonym buildzie pobierz artefakt APK na telefon.
5. Otwórz APK i wybierz `Aktualizuj`.

## Sprawdzenie stanu

```bash
cd ~/vod
./scripts/mobile_status.sh
```

## Ważne

- Stały keystore NIE musi znajdować się na telefonie.
- Sekrety podpisu są w GitHub Secrets.
- Nie kopiuj `vod-release.jks` do repozytorium.
- Do budowania kolejnych APK wystarczy push do `main`.
