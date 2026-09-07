# Architecture

## Overview

Tropicube separates proxy orchestration, shared network services, lobby presentation, and game-specific rules. Velocity is the only public player entry point. Paper backends are created dynamically and communicate through Redis without becoming directly accessible from the Internet.

The local editor keeps Maven resources as the source of truth and Docker mirrors as the build source, with named rendering shared through `tropicube-language-api`. Menu, scoreboard, and tablist layouts live in versioned module-owned manifests. After validation the editor installs languages and manifests, then invokes `languageeditorreload` through internal RCON. Core writes each set under a new Redis generation (`runtime-ui:generation:<id>:*`) and changes `runtime-ui:active` only after all files and their hash manifest exist. New instances verify and restore that generation before loading managers.

## Maven modules

| Module | Main contracts |
|---|---|
| Docker API | `ServerTemplate`, `ServerInstance`, Redis access, Docker lifecycle, shared nick and access-level payloads |
| Language API | Safe named and legacy positional MiniMessage placeholder rendering; runtime defaults do not consume positional arguments |
| Velocity | Dynamic registration, routing, queues, health checks, nick profiles, and proxy commands |
| Core | SQL profiles, economy, cosmetic grades, VIP/mod levels, localization, moderation, and Paper-side nick application |
| Lobby | Server catalogs, menus, custom game creation, reconnect and replay entry points |
| SheepWars | Explicit game state machine, teams, kits, sheep abilities, scoreboard, spectators, and match cleanup |
| Fallen Kingdoms | Empty implementation slot governed by the separate design and technical specification |

Game modules may depend on Core and Docker API, but they must never depend on another game's business classes.

Each placeholder name represents one business meaning and is reused by translations receiving the same information. Core and Velocity enrich every localized rendering with `{instance_name}`. Paper resolves it from the instance environment, while the proxy resolves it from the player's current connection; the shared language API excludes that runtime value when mapping any remaining positional arguments. Player-targeted rendering also exposes `{player_grade}` as the formatted grade prefix without the username. Core resolves it from its local cache without SQL and publishes `tropicube:player:grade-display:<uuid>` to Redis with a 24-hour TTL so Velocity can maintain an in-memory cache. This independent value follows last-writer-wins semantics, and Velocity temporarily uses the default grade until the first cache load arrives.

## Runtime topology

```text
Internet
  -> Java 25565/tcp -> Velocity
  -> Bedrock 19132/udp -> Geyser -> Floodgate -> Velocity
      -> Lobby Paper containers
      -> SheepWars Paper containers
      -> Redis
      -> Docker socket proxy
Paper containers -> MySQL
```

Velocity authenticates Java profiles directly. Geyser translates Bedrock traffic on the same proxy and Floodgate supplies stable UUID identities without disabling Java online mode. Velocity then forwards both profile types to Paper through modern forwarding. Geyser and Floodgate are not installed on Paper because no backend consumes their API. Dynamic Paper ports remain private. Docker access is restricted through a socket proxy, and secrets come from the environment.

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

The match-owned aerial power-up manager renders exactly one wool target at a candidate center, checks arrow segments on the Paper thread, grants the selected weighted effect only to living teammates, and chooses another center when it respawns. It owns timing and display cleanup, starts only for `PLAYING`, and stops before result processing or plugin shutdown.

## Redis contracts

