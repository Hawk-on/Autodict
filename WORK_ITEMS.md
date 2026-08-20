# Opne work items

Levande liste over kjende feil/oppgåver som treng vidare arbeid. Skriven slik at ein annan
Claude/Copilot-økt (eller menneske) kan plukke opp arbeidet utan tidlegare kontekst – les
`CLAUDE.md` først for prosjektoversikt og kjerneprinsipp.

## 1. Transkripsjon fungerer ikkje (verken med eller utan auto-transkripsjon)

**Rapportert av brukar:** transkribering skjer ikkje, korkje ved manuelt trykk på
"Transkriber" eller via "Transkriber automatisk etter opptak"-innstillinga.

**Status:** Ikkje løyst. Grundig kodegjennomgang er gjort utan å finne ein openbar logikkfeil –
sjå detaljar under. Neste steg krev testing på ei ekte eining (emulator har typisk ikkje
mikrofon/lyd), sidan miljøet her manglar Android SDK og kan ikkje køyre appen.

**Filer undersøkt (ingen tydeleg feil funnen):**
- `app/src/main/java/com/autodict/ui/detail/EntryDetailViewModel.kt` – `transcribe()`
- `app/src/main/java/com/autodict/ui/record/RecordViewModel.kt` – auto-transkripsjon etter opptak
- `app/src/main/java/com/autodict/data/transcribe/WhisperTranscriber.kt`
- `app/src/main/java/com/autodict/data/transcribe/TranscriberHolder.kt`
- `app/src/main/java/com/autodict/data/transcribe/ModelDownloader.kt`
- `app/src/main/java/com/autodict/data/audio/AudioLoader.kt`
- `app/src/main/cpp/whisper_jni.cpp` + `WhisperJni.kt` (JNI-bru)

**Hypotesar å sjekke (i prioritert rekkjefølge):**
1. **Modellen er ikkje lasta ned** – `ModelDownloader.isDownloaded()` kan gi feil svar, eller
   nedlastinga kan ha feila stille. Sjekk `Innstillingar → Transkripsjonsmodell` – står det
   "✓ Modell lasta ned", eller kjem det ei feilmelding?
2. **Native lib (`.so`) manglar eller feilar ved lasting** – `WhisperJni` kan kaste
   `UnsatisfiedLinkError` som ikkje blir fanga/vist til brukaren. Sjå `adb logcat` for
   `System.loadLibrary`-feil eller JNI-crashar.
3. **`ui.message`/feilmelding blir sett, men ikkje vist** – tilsvarande buggen som vart fiksa i
   `SettingsScreen.kt` (sjå commit `ec1691b`, "Fiks: Google-konto-melding usynleg i
   Innstillingar"): meldinga kan hamne utanfor synleg område i `EntryDetailScreen.kt`. Sjekk om
   det står noko i `ui.message`-teksten på detaljskjermen (linje ca. 130 i
   `EntryDetailScreen.kt`) når brukaren trykkjer "Transkriber", sjølv om ingenting synleg skjer.
4. **Lydfila kan ikkje dekodast** – `AudioLoader.load()` returnerer `null` for enkelte format
   (t.d. viss Opus-arkivering (M3b) og dekoding ikkje heng saman på denne eininga/Android-
   versjonen). Sjå om meldinga "Klarte ikkje lese lydfila." dukkar opp.

**Kva som trengst for å gå vidare:**
- `adb logcat` frå augeblikket brukaren trykkjer "Transkriber" (helst med
  `adb logcat | grep -i -E "whisper|autodict|AndroidRuntime"`).
- Stadfesting av om det kjem NOKA melding på skjermen (t.d. "Transkriberer …", ei feilmelding,
  eller ingenting i det heile).
- Kva modell er vald i Innstillingar, og om han er nedlasta.

## 2. Målform (språk) er alltid nynorsk, uavhengig av valet i Innstillingar

**Rapportert av brukar:** transkribert tekst kjem alltid ut på nynorsk, sjølv om "Bokmål" er
vald i `Innstillingar → Transkripsjon`.

**Status:** Ikkje løyst. Ingen kodefeil funnen ved gjennomgang – språkvalet ser ut til å bli
lese/skrive/sendt korrekt gjennom heile kjeda.

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
