#!/usr/bin/env bash
# Empaqueta OpenLiberty + WAR + driver MySQL en un JAR ejecutable.
# Requiere Maven y bootstrap.properties (la conexión queda DENTRO del JAR).
# Después: ./run-prod.sh  →  java -jar
set -euo pipefail
cd "$(dirname "$0")"

CONFIG_DIR="src/main/liberty/config"
BOOTSTRAP="$CONFIG_DIR/bootstrap.properties"
PASSWORD_DROPIN="$CONFIG_DIR/configDropins/overrides/openscience-db-password.xml"
DIST_DIR="dist"
JAR_NAME="openscience-uss.jar"

# shellcheck disable=SC1091
source "$(dirname "$0")/scripts/public-urls.sh"

if [[ -f prod.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source prod.env
  set +a
fi

if [[ ! -f "$BOOTSTRAP" ]]; then
  echo "Falta $BOOTSTRAP"
  echo "Copie el ejemplo y complete host, puerto, base, usuario y password:"
  echo "  cp $CONFIG_DIR/bootstrap.properties.example $BOOTSTRAP"
  exit 1
fi

if [[ ! -f "$PASSWORD_DROPIN" ]]; then
  echo "Aviso: no hay $PASSWORD_DROPIN"
  echo "Si db_password no está en bootstrap.properties, copiar:"
  echo "  cp $CONFIG_DIR/configDropins/overrides/openscience-db-password.xml.example $PASSWORD_DROPIN"
fi

MVN_ARGS=(-DskipTests -DlooseApplication=false)
if [[ -n "${DB_HOST:-}" ]]; then
  MVN_ARGS+=("-Dliberty.var.db_host=${DB_HOST}")
fi
if [[ -n "${DB_PORT:-}" ]]; then
  MVN_ARGS+=("-Dliberty.var.db_port=${DB_PORT}")
fi
if [[ -n "${DB_NAME:-}" ]]; then
  MVN_ARGS+=("-Dliberty.var.db_name=${DB_NAME}")
fi
if [[ -n "${DB_USER:-}" ]]; then
  MVN_ARGS+=("-Dliberty.var.db_user=${DB_USER}")
fi
if [[ -n "${HTTP_PORT:-}" ]]; then
  MVN_ARGS+=("-Dliberty.var.httpPort=${HTTP_PORT}")
fi

if [[ -z "${APP_PUBLIC_URL:-}" ]]; then
  echo "AVISO: APP_PUBLIC_URL no está en prod.env. Se empaquetará https://apps.cienciavida.org/openscience"
fi
if [[ -z "${EVENTS_PUBLIC_URL:-}" && -z "${EVENTS_PUBLIC_BASE_URL:-}" ]]; then
  echo "AVISO: EVENTS_PUBLIC_URL no está en prod.env. Se empaquetará https://apps.cienciavida.org/events"
fi

echo "OPENSCIENCE-USS empaquetando JAR ejecutable"
echo "  MySQL: $BOOTSTRAP (queda dentro del JAR; para cambiarla vuelva a empaquetar)"
echo "  Puerto: ${HTTP_PORT:-9080}"
echo "  App URL: $(production_app_url)"
echo "  Events URL: $(production_events_url)"

BOOTSTRAP_BAK=""
SERVER_ENV_BAK=""
restore_packaged_url_files() {
  if [[ -n "$BOOTSTRAP_BAK" && -f "$BOOTSTRAP_BAK" ]]; then
    mv "$BOOTSTRAP_BAK" "$BOOTSTRAP"
  fi
  if [[ -n "$SERVER_ENV_BAK" && -f "$SERVER_ENV_BAK" ]]; then
    mv "$SERVER_ENV_BAK" "$CONFIG_DIR/server.env"
  fi
}
trap restore_packaged_url_files EXIT

EVENTS_URL="$(production_events_url)"
APP_URL="$(production_app_url)"
if [[ -f "$BOOTSTRAP" ]]; then
  BOOTSTRAP_BAK="${BOOTSTRAP}.devbak"
  cp "$BOOTSTRAP" "$BOOTSTRAP_BAK"
  sanitize_public_urls "$BOOTSTRAP" "$EVENTS_URL" "$APP_URL"
fi
if [[ -f "$CONFIG_DIR/server.env" ]]; then
  SERVER_ENV_BAK="$CONFIG_DIR/server.env.devbak"
  cp "$CONFIG_DIR/server.env" "$SERVER_ENV_BAK"
  sanitize_public_urls "$CONFIG_DIR/server.env" "$EVENTS_URL" "$APP_URL"
fi

mvn "${MVN_ARGS[@]}" clean package \
  liberty:create \
  liberty:install-feature \
  liberty:deploy \
  liberty:package

mkdir -p "$DIST_DIR"
cp "target/${JAR_NAME}" "${DIST_DIR}/${JAR_NAME}"

echo
echo "Listo: ${DIST_DIR}/${JAR_NAME}"
echo "Arranque: ./run-prod.sh"
echo "En hidra hace falta JDK 17+; Maven solo para este paso."
