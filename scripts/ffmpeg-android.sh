#!/bin/sh
# Build FFmpeg 7.1.1 (LGPL-compatible, minimal) for Android aarch64.
# Same recipe as the certification environment; parameterized for CI.
# The engine's ffmpeg-sys-next pin is "7.1" (major.minor) — n7.1.1 satisfies it.
# Static + PIC so it links into the JNI cdylib.
#
# Environment:
#   NDK     NDK root            (default: /home/z/android/android-ndk-r27c)
#   SRC     FFmpeg source tree  (default: /home/z/dl/ffmpeg-7.1.1)
#   PREFIX  install prefix      (default: /home/z/android/ffmpega64)
#   API     android API level   (default: 24)
set -e
NDK="${NDK:-/home/z/android/android-ndk-r27c}"
API="${API:-24}"
SRC="${SRC:-/home/z/dl/ffmpeg-7.1.1}"
PREFIX="${PREFIX:-/home/z/android/ffmpega64}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$PREFIX"
cd "$SRC"

export PKG_CONFIG_PATH=""
./configure \
  --prefix="$PREFIX" \
  --enable-cross-compile \
  --target-os=android \
  --arch=aarch64 \
  --cpu=armv8-a \
  --cc="$TC/aarch64-linux-android${API}-clang" \
  --cxx="$TC/aarch64-linux-android${API}-clang++" \
  --ar="$TC/llvm-ar" \
  --nm="$TC/llvm-nm" \
  --ranlib="$TC/llvm-ranlib" \
  --strip="$TC/llvm-strip" \
  --extra-cflags="-fPIC -O2 -march=armv8-a" \
  --extra-ldflags="-fPIC" \
  --disable-programs \
  --disable-doc \
  --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
  --disable-network \
  --disable-autodetect \
  --disable-debug \
  --enable-static --disable-shared --enable-pic \
  --enable-avcodec --enable-avformat --enable-avutil --enable-swscale --enable-swresample \
  --disable-avfilter --disable-postproc --disable-avdevice \
  --enable-demuxer=mov,matroska,mp4,wav,avi,flv \
  --enable-muxer=mp4,mov,wav \
  --enable-decoder=h264,mpeg4,mjpeg,aac,mp3,pcm_s16le,pcm_f32le,pcm_s24le,vp8,vp9,flac,opus,vorbis \
  --enable-encoder=mpeg4,aac,pcm_s16le \
  --enable-parser=h264,mpeg4,aac,vp8,vp9,mpegaudio,opus,vorbis,flac \
  --enable-protocol=file \
  --disable-linux-perf
make -j"$(nproc)"
make install
echo FFMPEG-ANDROID-OK "$PREFIX"
