#!/bin/sh
# Build the ove-android JNI bridge for Android aarch64 and place
# liboveandroid.so into the app's jniLibs.
#
# Environment:
#   NDK      NDK root          (default /home/z/android/android-ndk-r27c)
#   FFPREFIX Android FFmpeg prefix (default /home/z/android/ffmpega64)
#   BRIDGE   bridge dir        (default: sibling of this script)
set -e
NDK="${NDK:-/home/z/android/android-ndk-r27c}"
FFPREFIX="${FFPREFIX:-/home/z/android/ffmpega64}"
BRIDGE="$(cd "$(dirname "$0")/.." && pwd)/bridge"
API=24
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

# 1. engine checkout at the pinned commit (read-only dependency)
sh "$BRIDGE/fetch-engine.sh"

# 2. toolchain
rustup target add aarch64-linux-android

# 3. build (ffmpeg-sys-next discovers the Android FFmpeg prefix)
export LIBCLANG_PATH="${LIBCLANG_PATH:-$TC/../lib/clang}"
export AR_aarch64_linux_android="$TC/llvm-ar"
export CC_aarch64_linux_android="$TC/aarch64-linux-android${API}-clang"
export CXX_aarch64_linux_android="$TC/aarch64-linux-android${API}-clang++"
export RANLIB_aarch64_linux_android="$TC/llvm-ranlib"
export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot -I$FFPREFIX/include"
export FFMPEG_DIR="$FFPREFIX"
export PKG_CONFIG_ALLOW_CROSS=1
export PKG_CONFIG_PATH="$FFPREFIX/lib/pkgconfig"
export RUSTFLAGS="-L $FFPREFIX/lib -C linker=$TC/aarch64-linux-android${API}-clang"

cd "$BRIDGE"
cargo build --release --target aarch64-linux-android

# 4. package into the app
SO="target/aarch64-linux-android/release/liboveandroid.so"
test -f "$SO"
mkdir -p ../app/src/main/jniLibs/arm64-v8a
cp "$SO" ../app/src/main/jniLibs/arm64-v8a/
"$TC/llvm-strip" ../app/src/main/jniLibs/arm64-v8a/liboveandroid.so
echo BRIDGE-ANDROID-OK
