#!/bin/sh
# Starts the API in the Vercel container.
# The Neon database added from the Vercel Marketplace provides PGHOST, PGUSER, PGPASSWORD and PGDATABASE;
# Spring wants a JDBC URL. The direct (unpooled) host is used, because Hibernate prepares statements.
set -e
if [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$PGHOST" ]; then
  host="${PGHOST_UNPOOLED:-$PGHOST}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${host}/${PGDATABASE:-neondb}?sslmode=require"
  export SPRING_DATASOURCE_USERNAME="$PGUSER"
  export SPRING_DATASOURCE_PASSWORD="$PGPASSWORD"
fi
# Quicker start-up matters more than peak speed for a container that sleeps when idle.
exec java -XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -Xshare:auto -jar app.jar
