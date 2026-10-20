#!/usr/bin/env bash
# ============================================================
# 流量消耗器 · 无 Gradle 构建脚本
# 依赖：.build/jdk-17.x + .build/sdk（platforms;android-34, build-tools;34.0.0）
# 用法：./build-apk.sh
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BUILD="${BUILD_DIR:-$HERE/../.build}"
SDK="${SDK_DIR:-$BUILD/sdk}"
JDK="${JDK_DIR:-$(ls -d "$BUILD"/jdk-* 2>/dev/null | head -1)}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
SRC="$HERE/app/src/main"
STAGE="${STAGE_DIR:-/tmp/llxhq-build}"
KS="$HERE/keystore/llxhq.jks"
OUT="$HERE/build-output"
PY="${PYTHON:-$(command -v python || command -v python3 || true)}"
APK_NAME="llxhq-traffic-consumer.apk"

[ -n "$PY" ] || { echo "需要 python3（用于合并 classes.dex）"; exit 1; }

if [ ! -d "$BUILD" ] || [ -z "$JDK" ] || [ ! -d "$SDK" ]; then
  cat <<'EOS'
未找到本地构建链（需要 jdk-17 + Android SDK）。
三种解决办法：
  1) 用 Android Studio 直接打开本工程构建，不需要本脚本（见 README「方式二」）；
  2) 自行准备组件后指定路径：
       jdk-17 目录需含 bin/javac.exe
       sdk 目录需含 platforms/android-34 与 build-tools/34.0.0
       BUILD_DIR=/path/to/dir ./build-apk.sh      # 该目录下应有 jdk-*/ 与 sdk/
       JDK_DIR=/path/to/jdk17 SDK_DIR=/path/to/sdk ./build-apk.sh
  3) 从 Android 官方下载 commandline-tools 后用 sdkmanager 安装：
       sdkmanager "platforms;android-34" "build-tools;34.0.0"
EOS
  exit 1
fi

for f in "$JDK/bin/javac.exe" "$BT/aapt2.exe" "$BT/zipalign.exe" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "缺少构建组件: $f"; exit 1; }
done

echo "==> 准备构建目录"
rm -rf "$STAGE"
mkdir -p "$STAGE/gen" "$STAGE/classes" "$STAGE/dex" "$OUT"
cp -r "$SRC/res" "$STAGE/res"
cp "$SRC/AndroidManifest.xml" "$STAGE/AndroidManifest.xml"
cp -r "$SRC/java" "$STAGE/src"
cp "$ANDROID_JAR" "$STAGE/android.jar"
cp "$BT/lib/d8.jar" "$STAGE/d8.jar"
cp "$BT/lib/apksigner.jar" "$STAGE/apksigner.jar"
cp "$HERE/tools/merge_apk.py" "$STAGE/merge_apk.py"

cd "$STAGE"

echo "==> aapt2 compile"
"$BT/aapt2.exe" compile --dir res -o res.zip

echo "==> aapt2 link"
"$BT/aapt2.exe" link \
  -o base.apk \
  -I android.jar \
  --manifest AndroidManifest.xml \
  -R res.zip \
  --java gen \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0.0 \
  --auto-add-overlay

echo "==> javac"
find src gen -name '*.java' > sources.txt
"$JDK/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -bootclasspath android.jar -classpath android.jar -d classes @sources.txt

echo "==> jar"
"$JDK/bin/jar.exe" cf classes.jar -C classes .

echo "==> d8"
"$JDK/bin/java.exe" -cp d8.jar com.android.tools.r8.D8 \
  --release --min-api 24 --lib android.jar --output dex classes.jar

echo "==> 合并 classes.dex"
"$PY" merge_apk.py base.apk dex/classes.dex unsigned.apk

echo "==> zipalign"
"$BT/zipalign.exe" -f -p 4 unsigned.apk aligned.apk

echo "==> 签名"
mkdir -p keystore
if [ ! -f keystore.jks ]; then
  "$JDK/bin/keytool.exe" -genkeypair -keystore keystore.jks -alias llxhq \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass llxhq2026 -keypass llxhq2026 \
    -dname "CN=Llxhq Traffic Consumer, O=Llxhq, C=CN"
fi
"$JDK/bin/java.exe" -jar apksigner.jar sign \
  --ks keystore.jks --ks-pass pass:llxhq2026 --key-pass pass:llxhq2026 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out signed.apk aligned.apk

echo "==> 校验"
"$JDK/bin/java.exe" -jar apksigner.jar verify --print-certs signed.apk

cp signed.apk "$OUT/$APK_NAME"
cp keystore.jks "$KS" 2>/dev/null || { mkdir -p "$HERE/keystore"; cp keystore.jks "$KS"; }

echo
echo "构建完成: $OUT/$APK_NAME"
ls -lh "$OUT/$APK_NAME"
