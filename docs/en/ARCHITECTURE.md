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

Every instance carries a backward-compatible functional mode: `LOBBY`, `QUICK_PLAY`, `RANKED_4V4`, `RANKED_8V8`, or `CUSTOM`. New network events use a versioned JSON envelope with a unique identifier, source, and timestamp so consumers can deduplicate them.

Velocity orchestrates network-wide and game-type maintenance. It blocks new entries and creations, lets active games finish until the deadline, then transfers players to a lobby or disconnects them cleanly. Short-lived state is shared through Redis; connection limits keep only in-memory counters and temporary quarantines.

`ServerInstance.Status` describes infrastructure and game availability:

```text
CREATING -> STARTING -> GAME_WAITING -> GAME_STARTING
                                      -> GAME_PLAYING -> GAME_ENDING
                                      -> STOPPING -> STOPPED
Any stage may transition to ERROR.
```

SheepWars publishes each game transition back to Redis. The lobby renders `GAME_PLAYING` as a blue `PLAYING` state. A playing SheepWars instance remains joinable while it has capacity because late arrivals become spectators.

Sheep distribution uses an immutable effective-weight table followed by an independent weighted draw for every successful insertion. The once-per-second game tick also advances per-player delivery deadlines; a deadline remains due while the player's sheep stock is full and restarts only after insertion succeeds.

## Redis contracts

| Key or channel | Producer | Consumers | Semantics |
|---|---|---|---|
| `instance:<id>` | Velocity / game backend | Velocity, Lobby, games | Serialized instance snapshot, including privacy and admitted UUIDs; refreshed 24-hour TTL |
| `player:server:<uuid>` | Velocity | Core, Lobby, commands | Current instance assignment |
| `party:member:<uuid>` | Core | Core, Velocity, Lobby | Player-to-party index with a 24-hour TTL |
| `party:<id>:leader` / `party:<id>:members` | Core | Core, Velocity, Lobby | Leader and atomic `uuid -> follow` membership hash, with a 24-hour TTL |
| `party:offline:<uuid>` | Velocity | Velocity | Durable disconnect timestamp with a 24-hour TTL, cleared on reconnect or reconciliation |
| `party:invites:<uuid>` | Core | Core, Lobby | Invitations indexed by leader UUID with configured TTL |
| `player:grade:<uuid>` | Core | Velocity | Current network grade, 24-hour TTL |
| `player:language:<uuid>` | Core | Velocity | Current interface language |
| `nick:<uuid>` | Velocity | Core and games | Nickname, signed skin, persistent fake display grade; 24-hour TTL |
| `nick:original:<uuid>` | Velocity | Core | Original name and signed skin used by `/nick off` |
| `sw:game-started:<id>` | SheepWars | Velocity, Lobby | Active match marker |
| `sw:left-game:<uuid>` | SheepWars | Lobby, Velocity | Five-minute reconnect target |
| `sw:next-game:<id>` | Velocity | SheepWars | Pre-created replay instance |
| `post-game:<uuid>` | SheepWars | Lobby | Replay suggestion, 120-second TTL |
| `settings:auto-replay:<uuid>` | Lobby | Lobby | Persistent `OFF`, remaining count, or `0` awaiting confirmation |
| `host-creation:<uuid>` | Velocity | Velocity / Lobby | Atomic custom-server creation lock |
| `host:<uuid>` | Velocity | Velocity, Lobby, SheepWars | Host ownership of one custom instance |
| `player:uuid:<name>` / `player:name:<uuid>` | Velocity | Velocity, SheepWars | Previously seen player-name resolution; refreshed 30-day TTL |

On `NICK_APPLY`, Core copies the `/nick` name and fake grade into a backend-local visual cache used by the tablist and lobby join announcements, while chat reads the same Redis identity. None of these display paths replaces the real grade used for permissions. For `NICK_CLEAR`, Velocity retains `nick:<uuid>` and `nick:original:<uuid>` until the backend currently owning the player restores the profile; that backend then deletes both keys. The request therefore remains retryable if a Pub/Sub message is lost or the player changes servers. The cache is cleared when the nick is disabled or the player is unloaded, then restored from `nick:<uuid>` after reconnecting. Lobby resolves the visible name from `Player#displayName`, which Core updates immediately on both activation and removal, because the Paper profile name may remain temporarily stale after `setPlayerProfile`.

