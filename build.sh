#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
[[ "$("$JAVA" -version 2>&1 | head -1)" == *'"17.'* ]] || {
    echo "Use JDK 17: set JAVA_HOME to its installation directory." >&2; exit 1;
}
for tool in aapt2 aapt d8 zipalign apksigner; do
    [ -x "$BT/$tool" ] || { echo "Missing Android build-tools 34.0.0: $tool" >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "Missing Android platform android-34." >&2; exit 1; }
[ -f config.properties ] || { cp config.example.properties config.properties; chmod 600 config.properties; }
mkdir -p out/gen out/classes out/dex out/res out/assets
# Delete only this build's generated files, never the project or its private settings.
find out/gen out/classes out/dex out/res out/assets -type f -delete
cp -R app/res/. out/res/
cp -R app/assets/. out/assets/
cp LICENSE out/assets/LICENSE.txt
python3 tools/configure.py
"$BT/aapt2" compile --dir out/res -o out/compiled.zip
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest app/AndroidManifest.xml \
    -A out/assets --java out/gen -o out/resources.apk out/compiled.zip
find app/src out/gen -name '*.java' -print > out/sources.list
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -classpath "$ANDROID_JAR" \
    -d out/classes @out/sources.list
"$JAR" cf out/classes.jar -C out/classes .
"$BT/d8" --min-api 28 --lib "$ANDROID_JAR" --output out/dex out/classes.jar
cp out/resources.apk out/unsigned.apk
(cd out/dex && "$BT/aapt" add ../unsigned.apk classes.dex >/dev/null)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

if [ ! -f art-tv.keystore ]; then
    [ ! -f .keystore-password ] || { echo "Signing password exists without its keystore; restore the key." >&2; exit 1; }
    (umask 077; python3 -c 'import secrets; print(secrets.token_hex(24))' > .keystore-password)
    export ART_TV_KEYSTORE_PASS="$(cat .keystore-password)"
    "$KEYTOOL" -genkeypair -keystore art-tv.keystore -storepass:env ART_TV_KEYSTORE_PASS \
        -keypass:env ART_TV_KEYSTORE_PASS -alias art-tv -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Art TV" >/dev/null
    chmod 600 art-tv.keystore
fi
[ -f .keystore-password ] || { echo "Restore .keystore-password before signing." >&2; exit 1; }
export ART_TV_KEYSTORE_PASS="$(cat .keystore-password)"
"$BT/apksigner" sign --ks art-tv.keystore --ks-key-alias art-tv \
    --ks-pass env:ART_TV_KEYSTORE_PASS --v4-signing-enabled false --out ArtTV.apk out/aligned.apk
"$BT/apksigner" verify --min-sdk-version 28 ArtTV.apk
echo "Built ArtTV.apk"
