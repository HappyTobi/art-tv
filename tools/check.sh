#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash build.sh
mkdir -p out/tests
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -encoding UTF-8 -d out/tests \
    app/src/io/happytobi/arttv/ArtChannelNames.java tests/ArtChannelNamesTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp out/tests io.happytobi.arttv.ArtChannelNamesTest
python3 -m unittest discover -s tests -p '*Test.py'
node tests/ArtSettingsTest.js
python3 tools/preview.py
