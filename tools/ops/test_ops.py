"""Failure-path tests for local operations; no Docker daemon or repository credentials used."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import tropicube_ops as ops


class OperationsTest(unittest.TestCase):
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

    def test_failed_backup_never_replaces_last_success(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(ops, "state_dir", return_value=Path(temporary)), \
                patch.dict(ops.os.environ, {"RESTIC_REPOSITORY": "sftp:test:/backup", "RESTIC_PASSWORD_FILE": "/test"}), \
                patch.object(ops, "schema_lock", side_effect=RuntimeError("locked")):
            ops.write_json(Path(temporary) / "last-backup.json", {"completed_at": 10})
            with self.assertRaises(RuntimeError): ops.backup()
            self.assertEqual(10, json.loads((Path(temporary) / "last-backup.json").read_text())["completed_at"])
            self.assertEqual("failed", json.loads((Path(temporary) / "backup-status.json").read_text())["state"])


if __name__ == "__main__": unittest.main()
