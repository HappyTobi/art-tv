#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ! "${1:-}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]{1,5}$ ||
      ( $# == 2 && "$2" != --apply ) ]]; then
    echo "Usage: $0 TV_IP:ADB_PORT [--apply]" >&2
    exit 2
fi
serial="$1"
cd "$(dirname "$0")"
file=local/restore-packages.txt
[[ -s "$file" ]] || { echo "Missing rollback list: $file (use the recorded list of apps you disabled)." >&2; exit 1; }
command -v adb >/dev/null || { echo "Install Android platform-tools and put adb on your PATH." >&2; exit 1; }

die() { echo "$*" >&2; exit 1; }
tv() { adb -s "$serial" "$@" | tr -d '\r'; }
has_line() { grep -Fxq -- "$1" <<<"$2"; }

stock=com.google.android.tvlauncher
home="$stock/.MainActivity"
home_full="$stock/$stock.MainActivity"
intent=(-a android.intent.action.MAIN -c android.intent.category.HOME)

while IFS= read -r package || [[ -n "$package" ]]; do
    [[ "$package" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]] ||
        die "Invalid package entry in $file: $package"
done < "$file"

if ! adb connect "$serial" || [[ "$(tv get-state)" != device ]]; then
    die "Cannot reach $serial over ADB. Check that the TV is awake and debugging is authorized."
fi
brand="$(tv shell getprop ro.product.brand)"
model="$(tv shell getprop ro.product.model)"
[[ "$brand" == [Ss][Oo][Nn][Yy] && "$model" == "BRAVIA 4K GB ATV3" ]] ||
    die "Refusing to restore '$brand $model'; expected Sony BRAVIA 4K GB ATV3."

disabled="$(tv shell pm list packages -d --user 0)"
system="$(tv shell pm list packages -s --user 0 "$stock")"
has_line "package:$stock" "$system" || die "Sony Home is not installed as a system app. No changes made."

is_stock_home() { has_line "$home" "$1" || has_line "$home_full" "$1"; }
home_candidates() {
    tv shell cmd package query-activities --brief --components --user 0 "${intent[@]}"
}
resolved_home() {
    tv shell cmd package resolve-activity --brief --user 0 "${intent[@]}"
}
enable_package() {
    local result
    if ! result="$(tv shell pm enable --user 0 "$1")" ||
        ! has_line "Package $1 new state: enabled" "$result"; then
        die "Could not enable $1: $result. Some earlier changes may already have applied."
    fi
}

echo "Restore plan for $serial (primary profile only):"
if has_line "package:$stock" "$disabled"; then
    echo "  Enable Sony Home"
else
    candidates="$(home_candidates)"
    is_stock_home "$candidates" || die "Sony Home is not registered as a Home activity. No changes made."
fi
while IFS= read -r package || [[ -n "$package" ]]; do
    if [[ "$package" != "$stock" ]] && has_line "package:$package" "$disabled"; then
        printf '  Enable %s\n' "$package"
    fi
done < "$file"
echo "  Make Sony Home the default if needed, then open it"
if [[ "${2:-}" != --apply ]]; then
    echo "Preview only. No changes made. Add --apply to restore."
    exit 0
fi

printf 'Type RESTORE to apply this plan: ' >&2
if ! IFS= read -r answer || [[ "$answer" != RESTORE ]]; then
    die "Cancelled. No changes made."
fi

if has_line "package:$stock" "$disabled"; then
    enable_package "$stock"
    candidates="$(home_candidates)"
    is_stock_home "$candidates" || die "Sony Home was enabled but its Home activity is unavailable."
fi
current="$(resolved_home)"
if ! is_stock_home "$current"; then
    result="$(tv shell cmd package set-home-activity --user 0 "$home")"
    has_line Success "$result" || die "Could not set Sony Home as default: $result"
    current="$(resolved_home)"
    is_stock_home "$current" || die "Android did not resolve Home to the Sony launcher."
fi

restored=()
while IFS= read -r package || [[ -n "$package" ]]; do
    if [[ "$package" != "$stock" ]] && has_line "package:$package" "$disabled"; then
        enable_package "$package"
        restored+=("$package")
    fi
done < "$file"

disabled_after="$(tv shell pm list packages -d --user 0)"
for package in "$stock" ${restored[@]+"${restored[@]}"}; do
    if has_line "package:$package" "$disabled_after"; then
        die "$package still appears in Android's disabled list."
    fi
done
current="$(resolved_home)"
is_stock_home "$current" || die "Sony Home is no longer the default after restoring packages."
launch="$(tv shell am start --user 0 -W "${intent[@]}" -n "$home")"
printf '%s\n' "$launch"
if ! has_line 'Status: ok' "$launch" || grep -q '^Error' <<<"$launch"; then
    die "Sony Home is the default, but Android did not confirm that it opened."
fi
echo "Restore complete. No app data or TV settings were erased."
