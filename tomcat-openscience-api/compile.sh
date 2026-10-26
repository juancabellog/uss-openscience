#!/usr/bin/env bash
# Compila el proxy y deja las clases en WEB-INF/classes.
# Uso (en hidra):
#   export CATALINA_HOME=/home/jcabello/apache-tomcat-9.0.63
#   ./compile.sh
#   cp -a . $CATALINA_HOME/webapps/openscience-api
set -euo pipefail
cd "$(dirname "$0")"
CATALINA_HOME="${CATALINA_HOME:-/home/jcabello/apache-tomcat-9.0.63}"
API="$CATALINA_HOME/lib/servlet-api.jar"
if [[ ! -f "$API" ]]; then
  echo "No está $API. Defina CATALINA_HOME."
  exit 1
fi
SRC="WEB-INF/src/cl/uss/ca/proxy/ApiProxyServlet.java"
OUT="WEB-INF/classes"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$API" -d "$OUT" "$SRC"
echo "OK $OUT/cl/uss/ca/proxy/ApiProxyServlet.class"
echo "Copiar este directorio a $CATALINA_HOME/webapps/openscience-api"
