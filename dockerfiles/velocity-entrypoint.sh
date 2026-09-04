#!/bin/sh
set -eu

case "${CFG_BEDROCK_PORT:-}" in
    ''|*[!0-9]*)
        echo "Invalid CFG_BEDROCK_PORT: expected an integer between 1 and 65535." >&2
        exit 64
        ;;
esac
if [ "$CFG_BEDROCK_PORT" -lt 1 ] || [ "$CFG_BEDROCK_PORT" -gt 65535 ]; then
    echo "Invalid CFG_BEDROCK_PORT: expected an integer between 1 and 65535." >&2
    exit 64
fi

# /server is a tmpfs at runtime. Seed it from the immutable image before
# delegating to the upstream certificate entrypoint and proxy launcher.
cp -a /opt/tropicube/server/. /server/

# This nested named volume keeps Floodgate's generated private key stable while
# the rest of the proxy runtime remains disposable. Never copy key.pem to Git.
chown 1000:1000 /server/plugins/floodgate

exec /__cacert_entrypoint.sh "$@"