SheepWars consumes `NICK_APPLY`, `NICK_RESET`, `NICK_CLEAR`, `GRADE_LOADED`, and `GRADE_CHANGED` to reassert its local rendering after Core. Its tablist never includes a grade prefix or icon: the synchronized `displayName` is colored by team, or gray for spectators. Chat uses the same identity so it follows `/nick off` immediately. Scoreboard teams separately continue to use the client-visible profile name for entity outlines.

Redis subscriber callbacks must not mutate Bukkit state. Paper plugins always schedule entity, inventory, world, and profile changes back onto the server scheduler.

On Paper, `TropicubeCore` is the sole runtime provider of `tropicube-docker-api`. Lobby and SheepWars declare it as Maven `provided` and resolve it through their mandatory Paper dependency on Core. Their shaded JARs must never embed `fr.tropicube.docker.*`, otherwise social objects crossing plugin boundaries belong to incompatible classloaders.

Friendships are durable MySQL pairs. Core checks that relation before publishing `PROXY:FRIEND_JOIN`; Velocity then revalidates both players, the target instance, whitelist, state, and capacity. Each friend in the Social menu is represented by a player head tied to that friend's profile UUID. Lobby starts dynamic resolution with the UUID alone, resolves skin textures asynchronously on the server, shares concurrent lookups, and caches complete profiles. A failed or incomplete lookup times out after five seconds, temporarily falls back to a textureless head, and can be retried when the menu is opened again. A `GAME_PLAYING` SheepWars arrival becomes a spectator. Party transfer requests use the same proxy boundary and include only online members whose individual `follow` flag is enabled. Moving between parties is one atomic Lua transition, including leader succession. Lobby exposes these actions through a Social hotbar menu loaded outside the Paper thread. It also atomically consumes the automatic-replay counter and requires `/replayconfirm` after each five-game batch.

On disconnect, Velocity writes `party:offline:<uuid>` and reconciles the member after `party.disconnect-grace-seconds`. The Lua transition atomically rechecks presence: reconnecting preserves membership; otherwise the member is removed and an offline leader is replaced by an online member. As soon as the last online member leaves, every party index is deleted without waiting for the individual grace period. A periodic sweep resumes expired markers after a proxy restart.

Velocity is the only whitelist writer. Both `/whitelist` and the SheepWars GUI reach the same ownership-checked mutation. Lobby snapshots are filtered before counts, pagination, best-server selection, and final clicks, while `ServerPreConnectEvent` independently enforces the boundary so hidden instances cannot be reached by a stale menu or direct command.

## Communication and staff security

Global chat and private messages use Redis. Public messages receive a random twelve-character ID; the message and a seven-message instance context remain in Redis for fifteen minutes. Only authorized staff see the clickable moderation suggestion. Capturing evidence copies the message, context, and SHA-256 digest to MySQL for exactly 90 days. Offline private messages expire after seven days and ignore lists remain in MySQL.

MySQL is authoritative for bans, while `ban:<uuid>` lets Velocity reject the login before a Paper transfer and a command event disconnects active sessions. Sensitive staff actions additionally require the fifteen-minute `staff-session:<uuid>` created by a non-replayable TOTP or one-time recovery code. TOTP secrets use AES-256-GCM under an environment-only key; no device or extra address data is collected.

## Persistence

MySQL stores durable profiles, friendships, grades, permissions, balances, moderation history, and SheepWars preferences. Redis accelerates reads and coordinates ephemeral network state. Any SQL schema change requires a compatible migration and suitable indexes; any Redis contract change must document its key, TTL, atomicity, and consumers.

## Shutdown

Every plugin cancels scheduled work and closes pools or subscriptions during shutdown. A completed game sends `PROXY:FINISH_GAME:<instanceId>`; Velocity transfers remaining players, unregisters the backend, removes the container and its labelled ephemeral `/data` volume, and clears Redis state. On startup, Velocity also removes labelled game volumes whose containers no longer exist.

Velocity mounts its runtime `/server` directory as `tmpfs`. The container seeds it from the immutable `/opt/tropicube/server` image directory on every start, and the data disappears automatically when the proxy stops. Deployment scripts remove a legacy anonymous `/server` volume once during migration.
