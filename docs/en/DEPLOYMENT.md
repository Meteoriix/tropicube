# Deployment

## Access-level migration

Migration V008 permanently removes `tropicube_permissions` and the former grade permission fields. Take a MySQL dump before the first deployment, then recreate Velocity and every Paper backend in one maintenance window because older builds do not understand `player:access:<uuid>`. Core serializes schema preparation across backends, and V008 resumes an interrupted execution from its `MIGRATION` audit without incrementing revisions or duplicating those rows. Confirm that V008 appears in `tropicube_schema_migrations`, then validate all eight grade defaults, `/level`, queue priority, and `/nick`. Rolling back requires the pre-V008 SQL backup.

## Supported platforms

The optional `language-editor` Compose profile starts LibreTranslate on localhost and keeps its models in `libretranslate-models`. It is not required by the Minecraft network and must not be exposed publicly.

The repository supports Windows through `deploy.ps1` and Linux through `deploy.sh`. Both paths build the same Maven reactor, redistribute plugin artifacts, validate Docker inputs, and rebuild the selected images. Java 25, Maven 3.9.11, Docker Compose, Git LFS, and Node.js are required. The host must expose `25565/tcp` for Java and `BEDROCK_PORT` (`19132` by default) over UDP for Bedrock.

## Initial setup

1. Clone the repository with Git LFS enabled.
2. Copy `.env.example` to `.env`.
3. Replace placeholder passwords and the forwarding secret locally.
4. Ensure `TROPICUBE_PROJECT_PATH` is an absolute path visible to the Docker host.
5. Validate Compose and scripts before starting services.

```powershell
git lfs pull
docker compose --env-file .env.example config --quiet
.\deploy.ps1 -OnlyImages -ValidateOnly
```

```bash
git lfs pull
docker compose --env-file .env.example config --quiet
./deploy.sh --only-images --validate-only
```

Never commit `.env`, real passwords, tokens, SQL exports, forwarding secrets, or third-party JARs.

## Build and validation

```powershell
.\mvnw.cmd clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
```

The Maven build creates normal and shaded plugin JARs under each module's `target/` directory. Deployment scripts copy the runtime artifacts into the Docker build contexts. The Velocity image downloads pinned official Geyser and Floodgate builds and BuildKit verifies their SHA-256 hashes, so no third-party JAR is committed. During `process-resources`, Core and Velocity also copy their `src/main/resources/languages/*.yml` files to the matching directories under `dockerfiles/configs`. These embedded files are the source of truth; direct edits to Docker language copies are overwritten. Deployment repeats and verifies the exact copy, including in `OnlyImages` mode. A red build must never be deployed.

Velocity RCON is enabled only inside its container for local editor language reloads; its port is not published on the host. The dedicated Core and Velocity `languageeditorreload` commands reject players. After these commands have been delivered once, the editor copies validated YAML files and reloads them without rebuilding images.

## Windows workflow

```powershell
.\deploy.ps1
.\deploy.ps1 -OnlyImages
.\deploy.ps1 -SkipRestart
.\deploy.ps1 -OnlyImages -ValidateOnly
```

- the default path builds, redistributes artifacts, rebuilds images, and recreates the required services;
- `-OnlyImages` skips Maven when already validated artifacts are available;
- `-SkipRestart` avoids recreating Velocity;
- `-ValidateOnly` checks inputs and artifact redistribution without building an image.

## Linux workflow

```bash
./deploy.sh
./deploy.sh --only-images
./deploy.sh --skip-restart
./deploy.sh --only-images --validate-only
```

The Linux script mirrors the Windows behavior. Paths must remain portable and quoted. Validate shell syntax and, when available, run ShellCheck.

## Compose services

| Service | Role |
|---|---|
| `velocity` | Public proxy, routing, queues, and instance orchestration |
| `redis` | Ephemeral state and event bus |
| `mysql` | Durable player and game data |
| Docker socket proxy | Restricted Docker API exposed to Velocity |

Paper game servers are dynamic containers. Their Minecraft ports remain on the internal Docker network. Velocity registers and unregisters them at runtime.

## Worlds and Git LFS

Minecraft `.mca` region files are tracked through Git LFS. Never replace them with regular Git blobs. Player data, logs, caches, runtime configuration, and generated server files must stay outside version control.

Before committing a world change:

```powershell
git lfs status
git lfs ls-files
```

## Forwarding

Velocity and every Paper backend must receive the same modern-forwarding secret from the environment. Do not place a real secret in `velocity.toml`, `paper-global.yml`, an image layer, or Git.

## Dynamic instance lifecycle

Velocity creates a container from the selected template with a labelled ephemeral `/data` volume, waits for health checks, registers it with the proxy, and stores its `ServerInstance` in Redis. Empty servers are stopped after their configured delay. A finished minigame asks Velocity to transfer players and immediately remove its container, data volume, and shared state. Labelled orphan volumes are purged on startup.

The Velocity container uses a `tmpfs` mount for `/server`, seeded from the image on every start. A nested named volume at `/server/plugins/floodgate` keeps Floodgate's generated private key and linking data across recreations. Never delete `floodgate-data` during a normal deployment or commit its `key.pem`. During the first deployment of this layout, the deployment scripts detect and remove only the legacy anonymous `/server` volume after recreating the proxy.

For private custom games, Velocity injects `HOST_UUID` and `CUSTOM_GAME_PRIVATE=true`; do not hard-code them in a template. If the host item or access list is missing, inspect `host:<uuid>` and the `whitelistedPlayers` field of `instance:<id>` in Redis. A target must have joined the network previously, unless the host supplies its UUID directly.

The proxy's normal shutdown may preserve or remove dynamic instances according to `remove-dynamic-servers-on-shutdown`. Production deployments should validate this choice explicitly.

## Operational checks

- confirm Velocity can reach Redis, MySQL, and the Docker socket proxy;
- confirm Java connects through `25565/tcp` and Bedrock through `BEDROCK_PORT/udp`;
- confirm the required Geyser pack loads, menus omit decorative panes, all eleven HeadDatabase icons render, and dynamic player entries use their status icons without a Steve fallback;
- run `geyser connectiontest <public-host> <BEDROCK_PORT>` from the Velocity console after firewall/NAT changes;
- confirm Paper receives forwarded identities and cannot be reached publicly;
- confirm lobby selectors reflect `GAME_WAITING`, `GAME_STARTING`, `GAME_PLAYING`, and `GAME_ENDING` correctly;
- confirm a playing SheepWars instance admits late arrivals as spectators;
- confirm shutdown removes scheduled tasks, subscriptions, containers, and ephemeral game volumes as configured;
- inspect logs without exposing credentials.

## Rollback

Keep image tags and deployment inputs reproducible. Roll back by restoring the prior code revision, rebuilding the affected images, and recreating services. Database changes must provide a compatible rollback or a documented forward-only migration. Never use destructive Git commands on a dirty worktree.
