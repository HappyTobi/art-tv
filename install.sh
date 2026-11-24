#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

clean=false
make_default=true
while [[ "${1:-}" == --* ]]; do
    case "$1" in
        --clean) clean=true ;;
        --keep-home) make_default=false ;;
        *) break ;;
    esac
    shift
done
if [[ $# != 1 || ! "$1" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]{1,5}$ ]]; then
    echo "Usage: $0 [--clean] [--keep-home] TV_IP:ADB_PORT (for example, 192.168.1.50:5555)" >&2
    exit 2
fi
serial="$1"
command -v adb >/dev/null || { echo "Install Android platform-tools and put adb on your PATH." >&2; exit 1; }
[[ -s ArtTV.apk ]] || { echo "ArtTV.apk is missing or empty. Run ./build.sh first." >&2; exit 1; }

package=io.happytobi.arttv
if "$clean"; then
    sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    build_tools="$sdk/build-tools/34.0.0"
    export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
    if [[ ! -x "$build_tools/apksigner" || ! -x "$build_tools/aapt" ]]; then
        echo "Set ANDROID_HOME to the Android SDK with build-tools 34.0.0 before reinstalling." >&2
        exit 1
    fi
    if ! "$build_tools/apksigner" verify --min-sdk-version 28 ArtTV.apk ||
        ! info="$("$build_tools/aapt" dump badging ArtTV.apk)" ||
        ! grep -q "^package: name='io\\.happytobi\\.arttv' " <<<"$info"; then
        echo "ArtTV.apk did not pass verification for $package. Run ./build.sh first." >&2
        exit 1
    fi
fi

if ! adb connect "$serial" || [[ "$(adb -s "$serial" get-state)" != device ]]; then
    echo "Cannot reach $serial over ADB. Check that the TV is awake, the address is current, and debugging is authorized." >&2
    exit 1
fi
brand="$(adb -s "$serial" shell getprop ro.product.brand | tr -d '\r')"
model="$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')"
if [[ "$brand" != [Ss][Oo][Nn][Yy] || "$model" != "BRAVIA 4K GB ATV3" ]]; then
    echo "Refusing to install on '$brand $model' at $serial; expected Sony BRAVIA 4K GB ATV3." >&2
    exit 1
fi

home_intent=(-a android.intent.action.MAIN -c android.intent.category.HOME)
stock=com.google.android.tvlauncher
stock_home="$stock/.MainActivity"
art_home="$package/.HomeActivity"
matches_home() {
    grep -Fxq -e "$1/.$2" -e "$1/$1.$2" <<<"$3"
}
home_candidates() {
    adb -s "$serial" shell cmd package query-activities --brief --components --user 0 "${home_intent[@]}" | tr -d '\r'
}
resolved_home() {
    adb -s "$serial" shell cmd package resolve-activity --brief --user 0 "${home_intent[@]}" | tr -d '\r'
}
set_home() {
    local result
    result="$(adb -s "$serial" shell cmd package set-home-activity --user 0 "$1" | tr -d '\r')" &&
        grep -Fxq 'Success' <<<"$result"
}

if "$make_default"; then
    candidates="$(home_candidates)"
    if ! matches_home "$stock" MainActivity "$candidates"; then
        echo "Sony Home is not available as a fallback. No installation or Home switch performed." >&2
        exit 1
    fi
fi

install_flags=(-r)
user_flags=()
if "$clean"; then
    user_flags=(--user 0)
    installed_packages="$(adb -s "$serial" shell pm list packages --user 0 "$package" | tr -d '\r')"
    if grep -Fxq "package:$package" <<<"$installed_packages"; then
        third_party="$(adb -s "$serial" shell pm list packages -3 --user 0 "$package" | tr -d '\r')"
        if ! grep -Fxq "package:$package" <<<"$third_party"; then
            echo "Refusing to uninstall $package: Android did not list it as a third-party app." >&2
            exit 1
        fi
        printf 'This deletes Art TV data (including artwork and HDMI selections) on %s. Type REINSTALL to continue: ' "$serial" >&2
        if ! IFS= read -r answer || [[ "$answer" != REINSTALL ]]; then
            echo "Cancelled. Nothing was removed." >&2
            exit 1
        fi
        if ! removed="$(adb -s "$serial" shell pm uninstall --user 0 "$package" | tr -d '\r')" ||
            [[ "$removed" != Success ]]; then
            printf 'Could not remove Art TV: %s\n' "$removed" >&2
            exit 1
        fi
    fi
    install_flags=(--user 0)
fi

if ! adb -s "$serial" install "${install_flags[@]}" ArtTV.apk; then
    echo "Install failed. If the old copy was removed, rerun the installer to retry." >&2
    exit 1
fi
installed="$(adb -s "$serial" shell pm path ${user_flags[@]+"${user_flags[@]}"} "$package")"
if [[ "$installed" != package:* ]]; then
    echo "Could not verify the installed package $package." >&2
    exit 1
fi
echo "Art TV is installed. It stays installed after reboot."

launch="$(adb -s "$serial" shell am start --user 0 -W -n "$art_home")"
printf '%s\n' "$launch"
if ! grep -q '^Status: ok[[:space:]]*$' <<<"$launch" || grep -q '^Error' <<<"$launch"; then
    echo "Art TV is installed, but Android did not confirm that it opened." >&2
    exit 1
fi

if "$make_default"; then
    candidates="$(home_candidates)"
    if ! matches_home "$package" HomeActivity "$candidates"; then
        echo "Art TV opened but is not registered as a Home app. Rebuild the APK before retrying." >&2
        exit 1
    fi
    current="$(resolved_home)"
    if ! matches_home "$package" HomeActivity "$current"; then
        if ! set_home "$art_home" || ! current="$(resolved_home)" ||
            ! matches_home "$package" HomeActivity "$current"; then
            if set_home "$stock_home" && current="$(resolved_home)" &&
                matches_home "$stock" MainActivity "$current"; then
                echo "Art TV opened, but Android did not confirm it as Home. Sony Home is the default." >&2
            else
                echo "Could not verify the default launcher. Sony Home was not disabled; see README to restore it." >&2
            fi
            exit 1
        fi
    fi
    echo "Art TV is the default Home app. Sony Home remains enabled."
else
    echo "No default Home change requested."
fi
