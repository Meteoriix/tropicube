#!/bin/sh
set -eu

# /server is a tmpfs at runtime. Seed it from the immutable image before
# delegating to the upstream certificate entrypoint and proxy launcher.
cp -a /opt/tropicube/server/. /server/

exec /__cacert_entrypoint.sh "$@"
