#!/system/bin/sh
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
BASE=/data/user/0/com.deepseek.harness.compat/files/星球卑一号实验室
T="$BASE/.probe/tx"
AJ="$BASE/.probe/lib/x/plat/android-34/android.jar"
R8="$BASE/.probe/lib/r8.jar"
AS="$BASE/.probe/lib/apksig.jar"
OUT="$HERE/build"
APP="$HERE/app/src/main"
PKG=com.tianyi.chat
PKGPATH=com/tianyi/chat

export LD_LIBRARY_PATH="$T/lib"
export PATH="$T/bin:$PATH"
export JAVA_HOME="$T"
export TMPDIR="$BASE/.probe/tmp"
mkdir -p "$TMPDIR"

JAVA="$T/lib/jvm/java-21-openjdk/bin/java"
JAVAC="$T/lib/jvm/java-21-openjdk/bin/javac"
KEYTOOL="$T/lib/jvm/java-21-openjdk/bin/keytool"
AAPT2="$T/bin/aapt2"
KOTLINC_JAR="$T/opt/kotlin/lib/kotlin-compiler.jar"
STDLIB="$T/opt/kotlin/lib/kotlin-stdlib.jar"
KS="$BASE/.probe/tianyi.keystore"

die() { echo "FAILED: $1"; exit 1; }
mkdir -p "$OUT"
rm -rf "$OUT/classes" "$OUT/dex" "$OUT/base.apk" "$OUT/res.zip" "$OUT/tianyi-signed.apk"

echo "== manifest =="
sed -e 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'"$PKG"'">|' "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml" || die "manifest"

echo "== kotlinc =="
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"
"$JAVA" -Xmx1600m -cp "$KOTLINC_JAR" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -jvm-target 11 -nowarn -classpath "$AJ:$STDLIB" -d "$OUT/classes" $(ls "$APP"/java/$PKGPATH/*.kt) 2>&1 | tail -40
[ -f "$OUT/classes/$PKGPATH/MainActivity.class" ] || die "kotlinc"

echo "== dex =="
mkdir -p "$OUT/dex"
CLASSES=$(find "$OUT/classes" -name "*.class" | tr '\n' ' ')
"$JAVA" -Xmx1600m -cp "$R8" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" --output "$OUT/dex" $CLASSES "$STDLIB" 2>&1 | tail -15
[ -f "$OUT/dex/classes.dex" ] || die "dex"

echo "== aapt2 =="
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip" 2>&1 | tail -8
[ -f "$OUT/res.zip" ] || die "aapt2 compile"
"$AAPT2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$OUT/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 --version-code 712 --version-name 1.0.712 -A "$APP/assets" "$OUT/res.zip" 2>&1 | tail -12
[ -f "$OUT/base.apk" ] || die "aapt2 link"

echo "== dex in =="
cd "$OUT/dex" || die "cd dex"
for D in classes*.dex; do zip -q -X "$OUT/base.apk" "$D" || die "zip $D"; done
cd "$HERE" || die "cd back"
unzip -l "$OUT/base.apk" | grep -E "dex|index.html"

echo "== sign =="
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KS" -storepass android -keypass android -alias tianyi -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Tianyi,OU=Local,O=Local,L=Local,ST=Local,C=CN" >/dev/null 2>&1 || die "keytool"
fi
if [ ! -f "$OUT/Sign.class" ]; then
  "$JAVAC" -nowarn -encoding UTF-8 -classpath "$AS" -d "$OUT" "$BASE/.probe/sign/Sign.java" 2>&1 | head
fi
"$JAVA" -cp "$AS:$OUT" Sign "$KS" android tianyi "$OUT/base.apk" "$OUT/tianyi-signed.apk" 2>&1 | tail -5
[ -s "$OUT/tianyi-signed.apk" ] || die "sign"
ls -l "$OUT/tianyi-signed.apk"
echo "APK_DONE"

