"""Failure-path tests for local operations; no Docker daemon or repository credentials used."""
import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch

import tropicube_ops as ops
import windows_task


class OperationsTest(unittest.TestCase):
    @unittest.skipUnless(ops.os.name == "nt", "Windows console regression")
    def test_child_process_has_no_console_and_preserves_output(self):
        result = ops.run([sys.executable, "-c",
                          "import ctypes; print(ctypes.windll.kernel32.GetConsoleWindow())"])
        self.assertEqual("0", result)

    def test_windowless_task_logs_output_and_propagates_failure(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(ops.os.environ):
            settings = {name: temporary for name in ("RESTIC_REPOSITORY", "RESTIC_PASSWORD_FILE",
                        "TROPICUBE_OPS_STATE", "TROPICUBE_BACKUP_MODE", "toolsDirectory", "dockerDirectory", "gitDirectory")}
            def failed(arguments):
                self.assertEqual(["diagnose"], arguments)
                print("diagnostic failure", file=sys.stderr)
                return 1
            with patch.object(ops, "main", side_effect=failed):
                self.assertEqual(1, windows_task.run_task("diagnose", settings))
            self.assertIn("diagnostic failure", (Path(temporary) / "diagnose-task.log").read_text())

    def test_windowless_backup_skips_stopped_stack_without_advancing_success(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(ops.os.environ):
            settings = {name: temporary for name in ("RESTIC_REPOSITORY", "RESTIC_PASSWORD_FILE",
                        "TROPICUBE_OPS_STATE", "TROPICUBE_BACKUP_MODE", "toolsDirectory", "dockerDirectory", "gitDirectory")}
            last = Path(temporary) / "last-backup.json"
            last.write_text('{"completed_at": 10}')
            with patch.object(ops, "docker", return_value=""), patch.object(ops, "main") as operation:
                self.assertEqual(0, windows_task.run_task("backup", settings))
                operation.assert_not_called()
            self.assertEqual(10, json.loads(last.read_text())["completed_at"])
            self.assertIn("skipped", (Path(temporary) / "backup-task.log").read_text())

    def test_local_backup_requires_explicit_development_mode(self):
        with tempfile.TemporaryDirectory() as temporary:
            environment = {"RESTIC_REPOSITORY": "local:" + temporary, "RESTIC_PASSWORD_FILE": "/test"}
            with patch.dict(ops.os.environ, environment, clear=True):
                with self.assertRaises(ValueError): ops.validate_backup_configuration()
                ops.os.environ["TROPICUBE_BACKUP_MODE"] = "local-development"
                ops.validate_backup_configuration()
                for destination in ("local:relative", "local:" + str(ops.ROOT / "backups"), "sftp:test:/backup"):
                    ops.os.environ["RESTIC_REPOSITORY"] = destination
                    with self.assertRaises(ValueError): ops.validate_backup_configuration()

    def test_backup_configuration_rejects_unknown_mode_and_missing_password(self):
        with patch.dict(ops.os.environ, {"RESTIC_REPOSITORY": "sftp:test:/backup"}, clear=True):
            with self.assertRaises(ValueError): ops.validate_backup_configuration()
            ops.os.environ["RESTIC_PASSWORD_FILE"] = "/test"
            ops.validate_backup_configuration()
            ops.os.environ["TROPICUBE_BACKUP_MODE"] = "local"
            with self.assertRaises(ValueError): ops.validate_backup_configuration()

    def test_manifest_rejects_tampering_and_traversal(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            names = ("mysql.sql", "redis.rdb", "configs.tar.gz", "worlds.tar.gz", "environment.env")
            for name in names: (root / name).write_bytes(b"test")
            manifest = {"format": 1, "files": {name: ops.checksum(root / name) for name in names}}
            ops.write_json(root / "manifest.json", manifest)
            ops.verify_backup(root)
            (root / "mysql.sql").write_bytes(b"altered")
            with self.assertRaises(ValueError): ops.verify_backup(root)
            manifest["files"] = {"../outside": "0" * 64}
            ops.write_json(root / "manifest.json", manifest)
            with self.assertRaises(ValueError): ops.verify_backup(root)

    def test_archive_excludes_player_state(self):
        import tarfile
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "world"; source.mkdir()
            (source / "region").mkdir(); (source / "region" / "r.0.0.mca").write_bytes(b"region")
            (source / "playerdata").mkdir(); (source / "playerdata" / "player.dat").write_bytes(b"private")
            (source / "session.lock").write_bytes(b"lock")
            ops.archive_tree(source, root / "archive.tar.gz")
            with tarfile.open(root / "archive.tar.gz") as archive:
                self.assertEqual(["region/r.0.0.mca"], [name.replace("\\", "/") for name in archive.getnames()])

    def test_missing_image_never_changes_tags(self):
        calls = []
        def docker(*args, **kwargs):
            calls.append(args)
            if "tropicube-sheepwars:20260906-120000" in args: raise RuntimeError("missing image")
            return "sha256:test"
        with patch.object(ops, "docker", side_effect=docker):
            with self.assertRaises(RuntimeError): ops.activate("20260906-120000", True)
        self.assertFalse(any(call[0] == "tag" for call in calls))

    def test_release_lot_covers_every_runtime_image(self):
        self.assertEqual(("lobby", "sheepwars", "fallenkingdoms", "velocity"), ops.IMAGES)
        self.assertEqual(("mysql", "redis", "docker-proxy"), ops.STATIC_SERVICES)

    def test_rollback_requires_a_complete_previous_image_set(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(ops, "state_dir", return_value=Path(temporary)):
            releases = Path(temporary) / "releases"
            releases.mkdir()
            ops.write_json(releases / "20260917-120000.json", {"previous": {}, "static_previous": {}})
            with self.assertRaises(ValueError):
                ops.rollback("20260917-120000")

    def test_rollback_refuses_to_retag_while_dynamic_backends_remain(self):
        calls = []
        responses = iter(("velocity", "dynamic-backend"))

        def docker(*args, **kwargs):
            calls.append(args)
            if args[0] == "ps":
                return next(responses)
            return ""

        with tempfile.TemporaryDirectory() as temporary, patch.object(ops, "state_dir", return_value=Path(temporary)), \
                patch.object(ops, "docker", side_effect=docker), patch.object(ops.time, "sleep"):
            releases = Path(temporary) / "releases"
            releases.mkdir()
            manifest = {
                "previous": {name: "sha256:" + name for name in ops.IMAGES},
                "static_previous": {name: "sha256:" + name for name in ops.STATIC_SERVICES},
            }
            ops.write_json(releases / "20260917-120000.json", manifest)
            with self.assertRaises(RuntimeError):
                ops.rollback("20260917-120000")
        self.assertFalse(any(call[0] == "tag" for call in calls))

    def test_failed_backup_never_replaces_last_success(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(ops, "state_dir", return_value=Path(temporary)), \
                patch.dict(ops.os.environ, {"RESTIC_REPOSITORY": "sftp:test:/backup", "RESTIC_PASSWORD_FILE": "/test"}), \
                patch.object(ops, "schema_lock", side_effect=RuntimeError("locked")):
            ops.write_json(Path(temporary) / "last-backup.json", {"completed_at": 10})
            with self.assertRaises(RuntimeError): ops.backup()
            self.assertEqual(10, json.loads((Path(temporary) / "last-backup.json").read_text())["completed_at"])
            self.assertEqual("failed", json.loads((Path(temporary) / "backup-status.json").read_text())["state"])


if __name__ == "__main__": unittest.main()
