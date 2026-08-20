# Opne work items

Levande liste over kjende feil/oppgåver som treng vidare arbeid. Skriven slik at ein annan
Claude/Copilot-økt (eller menneske) kan plukke opp arbeidet utan tidlegare kontekst – les
`CLAUDE.md` først for prosjektoversikt og kjerneprinsipp.

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

## 2. Målform (språk) er alltid nynorsk, uavhengig av valet i Innstillingar

**Rapportert av brukar:** transkribert tekst kjem alltid ut på nynorsk, sjølv om "Bokmål" er
vald i `Innstillingar → Transkripsjon`.

**Status:** Ikkje løyst. Ingen kodefeil funnen ved gjennomgang – språkvalet ser ut til å bli
lese/skrive/sendt korrekt gjennom heile kjeda. `WhisperTranscriber` loggar no kva målform som
faktisk går til whisper (`autodict-whisper`-taggen), og sjølvtesten viser kva som er vald – det
avgjer om feilen er i appen eller i modellen sin oppførsel.

**Viktig datapunkt frå tidlegare i prosjektet:** før målform-valet fanst (v0.2.0, `language`
hardkoda til `"no"`, **medium**-modellen) rapporterte brukaren at striladialekt vart
transkribert til **bokmål**. Same språkkode gir altså bokmål før og nynorsk no. Det peikar bort
frå hypotese 2 (at modellen alltid normaliserer til nynorsk) og mot noko som har endra seg
sidan: modellstorleik (**small** er standard no, medium vart testa då), eller `entry.language`
som blir lese frå frontmatter i staden for innstillinga.

**Merk òg:** detaljskjermen brukar med vilje `entry.language` (oppføringa si lagra målform),
ikkje innstillinga. Endrar du innstillinga og re-transkriberer ei **gammal** oppføring, får du
framleis den gamle målforma. Bruk «Som bokmål»-knappen for å tvinge den andre. Dette er
forventa, men lett å oppfatte som ein bug.

**Filer undersøkt (ingen tydeleg feil funnen):**
- `app/src/main/java/com/autodict/data/transcribe/TargetLanguage.kt` – `"no"` (Bokmål) vs.
  `"nn"` (Nynorsk) er distinkte kodar, korrekt mappa
- `app/src/main/java/com/autodict/ui/settings/SettingsViewModel.kt` /
  `SettingsScreen.kt` – `selectLanguage()`/`AppSettings.setTranscriptionLanguage()`
- `app/src/main/java/com/autodict/ui/detail/EntryDetailViewModel.kt` – `target.code` blir sendt
  til `transcriber.transcribe(..., target.code)`
- `app/src/main/cpp/whisper_jni.cpp`/`WhisperJni.kt` – språkkoden blir vidaresendt til
  whisper.cpp

**Stadfesta via whisper.cpp-kjeldekode (v1.9.1, pinna versjon i `CMakeLists.txt`):**
`"no"` = Norwegian/Bokmål (id 29) og `"nn"` = Nynorsk (id 83) er begge gyldige, distinkte
oppføringar i `g_lang`-tabellen i whisper.cpp. Dette utelukkar at whisper.cpp sjølv slår
saman/forvekslar dei to språkkodane – feilen må liggje andre stader (t.d. faktisk
JNI-kallet, ein defaultverdi som overstyrer valet, eller NB-Whisper-modellen sjølv som
kanskje normaliserer alt mot nynorsk uavhengig av `language`-parameteren).

**Hypotesar å sjekke:**
1. **Feil parameter faktisk sendt til whisper.cpp** – legg til logging i `WhisperJni.kt`/
   `whisper_jni.cpp` som skriv ut kva språkkode som faktisk blir motteke native-sida, og
   samanlikn med kva UI viser at er vald.
2. **NB-Whisper-modellen normaliserer alltid til nynorsk** – ifølgje NB-Whisper sin eigen
   dokumentasjon (https://github.com/NbAiLab/nb-whisper) kan modellen vere trena til å
   normalisere dialekttale til éin bestemt målform uavhengig av `language`-token, eller
   `language`-parameteren kan ha ei anna rolle enn forventa for denne spesifikke modellen
   (t.d. kun brukt for språkdeteksjon, ikkje utskriftsmålform). Verd å lese README/paper til
   NB-Whisper nøye for å avklare kva `language: "no"` faktisk styrer for denne modellen.
3. **Standardverdi (`TargetLanguage.DEFAULT`) overstyrer lagra val** – sjekk om
   `TargetLanguage.fromCode(entry.language)` i `EntryDetailViewModel.transcribe()` brukar
   riktig kjelde (oppføringa sitt lagra `language`-felt) vs. settingsvalet, særleg for
   NYE oppføringar der `entry.language` kanskje ikkje er sett enno.

**Kva som trengst for å gå vidare:**
- Test: set målform til "Bokmål" i Innstillingar, ta opp ei ny oppføring, transkriber, og sjekk
  kva som faktisk kjem ut. Samanlikn med logcat-verdien for språkkode sendt til JNI/whisper.cpp.
- Vurder å teste med ein annan (ikkje-NB-Whisper) Whisper-modell for å isolere om problemet er
  spesifikt for NB-Whisper-modellen sin oppførsel, eller ein generell bug i appen.

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
