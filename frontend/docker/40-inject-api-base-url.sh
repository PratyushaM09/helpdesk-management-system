#!/bin/sh
# Runs automatically before nginx starts (official nginx image convention:
# every executable script under /docker-entrypoint.d/ is run in sorted
# order). Rewrites the one literal in js/core/config.js so the API base URL
# is an environment variable at container-start time, not a value baked
# into the image at build time (Phase 5, Milestone 1 requirement).
#
# Defaults to the exact value config.js already ships with, so a container
# started with no API_BASE_URL override behaves identically to today.
set -e

CONFIG_FILE="/usr/share/nginx/html/js/core/config.js"
TARGET_URL="${API_BASE_URL:-http://localhost:8080/api/v1}"

if [ -f "$CONFIG_FILE" ]; then
  sed -i "s#http://localhost:8080/api/v1#${TARGET_URL}#g" "$CONFIG_FILE"
fi
