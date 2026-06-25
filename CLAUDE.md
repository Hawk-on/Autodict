# CLAUDE.md

Rettleiing for Claude Code (og andre) når det arbeidast i dette repoet.

## Kva er Autodict

Android native-app (Kotlin + Jetpack Compose) som tek opp tale, transkriberer **offline**
(whisper.cpp + NB-Whisper) og lagrar kvar dagbok-oppføring som **Markdown + lydfil** i ei
**brukarvald mappe** (Storage Access Framework). I tillegg: kalender-hendingar, Google Tasks,
del-til-Keep, og regelbasert/Claude-basert uttrekk av handlingspunkt.

Detaljert plan: `/root/.claude/plans/eg-vil-lage-ein-witty-sonnet.md` (om tilgjengeleg).

## Kjerneprinsipp (IKKJE bryt desse)

1. **Filene er databasen.** All metadata ligg i YAML-frontmatter i `.md`-filene. Den lokale
   indeksen er reint cache og må alltid kunne byggjast på nytt frå mappa. Ikkje innfør ei
   skjult datakjelde som ikkje speglar filene.
2. **Lagre URI-ar, ikkje filstiar.** SAF gir URI-ar; rekn ut `DocumentFile` på nytt frå den
   persisterte tree-URI-en kvar økt.
3. **Atomisk skriving.** Skriv til temp + rename; hald aldri ei fil open under sync. Hopp
   pent over "conflicted copy"-filer (Syncthing/Dropbox).
4. **Offline-først.** Transkripsjon og handlingsuttrekk skal fungere utan nett. Nett-ting
   (Google Tasks, Claude API, modell-nedlasting) er opt-in og må feile pent offline.
5. **Ingenting ut utan stadfesting.** Kalender/Tasks/Keep-handlingar skjer berre etter at
   brukaren har stadfesta dei foreslåtte handlingspunkta.
6. **Ingen hemmelegheiter i repo.** API-nøklar (Claude) lagrast trygt på eininga
   (EncryptedSharedPreferences/DataStore), aldri commitast.
7. **Attribusjon og lisensar.** `THIRD_PARTY_NOTICES.md` er ei levande sjekkliste – oppdater
   den når ein modell/bibliotek faktisk blir lagt til. Unngå copyleft (særleg `espeak-ng`
   GPL i offline-TTS); vel permissive alternativ. Krediter modell-kjeldene (NB-Whisper,
   Borealis frå Nasjonalbiblioteket).

## Prosjektstruktur

```
app/src/main/java/com/autodict/
  AutodictApp.kt, MainActivity.kt
  data/storage/      # SAF: SafRepository, tree-URI i DataStore
  data/audio/        # AudioRecorder (AudioRecord 16 kHz mono), WavWriter
  data/transcribe/   # Transcriber-interface, WhisperEngine (JNI), ModelDownloader
  data/markdown/     # FrontmatterSerializer, EntryFileMapper
  data/index/        # lokal cache/indeks
  data/actions/      # ActionExtractor (RuleBased + LLM + Claude), NorwegianDateTimeParser
  data/llm/          # LlmEngine (llama.cpp/GGUF JNI), delt motor (M8)
  data/tts/          # SpeechSynthesizer (system/Piper/personleg stemme) (M9)
  data/integration/  # CalendarIntentLauncher, GoogleTasksClient, ShareToKeep
  domain/            # model/, repository/, usecase/
  ui/                # theme, navigation, record, list, detail, settings, actions
app/src/main/cpp/    # whisper.cpp + (seinare) llama.cpp + JNI
```

**Sentrale interface** (legg implementasjonar bak desse):
- `Transcriber` – no-op/manuell i MVP, `WhisperTranscriber` i M4.
- `ActionExtractor` – `RuleBasedExtractor` (offline) i M5, `LlmActionExtractor` (Borealis) i M8,
  `ClaudeActionExtractor` i M7. Tre nivå: reglar → lokal LLM → Claude (opt-in).
- `LlmEngine` – delt llama.cpp/GGUF-motor (Borealis) for uttrekk, oppreinsking, tittel,
  tags, oppsummering. Éin singleton, sekvensiell kø (M8).
- `SpeechSynthesizer` – opplesing: system-TTS / Piper (offline) / personleg stemme (M9).

## Kommandoar

```bash
./gradlew :app:assembleDebug   # byggje debug-APK
./gradlew test                 # JVM unit-testar
./gradlew lint                 # Android lint
```

Det finst òg ein prosjekt-skill (`.claude/skills/build-app`) som køyrer desse.

## Miljømerknad (VIKTIG for web-/remote-økter)

Dette remote-miljøet **kan ikkje byggje appen**:
- Android SDK er ikkje installert.
- Nettpolicyen blokkerer Google sitt Maven-repo (`maven.google.com`, `dl.google.com` → 403),
  så AGP/AndroidX/Compose kan ikkje lastast ned. Maven Central, GitHub og Gradle-tenester er
  opne.

Difor: skriv og rett kode her, men **bygg og verifisering skjer på ei maskin/CI med Android
SDK og tilgang til Google Maven** (eller etter at nettpolicyen er endra). SessionStart-hooken
(`.claude/settings.json`) prøver å setje opp SDK når policyen tillèt det.

## Versjonar

`gradle/libs.versions.toml` er einaste kjelde til sanning. Verdiane no er ein koherent
baseline; planen siktar mot nyaste (Compose BOM 2026.05.01, AGP 8.9+). Bump og verifiser på ei
maskin med Google Maven-tilgang.

## Konvensjonar

- Norsk (nynorsk) i UI-tekst, kommentarar og commit-meldingar er heilt greitt.
- Coroutines for I/O (`Dispatchers.IO`) og CPU-tungt arbeid (`Dispatchers.Default`).
- Hald `Application`/Activity tynne; logikk i `data`/`domain`.

## Git

- Utvikling på branch `claude/android-voice-diary-app-iwxZP`.
- Commit per milepæl med klare meldingar. Ikkje opprett PR utan at brukaren ber om det.
