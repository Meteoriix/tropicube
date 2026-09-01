#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
frontend="$repo_root/tools/language-editor/frontend"
backend="$repo_root/tools/language-editor/backend"

if [[ ! -d "$frontend/node_modules" ]]; then
  npm --prefix "$frontend" ci
fi
npm --prefix "$frontend" run build
bash "$repo_root/mvnw" -q -f "$backend/pom.xml" package
java -jar "$backend/target/tropicube-language-editor.jar"
