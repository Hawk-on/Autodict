# Autodict

Ein **Android native-app** (Kotlin + Jetpack Compose) som tek opp tale og lagar ei
**taledagbok** med **transkripsjon på eininga (offline)** og god norsk-støtte.

Kvar oppføring lagrast som ei **menneskeleseleg Markdown-fil** saman med lydfila, i ei
**mappe du sjølv vel** – så dagboka er enkel å flytte mellom system, ta vare på over tid og
synkronisere via t.d. Dropbox, Google Drive eller Syncthing.

> Status: tidleg utvikling. Sjå milepælane under for kva som er på plass.

## Funksjonar (mål)

- 🎙️ **Opptak** av tale (16 kHz mono), med opptak i bakgrunnen.
- 🧠 **Offline transkripsjon** med [whisper.cpp](https://github.com/ggml-org/whisper.cpp) og
  **NB-Whisper** frå Nasjonalbiblioteket – finjustert på norsk (bokmål + nynorsk).
- 📄 **Markdown per oppføring** med YAML-frontmatter (metadata) + lydfil ved sida.
- 📁 **Brukarvald lagringsmappe** via Storage Access Framework (SAF) – peik den mot ei
  sky-synka mappe etter eige ønske.
- 🗓️ **Kalender:** opprett hendingar frå det du seier (opnar kalender-appen ferdig utfylt).
- ✅ **Google Tasks:** opprett gjeremål automatisk; **del-til-Keep** for notat (eitt trykk).
- 🤖 **Assistent-uttrekk:** finn avtalar/gjeremål i transkriptet – tre nivå: regelbasert
  offline, lokal LLM (Borealis offline), eller Claude API (opt-in) for smartast tolking.
- 🧩 **Lokal LLM-hjerne (offline):** ein liten norsk modell (NB-Llama-3.2 eller Borealis-open
  via llama.cpp) som driv fleire funksjonar – uttrekk, oppreinsking, auto-tittel, tags,
  oppsummering.
- 🔊 **Opplesing (TTS):** les opp oppføringar/oppsummeringar offline; eksperimentelt med di
  eiga stemme (on-device stemmekloning).

## Datalagring og format

Dagboka *er* berre filer – det finst inga skjult database. Strukturen er:

```
<vald mappe>/
  2026/
    2026-06/
      2026-06-02T14-03-12-eit-kort-notat.md
      2026-06-02T14-03-12-eit-kort-notat.wav   (eller .opus)
```

Kvar `.md`-fil har YAML-frontmatter med all metadata:

```markdown
---
id: 2026-06-02T14-03-12
created: 2026-06-02T14:03:12+02:00
updated: 2026-06-02T14:05:00+02:00
title: Eit kort notat
audio: 2026-06-02T14-03-12-eit-kort-notat.wav
duration_seconds: 42
language: no
transcribed: true
model: nb-whisper-small-q5_0
tags: []
---

Transkribert eller manuelt skriven tekst her.

## Handlingspunkt
- [ ] Ringe tannlegen i morgon kl 15  (→ kalender)
```

**Kjerneprinsipp: filene er databasen.** All metadata ligg i frontmatter, så appen kan byggje
opp att den interne indeksen frå mappa åleine. Det gjer dagboka robust mot reinstall og
portabel på tvers av system.

## Byggje

> **Krav:** JDK 17+, Android SDK (compileSdk 35), og nettilgang til Google sitt Maven-repo
> (`maven.google.com`) for AndroidX/Compose og Android Gradle Plugin.

```bash
./gradlew :app:assembleDebug      # byggjer debug-APK
./gradlew test                    # unit-testar
./gradlew lint                    # statisk analyse
```

APK-en hamnar i `app/build/outputs/apk/debug/`. Sideload på telefon for testing (mikrofon,
lyd og transkripsjon kan ikkje testast i emulator utan vidare).

## Teknologi

- Kotlin + Jetpack Compose, Material 3
- minSdk 26, targetSdk 35
- whisper.cpp (NDK/JNI) + NB-Whisper GGML-modellar (M4)
- Storage Access Framework + DocumentFile
- DataStore, kotlinx.serialization, coroutines

## Lisensar og attribusjon

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) – MIT.
- **NB-Whisper** av Nasjonalbiblioteket (NbAiLab). Sjå modellkortet på Hugging Face for
  nøyaktige lisensvilkår og attribusjonskrav før distribusjon.

## Status / milepælar

- [x] **M0** – Prosjektskjelett + dokumentasjon
- [ ] **M1** – SAF-lagring
- [ ] **M2** – MVP: opptak + manuell tekst + lagring
- [ ] **M3** – Lokal indeks + Opus-arkiv
- [ ] **M4** – Offline transkripsjon (whisper.cpp + NB-Whisper)
- [ ] **M5** – Handlingsuttrekk (offline, regelbasert) + kalender
- [ ] **M6** – Google Tasks + del-til-Keep
- [ ] **M7** – Claude API (opt-in)
- [ ] **M8** – Lokal LLM-hjerne (NB-Llama/Borealis + llama.cpp): assistent + sammendrag dag/veke/månad
- [ ] **M9** – Opplesing / TTS (Android system-TTS for norsk)
- [ ] **M10** – Hurtigopptak-widget + polering
- [ ] **M11** – Forteljing & bok-eksport (rettleiande spørsmål, forteljings-modus, PDF/EPUB)
