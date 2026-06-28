// JNI-bru til whisper.cpp for Autodict.
//
// M4a koplar berre opp byggjet (CI kompilerer denne mot whisper.cpp). Funksjonane brukar
// reell whisper-API, men UI-laget tek dei ikkje i bruk før M4c.
//
// Funksjonsnamna må matche `com.autodict.data.transcribe.WhisperJni` (package_class_metode).

#include <jni.h>
#include <string>
#include <vector>

#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "autodict-whisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_autodict_data_transcribe_WhisperJni_nativeSystemInfo(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(whisper_print_system_info());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_autodict_data_transcribe_WhisperJni_nativeInit(JNIEnv* env, jobject /* this */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    if (ctx == nullptr) {
        LOGE("klarte ikkje å laste modell: %s", path);
    }
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_autodict_data_transcribe_WhisperJni_nativeTranscribe(
        JNIEnv* env, jobject /* this */, jlong ctxPtr, jfloatArray audio, jstring language) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const jsize n = env->GetArrayLength(audio);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(audio, 0, n, pcm.data());

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    const char* lang = env->GetStringUTFChars(language, nullptr);
    wparams.language = lang;          // "nn" / "nb" – eksplisitt målform
    wparams.translate = false;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;
    wparams.n_threads = 4;

    std::string text;
    if (whisper_full(ctx, wparams, pcm.data(), n) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) {
            text += whisper_full_get_segment_text(ctx, i);
        }
    } else {
        LOGE("whisper_full feila");
    }

    env->ReleaseStringUTFChars(language, lang);
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_autodict_data_transcribe_WhisperJni_nativeFree(JNIEnv* /* env */, jobject /* this */, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}
