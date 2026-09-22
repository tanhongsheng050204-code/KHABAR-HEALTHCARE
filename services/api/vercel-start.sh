#!/bin/sh
# Starts the API in the Vercel container. Any one of these gives it a Postgres database:
#   - DATABASE_URL: a postgresql:// connection string, e.g. Supabase's "Session pooler" URI
#   - PGHOST, PGUSER, PGPASSWORD, PGDATABASE: what a Neon database added from the Vercel Marketplace provides
#   - SPRING_DATASOURCE_URL (+ _USERNAME, _PASSWORD): a JDBC URL, used as it is
set -e
if [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$DATABASE_URL" ]; then
  rest="${DATABASE_URL#*://}"      # user:password@host:port/database?options
  creds="${rest%@*}"               # everything before the last @ (the password may contain @)
  target="${rest##*@}"             # host:port/database?options
  case "$target" in *\?*) sep="&" ;; *) sep="?" ;; esac
  # The user and password stay percent-encoded in the URI, and the JDBC driver decodes them.
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${target}${sep}sslmode=require&user=${creds%%:*}&password=${creds#*:}"
elif [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$PGHOST" ]; then
  # Hibernate prepares statements, so use the direct (unpooled) host when there is one.
  host="${PGHOST_UNPOOLED:-$PGHOST}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${host}/${PGDATABASE:-neondb}?sslmode=require"
  export SPRING_DATASOURCE_USERNAME="$PGUSER"
  export SPRING_DATASOURCE_PASSWORD="$PGPASSWORD"
fi
# Quicker start-up matters more than peak speed for a container that sleeps when idle.
exec java -XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -Xshare:auto -jar app.jar
