# Helpers to keep Events/USS public URLs out of developer localhost when packaging.
# shellcheck shell=bash

production_events_url() {
  echo "${EVENTS_PUBLIC_BASE_URL:-${EVENTS_PUBLIC_URL:-https://apps.cienciavida.org/events}}"
}

production_app_url() {
  echo "${APP_PUBLIC_URL:-https://apps.cienciavida.org/openscience}"
}

upsert_kv() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp
  if [[ ! -f "$file" ]]; then
    return 0
  fi
  tmp="${file}.urltmp"
  if grep -q "^${key}=" "$file"; then
    awk -v k="$key" -v v="$value" '
      BEGIN { FS="="; OFS="=" }
      $1 == k { print k "=" v; next }
      { print }
    ' "$file" > "$tmp" && mv "$tmp" "$file"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$file"
  fi
}

sanitize_public_urls() {
  local file="$1"
  local events_url="$2"
  local app_url="$3"
  upsert_kv "$file" "EVENTS_PUBLIC_URL" "$events_url"
  upsert_kv "$file" "EVENTS_PUBLIC_BASE_URL" "$events_url"
  upsert_kv "$file" "APP_PUBLIC_URL" "$app_url"
  upsert_kv "$file" "EVENTS_ALLOW_LOCAL_MAIL_LINKS" "false"
  upsert_kv "$file" "EVENTS_REQUIRE_PUBLIC_URL" "true"
  upsert_kv "$file" "openscience.events.publicUrl" "$events_url"
  upsert_kv "$file" "openscience.app.publicUrl" "$app_url"
  upsert_kv "$file" "openscience.events.allowLocalMailLinks" "false"
  upsert_kv "$file" "openscience.events.requirePublicUrl" "true"
}
