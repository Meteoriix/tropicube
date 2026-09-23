# Fallen Kingdoms — technical specification

## Document status

This specification defines the approved Tropicube Fallen Kingdoms V1. As of 15 September 2026, the playable Paper runtime and active Velocity template ship with Cactus. Sessions use a generic map catalog and implement voting, persistent preferences, phases, protections, hearts, lives, kits, ruins, configurable combat, localized HUD, Paper events, idempotent MySQL results, and Redis-driven instance cleanup. The Lobby sends custom settings through an allowlist validated by Velocity and Paper. Paper/Docker scenarios below remain the manual production acceptance checks.

The document distinguishes services already provided by the network from FK work that remains to be delivered. Future behavior must never be presented as available gameplay.

## First-version scope

V1 must deliver a complete match lifecycle: waiting lobby, balanced kingdom selection, kits, map validation, timed phase transitions, protections, PvP, hearts, last lives, elimination, sudden death, victory, statistics, reconnect handling, world restoration, Redis/Velocity integration, localization, Docker templates, and cleanup.

The implementation must remain independent from SheepWars business classes. Only genuinely shared services belong in Core or Docker API.

## Approved V1 rules

Quick Play matches require at least six players: two to five kingdoms, three to six players per kingdom, and a final size difference of at most one. The target kingdom count is `max(2, ceil(players / 6))`, capped at five; the public thresholds are 6–12, 13–18, 19–24, and 25–30 players for two through five kingdoms. Custom matches retain the historical minimum of four players per kingdom, while beta queues keep their explicit one- and two-player overrides. Each map layout declares an eligible color pool. Allocation chooses the combination and balanced capacities that honor the most preferences, with session randomness used only to break equivalent solutions or oversubscribed choices.

All deadlines are measured from `PREPARATION`: preparation starts at 00:00, common-area PvP opens at 10:00, enemy bases and hearts open at 20:00, all remaining hearts are destroyed at 40:00 for sudden death, and the match resolves at 60:00. The pure day calculation is `elapsed / 600 + 1`, capped from day 1 through day 6. One team with living players wins early. The public combat profile is the intentionally limited `LEGACY_1_8` profile: no attack cooldown or sweep attack, emulated damage and knockback, and disabled shields and off-hand swaps. A custom match may explicitly select native Paper 26.3 combat. Friendly direct and indirect damage is always cancelled.

`WAITING` and `COUNTDOWN` cancel player damage, hunger, building, physical block destruction, and item drops while holding a clear daytime world. `PREPARATION` restarts at sunrise and uses five real minutes per half-day. Until `ASSAULT`, each participant receives only the nearby particle wall portions for enemy bases; movement and teleport checks remain the physical authority.

Each heart has 500 health. Before assault it is protected and inaccessible; during assault only enemy melee and projectile damage applies. TNT makes approved breaches but never directly damages a heart. Heart destruction puts surviving members into last life, cancels pending respawns, and permanently eliminates members already dead. A kingdom becomes neutral ruins only when its final survivor dies. Before its heart falls, a death drops inventory, spectates for ten seconds, then respawns without kit. Disconnect counts as a death; a returning roster member may only resume while their heart remains alive, and late arrivals spectate.

During active phases, each kingdom member sees their own heart's current and maximum health in the actionbar, including zero after destruction. A valid applied hit flashes the defending members' complete actionbar between white and red and plays `minecraft:entity.blaze.hurt`; duration, cadence, and sound cooldown are configurable, and a later hit extends the alert. Respawn and territory alerts retain priority and are rendered again in the player's current language. Hitting an enemy heart still displays its temporary bossbar independently.

Each personal scoreboard owns colored kingdom teams and a gray spectator team, using the real profile name for entries so visual nicks remain compatible. Only living allies glow for one another through the module-local `glowingentities` dependency. The view is rebuilt on start, reconnect, respawn, elimination, language or identity changes, and is cleared on disconnect, ending, and plugin shutdown. Kit menu lore is derived from immutable `KitDefinition.KitItem` values and displays aggregated quantities, stack counts, native potion names, and enchantments.

