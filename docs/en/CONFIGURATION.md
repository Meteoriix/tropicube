# Configuration

## Environment variables

Secrets and deployment-specific values belong in `.env`, never in Git. `.env.example` documents safe placeholders.

The local language editor uses `LIBRETRANSLATE_URL` (default `http://127.0.0.1:5000`), optional `LIBRETRANSLATE_API_KEY`, `TROPICUBE_LANGUAGE_EDITOR_PORT` (default `8765`), `TROPICUBE_DOCKER_COMMAND` (default `docker`), and optional `TROPICUBE_MINECRAFT_CLIENT_JAR` for Minecraft 26.2 textures. Keep the API key exclusively in the local environment.

`menus.yml`, `scoreboards.yml`, and `tablists.yml` use the versioned `version: 1` schema. Menus declare their title, rows, frame, static buttons, and dynamic regions; scoreboards declare a title and variants containing 1–15 lines; tablists declare one header key and one footer key for every state. Builds synchronize embedded resources with Docker mirrors.

For a menu, `frame` is `network`, `neutral`, or `none`. Each dynamic region declares unique slots valid for the inventory size; static buttons may deliberately serve as a loading or empty state for a region. The registry rejects any other frame or invalid layout at startup.

| Variable | Purpose |
|---|---|
| `TROPICUBE_REDIS_HOST`, `TROPICUBE_REDIS_PORT`, `TROPICUBE_REDIS_PASSWORD` | Redis connection |
| `TROPICUBE_MYSQL_HOST`, `TROPICUBE_MYSQL_PORT`, `TROPICUBE_MYSQL_DATABASE` | MySQL location |
| `TROPICUBE_MYSQL_USER`, `TROPICUBE_MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Database credentials |
| `VELOCITY_FORWARDING_SECRET` | Shared Velocity/Paper forwarding secret |
| `TROPICUBE_PROJECT_PATH` | Absolute Docker-host project path used for bind mounts |
| `TOTP_MASTER_KEY` | Base64-encoded AES-256 key used to encrypt staff TOTP secrets |
| `BEDROCK_PORT` | Public Geyser UDP port from `1` to `65535` (`19132` by default) |

The TOTP key must encode exactly 32 random bytes, for example with `openssl rand -base64 32`. Losing it makes existing enrollments unreadable. When absent, Core starts but deliberately keeps protected staff commands locked.

### Report privacy framework

The implementation minimizes captured context and enforces retention, but code alone does not establish GDPR compliance. Before opening the service, the controller must document purpose and legal basis, register the processing, inform players before collection, restrict recipients, provide an effective rights-request channel, assess whether a DPIA is needed, cover processors contractually, and define a breach procedure. The chosen 90-day evidence period is an operator decision rather than a universally approved CNIL period and must be justified and reviewed. See CNIL guidance on [retention](https://www.cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees), [security and minimisation](https://www.cnil.fr/fr/securite-des-donnees-les-regles-essentielles), and [data-subject rights](https://www.cnil.fr/fr/preparer-lexercice-des-droits-des-personnes).

## Velocity

Source: `tropicube-velocity/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeVelocity/config.yml`.

- `party.disconnect-grace-seconds` defaults to `60`, removes members who remain offline after that grace period and transfers leadership to an online member; fully offline parties are deleted immediately;
- `remove-dynamic-servers-on-shutdown` controls cleanup during a normal proxy shutdown;
- health-check values define probe frequency, timeout, and failed-start cleanup;
- `nick.skin-uuids` extends the Mojang skin pool;
- `templates` define Docker image, server type, capacity, scaling, auto-stop, volumes, and environment variables. `max-players` is the participant limit; `spectator-slots` adds backend capacity only for an ongoing game (`8` for SheepWars).

Core publishes persistent, revisioned `player:access:<uuid>` values. Velocity keeps a local fail-closed cache for proxy authorization, `/nick`, and queue priority. `admin-uuids`, `nick.allowed-grades`, and Docker `OPS` are no longer authority sources.

Velocity also accepts the following operational settings:

- `connection-protection.address-limit`, `global-limit`, `window-seconds`, and `quarantine-seconds` control adaptive limits before authentication. No address history is persisted.
- `motd.line-1`, `line-2`, and `maintenance-line` describe only the public Velocity endpoint. `{games}` is replaced with the enabled, deduplicated game types.
- `announcements.interval-seconds` and `announcements.entries[].message-key/target` define the localized rotation. A target is `network`, a type, a template, or an instance.
- `maintenance.default-deadline-minutes` supplies `/maintenance ... on` when no duration is provided. It must range from 1 to 1,440 minutes; the drain is persisted in Redis for at most seven days.

## Geyser and Floodgate

Both plugins run on Velocity only. `Dockerfile.velocity` downloads the official Geyser-Velocity `2.11.2-b1234` and Floodgate-Velocity `2.2.5-b140` artifacts from pinned build URLs and verifies pinned SHA-256 values. Third-party JARs stay out of Git. Before upgrading, verify Java `26.2` and current Bedrock support, then update the URLs, hashes, configuration comments, tests, and documentation together.

`dockerfiles/configs/Geyser-Velocity/config.yml` uses Geyser schema `config-version: 7`, listens on `0.0.0.0:${CFG_BEDROCK_PORT}`, advertises the same UDP port, and requires Floodgate authentication. Direct connection remains enabled. Command suggestions are disabled and Bedrock scaffolding is blocked for SheepWars parity.

Custom content and Geyser's integrated pack are enabled and required. The pack improves Bedrock inventory rendering; `custom_mappings/tropicube-heads.json` pre-registers all eleven static HeadDatabase textures used by menus so they are not replaced with a Steve head. Any new HeadDatabase icon must add its texture hash to this file, followed by a Velocity image rebuild and restart to regenerate the Bedrock pack. Player skins resolved after login cannot enter that already-built pack, so Bedrock interfaces use explicit vanilla icons for those dynamic entries while Java keeps player heads.

`dockerfiles/configs/floodgate/config.yml` does not require Bedrock users to link a Java account. A `.` prefix plus space-to-underscore replacement prevents Java-name collisions; social invitation actions and private SheepWars whitelists accept this strict form. The generated `key.pem` lives in the named `floodgate-data` volume. It must never enter the image or Git and must be backed up as a durable secret.

## Core

Source: `tropicube-core/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeCore/config.yml`.

Core configuration covers Redis, MySQL, the default language, economy cache rules, and the complete cosmetic grade catalog. Each grade defines validated `default-vip-level` (0–3) and `default-mod-level` (0–4); applying a grade always replaces both current levels. `access.audit-retention-days` defaults to 365 and drives the daily SQL audit purge.

Guild limits are `guilds.max-members` (`50`), `guilds.max-officers` (`5`), and `guilds.weekly-contribution-cap` (`5000` XP per member). Match and mission XP automatically feeds the capped guild contribution.

`TropicubeCore/missions.yml` is a versioned business catalog. Every mission declares an event, target, network-XP reward, and currency reward. Stable IDs and a version bump are required when it changes; startup rejects invalid or undersized catalogs.

Supported language resources are `fr`, `en`, `de`, and `es`. A newly created player profile uses the Minecraft client locale when it maps to one of these languages, otherwise it starts in English; existing profile choices are preserved. Embedded and deployment copies must expose identical key trees and named `lower_snake_case` placeholders such as `{player}`, `{balance}`, or `{countdown}`. `{instance_name}` is supplied automatically: Core reads the visible name injected through `SERVER_NAME`, falling back to `INSTANCE_ID` and then the Paper server name, while Velocity reads the server currently associated with the player. The internal `<tc>` and `<sw>` tags insert the network and SheepWars identities. Their use is defined in the [message style guide](MESSAGING_STYLE.md) and is limited to standalone notifications.

## Lobby

Lobby configuration defines spawn coordinates, void recovery, scoreboard refresh, double-jump, selector layout, and feature toggles. `lobby.welcome.enabled` globally controls the immersive welcome: its title, sound, and particles play only on the initial lobby arrival after connecting to the proxy, while later lobby returns use only the discreet action bar. Each player can disable these effects persistently under Profile > Settings. `auto-replay.batch-size` defaults to five and is clamped from 1 to 100. The selector reads live instance snapshots from Redis. `GAME_PLAYING` appears as a blue `PLAYING` entry and, for SheepWars, connects available players as spectators.

Every `vip-shop.entries` grade key must exist in Core, be unique, and use a strictly increasing catalog price. An upgrade charges the target price minus the already purchased grade price. The Grades tab localizes proven perks under `lobby.shop-active-<grade>` and unimplemented promises under `lobby.shop-soon-<grade>`; an item moves to the active section only after its behavior exists. Each `lang-selector.languages` entry stores its MiniMessage description in one `lore` field and uses `<br>` for line breaks. Legacy `lore1`/`lore2` entries remain readable and are merged in memory during migration.

## SheepWars

Source: `tropicube-sheepwars/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeSheepwars/config.yml`.

Main sections:

- `redis`: shared-state connection;
- `default-settings`: player limits, automatic start, countdown, duration, sheep interval, random kits, map voting, and sheep weights;
- `custom-game-default-settings`: defaults for host-created games;
- `team-powerups`: enable flag, respawn delay, hit radius, weighted healing/poison-arrow/speed effects, and their startup-validated strengths for the single active target;
- `CUSTOM_GAME_PRIVATE`: an internal Velocity-injected environment flag that enables the private-host whitelist item; it must not be configured manually in a template;
- `force-settings`: disabled classes, kits, and sheep types;
- `locations`: world, waiting lobby, void limits, maps, red/blue spawns, and any number of optional numbered `powerups.target1`, `target2`, and subsequent candidate centers. Only one is active and respawning avoids the previous candidate. A map without centers remains playable without an aerial target.

`default-settings.sheep-give-delay` defaults to twenty seconds. A full ten-minute match therefore contains twenty-nine useful periodic deadlines plus the starting sheep. Each successful delivery restarts an individual player's interval; a deadline blocked by the five-sheep stock limit remains due and is retried every second. Active weights are normalized and used directly by a fresh independent draw for every delivery.

Gameplay values are validated and clamped at their boundary. A map supports at most eight spawns per team, so effective capacity is at most sixteen participants. Spectators use remaining server capacity and do not receive a team or affect victory checks.

Host player limits are clamped from 2 to 16. Maximum capacity cannot be reduced below the connected participant count and is immediately republished to Velocity. The host menu exposes every `default-settings` leaf, including sheep weights; `gameplay-balance` remains startup-only because its services are immutable for an instance lifetime.

## Docker Compose

Compose defines Velocity, Redis, MySQL, the Docker socket proxy, and any static support services. Dynamic Paper instances are created from Velocity templates. Do not publish their ports directly. Keep forwarding and database secrets environment-backed and identical across the components that consume them.

## Compatibility rules

New options require a safe default, startup validation, documentation, and deployment-copy synchronization. Old configuration should remain readable whenever practical. Redis changes must identify keys and TTLs; SQL changes require migrations and indexes.

## Private guild input in Lobby

`TropicubeLobby/config.yml` exposes `guilds.input-timeout-seconds` for each private input step (name, tag or username). Default: `120` seconds; only integers from `10` to `600` are accepted. Fractional, textual and out-of-range values fail startup with the key and supplied value. The bundled resource and Docker copy match; existing configuration files receive the default through the current updater. Member, officer and contribution limits remain owned by Core.

## Pre-opening reliability settings

Core SQL defaults: `database.pool.max-size=10`, `pool.min-idle=2`, `connection-timeout-millis=30000` (minimum 250), `socket-timeout-millis=30000`, `max-concurrent=10`, `queue-capacity=100`, `shutdown-timeout-seconds=10`. Concurrency cannot exceed pool size; all durations and capacities are positive. Core/Velocity Redis defaults: `redis.pool.max-total=20`, `max-idle=10`, `min-idle=2`; connection, socket and borrow timeouts are 2000 ms. Idle bounds must be ordered within the pool maximum.

Velocity `docker.memory-budget-mib=16384` covers dynamic container limits only. Reserve memory separately for the OS and static services. Template `memory-overhead-mib=0` computes `max(512, ceil(ram-max/4))` MiB beyond Java heap; positive values override the margin. Memory environment variables are derived from these settings. Existing configurations receive compatibility defaults on restart.

Operations need Python 3.11+, Restic and SSH. Configure `RESTIC_REPOSITORY` as an off-host `sftp:` repository, `RESTIC_PASSWORD_FILE`, and optionally `TROPICUBE_OPS_STATE` (default `.runtime/ops`; systemd uses `/var/lib/tropicube-ops`). Keep recovery credentials outside Git and outside this host. Static and dynamic containers use Docker local logging with five 20 MiB files.
## Fallen Kingdoms

`dockerfiles/configs/TropicubeFallenKingdoms/config.yml` declares the `cactus` map. Its playable region is `(-947, 0, -898)` to `(-468, 150, -433)`. The five bases, hearts, and spawns are configured for blue, red, green, yellow, and orange kingdoms. Its 490-block initial border is centered at `(-702, -653)`, covering the maximum 245-block distance from the center to the declared playable region. Sudden death still ends with a 50-block border.

The same configuration is embedded in the module. Update both files until FK resource synchronization is added.

`game.default-map` selects the local map and the instance `MAP_ID` environment variable overrides it in production. Every enabled `locations.maps` entry owns its playable region, border, layouts, and bases, so another map requires configuration rather than Java changes. Startup validates positions, overlaps, layouts, phase times, protections, ruin settings, and the item list under `kits.definitions`.

Custom matches use the FK setup screen in the Lobby. Velocity only accepts `FK_AUTO_START`, `FK_COMBAT_PROFILE`, `FK_COUNTDOWN_SECONDS`, `FK_MAX_PLAYERS_PER_KINGDOM`, `FK_MAX_KINGDOMS`, `FK_PVP_AT_SECONDS`, `FK_ASSAULT_AT_SECONDS`, `FK_SUDDEN_DEATH_AT_SECONDS`, `FK_FORCE_END_AT_SECONDS`, `FK_HEART_HEALTH`, `FK_RESPAWN_DELAY_SECONDS`, `FK_ENABLED_KITS`, `FK_RUIN_WAVES`, `FK_RUIN_RADIUS`, and `FK_RUIN_DESTRUCTION_RATIO`. The proxy rejects every other key and command-like value; Paper then validates ranges, phase ordering, kits, and mandatory protections. Host instances disable automatic start by default and their owner may use `status`, `start`, and `cancel`.

`scoreboards.yml` and `tablists.yml` provide `waiting`, `active`, and `ending` variants and participate in Core UI reloads. Migration `V011__fallenkingdoms_preferences.sql` stores each player's last kit and preferred kingdom color.
