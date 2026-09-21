#!/usr/bin/env python3
"""Boot a verified image lot in a separate Compose project, with owned dynamic resources."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]


def command(*args, timeout=120):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    if result.returncode: raise RuntimeError(f"{args[0]} failed (exit {result.returncode}); test logs retained privately")
    return result.stdout.strip()


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    args = parser.parse_args()
    if not re.fullmatch(r"\d{8}-\d{6}", args.tag): raise ValueError("Expected UTC image lot")
    project = "tropicube-smoke-" + uuid.uuid4().hex[:8]
    for name in ("lobby", "sheepwars", "fallenkingdoms", "velocity"):
        command("docker", "image", "inspect", f"tropicube-{name}:{args.tag}", "--format", "{{.Id}}")
    private = ROOT / ".runtime" / "smoke"
    private.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=project, dir=private) as temporary:
        stage = Path(temporary)
        # Only demonstration values enter the isolated stack. Production .env is never read.
        spec = json.loads(command("docker", "compose", "--env-file", ".env.example", "config", "--format", "json"))
        spec["name"] = project
        for name, network in spec["networks"].items(): network["name"] = project + "-" + name
        for name, volume in spec["volumes"].items(): volume["name"] = project + "-" + name
        services = {name: spec["services"][name] for name in ("mysql", "redis", "docker-proxy", "velocity")}
        spec["services"] = services
        for name, service in services.items():
            service["container_name"] = project + "-" + name
            service.pop("build", None)
            service.pop("ports", None)
            if name == "velocity":
                service["image"] = "tropicube-velocity:" + args.tag
                service["environment"]["TOTP_MASTER_KEY"] = base64.b64encode(os.urandom(32)).decode()
        source = (ROOT / "dockerfiles/configs/TropicubeVelocity/config.yml").read_text(encoding="utf-8")
        source = source.replace('network: "tropicube-net"', 'network: "' + project + '-tropicube-net"')
        source = source.replace('container-prefix: "tropicube"', 'container-prefix: "' + project + '"')
        source = source.replace(":latest", ":" + args.tag)
        # Dynamic backend bindings use a dedicated range; refuse a collision rather than stopping anything.
        source = re.sub(r"(?m)(^\s+(?:port-range-start|port-range-end|rcon-port-range-start|rcon-port-range-end|port-min|port-max):\s+)(\d+)",
                        lambda match: match[1] + str(int(match[2]) + 10000), source)
        (stage / "config.yml").write_text(source, encoding="utf-8")
        services["velocity"].setdefault("volumes", []).append({"type": "bind", "source": str(stage / "config.yml"),
                "target": "/opt/tropicube/server/plugins/tropicube-velocity/config.yml", "read_only": True})
        (stage / "compose.json").write_text(json.dumps(spec), encoding="utf-8")
        compose = ["docker", "compose", "-p", project, "-f", str(stage / "compose.json")]
        logs = private / (project + ".log")
        backend_logs = {}
        try:
            command(*compose, "up", "-d", "--wait", "--wait-timeout", "180", timeout=240)
            command("docker", "exec", project + "-velocity", "rcon-cli", "tropi", "start", "sheepwars")
            command("docker", "exec", project + "-velocity", "rcon-cli", "tropi", "start", "fallenkingdoms")
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                containers = command("docker", "ps", "-q", "--filter", "label=fr.tropicube.owner=" + project).splitlines()
                ready = set()
                for container in containers:
                    output = command("docker", "logs", container, timeout=15)
                    backend_logs[container] = output
                    if "TROPICUBE_BACKEND_READY" in output:
                        template = command("docker", "inspect", container, "--format", '{{index .Config.Labels "fr.tropicube.template-id"}}')
                        ready.add(template)
                if {"lobby", "sheepwars", "fallenkingdoms"}.issubset(ready):
                    print("Isolated lot ready: Velocity, Lobby, SheepWars and Fallen Kingdoms")
                    break
                time.sleep(3)
            else: raise RuntimeError("Backends failed to become ready")
            fatal_markers = ("[ERROR]", "Exception:", "java.lang.")
            if any(marker in output for output in backend_logs.values() for marker in fatal_markers):
                raise RuntimeError("A backend logged an error or exception")
            command(*compose, "stop", "velocity", timeout=180)
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if not command("docker", "ps", "-aq", "--filter", "label=fr.tropicube.owner=" + project): break
                time.sleep(2)
            else: raise RuntimeError("Dynamic containers survived proxy shutdown")
            print("Isolated lot shutdown: owned dynamic containers removed")
        finally:
            with logs.open("w", encoding="utf-8") as output:
                subprocess.run([*compose, "logs", "--no-color"], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
                for container, content in backend_logs.items():
                    output.write("\n--- " + container + " ---\n" + content + "\n")
                containers = command("docker", "ps", "-aq", "--filter", "label=fr.tropicube.owner=" + project).splitlines()
                for container in containers:
                    subprocess.run(["docker", "logs", container], stdout=output, stderr=subprocess.STDOUT)
                    command("docker", "rm", "-f", "-v", container)
            volumes = command("docker", "volume", "ls", "-q", "--filter", "label=fr.tropicube.owner=" + project).splitlines()
            for volume in volumes: command("docker", "volume", "rm", volume)
            command(*compose, "down", "--volumes", "--remove-orphans", timeout=180)
            print("Private test logs:", logs)


if __name__ == "__main__": main()
