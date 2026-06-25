# Tredjeparts-lisensar og attribusjon

Levande sjekkliste. **Fyll ut / verifiser når komponenten faktisk blir lagt til** (milepæl i
parentes). Mål: oppfylle attribusjonskrav og halde appen fri for utilsikta copyleft.

> Status: planlagt. Ingen av desse er integrert i koden enno (per M0).

## Bibliotek / motorar

| Komponent | Bruk | Lisens | Copyleft | Plikter |
|---|---|---|---|---|
| Kotlin, Jetpack Compose, AndroidX, Gradle | App-rammeverk | Apache 2.0 | Nei | Behald lisensvarsel |
| whisper.cpp (ggml-org) | Transkripsjon (M4) | MIT | Nei | Behald MIT-varsel |
| llama.cpp (ggml-org) | Lokal LLM (M8) | MIT | Nei | Behald MIT-varsel |
| sherpa-onnx (k2-fsa) | Offline TTS-motor (M9) | Apache 2.0 | Nei | Behald varsel |
| OkHttp / Retrofit (om brukt) | Nett (Tasks/Claude) | Apache 2.0 | Nei | Behald varsel |

## Modellar

| Modell | Bruk | Lisens | Merknad / attribusjon |
|---|---|---|---|
| **NB-Whisper** "main" (NbAiLab / Nasjonalbiblioteket) | Transkripsjon (M4) | Apache 2.0 | Standard `small q5_0`. Krediter Nasjonalbiblioteket. Attribusjon etter åndsverklova gjeld for nedlasting i Noreg. |
| **NB-Llama-3.2-3B/1B-Instruct** (NbAiLab) | Lokal LLM (M8), førsteval | Llama 3.2 Community License | Reinaste lisens. Krediter NB + følg Llama-vilkåra (namnekrav, 700M MAU-klausul). |
| **Borealis – open** (Nasjonalbiblioteket) | Lokal LLM (M8), alternativ | Gemma-lisens | Sterkast norsk, men preview/ikkje safety-aligna. Følg Gemma "prohibited use"-policy. Krediter NB. |
| Borealis – full (Nasjonalbiblioteket) | (alternativ) | NB-lisens (Apache 2.0 + restriksjonar) | Ikkje gjenskape treningsdata; ikkje tenester for tilgang til lisensiert presse. |
| **Android system-TTS** (Google, på eininga) | Opplesing (M9), **standard** | OS-API (ingen bundling) | **Lisens-reint** – beste norsk offline. Ingen attribusjon/GPL. |
| Piper-stemme (norsk) | Opplesing (M9), valfritt | GPL (espeak-ng) + **pr. stemme** (CC-BY/CC0/CC-BY-SA) | ⚠️ GPL via espeak-ng – isoler i nedlastbar modul. Svak norsk kvalitet. Verifiser stemmelisens. |
| NeuTTS Air (Neuphonic) | Personleg stemme (utsett) | Apache 2.0 (+ espeak GPL) | ⚠️ Støttar ikkje norsk i 2026. Utsett til norsk finst. |

## ⚠️ Copyleft å passe på (gjeld berre M9 / offline-TTS)

- **espeak-ng = GPLv3.** Bundla av Piper og brukt som frontend i sherpa-onnx for fonemisering.
  Lenkjar vi det inn, smittar GPL over på appen. **Unngå** ved å bruke system-TTS eller ein
  espeak-fri modell/G2P (t.d. Kokoro / ONNX-basert fonemisering). GPL dekkjer ikkje lyd-utdata.
- **piper1-gpl** (fork) er GPL; opphavleg rhasspy/piper er MIT — vel MIT/Apache-vegen.
- **CC-BY-SA-stemmer** er innhalds-copyleft på modellen/avleidingar.

## Korleis vise attribusjon i appen (gjer i M10 / polering)

- Legg ein "Om / Opne kjeldelisensar"-skjerm i innstillingar med lisenstekstane over.
  Vurder `com.mikepenz:aboutlibraries` (Apache 2.0) for auto-generert OSS-liste.
- Ta med modell-attribusjon (NB-Whisper, Borealis) eksplisitt — ikkje berre bibliotek.
