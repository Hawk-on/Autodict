#!/usr/bin/env bash
# SessionStart-hook for Autodict.
# Mål: gjere økta klar til å byggje Android-appen når miljøet tillèt det, og elles seie tydeleg
# frå om kva som manglar. Hooken feilar ALDRI økta (avsluttar alltid med exit 0).

set +e

echo "── Autodict: oppsett av økt ──"

# 1) JDK
if command -v java >/dev/null 2>&1; then
  echo "JDK: $(java -version 2>&1 | head -1)"
else
  echo "⚠️  JDK ikkje funne (treng 17+)."
fi

# 2) Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
  echo "Android SDK: $SDK"
else
  echo "⚠️  Android SDK ikkje funne (ANDROID_HOME/ANDROID_SDK_ROOT er ikkje sett)."
  echo "    Bygg krev SDK med compileSdk 35. Installer på utviklarmaskin/CI."
fi

# 3) Tilgang til Google sitt Maven-repo (AGP/AndroidX/Compose)
CODE=$(curl -so /dev/null -m 8 -w "%{http_code}" https://maven.google.com/ 2>/dev/null)
if [ "$CODE" = "200" ]; then
  echo "Google Maven: tilgjengeleg (HTTP 200)."
else
  echo "⚠️  Google Maven (maven.google.com) gir HTTP ${CODE:-?} – truleg blokkert av nettpolicy."
  echo "    Då kan AGP/AndroidX/Compose ikkje lastast ned, og './gradlew' kan ikkje byggje her."
  echo "    Skriv/rett kode her; bygg og verifiser på maskin/CI med Google Maven-tilgang."
fi

echo "── ferdig ──"
exit 0
