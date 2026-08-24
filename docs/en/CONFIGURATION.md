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
| `TOTP_MASTER_KEY` | Base64-encoded AES-256 key used to encrypt staff TOTP secrets |

The TOTP key must encode exactly 32 random bytes, for example with `openssl rand -base64 32`. Losing it makes existing enrollments unreadable. When absent, Core starts but deliberately keeps protected staff commands locked.

### Report privacy framework

The implementation minimizes captured context and enforces retention, but code alone does not establish GDPR compliance. Before opening the service, the controller must document purpose and legal basis, register the processing, inform players before collection, restrict recipients, provide an effective rights-request channel, assess whether a DPIA is needed, cover processors contractually, and define a breach procedure. The chosen 90-day evidence period is an operator decision rather than a universally approved CNIL period and must be justified and reviewed. See CNIL guidance on [retention](https://www.cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees), [security and minimisation](https://www.cnil.fr/fr/securite-des-donnees-les-regles-essentielles), and [data-subject rights](https://www.cnil.fr/fr/preparer-lexercice-des-droits-des-personnes).

## Velocity

Source: `tropicube-velocity/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeVelocity/config.yml`.

- `admin-uuids` grants `tropicube.admin`, the `find`, `send`, `pull`, `maintenance`, `announce` and `diagnostic` proxy administration permissions, and `tropicube.bypass.whitelist` to approved UUIDs;
- `party.disconnect-grace-seconds` defaults to `60`, removes members who remain offline after that grace period and transfers leadership to an online member; fully offline parties are deleted immediately;
- `remove-dynamic-servers-on-shutdown` controls cleanup during a normal proxy shutdown;
- health-check values define probe frequency, timeout, and failed-start cleanup;
- `nick.allowed-grades` defines grades that may enable `/nick`;
- `nick.skin-uuids` extends the Mojang skin pool;
- `templates` define Docker image, server type, capacity, scaling, auto-stop, volumes, and environment variables. `max-players` is the participant limit; `spectator-slots` adds backend capacity only for an ongoing game (`8` for SheepWars).

Core publishes `player:grade:<uuid>` with a 24-hour TTL and refreshes it on load and grade changes. An active nick is stored under `nick:<uuid>` with nickname, signed skin, and fake display grade. Legacy payloads without a grade default to `PREMIUM`. The full identity gets a fresh 24-hour TTL on disconnect and reconnect; there is no separate 30-second reconnect limit.

Velocity also accepts the following operational settings:

- `connection-protection.address-limit`, `global-limit`, `window-seconds`, and `quarantine-seconds` control adaptive limits before authentication. No address history is persisted.
- `motd.line-1`, `line-2`, and `maintenance-line` describe only the public Velocity endpoint. `{online}` is replaced with the current player count.
- `announcements.interval-seconds` and `announcements.entries[].message-key/target` define the localized rotation. A target is `network`, a type, a template, or an instance.
- `/maintenance` persists a drain in Redis for at most seven days. Its deadline ranges from 1 to 1,440 minutes.

## Core

Source: `tropicube-core/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeCore/config.yml`.

Core configuration covers Redis, MySQL, the default language, economy cache rules, and the complete grade catalog. Social limits are `social.friends.max-count` (`100`), `social.friends.request-expiry-days` (`30`), `social.party.max-size` (`8`), and `social.party.invite-expiry-seconds` (`60`). Grade definitions are synchronized to MySQL at startup. Existing values are preserved by the configuration updater when new keys are introduced.

Guild limits are `guilds.max-members` (`50`), `guilds.max-officers` (`5`), and `guilds.weekly-contribution-cap` (`5000` XP per member).

`TropicubeCore/missions.yml` is a versioned business catalog. Every mission declares an event, target, network-XP reward, and currency reward. Stable IDs and a version bump are required when it changes; startup rejects invalid or undersized catalogs.

Supported language resources are `fr`, `en`, `de`, and `es`. A newly created player profile uses the Minecraft client locale when it maps to one of these languages, otherwise it starts in English; existing profile choices are preserved. Embedded and deployment copies must expose identical key trees and placeholders. The internal `<tc>` and `<sw>` tags insert the network and SheepWars identities. Their use is defined in the [message style guide](MESSAGING_STYLE.md) and is limited to standalone notifications.

## Lobby

Lobby configuration defines spawn coordinates, void recovery, scoreboard refresh, double-jump, selector layout, and feature toggles. `lobby.welcome.enabled` globally controls the immersive welcome: its title, sound, and particles play only on the initial lobby arrival after connecting to the proxy, while later lobby returns use only the discreet action bar. Each player can disable these effects persistently under Profile > Settings. `auto-replay.batch-size` defaults to five and is clamped from 1 to 100. The selector reads live instance snapshots from Redis. `GAME_PLAYING` appears as a blue `PLAYING` entry and, for SheepWars, connects available players as spectators.

Every `vip-shop.entries` grade key must exist in Core, be unique, and use a strictly increasing catalog price. An upgrade charges the target price minus the already purchased grade price. The Grades tab localizes proven permissions under `lobby.shop-active-<grade>` and unimplemented promises under `lobby.shop-soon-<grade>`; an item moves to the active section only after its behavior exists.

## SheepWars

Source: `tropicube-sheepwars/src/main/resources/config.yml`; deployment copy: `dockerfiles/configs/TropicubeSheepwars/config.yml`.

Main sections:

- `redis`: shared-state connection;
- `default-settings`: player limits, automatic start, countdown, duration, sheep interval, random kits, map voting, and sheep weights;
- `custom-game-default-settings`: defaults for host-created games;
- `CUSTOM_GAME_PRIVATE`: an internal Velocity-injected environment flag that enables the private-host whitelist item; it must not be configured manually in a template;
- `force-settings`: disabled classes, kits, and sheep types;
- `locations`: world, waiting lobby, void limits, maps, and red/blue spawns.

`default-settings.sheep-give-delay` defaults to twenty seconds. A full ten-minute match therefore contains twenty-nine useful periodic deadlines plus the starting sheep. Each successful delivery restarts an individual player's interval; a deadline blocked by the five-sheep stock limit remains due and is retried every second. Active weights are normalized and used directly by a fresh independent draw for every delivery.

Gameplay values are validated and clamped at their boundary. A map supports at most eight spawns per team, so effective capacity is at most sixteen participants. Spectators use remaining server capacity and do not receive a team or affect victory checks.

Host player limits are clamped from 2 to 16. Maximum capacity cannot be reduced below the connected participant count and is immediately republished to Velocity. The host menu exposes every `default-settings` leaf, including sheep weights; `gameplay-balance` remains startup-only because its services are immutable for an instance lifetime.

## Docker Compose

Compose defines Velocity, Redis, MySQL, the Docker socket proxy, and any static support services. Dynamic Paper instances are created from Velocity templates. Do not publish their ports directly. Keep forwarding and database secrets environment-backed and identical across the components that consume them.

## Compatibility rules

New options require a safe default, startup validation, documentation, and deployment-copy synchronization. Old configuration should remain readable whenever practical. Redis changes must identify keys and TTLs; SQL changes require migrations and indexes.
