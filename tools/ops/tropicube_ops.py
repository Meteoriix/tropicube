#!/usr/bin/env python3
"""Local operations for the single-host Compose deployment. Never prints subprocess secrets."""
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
IMAGES = ("lobby", "sheepwars", "velocity")
MYSQL_CONTAINER = "tropicube-mysql"
REDIS_CONTAINER = "tropicube-redis"


def run(args, *, output=None, timeout=120, input=None):
    result = subprocess.run(args, cwd=ROOT, input=input, stdout=output or subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout)
    if result.returncode:
        # docker inspect and tool stderr can contain credentials; keep diagnostics deliberately bounded.
        raise RuntimeError(f"{args[0]} failed (exit {result.returncode}); inspect the service privately")
    return result.stdout.decode("utf-8").strip() if result.stdout else ""


def docker(*args, **kwargs):
    return run(["docker", *args], **kwargs)


def mysql(sql):
    return docker("exec", "-i", MYSQL_CONTAINER, "sh", "-c",
                  'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --batch --skip-column-names --unbuffered -u "$MYSQL_USER" "$MYSQL_DATABASE"',
                  input=sql.encode(), timeout=10)


def redis(*args):
    result = docker("exec", REDIS_CONTAINER, "sh", "-c",
                  'export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --raw "$@"', "redis-cli", *args, timeout=120 if "--rdb" in args else 8)
    if re.match(r"^(?:NOAUTH|WRONGPASS|ERR|LOADING|MISCONF)\b", result):
        raise RuntimeError("Redis rejected the operation; check service health and authentication")
    return result


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def state_dir():
    path = Path(os.environ.get("TROPICUBE_OPS_STATE", str(ROOT / ".runtime" / "ops"))).resolve()
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    return path


@contextlib.contextmanager
def operation_lock():
    """One activation/backup per host; OS releases the lock even after process termination."""
    with (state_dir() / "operation.lock").open("a+b") as lock:
        if os.name == "nt":
            import msvcrt
            lock.seek(0)
            if lock.read(1) == b"":
                lock.write(b"0"); lock.flush()
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            if os.name == "nt":
                lock.seek(0); msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)


@contextlib.contextmanager
def schema_lock():
    """Same MySQL advisory lock as Core, held by a dedicated connection during the dump."""
    command = ["docker", "exec", "-i", MYSQL_CONTAINER, "sh", "-c",
               'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --batch --skip-column-names --unbuffered -u "$MYSQL_USER" "$MYSQL_DATABASE"']
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    connection_id = None
    try:
        process.stdin.write(b"SELECT IF(GET_LOCK('tropicube:core:schema',30)=1,CONNECTION_ID(),0); DO SLEEP(3600);\n")
        process.stdin.close()
        # Read with a bounded helper; a stalled database must not wedge the timer indefinitely.
        import concurrent.futures
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)
        try:
            line = executor.submit(process.stdout.readline).result(timeout=45).decode().strip()
            if not line.isdigit() or line == "0": raise RuntimeError("Cannot acquire schema backup lock")
            connection_id = int(line)
            yield process
        finally:
            if connection_id:
                mysql(f"KILL CONNECTION {connection_id};")
            process.kill()
            process.wait(timeout=10)
            executor.shutdown(wait=False, cancel_futures=True)
    finally:
        if process.poll() is None:
            process.kill(); process.wait(timeout=10)


