#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
npm run build:events -- --base-href /events/
# Angular suele omitir archivos ocultos en assets.
if [[ -f projects/openscience-events/public/.htaccess ]]; then
  cp projects/openscience-events/public/.htaccess dist/openscience-events/browser/.htaccess
fi
echo "Listo: dist/openscience-events/browser  (base href /events/)"
