#!/bin/sh
# Starts the agents on localhost, then the API on the Space's public port.
# Database: DATABASE_URL (a postgresql:// URI, e.g. Supabase's transaction pooler) or SPRING_DATASOURCE_URL.
set -e
DATABASE_URL="${DATABASE_URL:-${DB_POSTGRES_URL:-$POSTGRES_URL}}"
if [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$DATABASE_URL" ]; then
  rest="${DATABASE_URL#*://}"
  creds="${rest%@*}"
  target="${rest##*@}"
  case "$target" in *\?*) sep="&" ;; *) sep="?" ;; esac
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${target}${sep}sslmode=require&prepareThreshold=0&user=${creds%%:*}&password=${creds#*:}"
fi
cd /app/agents
/opt/agents/bin/uvicorn main:app --host 127.0.0.1 --port 8000 &
cd /app
exec java -XX:MaxRAMPercentage=60 -jar api.jar