def checksum(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def archive_tree(source, destination):
    """Only sources, never player data, symlink targets, third-party JARs or build output."""
    excluded = {"players", "playerdata", "stats", "advancements", "session.lock", "level.dat_old"}
    with tarfile.open(destination, "w:gz") as archive:
        for path in sorted(source.rglob("*")):
            relative = path.relative_to(source)
            if path.is_symlink() or any(part in excluded for part in relative.parts): continue
            if path.is_file(): archive.add(path, arcname=str(relative), recursive=False)


def validate_backup_configuration():
    if not os.environ.get("RESTIC_REPOSITORY", "").startswith("sftp:"):
        raise ValueError("RESTIC_REPOSITORY must name an off-host sftp: repository")
    if not os.environ.get("RESTIC_PASSWORD_FILE"):
        raise ValueError("RESTIC_PASSWORD_FILE is required")


def backup():
    validate_backup_configuration()
    state = state_dir()
    write_json(state / "backup-status.json", {"state": "running", "at": time.time()})
    try:
        with tempfile.TemporaryDirectory(prefix="backup-", dir=state) as temporary:
            stage = Path(temporary)
            with schema_lock() as holder:
                with (stage / "mysql.sql").open("wb") as dump:
                    docker("exec", MYSQL_CONTAINER, "sh", "-c",
                           'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump -u "$MYSQL_USER" --single-transaction --quick --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"',
                           output=dump, timeout=1800)
                if holder.poll() is not None: raise RuntimeError("Schema lock connection lost during dump")
            remote_rdb = "/tmp/tropicube-backup-" + stage.name + ".rdb"
            try:
                redis("--rdb", remote_rdb)
                docker("cp", REDIS_CONTAINER + ":" + remote_rdb, str(stage / "redis.rdb"))
            finally:
                docker("exec", REDIS_CONTAINER, "rm", "-f", remote_rdb)
            docker("cp", "tropicube-velocity:/server/plugins/floodgate", str(stage / "floodgate"))
            archive_tree(ROOT / "dockerfiles" / "configs", stage / "configs.tar.gz")
            archive_tree(ROOT / "dockerfiles" / "worlds", stage / "worlds.tar.gz")
            shutil.copy2(ROOT / ".env", stage / "environment.env")
            manifest = {"format": 1, "created_at": time.time(), "git_revision": run(["git", "rev-parse", "HEAD"]),
                        "images": {name: docker("image", "inspect", "tropicube-" + name + ":latest", "--format", "{{.Id}}") for name in IMAGES},
                        "files": {str(p.relative_to(stage)): checksum(p) for p in stage.rglob("*") if p.is_file()}}
            write_json(stage / "manifest.json", manifest)
            run(["restic", "backup", str(stage), "--tag", "tropicube", "--json"], timeout=3600)
            run(["restic", "forget", "--tag", "tropicube", "--group-by", "host,tags", "--keep-daily", "7",
                 "--keep-weekly", "4", "--keep-monthly", "3", "--prune"], timeout=3600)
            run(["restic", "check"], timeout=3600)
        write_json(state / "last-backup.json", {"completed_at": time.time()})
        write_json(state / "backup-status.json", {"state": "ok", "at": time.time()})
    except Exception:
        write_json(state / "backup-status.json", {"state": "failed", "at": time.time()})
        raise


def verify_backup(path):
    root = Path(path).resolve()
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != 1: raise ValueError("Unsupported backup manifest")
    for name, expected in manifest["files"].items():
        candidate = (root / name).resolve()
        if not candidate.is_relative_to(root) or not candidate.is_file() or checksum(candidate) != expected:
            raise ValueError("Backup integrity failure")
    for required in ("mysql.sql", "redis.rdb", "configs.tar.gz", "worlds.tar.gz", "environment.env"):
        if required not in manifest["files"]: raise ValueError("Incomplete backup manifest")
    return manifest


def diagnose():
    result = {"at": time.time(), "alerts": [], "services": {}}
    for name in ("redis", "mysql", "docker-proxy", "velocity"):
        try:
            state = json.loads(docker("inspect", "tropicube-" + name, "--format", "{{json .State}}"))
            healthy = state.get("Running", False) and state.get("Health", {}).get("Status") == "healthy"
            result["services"][name] = "healthy" if healthy else "unavailable"
            if not healthy: result["alerts"].append("service:" + name)
        except Exception: result["alerts"].append("service:" + name)
    total, used, _ = shutil.disk_usage(state_dir())
    result["disk_used_percent"] = round(used * 100 / total, 1)
    if used / total > .8: result["alerts"].append("disk>80%")
    try:
        last = json.loads((state_dir() / "last-backup.json").read_text())["completed_at"]
        result["backup_age_hours"] = round((time.time() - last) / 3600, 2)
        if time.time() - last > 26 * 3600: result["alerts"].append("backup>26h")
        status = json.loads((state_dir() / "backup-status.json").read_text())
        if status["state"] == "failed": result["alerts"].append("backup_failed")
    except (OSError, ValueError, KeyError): result["alerts"].append("backup_missing")
    for name, operation in (("mysql", lambda: mysql("SELECT 1;")), ("redis", lambda: redis("PING"))):
        started = time.monotonic()
        try:
            operation()
            result[name + "_probe_millis"] = round((time.monotonic() - started) * 1000)
        except Exception: result["alerts"].append(name + "_probe_failed")
    try:
        raw = docker("stats", "--no-stream", "--format", "{{json .}}", timeout=30)
        # Explicit allowlist: never export environment, IPs, commands or player data.
        result["containers"] = [{key: row[key] for key in ("Name", "CPUPerc", "MemUsage", "MemPerc")}
                                for row in map(json.loads, raw.splitlines()) if row["Name"].startswith("tropicube-")]
        result["runtime"] = json.loads(redis("GET", "tropicube:health:proxy") or "null")
        if result["runtime"] is None: result["alerts"].append("proxy_metrics_missing")
        elif result["runtime"]["active_instances"] < result["runtime"]["expected_min"]:
            result["alerts"].append("instances_below_minimum")
        aggregate = {"active_sql": 0, "queued_sql": 0, "ready_backends": 0}
        keys = redis("--scan", "--pattern", "tropicube:health:backend:*").splitlines()
        values = redis("MGET", *keys[:100]).splitlines() if keys else []
        for raw_value in values:
            value = json.loads(raw_value or "null")
            if value:
                aggregate["active_sql"] += value["active_sql"]
                aggregate["queued_sql"] += value["queued_sql"]
                aggregate["ready_backends"] += int(value["ready"])
        result["backend_totals"] = aggregate
    except Exception: result["alerts"].append("runtime_metrics_unavailable")
    write_json(state_dir() / "diagnostic.json", result)
    print(json.dumps(result, indent=2))
    return 1 if result["alerts"] else 0


def activate(tag, restart):
    if not re.fullmatch(r"[0-9]{8}-[0-9]{6}", tag): raise ValueError("Expected UTC image lot YYYYMMDD-HHMMSS")
    # Resolve the entire lot before any live tag is changed.
    target = {name: docker("image", "inspect", f"tropicube-{name}:{tag}", "--format", "{{.Id}}") for name in IMAGES}
    previous = {}
    for name in IMAGES:
        try: previous[name] = docker("image", "inspect", f"tropicube-{name}:latest", "--format", "{{.Id}}")
        except RuntimeError: pass
    manifest = {"lot": tag, "images": target, "previous": previous, "state": "prepared", "created_at": time.time()}
    path = state_dir() / "releases" / (tag + ".json")
    if path.exists(): raise ValueError("Release manifest already exists")
    write_json(path, manifest)
    running = docker("ps", "--filter", "name=^/tropicube-velocity$", "--format", "{{.ID}}")
    if running:
        validate_backup_configuration()
        if redis("PING") != "PONG": raise RuntimeError("Redis backup authentication unavailable")
        if not restart: raise ValueError("Cannot activate live tags with --skip-restart; candidate images remain staged")
        docker("exec", "tropicube-velocity", "rcon-cli", "maintenance network on 1 Infrastructure deployment")
        # Drain before replacement; the existing command disconnects residual players at its deadline.
        for _ in range(13): time.sleep(5)
        backup()
        docker("compose", "stop", "velocity", timeout=180)
        remaining = docker("ps", "--filter", "label=fr.tropicube.dynamic=true", "--format", "{{.ID}}")
        if remaining: raise RuntimeError("Dynamic backends are still running; stop them before replacing the lot")
    try:
        for name, image_id in previous.items():
            docker("tag", image_id, f"tropicube-{name}:previous")
        for name, image_id in target.items():
            docker("tag", image_id, f"tropicube-{name}:latest")
        if restart:
            docker("compose", "up", "-d", "--no-build", "--wait", "--wait-timeout", "180", "velocity", timeout=240)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                containers = docker("ps", "--filter", "label=fr.tropicube.dynamic=true", "--filter",
                                    "label=fr.tropicube.template-id=lobby", "--format", "{{.ID}}").splitlines()
                instance_ids = [docker("inspect", container, "--format", '{{index .Config.Labels "fr.tropicube.instance-id"}}')
                                for container in containers]
                if any(json.loads(redis("GET", "tropicube:health:backend:" + instance) or "{}").get("ready", False)
                       for instance in instance_ids): break
                time.sleep(3)
            else: raise RuntimeError("No ready backend after deployment")
            # Maintenance intentionally survives a restart; clear it only after backend health succeeds.
            docker("exec", "tropicube-velocity", "rcon-cli", "maintenance network off")
        manifest["state"] = "active"
        write_json(path, manifest)
    except Exception:
        # Never restart an old binary against an unknown schema after a failed deployment.
        manifest["state"] = "failed"
        write_json(path, manifest)
        raise RuntimeError("Activation failed. Network must remain closed; inspect release manifest and schema before rollback") from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("backup")
    sub.add_parser("diagnose")
    verify = sub.add_parser("verify-backup"); verify.add_argument("directory")
    release = sub.add_parser("activate"); release.add_argument("tag"); release.add_argument("--skip-restart", action="store_true")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.command == "diagnose": return diagnose()
        if args.command == "verify-backup": verify_backup(args.directory); print("Backup integrity verified"); return 0
        with operation_lock():
            if args.command == "backup": backup()
            elif args.command == "activate": activate(args.tag, not args.skip_restart)
        return 0
    except Exception as error:
        print(f"Operations failed: {type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
