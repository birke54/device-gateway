#!/bin/sh
# Runs as root, fixes ownership of the persisted-data volume to the requested
# PUID/PGID, then drops privileges and launches the app as that user. This is
# the Unraid convention (defaults 99:100 = nobody:users) and means the mounted
# appdata folder can be owned by anyone.
set -e

PUID="${PUID:-99}"
PGID="${PGID:-100}"

mkdir -p /data
# Best-effort: a read-only mount or odd filesystem shouldn't abort startup.
chown -R "${PUID}:${PGID}" /data 2>/dev/null || true

# JAVA_OPTS is intentionally unquoted so multiple flags word-split.
exec gosu "${PUID}:${PGID}" java ${JAVA_OPTS} -jar /app/app.jar
