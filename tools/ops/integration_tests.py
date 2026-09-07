#!/usr/bin/env python3
"""Create disposable MySQL/Redis, execute real integration tests, then remove only that test project."""
import os
from pathlib import Path
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]


def main():
    project = "tropicube-test-" + uuid.uuid4().hex[:12]
    compose = ["docker", "compose", "-p", project, "-f", str(Path(__file__).with_name("compose.test.yml"))]
    try:
        subprocess.run([*compose, "up", "-d", "--wait", "--wait-timeout", "180"], check=True, cwd=ROOT)
        def port(service, internal):
            return subprocess.check_output([*compose, "port", service, internal], text=True).strip().rsplit(":", 1)[1]
        env = os.environ.copy()
        env["TROPICUBE_TEST_MYSQL_URL"] = "jdbc:mysql://127.0.0.1:" + port("mysql", "3306") + "/tropicube_integration?socketTimeout=10000"
        env["TROPICUBE_TEST_REDIS_PORT"] = port("redis", "6379")
        wrapper = [str(ROOT / "mvnw.cmd")] if os.name == "nt" else ["bash", str(ROOT / "mvnw")]
        result = subprocess.run([*wrapper, "--batch-mode", "--no-transfer-progress", "-pl", "tropicube-core", "-am",
                               "-Dtest=*IntegrationTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"], cwd=ROOT, env=env).returncode
        if result: return result
        # Exercise the production lock helper with the isolated service, never the live containers.
        import tropicube_ops as ops
        ops.MYSQL_CONTAINER = subprocess.check_output([*compose, "ps", "-q", "mysql"], text=True).strip()
        with ops.schema_lock() as holder:
            if ops.mysql("SELECT GET_LOCK('tropicube:core:schema',0);") != "0":
                raise AssertionError("Backup did not exclude concurrent migrations")
        if ops.mysql("SELECT GET_LOCK('tropicube:core:schema',0);") != "1":
            raise AssertionError("Backup lock was not released")
        print("Production backup lock: acquisition, exclusion and release verified")
        # Round-trip a real dump in a second disposable schema, using only synthetic data.
        ops.mysql("CREATE TABLE integration_recovery (id INT PRIMARY KEY, balance DECIMAL(19,2)); "
                  "INSERT INTO integration_recovery VALUES (1,123.45);")
        import tempfile
        with tempfile.TemporaryFile() as dump:
            ops.docker("exec", ops.MYSQL_CONTAINER, "sh", "-c",
                       'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump -u "$MYSQL_USER" --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"', output=dump)
            dump.seek(0)
            subprocess.run(["docker", "exec", "-i", ops.MYSQL_CONTAINER, "sh", "-c",
                            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -uroot -e "CREATE DATABASE tropicube_restore" && exec mysql -uroot tropicube_restore'],
                           stdin=dump, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        restored = ops.docker("exec", ops.MYSQL_CONTAINER, "sh", "-c",
                              'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -uroot --batch --skip-column-names tropicube_restore -e "SELECT balance FROM integration_recovery WHERE id=1"')
        if restored != "123.45": raise AssertionError("Restored monetary value differs")
        print("MySQL dump/import round trip: monetary value preserved")
        return 0
    finally:
        subprocess.run([*compose, "down", "--volumes", "--remove-orphans"], cwd=ROOT, check=True)


if __name__ == "__main__":
    sys.exit(main())
