#!/usr/bin/env bash
# Pre-pull the digest-pinned container images that Testcontainers suites use.
#
# A cold runner pulling an image inside a test is subject to Testcontainers' short pull timeout, and a
# slow or briefly unavailable registry then shows up as a ContainerFetchException in an unrelated test.
# Pulling here, with retries and a long timeout, either warms the local cache so the test finds the image
# or fails with the registry's own error (for example "manifest unknown" for a removed digest).
#
# Images are discovered from test sources: any "<registry>/<name>@sha256:<digest>" string literal.
set -euo pipefail

root="${1:-.}"
mapfile -t images < <(
  grep -rhoE --include='*.kt' '"[A-Za-z0-9._/-]+@sha256:[0-9a-f]{64}"' \
    "$root" --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude 2>/dev/null |
    tr -d '"' | sort -u
)

if [ "${#images[@]}" -eq 0 ]; then
  echo "No digest-pinned test images found."
  exit 0
fi

for image in "${images[@]}"; do
  pulled=0
  for attempt in 1 2 3 4; do
    echo "Pulling ${image} (attempt ${attempt}/4)"
    if timeout 300 docker pull "${image}"; then
      pulled=1
      break
    fi
    sleep $((attempt * 15))
  done
  if [ "${pulled}" -ne 1 ]; then
    echo "::error::Could not pull ${image} after 4 attempts. If the registry reports the digest is unknown, replace the pinned digest in the test that references it."
    exit 1
  fi
done
