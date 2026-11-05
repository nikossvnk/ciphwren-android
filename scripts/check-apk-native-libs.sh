#!/bin/bash
# Checks the native libraries of an APK: exactly the four supported ABIs, each with the Etebase
# library and JNA's dispatcher, ELF LOAD segments aligned for 16 KB pages, and zip entries
# aligned so that the uncompressed libraries can be mapped from the APK on 16 KB page devices.
#
# Usage: scripts/check-apk-native-libs.sh <apk>
# Needs ANDROID_HOME (for zipalign), readelf and unzip.
set -euo pipefail

apk=$1
abis="armeabi-v7a arm64-v8a x86 x86_64"
libs="libetebase_uniffi.so libjnidispatch.so"

zipalign="$(ls "$ANDROID_HOME"/build-tools/*/zipalign | sort -V | tail -1)"
"$zipalign" -c -P 16 4 "$apk"

contents=$(mktemp -d)
trap 'rm -rf "$contents"' EXIT
unzip -q "$apk" 'lib/*' -d "$contents"

actual=$(ls "$contents/lib" | sort | tr '\n' ' ')
expected=$(printf '%s\n' $abis | sort | tr '\n' ' ')
if [ "$actual" != "$expected" ]; then
    echo "::error::ABIs in $apk are '$actual', expected '$expected'"
    exit 1
fi

status=0
for abi in $abis; do
    for lib in $libs; do
        if [ ! -f "$contents/lib/$abi/$lib" ]; then
            echo "::error::lib/$abi/$lib is missing from $apk"
            status=1
        fi
    done
    for so in "$contents/lib/$abi"/*.so; do
        if ! readelf -lW "$so" | awk '
                $1 == "LOAD" { found = 1; if ($NF != "0x4000" && $NF != "0x10000") bad = 1 }
                END { exit (!found || bad) }'; then
            echo "::error::${so#"$contents/"} has LOAD segments not aligned for 16 KB pages"
            status=1
        fi
    done
done
exit $status
