cmake_minimum_required(VERSION 3.22.1)
project(autodict_whisper LANGUAGES C CXX)

# whisper.cpp (MIT, ggml-org) hentast ved CONFIGURE-tid via FetchContent og pinnast til
# ein release. Vi vendorar det ikkje i repoet, og kan ikkje hente det i dette
# sandbox-miljøet (git-/nett-proxyen blokkerer eksterne repo) – sjølve byggjet skjer på
# GitHub-CI eller di eiga maskin, der nettet er ope.
include(FetchContent)

# Slå av alt vi ikkje treng i ein app-kontekst.
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# OpenMP er ikkje tilgjengeleg som standard på Android NDK; ggml brukar pthreads.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Kryss-kompilering til arm64 – ikkje bruk -march=native frå vertsmaskina.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whispercpp
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.1.tar.gz
)
FetchContent_MakeAvailable(whispercpp)

# JNI-bru: libautodict-whisper.so
add_library(autodict-whisper SHARED whisper_jni.cpp)

find_library(log-lib log)

# Lenkjer mot whisper-målet frå FetchContent (drar inn ggml + include-stiar transitivt).
target_link_libraries(autodict-whisper PRIVATE whisper ${log-lib})
