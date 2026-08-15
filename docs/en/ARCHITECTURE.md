# Architecture

## Overview

Tropicube separates proxy orchestration, shared network services, lobby presentation, and game-specific rules. Velocity is the only public player entry point. Paper backends are created dynamically and communicate through Redis without becoming directly accessible from the Internet.

## Maven modules

| Module | Main contracts |
|---|---|
| Docker API | `ServerTemplate`, `ServerInstance`, Redis access, Docker lifecycle, shared nick and grade payloads |
| Velocity | Dynamic registration, routing, queues, health checks, nick profiles, and proxy commands |
| Core | SQL profiles, economy, grades, permissions, localization, moderation, and Paper-side nick application |
| Lobby | Server catalogs, menus, custom game creation, reconnect and replay entry points |
| SheepWars | Explicit game state machine, teams, kits, sheep abilities, scoreboard, spectators, and match cleanup |
| Fallen Kingdoms | Empty implementation slot governed by the separate design and technical specification |

Game modules may depend on Core and Docker API, but they must never depend on another game's business classes.

## Runtime topology

```text
Internet
  -> Velocity
      -> Lobby Paper containers
      -> SheepWars Paper containers
      -> Redis
      -> Docker socket proxy
Paper containers -> MySQL
```

Velocity forwards authenticated profiles to Paper using modern forwarding. Dynamic Paper ports remain private. Docker access is restricted through a socket proxy, and secrets come from the environment.

## Server lifecycle

`ServerInstance.Status` describes infrastructure and game availability:

```text
CREATING -> STARTING -> GAME_WAITING -> GAME_STARTING
                                      -> GAME_PLAYING -> GAME_ENDING
                                      -> STOPPING -> STOPPED
Any stage may transition to ERROR.
```

SheepWars publishes each game transition back to Redis. The lobby renders `GAME_PLAYING` as a blue `PLAYING` state. A playing SheepWars instance remains joinable while it has capacity because late arrivals become spectators.

## Redis contracts

| Key or channel | Producer | Consumers | Semantics |
|---|---|---|---|
| `instance:<id>` | Velocity / game backend | Velocity, Lobby, games | Serialized instance snapshot, including privacy and admitted UUIDs; refreshed 24-hour TTL |
| `player:server:<uuid>` | Velocity | Core, Lobby, commands | Current instance assignment |
| `player:grade:<uuid>` | Core | Velocity | Current network grade, 24-hour TTL |
| `player:language:<uuid>` | Core | Velocity | Current interface language |
| `nick:<uuid>` | Velocity | Core and games | Nickname, signed skin, persistent fake display grade; 24-hour TTL |
| `nick:original:<uuid>` | Velocity | Core | Original name and signed skin used by `/nick off` |
| `sw:game-started:<id>` | SheepWars | Velocity, Lobby | Active match marker |
| `sw:left-game:<uuid>` | SheepWars | Lobby, Velocity | Five-minute reconnect target |
| `sw:next-game:<id>` | Velocity | SheepWars | Pre-created replay instance |
| `post-game:<uuid>` | SheepWars | Lobby | Replay suggestion, 120-second TTL |
| `host-creation:<uuid>` | Velocity | Velocity / Lobby | Atomic custom-server creation lock |
| `host:<uuid>` | Velocity | Velocity, Lobby, SheepWars | Host ownership of one custom instance |
| `player:uuid:<name>` / `player:name:<uuid>` | Velocity | Velocity, SheepWars | Previously seen player-name resolution; refreshed 30-day TTL |

On `NICK_APPLY`, Core copies the `/nick` name and fake grade into a backend-local visual cache used by the tablist and lobby join announcements, while chat reads the same Redis identity. None of these display paths replaces the real grade used for permissions. The cache is cleared when the nick is disabled or the player is unloaded, then restored from `nick:<uuid>` after reconnecting. Lobby resolves the visible name from `Player#displayName`, which Core updates immediately on both activation and removal, because the Paper profile name may remain temporarily stale after `setPlayerProfile`.

SheepWars consumes `NICK_APPLY`, `NICK_RESET`, `NICK_CLEAR`, `GRADE_LOADED`, and `GRADE_CHANGED` to reassert its local rendering after Core. Its tablist never includes a grade prefix or icon: the synchronized `displayName` is colored by team, or gray for spectators. Chat uses the same identity so it follows `/nick off` immediately. Scoreboard teams separately continue to use the client-visible profile name for entity outlines.

Redis subscriber callbacks must not mutate Bukkit state. Paper plugins always schedule entity, inventory, world, and profile changes back onto the server scheduler.

Velocity is the only whitelist writer. Both `/whitelist` and the SheepWars GUI reach the same ownership-checked mutation. Lobby snapshots are filtered before counts, pagination, best-server selection, and final clicks, while `ServerPreConnectEvent` independently enforces the boundary so hidden instances cannot be reached by a stale menu or direct command.

## Persistence

MySQL stores durable profiles, grades, permissions, balances, moderation history, and SheepWars preferences. Redis accelerates reads and coordinates ephemeral network state. Any SQL schema change requires a compatible migration and suitable indexes; any Redis contract change must document its key, TTL, atomicity, and consumers.

## Shutdown

Every plugin cancels scheduled work and closes pools or subscriptions during shutdown. A completed game sends `PROXY:FINISH_GAME:<instanceId>`; Velocity transfers remaining players, unregisters the backend, removes the container and anonymous volumes, and clears Redis state.
