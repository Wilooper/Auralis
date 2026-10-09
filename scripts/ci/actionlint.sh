#!/usr/bin/env bash
set -euo pipefail
tools_dir=$(mktemp -d)
archive="$tools_dir/actionlint.tar.gz"
curl --fail --silent --show-error --location --retry 3 \
  https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
  --output "$archive"
printf '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8  %s\n' "$archive" | sha256sum --check --strict
tar --no-same-owner -xzf "$archive" -C "$tools_dir" actionlint
"$tools_dir/actionlint" -color
