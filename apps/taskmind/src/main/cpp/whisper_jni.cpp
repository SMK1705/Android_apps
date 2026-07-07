// JNI bridge for TaskMind's Whisper second pass (#207): runs whisper.cpp on decoded PCM.
#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "whisper_jni"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_rajasudhan_taskmind_data_source_transcription_NativeWhisperEngine_nativeTranscribe(
    JNIEnv* env, jobject /*thiz*/, jfloatArray samples, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return nullptr;

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU inference on-device
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGW("whisper_init_from_file failed");
        return nullptr;
    }

    const jsize n = env->GetArrayLength(samples);
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) {  // OOM pinning the array — don't hand whisper_full a null buffer.
        whisper_free(ctx);
        return nullptr;
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;
    wparams.translate = false;
    wparams.language = nullptr;  // auto-detect — the audio is code-switched (Hindi/Tamil/English)
    wparams.n_threads = 4;
    wparams.no_timestamps = true;

    std::string result;
    if (whisper_full(ctx, wparams, data, static_cast<int>(n)) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; ++i) {
            const char* text = whisper_full_get_segment_text(ctx, i);
            if (text != nullptr) result += text;
        }
    } else {
        LOGW("whisper_full failed");
    }

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);  // read-only, don't copy back
    whisper_free(ctx);

    if (result.empty()) return nullptr;
    return env->NewStringUTF(result.c_str());
}
