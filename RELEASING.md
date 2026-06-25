# Slepp (releases)

Autodict blir distribuert som **signert APK via GitHub Releases**. Ein release blir bygd av
CI (`.github/workflows/release.yml`) når du pushar ein tag `vX.Y.Z`. APK-en blir:

1. **signert** med ein release-keystore (Android v2/v3-scheme) – tryggleiksgrensa Android sjølv
   verifiserer ved install/oppdatering,
2. **checksummert** (`SHA256SUMS`),
3. **attestert** med build-proveniens (SLSA, keyless via GitHub OIDC + sigstore/Rekor).

> **Nøkkelen er kritisk.** Same release-nøkkel *må* brukast på alle framtidige oppdateringar.
> Mistar du han, kan ingen oppdatere appen sin utan å avinstallere først. Ta backup, og hald
> han hemmeleg – aldri i repo (CLAUDE.md-prinsipp 6).

---

## Eingongs-oppsett

### 1. Lag ein release-keystore (lokalt, éin gong)

```bash
keytool -genkeypair -v \
  -keystore autodict-release.keystore \
  -alias autodict \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

Hugs passordet og aliaset (`autodict` over). Legg `autodict-release.keystore` ein trygg stad
(passordforvaltar / kryptert backup) – **ikkje** i repoet.

### 2. Legg keystore + passord som GitHub-secrets

`Settings → Secrets and variables → Actions → New repository secret`:

| Secret                      | Verdi                                                        |
| --------------------------- | ----------------------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64`   | `base64 -w0 autodict-release.keystore` (heile fila base64)  |
| `RELEASE_KEYSTORE_PASSWORD` | store-passordet frå steg 1                                  |
| `RELEASE_KEY_ALIAS`         | `autodict` (eller det aliaset du valde)                     |
| `RELEASE_KEY_PASSWORD`      | key-passordet (likt store-passordet med PKCS12 over)        |

```bash
# macOS/Linux – kopier base64 til utklippstavla:
base64 -w0 autodict-release.keystore   # Linux
base64 autodict-release.keystore       # macOS (utan -w0)
```

---

## Lage ein release

Taggen er einaste sanningskjelde for versjon – du redigerer **ikkje** `build.gradle.kts`.
CI utleier automatisk frå `vMAJOR.MINOR.PATCH`:

- `versionName` = taggen utan `v` (t.d. `v0.2.0` → `0.2.0`),
- `versionCode` = `major*10000 + minor*100 + patch` (t.d. `0.2.0` → `200`). Monotont aukande,
  så sideload-oppdateringar går alltid igjennom. (Krev `minor` og `patch` < 100.)

1. Sørg for at endringane er på `main`.
2. Tagg og push frå `main`:

   ```bash
   git checkout main && git pull
   git tag -a v0.2.0 -m "Autodict 0.2.0"
   git push origin v0.2.0
   ```

3. `Release`-workflowen utleier versjon, byggjer, signerer, attesterer og publiserer. Sjekk
   `Actions`-fana → grønt → `Releases` har APK + `SHA256SUMS`.

> Taggen *må* vere `vMAJOR.MINOR.PATCH` – workflowen feiler tidleg på andre former.

---

## Verifisering (for den som lastar ned)

**Checksum:**

```bash
sha256sum -c SHA256SUMS
```

**APK-signatur:**

```bash
apksigner verify --print-certs autodict-v0.2.0.apk
```

**Build-proveniens** (at APK-en kom frå dette repoet sin CI):

```bash
gh attestation verify autodict-v0.2.0.apk --repo Hawk-on/Autodict
```

---

## Migrere til Play Store seinare

Det er mogleg utan å miste sideload-brukarane:

- Meld den same release-nøkkelen inn i **Play App Signing** (last opp eksisterande nøkkel), så
  held Play fram med same signatur. Då kan ein sideload-installert app oppdaterast frå Play.
- Alternativt lèt du Play generere ein ny app-signeringsnøkkel – men då vil ikkje gamle
  sideload-installasjonar kunne oppdaterast over Play (ny signatur = ny app for Android).

Sideload-sleppet held fram å fungere uavhengig av dette.