Every enabled map declares its game-owned loot chest IDs and positions. Day 1 initializes them empty; the day 2 through day 6 boundaries replace each inventory with three deterministic-testable weighted draws and broadcast a localized refill message. Startup loads chunks asynchronously and validates the chest blocks on the Paper thread. Persistent markers distinguish them from player-built storage. Withdrawals are allowed, while deposits, shift transfers, hoppers, block movement, explosions, and breaking are rejected.

At sudden death, respawns stop and the border reaches exactly 50 × 50 blocks at 60:00. At the limit, the team or teams with the most survivors win; an equal maximum is a draw. An administrative abort records no competitive statistics.

The default configuration validates a 30-second countdown, a 10-second result display, the ordered 10/20/40/60-minute phase deadlines, positive heart health, at least one kit, valid material identifiers, compatible layouts, non-overlapping coherent regions, ten unique Cactus loot positions outside bases, and positions inside the playable map. Custom matches may vary only explicitly allowed gameplay settings; technical protections, no friendly damage, container preservation, and non-blocking I/O cannot be overridden.

## Existing platform baseline and required FK work

| Area | Available network capability | Fallen Kingdoms work |
|---|---|---|
| Dynamic instances | Velocity creates, restores, probes, and destroys instances with `ServerInstance` lifecycle states. | Add a `FALLENKINGDOMS` template, image, non-overlapping port range, and `GAME_WAITING` through `GAME_ENDING` publication. |
| Match finish | Velocity handles `PROXY:FINISH_GAME:<instanceId>`, lobby transfer, and ephemeral volume cleanup. | Publish it only after the immutable result is fixed and gameplay is frozen. |
| Worlds | Pre-warmed Paper images are copied into an instance-owned `/data` volume. | Package immutable validated maps; keep `.mca` regions in Git LFS and never commit runtime/player data. |
| Core | Profiles, language, access levels, economy, moderation, and a Java API are available. | Use Core for network concerns while keeping FK domain logic isolated. |
| UI and language | Core distributes versioned language/UI generations through Redis. | Add FK menu, scoreboard, tablist, and four-language resources to the bundle, editor, and deployment mirrors. |
| Bedrock | Geyser/Floodgate are connected at Velocity. | Preserve the same player flow and provide safe visual fallbacks. |

## Confirmed rules

### Players and kingdoms

The match supports several kingdoms with configurable capacity and balanced assignment. Each player owns one kingdom membership and one kit for the match. Late arrivals follow the explicit spectator or replacement policy and never receive an unsafe implicit active role.

### Timeline

Preparation, PvP opening, siege opening, sudden death, and final timeout are explicit scheduled transitions. Default durations are configurable and validated. Repeated or late scheduler callbacks must be idempotent.

### Combat

PvP is disabled before its opening transition. Base and heart protections depend on the current phase, attacker kingdom, target kingdom, material, location, and objective state. Damage and interaction listeners remain thin adapters over pure policy objects.

### Hearts, last lives, and elimination

Each kingdom owns a heart with an explicit healthy/destroyed state. Before destruction, normal death follows configured respawn rules. After destruction, affected players enter their last-life condition; their next qualifying death eliminates them. A kingdom is ruined when its objective and eligible players satisfy the configured ruin rule.

### Death, disconnect, and late arrival

Disconnect state is retained for a bounded reconnect window. A reconnect restores the same kingdom, kit, life state, and safe location when the match still accepts it. Disconnecting must not evade death, last-life, or elimination rules. Late arrival cannot change kingdom balance or victory unexpectedly.

### Sudden death and final result

Sudden death prevents an indefinite fortified stalemate by tightening protections or applying configured pressure. At the hard time limit, a deterministic comparator ranks kingdoms using documented objective and survivor criteria; an exact tie may produce a draw.

## Configurable rules

- minimum and maximum players;
- kingdom count and capacity;
- countdown and phase durations;
- PvP, siege, sudden-death, and final timeout timings;
- respawn delay and reconnect grace period;
- heart health or destruction mechanics;
- last-life behavior;
- enabled kits and kit balance;
- permitted and forbidden blocks by phase and region;
- map catalog, spawns, hearts, regions, and world boundaries;
- victory comparator and tie handling.

Every option requires a safe default, startup validation, and documentation.

## Temporary assumptions

