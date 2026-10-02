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
# libclang for bindgen: prefer an explicitly provided path, then the
# NDK's bundled libclang, then deb-extracted llvm-19, then the system llvm.
if [ -z "${LIBCLANG_PATH:-}" ]; then
    if ls "$TC"/../lib64/libclang* >/dev/null 2>&1; then
        export LIBCLANG_PATH="$TC/../lib64"
    elif [ -d "$HOME/my-project/ffmpeg-dev/usr/lib/llvm-19/lib" ]; then
        export LIBCLANG_PATH="$HOME/my-project/ffmpeg-dev/usr/lib/llvm-19/lib"
    else
        for d in /usr/lib/llvm-*/lib; do
            if ls "$d"/libclang.so* >/dev/null 2>&1; then
                export LIBCLANG_PATH="$d"
                break
            fi
        done
    fi
fi
export AR_aarch64_linux_android="$TC/llvm-ar"
export CC_aarch64_linux_android="$TC/aarch64-linux-android${API}-clang"
export CXX_aarch64_linux_android="$TC/aarch64-linux-android${API}-clang++"
export RANLIB_aarch64_linux_android="$TC/llvm-ranlib"
# bindgen: cross-target via --target (lets the HOST libclang supply its own
# builtin headers) + explicit NDK unified-sysroot include paths. This avoids
# version-fragile clang resource-dir paths.
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
# the host libclang's own builtin headers (stddef.h etc.) — discovered by
# locating stddef.h inside the libclang tree (version-agnostic)
HOST_RES=""
if [ -n "${LIBCLANG_PATH:-}" ]; then
    HOST_RES="$(dirname "$(dirname "$(find "$LIBCLANG_PATH" -maxdepth 4 -name stddef.h -path '*clang*' 2>/dev/null | head -1)")")"
fi
export BINDGEN_EXTRA_CLANG_ARGS="--target=aarch64-linux-android${API} -I$SYSROOT/usr/include -I$SYSROOT/usr/include/aarch64-linux-android -I$FFPREFIX/include -D__ANDROID_API__=${API}"
[ -n "$HOST_RES" ] && export BINDGEN_EXTRA_CLANG_ARGS="$BINDGEN_EXTRA_CLANG_ARGS -resource-dir=$HOST_RES"
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