| Key or channel | Producer | Consumers | Semantics |
|---|---|---|---|
| `instance:<id>` | Velocity / game backend | Velocity, Lobby, games | Serialized instance snapshot, including privacy and admitted UUIDs; refreshed 24-hour TTL |
| `player:server:<uuid>` | Velocity | Core, Lobby, commands | Current instance assignment |
| `party:member:<uuid>` | Core | Core, Velocity, Lobby | Player-to-party index with a 24-hour TTL |
| `party:<id>:leader` / `party:<id>:members` | Core | Core, Velocity, Lobby | Leader and atomic `uuid -> follow` membership hash, with a 24-hour TTL |
| `party:offline:<uuid>` | Velocity | Velocity | Durable disconnect timestamp with a 24-hour TTL, cleared on reconnect or reconciliation |
| `party:invites:<target>` / `party:invites:sent:<leader>` | Core | Core, Lobby | Received (`leader -> party`) and sent (`target -> party`) indexes for the same invitation, using the configured invitation TTL; Lua scripts atomically update both indexes on creation, acceptance, denial, and cancellation. The sent index is populated for new or renewed invitations |
| `player:access:<uuid>` | Core | Velocity | Persistent `vipLevel:modLevel:revision`; consumed through a local fail-closed cache |
| `player:language:<uuid>` | Core | Velocity | Current interface language |
| `nick:<uuid>` | Velocity | Core and games | Nickname, signed skin, persistent fake display grade; 24-hour TTL |
| `nick:original:<uuid>` | Velocity | Core | Original name and signed skin used by `/nick off` |
| `sw:game-started:<id>` | SheepWars | Velocity, Lobby | Active match marker |
| `sw:left-game:<uuid>` | SheepWars | Lobby, Velocity | Five-minute reconnect target |
| `sw:next-game:<id>` | Velocity | SheepWars | Pre-created replay instance |
| `post-game:<uuid>` | SheepWars | Lobby | Replay suggestion, 120-second TTL |
| `settings:auto-replay:<uuid>` | Lobby | Lobby | Persistent `OFF`, remaining count, or `0` awaiting confirmation |
| `matchmaking:player:<uuid>` | Velocity | Lobby | The player's only active queue, 30-minute TTL; removed on cancellation, transfer, or disconnect |
| `matchmaking:ranked:stats:<template>` | Velocity | Lobby | Versioned UUID-free live groups, reserved players, capacity, oldest wait, and update time; refreshed every 5 seconds with a 15-second TTL |
| `host-creation:<uuid>` | Velocity | Velocity / Lobby | Atomic custom-server creation lock |
| `host:<uuid>` | Velocity | Velocity, Lobby, SheepWars | Host ownership of one custom instance |
| `player:uuid:<name>` / `player:name:<uuid>` | Velocity | Velocity, SheepWars | Previously seen player-name resolution; refreshed 30-day TTL |
| `session:initial-lobby-welcome:<uuid>` | Velocity | Lobby | One-shot 60-second marker created only when the first server selected after proxy login is a lobby; authorizes the full welcome title and is then deleted immediately |

On `NICK_APPLY`, Core copies the `/nick` name and fake grade into a backend-local visual cache used by the tablist and lobby join announcements, while chat reads the same Redis identity. None of these display paths replaces the real grade used for permissions. For `NICK_CLEAR`, Velocity retains `nick:<uuid>` and `nick:original:<uuid>` until the backend currently owning the player restores the profile; that backend then deletes both keys. The request therefore remains retryable if a Pub/Sub message is lost or the player changes servers. The cache is cleared when the nick is disabled or the player is unloaded, then restored from `nick:<uuid>` after reconnecting. Lobby resolves the visible name from `Player#displayName`, which Core updates immediately on both activation and removal, because the Paper profile name may remain temporarily stale after `setPlayerProfile`.

SheepWars consumes `NICK_APPLY`, `NICK_RESET`, `NICK_CLEAR`, `GRADE_LOADED`, and `GRADE_CHANGED` to reassert its local rendering after Core. Its tablist never includes a grade prefix or icon: the synchronized `displayName` is colored by team, or gray for spectators. Chat uses the same identity so it follows `/nick off` immediately. Scoreboard teams separately continue to use the client-visible profile name for entity outlines.

Redis subscriber callbacks must not mutate Bukkit state. Paper plugins always schedule entity, inventory, world, and profile changes back onto the server scheduler.

On Paper, `TropicubeCore` is the sole runtime provider of `tropicube-docker-api`. Lobby and SheepWars declare it as Maven `provided` and resolve it through their mandatory Paper dependency on Core. Their shaded JARs must never embed `fr.tropicube.docker.*`, otherwise social objects crossing plugin boundaries belong to incompatible classloaders.

