#!/usr/bin/env bash
# deploy.sh — Build, distribute and redeploy all Tropicube plugin images on Linux.

set -Eeuo pipefail

skip_tests=false
only_images=false
skip_restart=false
validate_only=false

usage() {
  cat <<'EOF'
Usage: ./deploy.sh [options]

Requires: Bash 4+, Java 25, Maven, Docker Engine with Compose v2, and Python 3.

Options:
  --skip-tests       Skip unit tests during the Maven build
  --only-images      Reuse existing Maven artifacts
  --skip-restart     Build images without recreating Velocity
  --validate-only    Validate and distribute artifacts without building images
  -h, --help         Show this help
EOF
}

while (($#)); do
  case "$1" in
    --skip-tests|-SkipTests) skip_tests=true ;;
    --only-images|-OnlyImages) only_images=true ;;
    --skip-restart|-SkipRestart) skip_restart=true ;;
    --validate-only|-ValidateOnly) validate_only=true ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly script_dir
cd "$script_dir"
readonly start_seconds=$SECONDS

step() { printf '\n==> %s\n' "$1"; }
ok() { printf '    %s\n' "$1"; }
fail() { printf '\n[ERROR] %s\n' "$1" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "$1 not found on PATH."; }

if ! $only_images; then require_command mvn; fi
require_command docker
if command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
  python_command=(python3)
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
  python_command=(python)
elif command -v py >/dev/null 2>&1 && py -3 --version >/dev/null 2>&1; then
  # Useful when validating the Linux script from Git Bash on Windows.
  python_command=(py -3)
else
  fail 'Python 3 is required for safe language-file merging.'
fi

docker info --format '{{.ServerVersion}}' >/dev/null 2>&1 || fail 'Docker daemon is not available.'
docker compose version >/dev/null 2>&1 || fail 'docker compose is not available.'
docker compose config --quiet || fail 'docker-compose.yml or .env is invalid.'

if ! $only_images; then
  step 'Building plugins (Maven)...'
  mvn_args=(clean package --batch-mode --no-transfer-progress -T 1C)
  if $skip_tests; then mvn_args+=(-DskipTests); fi
  mvn "${mvn_args[@]}" || fail 'Maven build failed.'
fi

step 'Distributing verified JARs...'
mkdir -p dockerfiles/plugins/{lobby,sheepwars,velocity}

