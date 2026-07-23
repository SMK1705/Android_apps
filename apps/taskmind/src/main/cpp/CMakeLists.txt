# Native build for TaskMind's Whisper second pass (#207) — the app's only native module.
# Builds a small JNI wrapper (whisper_jni.cpp) linked against whisper.cpp, which is fetched at configure
# time pinned to a release tag (kept out of the repo — only this CMake + the JNI wrapper are vendored).
cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Static-link whisper + ggml INTO libwhisper_jni.so so the APK ships one self-contained native lib — AGP
# reliably packages the JNI .so; separate whisper/ggml .so files could be dropped and fail at load time.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
# Cross-compiling for Android: GGML_NATIVE must be OFF or ggml adds -march=native, which the NDK's
# cross-compiler rejects. Trim tests/examples/OpenMP — none are used on-device.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG v1.7.4
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper)

add_library(whisper_jni SHARED whisper_jni.cpp)

# 16 KB page-size support: align the .so's ELF LOAD segments to 16 KB so it loads on 16 KB-page devices
# (Android 15+). NDK r28 defaults to this; r27 (used here) still defaults to 4 KB, so set it explicitly.
target_link_options(whisper_jni PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(whisper_jni whisper ${log-lib})
