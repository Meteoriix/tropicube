"""Scheduled-task entry point for pythonw.exe: no PowerShell or console window.

The scheduler waits for this process and receives the operation's exit code.
Output is redirected because pythonw has no standard console streams.
"""
import argparse
import contextlib
import json
import os
import sys

import tropicube_ops as ops


def run_task(command, settings):
    """Load the same private settings as windows.ps1 and retain the last task log."""
    for name in ("RESTIC_REPOSITORY", "RESTIC_PASSWORD_FILE", "TROPICUBE_OPS_STATE", "TROPICUBE_BACKUP_MODE"):
        os.environ[name] = settings[name]
    os.environ["PATH"] = os.pathsep.join([settings[name] for name in
                                         ("toolsDirectory", "dockerDirectory", "gitDirectory")]
                                        + [os.environ.get("PATH", "")])
    with (ops.state_dir() / (command + "-task.log")).open("w", encoding="utf-8") as log, \
            contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
        try:
            if command == "backup":
                try:
                    running = ops.docker("ps", "--filter", "name=^/tropicube-velocity$", "--format", "{{.ID}}")
                except RuntimeError:
                    running = ""
                if not running:
                    print("Backup skipped: development stack is stopped.")
                    return 0
            return ops.main([command])
        except Exception as error:
            # Do not expose subprocess output or private settings in error messages.
            print(f"Scheduled task failed: {type(error).__name__}", file=sys.stderr)
            return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("backup", "diagnose"))
    args = parser.parse_args()
    settings = json.loads((ops.ROOT / ".runtime/windows/settings.json").read_text(encoding="utf-8-sig"))
    return run_task(args.command, settings)


if __name__ == "__main__":
    sys.exit(main())
