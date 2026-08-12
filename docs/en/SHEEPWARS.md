# SheepWars

## Overview

SheepWars is a team PvP minigame played on opposing islands. Players fight with conventional weapons and throwable sheep that trigger special abilities. Tropicube uses a single-elimination round between red and blue: a team wins when the other team has no living participant. When time expires, the team with more survivors wins; equal survivor counts produce a draw.

The implementation contains fifteen sheep types, three classes, nine kits, map voting, host-controlled custom games, Docker orchestration, four interface languages, reconnect support, replay matchmaking, and late-join spectators.

## Game loop

1. The instance waits while profiles, languages, kits, classes, teams, and map votes are loaded.
2. In a standard game, reaching the configured minimum starts the countdown automatically. A custom-game host may start manually or enable automatic start.
3. At match start, participants are assigned to shuffled team spawns and receive colored leather armor, a sword, an Infinity bow, one arrow, and one random special sheep.
4. Every living player receives another sheep at the configured interval. Members of the smaller team receive three additional sheep at the start.
5. Death is final for the round and changes the player to spectator mode.
6. Players joining after the match starts also become spectators. They have no team, cannot deal or receive game damage, and do not affect survivor counts or victory.
7. After the result screen, Velocity transfers everyone to a lobby and destroys the finished game container. A pre-created equivalent instance may be offered for replay.

Default values are a ten-second countdown, a 600-second match, and a twenty-second sheep distribution interval. Gameplay values remain configurable.

## Classes and kits

Classes group kits by role; the selected kit provides the actual bonus.

| Class | Kit | Effect |
|---|---|---|
| DPS | Swordsman | Sharpness I stone sword instead of a wooden sword |
| DPS | Archer | Power I in addition to Infinity |
| DPS | Death Shepherd | Sheep deal 50% more damage |
| Tank | Colossus | Four additional hearts |
| Tank | Anchor | 80% knockback resistance |
| Tank | Steel Feather | 80% less fall damage |
| Support | Breeder | Sheep have 40 health instead of 16 |
| Support | Medic | Arrows heal teammates |
| Support | Acrobat | Permanent Jump Boost II |

A host may disable classes or kits. Random-kit mode ignores personal selection and assigns an enabled kit at match start.

## Special sheep

| Sheep | Main behavior |
|---|---|
| Boarding | Carries its thrower across the gap |
| TNT | Produces a strong explosion |
| Distortion | Animates and relocates blocks around impact |
| Darkness | Slows and blinds nearby enemies |
| Fire | Ignites enemies around impact |
| Poison | Creates a persistent poison area |
| Swap | Exchanges the thrower with a nearby target, or dashes without one |
| Meteor | Calls down a meteor shower |
| Searching | Pursues the nearest enemy |
| Healing | Heals the thrower and nearby allies |
| Lightning | Strikes a target and chains to nearby enemies |
| Gravity | Pulls enemies inward, then launches them upward |
| Mecha | Deploys a durable golem unit against the opposing team |
| Strength | Temporarily increases nearby allied damage |
| Fragmentation | Releases five secondary explosive sheep |

`default-settings.sheep-probabilities` controls relative weights. Each player owns an independent weighted draw deck. Tokens are consumed without replacement to smooth distribution, and consecutive duplicates are avoided whenever the configured weights permit it. Forced settings and host menus can disable individual types.

## Maps and teams

A map defines a world, waiting lobby, void limit, and up to eight red plus eight blue spawns. Spawn lists are shuffled per match. A map cannot start when required locations are missing.

Players may request a team during the waiting phase. The manager enforces balance and assigns a team automatically when needed. Team selection closes once play begins.

Each viewer receives a personal scoreboard containing red, blue, and spectator teams. Entries use `PlayerProfile.getName()` rather than the historical Bukkit connection name, so `/nick` identities receive the correct nameplate, tablist, and glow color. Colored teams are installed before glow metadata is sent. Living teammates glow only for their own team; dead players and late arrivals appear gray as spectators.

## Custom games and host role

Velocity atomically reserves `host-creation:<uuid>` before creating a custom instance, then records ownership with `host:<uuid>`. A player may own only one active or pending custom server.

Before the match, the host can:

- start or cancel the countdown;
- change minimum and maximum players;
- change countdown, duration, and sheep interval;
- enable automatic start, random kits, and map voting;
- enable or disable sheep, classes, and kits.

Automatic start defaults to off for custom games and on for standard games. Ownership remains until match completion, then is removed.

## Spectators and reconnects

`ServerInstance.Status.GAME_PLAYING` remains joinable while capacity is available. The lobby displays it as blue `PLAYING` and explains that clicking will spectate.

When a player joins during `PLAYING`, SheepWars creates a non-living `GamePlayer` with no team, resets unsafe inventory and effects, teleports near an active participant when possible, and applies `GameMode.SPECTATOR`. Scoreboards and tablist show spectator state. The player remains included for end-of-game transfer but excluded from team lists, sheep distribution, combat, and victory checks.

The lobby command `/sw join` uses `sw:left-game:<uuid>` for five minutes. Rejoining an active match follows the same spectator path; a disconnected living participant is eliminated before removal.

## Interface and languages

All actions use hotbar items and inventory menus; the SheepWars Paper plugin declares no dedicated command. The sidebar shows state, survivor counts, personal team or spectator mode, kills, time, and end-of-game statistics. The tablist displays team colors, survivor counts, timer, or spectator status.

Player-facing messages come from TropicubeCore and exist in French, English, German, and Spanish.

## Configuration

The source file is `tropicube-sheepwars/src/main/resources/config.yml`. Main sections are:

- `redis`;
- `default-settings`;
- `custom-game-default-settings`;
- `force-settings`;
- `locations`.

At deployment, `INSTANCE_ID`, `SERVER_NAME`, `IS_HOST`, and `HOST_UUID` connect the Paper plugin to Velocity orchestration.

## State and cleanup

The explicit game states are `WAITING`, `STARTING`, `PLAYING`, `ENDING`, and `ENDED`. Transitions publish corresponding `ServerInstance` states to Redis. Scheduled countdown, game tick, and sheep-distribution tasks are retained and cancelled on transition or shutdown. Late events cannot restart an ended match.

After the result delay, SheepWars writes replay markers and sends `PROXY:FINISH_GAME:<instanceId>`. Velocity transfers players, retries lobby selection when required, unregisters the backend, removes the container and anonymous volumes, and clears Redis state.

See [Deployment](DEPLOYMENT.md) and [Configuration](CONFIGURATION.md) for maps, images, templates, and operational setup.
