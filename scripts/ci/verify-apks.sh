#!/usr/bin/env bash
set -euo pipefail
apk_dir=${1:?Pass the APK output directory}
sdk_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/35.0.0"
shopt -s nullglob
apks=("$apk_dir"/*.apk)
if (( ${#apks[@]} == 0 )); then
  echo 'No APKs produced' >&2
  exit 1
fi
for apk in "${apks[@]}"; do
  "$sdk_tools/apksigner" verify --verbose "$apk"
  "$sdk_tools/zipalign" -c -P 16 4 "$apk"
done
(cd "$apk_dir" && sha256sum -- *.apk > SHA256SUMS.txt)
