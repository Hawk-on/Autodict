# Opne work items

Levande liste over kjende feil/oppgåver som treng vidare arbeid. Skriven slik at ein annan
Claude/Copilot-økt (eller menneske) kan plukke opp arbeidet utan tidlegare kontekst – les
`CLAUDE.md` først for prosjektoversikt og kjerneprinsipp.

> **Status: ingen opne feil.** Begge punkta under er løyste (v0.4.0) og står att som
> dokumentasjon – dei har ein felles lærdom som er verdt å hugse: **test alltid transkripsjon
> med release-APK-en frå Releases**, ikkje debug-APK-en frå CI.

## 1. ~~Transkripsjon fungerer ikkje~~ — LØYST (v0.4.0)

**Rapportert av brukar:** transkribering skjer ikkje, korkje ved manuelt trykk på
"Transkriber" eller via "Transkriber automatisk etter opptak"-innstillinga.

**Status: LØYST.** Transkripsjonen fungerer i release-bygget (v0.4.0). Feilen låg ikkje i
app-koden – brukaren hadde testa med **debug-APK-en frå CI**, som skil seg frå release på to
måtar som begge gir nøyaktig dette symptomet:

1. **Native kode bygd med `CMAKE_BUILD_TYPE=Debug`.** AGP gjer dette for debug-varianten, og
   for whisper.cpp/ggml tyder det `-O0`: ingen SIMD, ingen inlining, og aktive `GGML_ASSERT`.
   Inferens som tek sekund i release tek då fleire minutt og ser ut som om appen heng.
2. **Ulik signatur enn release-APK-en.** Ein må avinstallere den andre først, og det slettar
   appdata – inkludert den nedlasta modellen (181–514 MB) og alle innstillingar. Appen står
   då att utan modell.

**Fiksa:** debug-varianten byggjer no native kode med `-DCMAKE_BUILD_TYPE=RelWithDebInfo`
(`app/build.gradle.kts`), så debug-APK-en frå CI er brukande til å teste transkripsjon.

**Lærdom verdt å ta vare på:** ved testing av transkripsjon – bruk **release-APK-en frå
Releases**. Byter du mellom debug og release, må modellen lastast ned på nytt.

**Diagnostikk lagt til undervegs (behald – nyttig framover):**
- `WhisperJni.loadError`/`isAvailable` – `System.loadLibrary` låg i eit `init`-blokk som
  kastar; feila lastinga vart objektet permanent ubrukeleg og kvart kall ga
  `NoClassDefFoundError` med tom melding.
- `WhisperTranscriber` loggar modell, målform (før/etter mapping) og samplingsrate;
  feilmeldingar tek med klassenamnet (`Throwable.message` er null for dei fleste JNI-feil).
- `AudioLoader` loggar kvifor dekodinga feila.
- **Innstillingar → Diagnostikk → "Test transkripsjon"** – sjekkar native bibliotek, modellfil
  og modell-lasting utan `adb`.

Loggtaggar: `autodict-whisper`, `autodict-audio`.

## 2. ~~Målform er alltid nynorsk~~ — LØYST (v0.4.0)

**Rapportert av brukar:** transkribert tekst kom alltid ut på nynorsk, sjølv om "Bokmål" var
vald i `Innstillingar → Transkripsjon`.

**Status: LØYST.** Brukaren stadfestar at målforma no følgjer valet – både nynorsk og bokmål
gir rett resultat, testa med **small** og **medium**.

**Rotårsak: same som bug 1** – testinga skjedde på debug-APK-en frå CI. Ved byte mellom
debug og release må ein avinstallere den andre, og det slettar appdata: både den nedlasta
modellen og **DataStore-innstillingane** (inkludert målform-valet). Ingen kodefeil fanst –
noko som stemmer med at to uavhengige kodegjennomgangar ikkje fann noko.

**Verdt å hugse (ikkje ein bug):** detaljskjermen brukar oppføringa si **lagra** målform
(`entry.language` frå frontmatter), ikkje innstillinga. Endrar du innstillinga og
re-transkriberer ei gammal oppføring, får du framleis den gamle målforma – bruk
«Som bokmål»/«Som nynorsk»-knappen for å byte. Dette er med vilje, men lett å lese som ein bug.

## Kontekst / referansar

- Begge bugane vart rapporterte saman med ein tredje (Google-konto-lenking synte ingenting) –
  den tredje er **fiksa** (manglande `SnackbarHost` i `SettingsScreen.kt`, commit `ec1691b`).
- Google Tasks-integrasjonen er sidan forenkla til rein del-intent (ingen OAuth/konto), sjå
  commit "Fjern OAuth-basert Google Tasks; bruk del-intent i staden" – ikkje relatert til
  bug 1/2, men nemnt for kontekst om nokon undrar seg over kvifor `GoogleTasksClient` er borte.
- Miljøet som denne fila vart skriven i (nettbasert Copilot-økt) **manglar Android SDK og
  emulator/eining**, og kan difor ikkje reprodusere eller feilsøkje desse to bugane vidare utan
  `adb logcat`-utdata og skjermobservasjonar frå brukaren. Sjå `.claude/skills/build-app/SKILL.md`
  for miljøavgrensingane.
