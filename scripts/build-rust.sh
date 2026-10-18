#!/usr/bin/env bash
# Compila o núcleo Rust para Android e copia as .so para app/src/main/jniLibs.
# Pré-requisitos: rustup (stable >= 1.95), cargo-ndk e o Android NDK (ANDROID_NDK_HOME).
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#   cargo install cargo-ndk --locked
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs"
PROFILE="${1:-release}"

# Páginas de 16 KB (exigência do Google Play para targetSdk 35+).
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"

cd "$ROOT/rust"
rm -rf "$OUT"
if [ "$PROFILE" = "release" ]; then
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "$OUT" build --release
else
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "$OUT" build
fi
echo "Bibliotecas geradas em $OUT:"
find "$OUT" -name '*.so' -print
