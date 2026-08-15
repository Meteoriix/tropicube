# Configuration

## Environment variables

Secrets and deployment-specific values belong in `.env`, never in Git. `.env.example` documents safe placeholders.

| Variable | Purpose |
|---|---|
| `TROPICUBE_REDIS_HOST`, `TROPICUBE_REDIS_PORT`, `TROPICUBE_REDIS_PASSWORD` | Redis connection |
| `TROPICUBE_MYSQL_HOST`, `TROPICUBE_MYSQL_PORT`, `TROPICUBE_MYSQL_DATABASE` | MySQL location |
| `TROPICUBE_MYSQL_USER`, `TROPICUBE_MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Database credentials |
| `VELOCITY_FORWARDING_SECRET` | Shared Velocity/Paper forwarding secret |
| `TROPICUBE_PROJECT_PATH` | Absolute Docker-host project path used for bind mounts |

## Velocity

Source: `tropicube-velocity/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeVelocity/config.yml`.

- `admin-uuids` grants proxy administration permissions to approved UUIDs;
- `remove-dynamic-servers-on-shutdown` controls cleanup during a normal proxy shutdown;
- health-check values define probe frequency, timeout, and failed-start cleanup;
- `nick.allowed-grades` defines grades that may enable `/nick`;
- `nick.skin-uuids` extends the Mojang skin pool;
- `templates` define Docker image, server type, capacity, scaling, auto-stop, volumes, and environment variables.

Core publishes `player:grade:<uuid>` with a 24-hour TTL and refreshes it on load and grade changes. An active nick is stored under `nick:<uuid>` with nickname, signed skin, and fake display grade. Legacy payloads without a grade default to `PREMIUM`. The full identity gets a fresh 24-hour TTL on disconnect and reconnect; there is no separate 30-second reconnect limit.

## Core

Source: `tropicube-core/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeCore/config.yml`.

Core configuration covers Redis, MySQL, the default language, economy cache rules, and the complete grade catalog. Grade definitions are synchronized to MySQL at startup. Existing values are preserved by the configuration updater when new keys are introduced.

Supported language resources are `fr`, `en`, `de`, and `es`. Embedded and deployment copies must expose identical key trees and placeholders.

## Lobby

Lobby configuration defines spawn coordinates, void recovery, scoreboard refresh, double-jump, selector layout, and feature toggles. The selector reads live instance snapshots from Redis. `GAME_PLAYING` appears as a blue `PLAYING` entry and, for SheepWars, connects available players as spectators.

## SheepWars

Source: `tropicube-sheepwars/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeSheepwars/config.yml`.

Main sections:

- `redis`: shared-state connection;
- `default-settings`: player limits, automatic start, countdown, duration, sheep interval, random kits, map voting, and sheep weights;
- `custom-game-default-settings`: defaults for host-created games;
- `CUSTOM_GAME_PRIVATE`: an internal Velocity-injected environment flag that enables the private-host whitelist item; it must not be configured manually in a template;
- `force-settings`: disabled classes, kits, and sheep types;
- `locations`: world, waiting lobby, void limits, maps, and red/blue spawns.

Gameplay values are validated and clamped at their boundary. A map supports at most eight spawns per team, so effective capacity is at most sixteen participants. Spectators use remaining server capacity and do not receive a team or affect victory checks.

## Docker Compose

Compose defines Velocity, Redis, MySQL, the Docker socket proxy, and any static support services. Dynamic Paper instances are created from Velocity templates. Do not publish their ports directly. Keep forwarding and database secrets environment-backed and identical across the components that consume them.

## Compatibility rules

New options require a safe default, startup validation, documentation, and deployment-copy synchronization. Old configuration should remain readable whenever practical. Redis changes must identify keys and TTLs; SQL changes require migrations and indexes.
