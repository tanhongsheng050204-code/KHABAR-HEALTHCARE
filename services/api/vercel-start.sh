#!/bin/sh
# Starts the API in the Vercel container. Any one of these gives it a Postgres database:
#   - DATABASE_URL: a postgresql:// connection string, e.g. one of Supabase's pooler URIs
#   - DB_POSTGRES_URL / POSTGRES_URL: what a Supabase database connected from the Vercel Marketplace provides
#     (the DB_ prefix is how it is connected to khabar-api): the transaction pooler. Preferred, because many
#     sleeping and waking instances share a few database connections instead of each holding its own; the
#     session pooler (…_NON_POOLING) allows only 15 clients in all, which a redeploy can use up.
#   - DB_POSTGRES_URL_NON_POOLING / POSTGRES_URL_NON_POOLING: the session pooler, if nothing else is set
#   - PGHOST, PGUSER, PGPASSWORD, PGDATABASE: what a Neon database added from the Vercel Marketplace provides
#   - SPRING_DATASOURCE_URL (+ _USERNAME, _PASSWORD): a JDBC URL, used as it is
set -e
DATABASE_URL="${DATABASE_URL:-${DB_POSTGRES_URL:-${POSTGRES_URL:-${DB_POSTGRES_URL_NON_POOLING:-$POSTGRES_URL_NON_POOLING}}}}"
if [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$DATABASE_URL" ]; then
  rest="${DATABASE_URL#*://}"      # user:password@host:port/database?options
  creds="${rest%@*}"               # everything before the last @ (the password may contain @)
  target="${rest##*@}"             # host:port/database?options
  case "$target" in *\?*) sep="&" ;; *) sep="?" ;; esac
  # The user and password stay percent-encoded in the URI, and the JDBC driver decodes them.
  # prepareThreshold=0: a transaction pooler hands each transaction to any server connection, so the
  # driver must not rely on statements prepared on an earlier one.
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${target}${sep}sslmode=require&prepareThreshold=0&user=${creds%%:*}&password=${creds#*:}"
elif [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$PGHOST" ]; then
  # Hibernate prepares statements, so use the direct (unpooled) host when there is one.
  host="${PGHOST_UNPOOLED:-$PGHOST}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${host}/${PGDATABASE:-neondb}?sslmode=require"
  export SPRING_DATASOURCE_USERNAME="$PGUSER"
  export SPRING_DATASOURCE_PASSWORD="$PGPASSWORD"
fi
# Quicker start-up matters more than peak speed for a container that sleeps when idle.
exec java -XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -Xshare:auto -jar app.jar