Friendships are durable MySQL pairs. Core checks that relation before publishing `PROXY:FRIEND_JOIN`; Velocity then revalidates both players, the target instance, whitelist, state, and capacity. Java represents each friend, party member, or request with a player head tied to that profile UUID. Bedrock uses a meaningful vanilla status icon instead, because Geyser cannot add a dynamic texture to the skull pack already generated at startup. Social offers Friends (HeadDatabase `117085`, slot 2), Party (HeadDatabase `117095`, slot 4) and Guilds (shield, slot 6), and titles its default page “Social • Friends”. The bottom-center button opens friend requests in the first view and party invitations in the second. Both request screens use the same incoming/sent two-column grid: received party invitations accept on left click or deny on right click, while sent invitations cancel on right click. For Java, Lobby starts dynamic resolution with the UUID alone, resolves skin textures asynchronously on the server, shares concurrent lookups, and caches complete profiles. A failed or incomplete lookup times out after five seconds, temporarily falls back to a textureless head, and can be retried when the menu is opened again. A `GAME_PLAYING` SheepWars arrival becomes a spectator. Party transfer requests use the same proxy boundary and include only online members whose individual `follow` flag is enabled. `/party warp <player>` uses a separate request for one member; Velocity revalidates leadership, membership, the target, and instance capacity before transferring that member alone. Moving between parties is one atomic Lua transition, including leader succession. Lobby exposes these actions through a Social hotbar menu loaded outside the Paper thread. It also atomically consumes the automatic-replay counter and requires `/replayconfirm` after each five-game batch.

On disconnect, Velocity writes `party:offline:<uuid>` and reconciles the member after `party.disconnect-grace-seconds`. The Lua transition atomically rechecks presence: reconnecting preserves membership; otherwise the member is removed and an offline leader is replaced by an online member. As soon as the last online member leaves, every party index is deleted without waiting for the individual grace period. A periodic sweep resumes expired markers after a proxy restart.

Velocity is the only whitelist writer. Both `/whitelist` and the SheepWars GUI reach the same ownership-checked mutation. Lobby snapshots are filtered before counts, pagination, best-server selection, and final clicks, while `ServerPreConnectEvent` independently enforces the boundary so hidden instances cannot be reached by a stale menu or direct command.

## Communication and staff security

Global chat and private messages use Redis. Public messages receive a random twelve-character ID; the message and a seven-message instance context remain in Redis for fifteen minutes. Only authorized staff see the clickable moderation suggestion. Capturing evidence copies the message, context, and SHA-256 digest to MySQL for exactly 90 days. Offline private messages expire after seven days and ignore lists remain in MySQL.

MySQL is authoritative for bans, while `ban:<uuid>` lets Velocity reject the login before a Paper transfer and a command event disconnects active sessions. Sensitive staff actions additionally require the persistent `staff-session:<uuid>` created by a non-replayable TOTP or one-time recovery code. Velocity deletes it on the actual network disconnect, not on a Paper transfer, and also clears any stale key at the next login after an abrupt proxy stop. Code consumption locks the MySQL row inside one transaction before the Redis session opens, preventing concurrent reuse. TOTP secrets use AES-256-GCM under an environment-only key; no device or extra address data is collected.

Core owns the localized persistent-data Profile entry. It uses the player's head on Java and a stable name-tag icon on Bedrock to avoid the Steve fallback. Lobby places it in slot 4 and SheepWars in slot 7 only while waiting; Core handles both clicks and exposes a bounded bridge to Lobby Settings. Core, Lobby, and SheepWars inventories reuse `NetworkMenuStyle` without introducing a game-to-game dependency. Java keeps the gray/aqua frame and decorative glints; Geyser clients are identified through their client brand or Floodgate prefix and receive a sparse layout without decorative panes or fake enchantments, keeping actions distinct on touch screens without misleading enchantment rendering. Geyser's integrated pack complements that layout, while its texture mapping translates static HeadDatabase icons. Java player heads apply the skin profile before an explicit localized `displayName`, preventing Minecraft's generated “Player's Head” label from replacing Profile button names.

The game selector routes clicks without blocking Paper: left to Quick Play, right to Ranked 4v4/8v8, and `Shift + left click` to the filtered public-instance browser. Velocity publishes an explicit `InstanceMode` for templates and instances, so Lobby never infers Quick Play, Ranked, or Custom behavior from display names. Private custom instances remain whitelist-filtered and creation is visibly locked below VIP+.

Grade purchases lock the profile and economy rows in one MySQL transaction, revalidate the expected grade, charge only the catalog-price difference, apply both configured default levels, write the access audit, and commit before caches are refreshed.

## Persistence

Network level `n` begins at `100 × (n-1)²` XP. Core loads it on join and displays it in the experience bar; the gauge shows progress toward the next level and refreshes after every XP gain or mission reward. Personal rotations use the `Europe/Paris` day and ISO week, with five daily and three weekly slots. Players receive two daily rerolls, plus two through `tropicube.missions.reroll.bonus`. Claiming completion, currency, transaction history, and XP is one SQL transaction.

