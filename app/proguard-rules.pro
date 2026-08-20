# R8-reglar for release-bygget.
#
# Utan minifisering blir APK-en ~52 MB, mest fordi `material-icons-extended` legg
# tusenvis av ikon inn som Kotlin-kode. R8 fjernar det vi ikkje brukar.

# --- JNI (whisper.cpp) ---
# Dei native funksjonsnamna i whisper_jni.cpp er bygde av pakke + klasse + metode
# (Java_com_autodict_data_transcribe_WhisperJni_nativeInit). Renamar R8 klassa,
# finn ikkje JNI-en metodane, og appen krasjar først når du transkriberer.
-keep class com.autodict.data.transcribe.WhisperJni { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- kotlinx.serialization ---
# Serialiserbare klassar treng generert serializer intakt (indeks-cachen).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.autodict.** {
    *** Companion;
}
-keepclasseswithmembers class com.autodict.** {
    kotlinx.serialization.KSerializer serializer(...);
}
