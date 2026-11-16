#!/bin/zsh
# Build firmata per Play: chiede la password, ricava la chiave in una cartella temporanea,
# compila l'AAB (e un APK per provarlo), poi cancella la chiave. Su disco non resta niente.
#   signing/release.sh          → app/build/outputs/bundle/release/app-release.aab (+ apk)
set -euo pipefail
cd "${0:A:h}/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
EXPECTED=signing/upload-cert.sha256

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
store=$(openssl rand -hex 16)

read -rs "pw?Password della chiave Sordina: "; echo
if [[ ! -f $EXPECTED ]]; then
  read -rs "pw2?Ripetila (prima volta): "; echo
  [[ "$pw" == "$pw2" ]] || { echo "Non coincidono."; exit 1; }
  unset pw2
fi
echo "Ricavo la chiave (qualche secondo)…"
fp=$(print -r -- "$pw" | "$JAVA_HOME/bin/java" signing/DeriveUploadKey.java "$tmp/upload.p12" "$store")
unset pw

if [[ -f $EXPECTED ]]; then
  [[ "$fp" == "$(<$EXPECTED)" ]] || { echo "Password sbagliata: l'impronta non è quella registrata."; exit 1; }
else
  print -r -- "$fp" > $EXPECTED
  echo "Prima volta: impronta salvata in $EXPECTED (va nel repo, non è segreta)."
fi
echo "Chiave: $fp"

SORDINA_KEYSTORE="$tmp/upload.p12" SORDINA_STORE_PASSWORD="$store" ./gradlew -q bundleRelease assembleRelease
ls -1 app/build/outputs/bundle/release/*.aab app/build/outputs/apk/release/*.apk