Unconfirmed historical details remain explicit assumptions, never hidden constants. Reversible values use configuration. Any ambiguity that changes persistence, architecture, kingdom balance, elimination, or player experience must be raised before implementation.

## State machine

```text
WAITING -> COUNTDOWN -> PREPARATION -> PVP -> ASSAULT -> SUDDEN_DEATH -> ENDING -> ENDED
   ^          |              |             |              |
   +-- invalid roster/map ---+-------------+--------------+
Any active state -> ENDING with ADMIN_ABORT on shutdown or administrator abort
```

### State invariants

- only one state is active;
- transitions validate their expected source;
- a completed transition may be called again without duplicating effects;
- `ENDING` and `ENDED` reject gameplay events;
- every state-owned task is cancelled when leaving that state;
- world mutations occur only on the Paper scheduler.

### Trigger events

Player thresholds trigger countdown evaluation. Scheduled deadlines trigger phase changes. Heart destruction and eligible-player counts trigger ruin or victory evaluation. Proxy shutdown, plugin disable, invalid map state, or administrator abort triggers controlled `ADMIN_ABORT` cleanup. A state-owned task captures the expected session ID and ignores late callbacks.

## Component responsibilities

| Component | Responsibility |
|---|---|
| `FallenKingdomsPlugin` | Dependency wiring and lifecycle only |
| `GameManager` | State machine and transition orchestration |
| `KingdomService` | Balanced assignment and kingdom state |
| `HeartService` | Objective ownership, damage, destruction, and events |
| `LifeService` | Death, respawn, last life, elimination, reconnect |
| `ProtectionPolicy` | Pure region/material/phase decisions |
| `KitCatalog` | Validated immutable kit definitions |
| `MapCatalog` | Validated maps, regions, spawns, and hearts |
| Paper listeners | Translate API events into domain operations |
| Redis gateway | Instance state, reconnect markers, and proxy commands |
| Statistics repository | Asynchronous durable match results |

## Domain model

### Match

Stores immutable settings and current state, transition timestamps, selected map, kingdom registry, player registry, and result. Mutable access remains encapsulated.

### Kingdoms and bases

A kingdom owns its ID, display color, members, base region, spawn, heart, and ruin state. Region operations use a dedicated immutable geometry model testable without Bukkit.

### Players

A match player stores UUID, kingdom, kit, connection state, eliminated state, last-life state, kills, deaths, objective contribution, and reconnect deadline.

### Hearts

Hearts expose explicit ownership and state. Damage validates phase, attacker, position, protection, and duplicate-destruction behavior atomically on the server thread.

### Kits and maps

Catalogs load configuration into validated immutable definitions. Invalid IDs, duplicate locations, overlapping mandatory regions, missing spawns, invalid world names, or unsafe bounds fail startup with actionable messages.

## YAML configuration

The future module configuration will contain Redis access, general timings, player and kingdom limits, reconnect policy, protection matrices, kit definitions, map definitions, heart locations, spawns, build regions, world boundaries, and forced operational overrides.

Deployment copies must remain synchronized with embedded resources. Player messages remain in the four Core languages with placeholder parity.

## Target commands and permissions

Normal gameplay uses menus and items. The waiting hotbar follows the SheepWars flow: kingdom, kit, and map in slots 0 through 2, host start or cancellation in slot 4, Profile in slot 7, and return to the lobby in slot 8. Items show the current choice and exact click. Inventories share the network frame, keep back on the left and close on the right, distinguish the selected entry, and show kingdom preferences, kit descriptions, and vote totals. The Paper administrative command is `/fkadmin status|start|cancel|stop|reload`, protected by `fallenkingdoms.admin`; reload is allowed only in `WAITING`. The proxy `/stats [player]` belongs to shared statistics delivery. Every command and permission must be documented when implemented.

## Protection matrix

Protection decisions combine match state, actor state, actor kingdom, target region, target kingdom, material, and action type. Unit tests cover own base, neutral land, enemy base, heart area, forbidden materials, liquids, explosions, fire, entities, containers, and boundary heights for every phase.

## Death and respawn

Death handling is idempotent. It records the cause, updates statistics, applies inventory policy, evaluates last life and elimination, chooses a safe respawn or spectator state, and then evaluates kingdom ruin and match victory. Late death events after match completion are ignored.

