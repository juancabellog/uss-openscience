#!/usr/bin/env bash
# Arranque de producción.
# 1ª vez: java -jar extrae Liberty.
# Si wlp/ ya existe: bin/server run (no vuelvas a java -jar).
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck disable=SC1091
source "$(dirname "$0")/scripts/public-urls.sh"

if [[ -f prod.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source prod.env
  set +a
fi

EVENTS_PUBLIC_URL="$(production_events_url)"
EVENTS_PUBLIC_BASE_URL="${EVENTS_PUBLIC_BASE_URL:-$EVENTS_PUBLIC_URL}"
APP_PUBLIC_URL="$(production_app_url)"
EVENTS_ALLOW_LOCAL_MAIL_LINKS=false
export EVENTS_PUBLIC_URL EVENTS_PUBLIC_BASE_URL APP_PUBLIC_URL EVENTS_ALLOW_LOCAL_MAIL_LINKS

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA_BIN="${JAVA_BIN:-java}"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1 && [[ ! -x "$JAVA_BIN" ]]; then
  echo "No se encontró java. Instale JDK 17+ o defina JAVA_HOME."
  exit 1
fi

EXTRACT_DIR="${WLP_JAR_EXTRACT_DIR:-$(pwd)/work/extract}"
# java -jar extrae a $EXTRACT_DIR/wlp ; a veces EXTRACT_DIR ya es .../wlp-extract
SERVER_BIN=""
for candidate in \
  "${EXTRACT_DIR}/wlp/bin/server" \
  "${EXTRACT_DIR}/bin/server"
do
  if [[ -x "$candidate" ]]; then
    SERVER_BIN="$candidate"
    break
  fi
done

HTTP_PORT="${HTTP_PORT:-9080}"

echo "OPENSCIENCE-USS producción"
echo "  Java:   $JAVA_BIN"
echo "  Work:   $EXTRACT_DIR"
echo "  API:    http://0.0.0.0:${HTTP_PORT}/api/v1"
echo "  App:    ${APP_PUBLIC_URL:-<no definido — correos pueden salir con localhost>}"
echo "  Events: ${EVENTS_PUBLIC_BASE_URL:-${EVENTS_PUBLIC_URL:-<no definido — configure EVENTS_PUBLIC_URL=https://apps.cienciavida.org/events>}}"
echo "  MySQL:  ${DB_USER:-<embebido>}@${DB_HOST:-<embebido>}/${DB_NAME:-<embebido>}"
echo "  Este proceso queda en primer plano. Ctrl+C para detener."

if [[ -n "$SERVER_BIN" ]]; then
  echo "  Modo:   servidor ya extraído ($SERVER_BIN)"
  EXTRACTED_ENV="$(cd "$(dirname "$SERVER_BIN")/../usr/servers/openscience" && pwd)/server.env"
  sanitize_public_urls "$EXTRACTED_ENV" "$EVENTS_PUBLIC_URL" "$APP_PUBLIC_URL"
  export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$JAVA_BIN")")}"
  exec "$SERVER_BIN" run openscience
fi

JAR=""
for candidate in dist/openscience-uss.jar target/openscience-uss.jar openscience-uss.jar; do
  if [[ -f "$candidate" ]]; then
    JAR="$candidate"
    break
  fi
done

if [[ -z "$JAR" ]]; then
  echo "No está el JAR ni un wlp extraído."
  echo "  ./package-prod.sh   o   java -jar openscience-uss.jar (1ª vez)"
  exit 1
fi

if [[ -d "${EXTRACT_DIR}/wlp" ]]; then
  echo "Ya existe ${EXTRACT_DIR}/wlp"
  echo "No use java -jar otra vez. Arranque así:"
  echo "  ${EXTRACT_DIR}/wlp/bin/server run openscience"
  echo "O borre el extracto para re-extraer (pierde ediciones a bootstrap):"
  echo "  rm -rf ${EXTRACT_DIR}/wlp"
  exit 1
fi

mkdir -p "$EXTRACT_DIR"
export WLP_JAR_EXTRACT_DIR="$EXTRACT_DIR"
echo "  Modo:   java -jar $JAR (primera extracción)"
# shellcheck disable=SC2086
exec "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$JAR"
