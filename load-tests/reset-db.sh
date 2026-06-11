#!/usr/bin/env bash
# Wipe PostgreSQL app data and Redis cache for a fair JVM vs native load test.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-private_chat}"
DB_USER="${DB_USER:-private_chat}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

echo "Truncating PostgreSQL tables in ${DB_NAME}@${DB_HOST} ..."
PGPASSWORD="${DB_PASSWORD}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
TRUNCATE TABLE messages, outbox, refresh_tokens, chat_members, chats, users RESTART IDENTITY CASCADE;
SQL

echo "Flushing Redis ${REDIS_HOST}:${REDIS_PORT} ..."
redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" FLUSHDB >/dev/null

echo "Database and Redis reset complete."
