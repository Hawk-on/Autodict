package com.autodict.data.transcribe

/**
 * NB-Whisper GGML-modellar (NbAiLabBeta / Nasjonalbiblioteket, Apache-2.0).
 *
 * Vi brukar **q5_0**-kvantiseringa som standard – planen sitt «vendepunkt»: god norsk
 * kvalitet og lita fil. Filnamnet er likt i alle storleik-repo (`ggml-model-q5_0.bin`).
 *
 * Storleikane under er omtrentlege (UI-hint), ikkje funksjonelle.
 */
enum class WhisperModel(
    val id: String,
    val displayName: String,
    val repo: String,
    val approxMb: Int,
) {
    BASE("base", "Base (rask)", "NbAiLabBeta/nb-whisper-base", 57),
    SMALL("small", "Small (tilrådd)", "NbAiLabBeta/nb-whisper-small", 181),
    MEDIUM("medium", "Medium (beste)", "NbAiLabBeta/nb-whisper-medium", 514);

    /** Nedlastings-URL for q5_0-GGML-fila på Hugging Face. */
    val downloadUrl: String
        get() = "https://huggingface.co/$repo/resolve/main/$GGML_FILE"

    /** Lokalt filnamn (unikt per storleik) i appen sin modellmappe. */
    val localFileName: String
        get() = "nb-whisper-$id-q5_0.bin"

    /** Modell-identifikator som skrivast i frontmatter (`model:`). */
    val frontmatterId: String
        get() = "nb-whisper-$id-q5_0"

    companion object {
        const val GGML_FILE = "ggml-model-q5_0.bin"
        val DEFAULT = SMALL
        fun fromId(id: String?): WhisperModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