Guilds persist independently from matches. Owner, officer, and member roles gate mutations; member/officer limits are checked server-side. Every network-XP gain from a match or mission triggers an asynchronous, per-member capped guild contribution and advances the contribution challenge. Ranked matches separately advance their challenge and aggregate seasonal score. A daily job transfers ownership after 30 inactive days to the oldest-joined active member and audits the transition.

Profiles aggregate identity, level, balance, social state, guild, and SheepWars statistics while enforcing summary/friends/private visibility. Lobby resolves preferences away from the Paper thread, hides only entities according to everyone/friends/party/nobody, and reapplies the filter after joins or changes. Smart selection favors a started countdown and then the fullest instance with enough capacity.

MySQL stores durable profiles, friendships, cosmetic grades, access levels and their audit, balances, moderation history, and SheepWars preferences. Individual permission rows no longer exist. Redis accelerates reads and coordinates ephemeral network state.

Core applies the ordered resources in `db/migration/index.txt` and records successful versions in `tropicube_schema_migrations`. The MySQL advisory lock `tropicube:core:schema` serializes the complete schema preparation across concurrently starting Paper backends. Conditional column changes query `information_schema.columns`; tests reject the unsupported MySQL variants `ADD/DROP COLUMN IF [NOT] EXISTS`.

## Shutdown

Every plugin cancels scheduled work and closes pools or subscriptions during shutdown. A completed game sends `PROXY:FINISH_GAME:<instanceId>`; Velocity transfers remaining players, unregisters the backend, removes the container and its labelled ephemeral `/data` volume, and clears Redis state. On startup, Velocity also removes labelled game volumes whose containers no longer exist.

Velocity mounts its runtime `/server` directory as `tmpfs`. The container seeds it from the immutable `/opt/tropicube/server` image directory on every start, and the data disappears automatically when the proxy stops. Deployment scripts remove a legacy anonymous `/server` volume once during migration.

## Guild management in Social

Lobby shows Friends, Party and Guilds in slots 2, 4 and 6 of Social. The guild shield replaces the Profile entry; membership remains visible in the player profile. Lobby owns the screens while Core supplies invitations, members, contributions, challenges, rankings and mutations. Member lists use 21 entries per page and resolve only the profiles needed by that page. Java retains player skins; Bedrock uses suitable vanilla icons.

Administrative writes and automatic succession acquire the MySQL named lock `tc:guild:<UUID derived from the database name>` across instances, with a five-second wait limit, then check and write through the same transactional connection. The lock is explicitly released before returning the connection to the pool. No schema change or new Redis key is required. Gameplay contributions do not acquire this lock. Menu actions carry the displayed guild ID, preventing stale screens from changing a player's new guild.

Core consumes private input before public chat filtering and network publication, including for muted players. Name/tag creation and invitation usernames are consumed once and handled on Paper. `!` cancels; navigation, logout and shutdown also invalidate input. SQL and profile resolution stay asynchronous. A loading response can only update the inventory that requested it. `/lang` refreshes guild screens and the current input step without restarting its deadline.

## Reliability contracts

Core prepares SQL, Redis, runtime UI restoration and the grade catalog on lifecycle workers. Lobby/SheepWars wait for Core, initialize their network client off-thread, then construct Paper components on the server scheduler. Admission stays closed until `TROPICUBE_BACKEND_READY`; deploy the proxy and backends together. Stopping the backend immediately closes admission. SQL work has bounded concurrency/queue and exceptional completion on overload or shutdown.

Dynamic memory is reserved atomically before creation and reconstructed from actual Docker limits at startup. Failed deletion retains the reservation. Matchmaking waits and retries on memory exhaustion. New resources carry `fr.tropicube.owner`; cleanup ignores other owners. Legacy containers must match the prefix and network; legacy volumes must match the data-volume prefix.

`tropicube_schema_checksums` records resource names and LF-normalized SHA-256 before migration execution, including interrupted DDL. Legacy history is checked against reviewed reference hashes. MySQL DDL still requires idempotent recovery. Backups and migrations share the `tropicube:core:schema` advisory lock.

Redis diagnostic keys `tropicube:health:proxy` and `tropicube:health:backend:<instanceId>` are atomically replaced every 60 seconds with a 180-second TTL. They contain aggregate capacity, queues and readiness, never player information. They do not restore games.
