# Fallen Kingdoms — technical specification

## Document status

This specification defines the approved direction for the first Tropicube Fallen Kingdoms implementation. The Maven module is currently an empty placeholder. No runtime behavior should be inferred until the module is implemented and tested.

## First-version scope

V1 must deliver a complete match lifecycle: waiting lobby, balanced kingdom selection, kits, map validation, timed phase transitions, protections, PvP, hearts, last lives, elimination, sudden death, victory, statistics, reconnect handling, world restoration, Redis/Velocity integration, localization, Docker templates, and cleanup.

The implementation must remain independent from SheepWars business classes. Only genuinely shared services belong in Core or Docker API.

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
LOADING -> WAITING -> STARTING -> PREPARATION -> PVP -> SIEGE
                                              -> SUDDEN_DEATH
                                              -> ENDING -> ENDED
Any active state -> ABORTED on unrecoverable failure
```

### State invariants

- only one state is active;
- transitions validate their expected source;
- a completed transition may be called again without duplicating effects;
- `ENDING`, `ENDED`, and `ABORTED` reject gameplay events;
- every state-owned task is cancelled when leaving that state;
- world mutations occur only on the Paper scheduler.

### Trigger events

Player thresholds trigger countdown evaluation. Scheduled deadlines trigger phase changes. Heart destruction and eligible-player counts trigger ruin or victory evaluation. Proxy shutdown, plugin disable, invalid map state, or unrecoverable persistence failure trigger controlled abort and cleanup.

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

Normal gameplay should use menus and items. Administrative setup may expose commands for map selection, region points, kingdom spawns, heart locations, validation, forced phase transitions, and diagnostics. Every command and permission must be documented when implemented.

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