resolve_artifact() {
  local module=$1
  local matches=()
  shopt -s nullglob
  matches=("$module"/target/"$module"-*-all.jar)
  shopt -u nullglob
  ((${#matches[@]} == 1)) || fail "Expected exactly one shaded artifact for $module, found ${#matches[@]}. Run a clean Maven build."
  printf '%s\n' "${matches[0]}"
}

core_jar=$(resolve_artifact tropicube-core)
lobby_jar=$(resolve_artifact tropicube-lobby)
sheepwars_jar=$(resolve_artifact tropicube-sheepwars)
velocity_jar=$(resolve_artifact tropicube-velocity)

artifact_is_fresh() {
  local artifact=$1
  shift
  local input_module newer
  [[ ! pom.xml -nt $artifact ]] || fail "$artifact is older than pom.xml; run without --only-images."
  for input_module in "$@"; do
    [[ ! "$input_module/pom.xml" -nt $artifact ]] || fail "$artifact is older than $input_module/pom.xml; run without --only-images."
    newer=$(find "$input_module/src" -type f -newer "$artifact" -print -quit 2>/dev/null || true)
    [[ -z $newer ]] || fail "$artifact is older than $newer; run without --only-images."
  done
}

copy_verified() {
  local source=$1 destination=$2
  mkdir -p "$(dirname -- "$destination")"
  cp -f -- "$source" "$destination"
  cmp -s -- "$source" "$destination" || fail "JAR verification failed after copy: $destination"
  ok "$destination"
}

if $only_images; then
  artifact_is_fresh "$core_jar" tropicube-core tropicube-docker-api
  artifact_is_fresh "$lobby_jar" tropicube-lobby tropicube-core tropicube-docker-api
  artifact_is_fresh "$sheepwars_jar" tropicube-sheepwars tropicube-core tropicube-docker-api
  artifact_is_fresh "$velocity_jar" tropicube-velocity tropicube-docker-api
fi

copy_verified "$core_jar" dockerfiles/plugins/lobby/tropicube-core.jar
copy_verified "$core_jar" dockerfiles/plugins/sheepwars/tropicube-core.jar
copy_verified "$lobby_jar" dockerfiles/plugins/lobby/tropicube-lobby.jar
copy_verified "$sheepwars_jar" dockerfiles/plugins/sheepwars/tropicube-sheepwars.jar
copy_verified "$velocity_jar" dockerfiles/plugins/velocity/tropicube-velocity.jar

step 'Merging missing language keys...'
stale_languages='dockerfiles/configs/TropicubeCore/languages/languages'
if [[ -d $stale_languages ]]; then
  rm -rf -- "$stale_languages"
  ok "Removed $stale_languages"
fi

"${python_command[@]}" - "$core_jar" "$velocity_jar" <<'PY'
import re
import sys
import zipfile
from pathlib import Path

TOP_SECTION = re.compile(r"^([\w-]+):\s*$")
TOP_VALUE = re.compile(r"^([\w-]+):\s+.")
LEAF = re.compile(r"^  ([\w-]+):\s")
NESTED_LEAF = re.compile(r"^ {4,}\S[^:]*:\s*\S")


def leaf_keys(lines, label):
    result = []
    seen = set()
    sections = set()
    section = None
    for line in lines:
        if NESTED_LEAF.match(line):
            raise SystemExit(f"[ERROR] Nested YAML leaf detected in {label}: {line}")
        match = TOP_SECTION.match(line)
        if match:
            section = match.group(1)
            if section in sections:
                raise SystemExit(f"[ERROR] Duplicate YAML section detected in {label}: {section}")
            sections.add(section)
            continue
        if TOP_VALUE.match(line):
            section = None
            continue
        match = LEAF.match(line)
        if match and section is not None:
            full_key = f"{section}.{match.group(1)}"
            if full_key in seen:
                raise SystemExit(f"[ERROR] Duplicate YAML key detected in {label}: {full_key}")
            seen.add(full_key)
            result.append(full_key)
    return result


def section_bounds(lines, section):
    start = next((i for i, line in enumerate(lines) if line == f"{section}:" or line.startswith((f"{section}: ", f"{section}:\t"))), -1)
    if start < 0:
        return None
    end = len(lines)
    for i in range(start + 1, len(lines)):
        line = lines[i]
        if line and not line.startswith((" ", "\t", "#")):
            end = i
            break
    return start, end


def section_block(lines, section):
    bounds = section_bounds(lines, section)
    if bounds is None:
        return []
    start, end = bounds
    while start > 0 and lines[start - 1].startswith("#"):
        start -= 1
    return lines[start:end]


def leaf_block(lines, section, leaf):
    bounds = section_bounds(lines, section)
    if bounds is None:
        return []
    section_start, section_end = bounds
    prefix = f"  {leaf}:"
    key_line = next((i for i in range(section_start + 1, section_end)
                     if lines[i] == prefix or lines[i].startswith((prefix + " ", prefix + "\t"))), -1)
    if key_line < 0:
        return []
    start = key_line
    while start - 1 > section_start and lines[start - 1].lstrip().startswith("#"):
        start -= 1
    end = key_line + 1
    while end < section_end:
        line = lines[end]
        if not line.strip() or len(line) - len(line.lstrip()) <= 2:
            break
        end += 1
    return lines[start:end]


def merge(default_text, destination):
    default_lines = default_text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    default_keys = leaf_keys(default_lines, f"JAR:{destination.name}")
    if not destination.exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text("\n".join(default_lines), encoding="utf-8")
        print(f"    [LangMerge] {destination.name} seeded from JAR defaults.")
        return

    disk_lines = destination.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n").split("\n")
    disk_keys = set(leaf_keys(disk_lines, str(destination)))
    missing = [key for key in default_keys if key not in disk_keys]
    if not missing:
        return

    print(f"    [LangMerge] {destination.name}: inserting {len(missing)} missing key(s).")
    by_section = {}
    for key in missing:
        section, leaf = key.split(".", 1)
        by_section.setdefault(section, []).append(leaf)

    for section, leaves in by_section.items():
        bounds = section_bounds(disk_lines, section)
        if bounds is None:
            if disk_lines and disk_lines[-1].strip():
                disk_lines.append("")
            disk_lines.extend(section_block(default_lines, section))
            continue
        _, end = bounds
        blocks = []
        for leaf in leaves:
            blocks.extend(leaf_block(default_lines, section, leaf))
        disk_lines[end:end] = blocks

    leaf_keys(disk_lines, str(destination))
    destination.write_text("\n".join(disk_lines), encoding="utf-8")
    print(f"    [LangMerge] {destination.name} updated.")


def process(jar_path, destination_dir):
    with zipfile.ZipFile(jar_path) as jar:
        for language in ("fr", "en", "es", "de"):
            entry = f"languages/{language}.yml"
            try:
                default_text = jar.read(entry).decode("utf-8")
            except KeyError:
                continue
            merge(default_text, Path(destination_dir, f"{language}.yml"))


process(sys.argv[1], "dockerfiles/configs/TropicubeCore/languages")
process(sys.argv[2], "dockerfiles/configs/TropicubeVelocity/languages")
PY

if $validate_only; then
  printf '\n==> Validation complete in %ss; no image or container was changed.\n' "$((SECONDS - start_seconds))"
  exit 0
fi

step 'Building Docker images in parallel (lobby / sheepwars / velocity)...'
build_tag="$(date -u +%Y%m%d-%H%M%S)"
readonly build_tag
build_log_dir=$(mktemp -d "${TMPDIR:-/tmp}/tropicube-build.XXXXXX")

cleanup_logs() {
  if [[ -n ${build_log_dir:-} && -d $build_log_dir && $build_log_dir == "${TMPDIR:-/tmp}"/tropicube-build.* ]]; then
    rm -rf -- "$build_log_dir"
  fi
}
trap cleanup_logs EXIT

build_names=(lobby sheepwars velocity)
declare -A build_files=(
  [lobby]=Dockerfile.lobby
  [sheepwars]=Dockerfile.sheepwars
  [velocity]=Dockerfile.velocity
)
declare -A build_pids=()

for name in "${build_names[@]}"; do
  docker build --pull -f "dockerfiles/${build_files[$name]}" \
    -t "tropicube-$name:latest" -t "tropicube-$name:$build_tag" . \
    >"$build_log_dir/$name.log" 2>&1 &
  build_pids[$name]=$!
done

build_failed=false
for name in "${build_names[@]}"; do
  printf '\n--- %s ---\n' "$name"
  if wait "${build_pids[$name]}"; then
    cat "$build_log_dir/$name.log"
    ok "Built tropicube-$name:latest"
  else
    cat "$build_log_dir/$name.log"
    printf '[ERROR] Docker build failed: %s\n' "$name" >&2
    build_failed=true
  fi
done
$build_failed && exit 1

if ! $skip_restart; then
  step 'Recreating the Velocity stack...'
  docker compose up -d --force-recreate velocity || fail 'Docker Compose deployment failed.'
  ok 'Velocity recreated; new game containers will use the freshly tagged images.'
fi

printf '\n==> Deploy complete in %ss.\n' "$((SECONDS - start_seconds))"
printf '    Lobby/SheepWars/Velocity: new containers use the latest image tags.\n'
printf '    Rollback tag: %s\n' "$build_tag"