## Kingdom ruin

Heart destruction is announced once. The ruined kingdom's eligible players receive their last-life state. Eliminated players become spectators and lose gameplay interaction. The kingdom remains represented for statistics even after all members are eliminated.

## Victory and cleanup

Victory produces an immutable result, persists statistics asynchronously, updates interfaces, cancels tasks, blocks further world changes, writes replay markers, and asks Velocity to transfer players. Cleanup removes entities, subscriptions, caches, Redis keys, and the dynamic container or restores a local development world.

## Custom Paper events

Domain transitions may publish typed Paper events for phase change, heart damage, heart destruction, player elimination, kingdom ruin, and match end. Events are informational adapters and cannot bypass state invariants.

## Map restoration

Production games use immutable world templates or disposable containers. Local development may restore from a clean directory. Player data and runtime world changes are never committed. `.mca` templates remain in Git LFS.

## Core, Redis, and Velocity

Core supplies language, player, grade, permission, economy, and database services where their responsibility is network-wide. MySQL stores durable statistics. Redis stores bounded ephemeral state with documented TTLs. Velocity creates, registers, routes, and destroys instances without learning Fallen Kingdoms business rules.

The shared cross-game statistics contract is future work. It must migrate existing SheepWars data only with explicit schema compatibility and regression tests; it is not a claim that a global `/stats` implementation currently exists.

## Future Fallen Kingdoms ranked play

Ranked Fallen Kingdoms is explicitly post-V1. The V1 result contract reserves a stable game identifier and immutable placements, wins, draws, eliminations, and objective contributions so that the ranked phase can introduce its own rating policy.

Competitive profiles are namespaced by game. A player's Fallen Kingdoms Elo is fully independent from their SheepWars Elo and from future games. The ranked phase will define one or more FK-specific fixed queue formats, eligible maps, kingdom sizes, party/reconnect policy, multi-kingdom placement calculation, and rating movement. It must not inherit SheepWars `RANKED_4V4`, `RANKED_8V8`, party limits, or matchmaking policy by default. Redis and MySQL work remain asynchronous, and rating/result writes are idempotent by match ID.

## Mandatory edge cases

- countdown threshold gained and lost repeatedly;
- host or team member disconnect during every phase;
- reconnect before and after grace expiry;
- simultaneous lethal events and heart destruction;
- all kingdoms eliminated in the same tick;
- invalid or partially configured map;
- plugin disable during an active transition;
- Redis, SQL, or proxy command failure;
- full server receiving a late join;
- duplicate scheduled callbacks;
- world event received after `ENDING`;
- no valid winner at hard timeout.

## Testable acceptance criteria

### Unit tests

State transitions, team balancing, phase deadlines, protection matrices, heart invariants, last lives, elimination, reconnect policy, victory comparison, kit and map validation, and repeated cleanup calls work without a Paper server.

### Resource and module tests

Configuration loads, four-language keys and placeholders match, embedded and deployment resources agree, plugin descriptors are valid, Maven dependencies preserve game boundaries, and documentation links build in both languages.

The later competitive module must prove that two game results for the same player update isolated Elo profiles and that no FK operation reads or writes SheepWars rating.

### Paper and Docker scenarios

Manual validation covers a complete match, every phase boundary, combat opening, legal and illegal building, heart destruction, reconnects, late arrival, sudden death, result transfer, container removal, world reset, `/lang`, and shutdown during play.

## Approved implementation sequence

1. Validate unresolved game-design decisions.
2. Add the Maven module resources and pure state machine.
3. Implement immutable configuration catalogs and tests.
4. Add kingdoms, hearts, lives, protections, and unit tests.
5. Add thin Paper adapters and interfaces.
6. Integrate Core, Redis, Velocity, Docker, and persistence.
7. Add maps and deployment templates through Git LFS.
8. Run targeted tests after every coherent step.
9. Finish with the complete reactor, Docker validation, documentation site, diff review, and staged in-game acceptance scenarios.

Ranked play follows the V1 foundation as a separate delivery with its own migration, fixed-format queue acceptance tests, multi-kingdom rating tests, and operational validation.
