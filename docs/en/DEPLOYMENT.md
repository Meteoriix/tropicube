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

The Maven build creates normal and shaded plugin JARs under each module's `target/` directory. Deployment scripts copy the runtime artifacts into the Docker build contexts. The operator-provided HeadDatabase and NoChatReports references live outside Git under `dockerfiles/plugins/lobby`; deployment copies them with hash verification to SheepWars and Fallen Kingdoms. The Velocity image downloads pinned official Geyser and Floodgate builds and BuildKit verifies their SHA-256 hashes, so no third-party JAR is committed. During `process-resources`, Core and Velocity also copy their `src/main/resources/languages/*.yml` files to the matching directories under `dockerfiles/configs`. These embedded files are the source of truth; direct edits to Docker language copies are overwritten. Deployment repeats and verifies the exact copy, including in `OnlyImages` mode. A red build must never be deployed.

Velocity RCON is enabled only inside its container for local editor language reloads; its port is not published on the host. The dedicated Core and Velocity `languageeditorreload` commands reject players. After these commands have been delivered once, the editor copies validated YAML files and reloads them without rebuilding images.

## Windows workflow

```powershell
.\deploy.ps1
.\deploy.ps1 -OnlyImages
.\deploy.ps1 -SkipRestart
.\deploy.ps1 -OnlyImages -ValidateOnly
```

- the default path is the development redeploy: it builds, redistributes and verifies images, then tags them as `latest` and recreates Velocity without maintenance, backup, or a drain delay; running dynamic games are interrupted;
- `-OnlyImages` skips Maven when already validated artifacts are available;
- `-SkipRestart` builds and verifies a UTC candidate lot without changing `latest` tags or recreating Velocity;
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

The enabled `fallenkingdoms` template uses private ports 25660–25669 and injects `MAP_ID=cactus`. Its image copies the immutable world from `dockerfiles/worlds/fallenkingdoms/`; player data, locks, and old level backups stay outside Git and the image context. Add another enabled `locations.maps` definition and change `MAP_ID` to deploy another map without changing game code. The embedded profile reserves 2–4 GiB; the development Docker configuration caps FK instances at 1–2 GiB so Lobby, SheepWars, and Fallen Kingdoms fit together within the local 8 GiB budget.

## Operational checks

- confirm Velocity can reach Redis, MySQL, and the Docker socket proxy;
- confirm Java connects through `25565/tcp` and Bedrock through `BEDROCK_PORT/udp`;
- confirm the required Geyser pack loads, menus omit decorative panes, all eleven HeadDatabase icons render, and dynamic player entries use their status icons without a Steve fallback;
- run `geyser connectiontest <public-host> <BEDROCK_PORT>` from the Velocity console after firewall/NAT changes;
- confirm Paper receives forwarded identities and cannot be reached publicly;
- confirm lobby selectors reflect `GAME_WAITING`, `GAME_STARTING`, `GAME_PLAYING`, and `GAME_ENDING` correctly;
- confirm a playing SheepWars instance admits late arrivals as spectators;
- start Fallen Kingdoms with 8 players, verify the Cactus spawns and regions, destroy a heart during a pending respawn, and confirm the final 50×50 border and Velocity cleanup;
- confirm SheepWars exposes one power-up block at a time, changes its location after activation, and shows no literal MiniMessage tag in the final summary;
- confirm shutdown removes scheduled tasks, subscriptions, containers, and ephemeral game volumes as configured;
- inspect logs without exposing credentials.

## Rollback

Keep image tags and deployment inputs reproducible. Roll back by restoring the prior code revision, rebuilding the affected images, and recreating services. Database changes must provide a compatible rollback or a documented forward-only migration. Never use destructive Git commands on a dirty worktree.

## Guild menu verification

Update Core and Lobby together, then check Java and Bedrock: three Social tabs, no Guilds button in Profile, private name/tag creation, invitations, lists exceeding 21 members, challenges and empty/populated rankings. Check member/officer/owner permissions and removal/transfer/leave confirmations, including last-member deletion. Private input must never reach other players' chat. Test `!`, timeout, logout, navigation during loading, double clicks and `/lang` in all four languages. Simulate a SQL failure and use Refresh. No production port or volume change is needed.

## Development redeploy

`./deploy.ps1` and `./deploy.sh` are the normal development commands. After image verification, they update all three `latest` tags and run `docker compose up -d --no-build --force-recreate --wait --wait-timeout 180 velocity`. They do not call `/maintenance`, create a Restic backup, or wait for a drain. Velocity's default `shutdown.stop-dynamic-servers: true` therefore immediately stops dynamic instances and running games.

## Reliable image lots, backups and acceptance

For a production release, `--skip-restart` / `-SkipRestart` builds and verifies only UTC candidate tags without modifying live tags. Activate a verified lot with `python3 tools/ops/tropicube_ops.py activate YYYYMMDD-HHMMSS`. On a running network, activation drains maintenance for one minute, requires an off-host backup, stops Velocity and requires all dynamic backends to stop. Previous image IDs are retained in private release manifests and local `previous` tags. Compose/proxy and lobby readiness must pass before maintenance is lifted. Incompatible schemas require a matching backup, not just older images.

Install the systemd units in `tools/ops/systemd`, configure `/etc/tropicube/ops.env` from the supplied example with mode 0600 and initialize the remote Restic repository. Enable the backup and diagnostic timers. Daily backups contain a consistent MySQL dump protected against concurrent migrations, Redis RDB, Floodgate data, source worlds, configurations and private environment. Ephemeral game volumes/player world data are excluded. Retention is 7 daily, 4 weekly, 3 monthly snapshots. The success timestamp advances only after backup, retention and repository checks succeed.

Restore first on an isolated host with fresh data volumes. Restore the encrypted snapshot, verify its manifest using `verify-backup`, import MySQL, restore configuration/Floodgate and load Redis RDB with AOF disabled initially. Enable AOF after loading and wait for rewrite before returning to normal Compose configuration. Start the matching images and verify data and routing. Never mix an older AOF with the restored RDB. Test recovery within two hours with at most 24 hours of lost data. Retain recovery keys and private third-party artifacts separately; image bytes are not part of these backups.

`diagnose` emits filtered JSON and a nonzero exit code on service, backup, disk or capacity alerts. The minute timer reports to the local journal; no external notification destination is configured. Run `python3 tools/ops/smoke_test.py <lot>` to boot/stop an isolated network with owned dynamic resources. Before opening, use Spark and diagnostics at 10/25/50 real players for an hour, plus twenty game cycles: no OOM, no sustained post-cleanup growth, tick p95 below 50 ms. These hardware/client acceptance tests remain mandatory.
