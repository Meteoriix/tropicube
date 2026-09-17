# SheepWars

## Overview

SheepWars is a team PvP minigame played on opposing islands. Players fight with conventional weapons and throwable sheep that trigger special abilities. Tropicube uses a single-elimination round between red and blue: a team wins when the other team has no living participant. When time expires, the team with more survivors wins; equal survivor counts produce a draw.

The implementation contains fifteen sheep types, three classes, nine kits, map voting, host-controlled custom games, Docker orchestration, four interface languages, reconnect support, replay matchmaking, and late-join spectators.

## Game loop

1. The instance waits while profiles, languages, kits, classes, teams, and map votes are loaded.
2. In a standard game, reaching the configured minimum starts the countdown automatically. A custom-game host may start manually or enable automatic start.
3. At match start, participants are assigned to shuffled team spawns and receive colored leather armor, a sword, an Infinity bow, one arrow, and one random special sheep.
4. Every living player receives another sheep at the configured interval. A deadline reached with a full sheep stock remains pending until space is available, then that player's full interval starts again. The smaller team receives a shared pool of three sheep per missing player, capped at two bonuses per recipient.
5. A single glowing wool block floats between the bases. An arrow crossing it consumes the target and immediately grants its bonus to every connected survivor on the shooter's team.
6. Death is final for the round and changes the player to spectator mode.
7. Players joining after the match starts also become spectators. They have no team, cannot deal or receive game damage, and do not affect survivor counts or victory.
8. After the result screen, Velocity transfers everyone to a lobby and destroys the finished game container. A pre-created equivalent instance may be offered for replay.

Default values are a ten-second countdown, a 600-second match, and a twenty-second sheep distribution interval. A survivor who keeps inventory space receives one starting sheep and twenty-nine useful periodic deliveries before timeout. Gameplay values remain configurable.

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
| Swap | Exchanges the thrower with a nearby target; without one, the impact ends without moving the thrower |
| Meteor | Calls down a meteor shower |
| Searching | Pursues the nearest enemy |
| Healing | Heals the thrower and nearby allies |
| Lightning | Strikes a target and chains to nearby enemies |
| Gravity | Pulls enemies inward, then launches them upward |
| Mecha | Deploys a durable golem unit against the opposing team |
| Strength | Temporarily increases nearby allied damage |
| Fragmentation | Releases five secondary explosive sheep |

`default-settings.sheep-probabilities` controls relative weights. Every delivery performs a weighted draw over the active distribution. After two identical sheep in a row for one player, that type is excluded from the next draw and the remaining weights are renormalized for that delivery only. Forced settings and host menus can disable individual types.

Every launched sheep records its thrower's UUID. A player who destroys another player's destructible sheep recovers one sheep of the same type while below the stock limit. The original thrower never recovers their own sheep, including when an explosion attributed to that thrower kills the entity.

## Aerial team power-ups

Each map may define as many candidate centers as needed under `locations.<map>.powerups.target1`, `target2`, and so on. At match start, exactly one randomly selected center displays a glowing wool `BlockDisplay`: lime announces team healing, purple poison arrows, and light blue team speed. Each of the six deployed maps provides five candidates.

Collision checks cover the arrow's full path between ticks, preventing fast projectiles from skipping the target. The shooter must be a living participant. The arrow and display are consumed, every connected living teammate receives the effect, and one target respawns after 45 seconds by default with a newly weighted type and a different location when several candidates exist. Match completion and plugin shutdown cancel the task and remove the display.

Defaults provide 6 HP of healing, three Poison I arrows that actually last 5 seconds per teammate, or Speed I for 8 seconds. Bonus arrows are identified when fired and apply the configured duration directly, bypassing vanilla's tipped-arrow duration reduction. Weights, strengths, durations, respawn time, and hit radius are startup-validated under `team-powerups`.

## Maps and teams

A map defines a world, waiting lobby, void limit, and up to eight red plus eight blue spawns. Spawn lists are shuffled per match. A map cannot start when required locations are missing.

When `map-vote-enabled` is `true`, every enabled map is available in the vote in every mode, including Ranked, with pagination when required. Every player has exactly one vote, and a map is randomly selected among the tied winners. The waiting scoreboard displays the current leader and vote count, or `Tie (n)` when several maps share the lead. Otherwise, the host chooses the map directly, and that choice immediately appears in every player's waiting sidebar.

Each map declares its environmental hazards. Galions disables elimination below the vertical limit because its lower area is water and refreshes Poison I for one second while a survivor remains in water. Other maps retain their `void_limit` elimination.

Blocks destroyed during a match do not create collectible drops. This also covers all four rail variants detached by block physics when their supporting block disappears.

Players may request a team during the waiting phase. The manager enforces balance and assigns a team automatically when needed. A successful selection immediately refreshes every player's tablist, including nicked names, so the displayed color follows the new team. Team selection closes once play begins.

Each viewer receives a personal scoreboard containing red, blue, and spectator teams. Entries use `PlayerProfile.getName()` rather than the historical Bukkit connection name, so `/nick` identities receive the correct nameplate and glow color. Colored teams are installed before glow metadata is sent. Living teammates glow only for their own team; dead players and late arrivals appear gray as spectators. The SheepWars tablist always hides both network and fake grades: it displays only the name in the player's team color, or gray for a spectator, with no leading icon. This rendering is reapplied after Core's local completion signal for every nick change and after every grade event, so `/nick off` immediately restores the real name in chat, the tablist, and scoreboard teams.

## Custom games and host role

Velocity atomically reserves `host-creation:<uuid>` before creating a custom instance, then records ownership with `host:<uuid>`. A player may own only one active or pending custom server.

Before the match, the host can:

- start or cancel the countdown;
- change minimum and maximum players;
- change countdown, duration, and sheep interval;
- enable automatic start, random kits, and map voting;
- enable or disable sheep, classes, and kits.

Automatic start defaults to off for custom games and on for standard games. Ownership remains until match completion, then is removed.

At creation time, the host chooses public or private access. A private game is absent from lobby selectors and counts for players who are not admitted. The host is automatically whitelisted and receives an iron-door item in waiting-hotbar slot 6. Its menu adds players through an anvil text field and removes existing members by clicking their heads. `/whitelist add|remove <player>` exposes the same operation. Velocity validates ownership, protects the host from removal, and checks the UUID again on every connection.

## Spectators and reconnects

`ServerInstance.Status.GAME_PLAYING` remains joinable while capacity is available. The lobby displays it as blue `PLAYING` and explains that clicking will spectate.

When a player joins during `PLAYING`, SheepWars creates a non-living `GamePlayer` with no team, resets unsafe inventory and effects, teleports near an active participant when possible, and applies `GameMode.SPECTATOR`. Scoreboards and tablist show spectator state. The player remains included for end-of-game transfer but excluded from team lists, sheep distribution, combat, and victory checks.

The argumentless lobby command `/rejoin` uses `sw:left-game:<uuid>` for five minutes. Rejoining an active match follows the same spectator path; a disconnected living participant is eliminated before removal.

The private-game host manages access through a zero-cost anvil accepting a valid Minecraft name or canonical UUID. The placeholder cannot be submitted. After an add or remove request, the menu waits for Velocity to save the instance and publish an acknowledgement before refreshing; a missing acknowledgement is reported after five seconds.

## Interface and languages

All game actions use hotbar items and inventory menus. Waiting slots 0, 1, and 2 open team, class/kit, and map vote; hosts keep settings in slot 4 and private whitelist management in slot 6. The shared Tropicube player center occupies slot 7 and the leave bed slot 8. The network XP display is hidden on backend entry and released on departure. At match start, every open inventory is closed and every waiting item is removed before combat equipment is granted. Under the tropical `🐑 SHEEPWARS` title, short separators and one blank line after the map keep every sidebar state airy, whether the player has selected a team or is spectating. While waiting, it shows current and maximum players, the required minimum, the current map-vote leader and vote count, team, class, and selected kit. During play, it shows time, map, survivor counts, personal team or spectator mode, class, selected kit, eliminations, and sheep thrown. The kit refreshes immediately after selection and after a language change. The tab list uses two-line `🐑 SHEEPWARS` and `🌴 TROPICUBE` branding and adapts its footer to waiting, starting, playing, and ending states.

Player-facing messages come from TropicubeCore and exist in French, English, German, and Spanish.

Standalone system announcements use the bracket-free `SHEEPWARS >` identity. Player joins and departures remain narrative and unprefixed, explicitly saying that the player joined or left the game. Menus, titles, scoreboards, and tab lists remain unprefixed for readability.

## Configuration

The source file is `tropicube-sheepwars/src/main/resources/config.yml`. Main sections are:

- `redis`;
- `default-settings`;
- `custom-game-default-settings`;
- `team-powerups` for target timing, collision, weighted effects, and effect strengths;
- `force-settings`;
- `locations`, including each map's numbered `powerups.target1`, `target2`, and subsequent candidate centers.

The host menu covers every `default-settings` option. Player limits stay between 2 and 16, cannot be lowered below current attendance, and maximum-capacity changes are published to Velocity immediately. When a party member joins the waiting room, SheepWars attempts to reuse a party mate's team only when the resulting red/blue size difference remains at most one.

At deployment, `INSTANCE_ID`, `SERVER_NAME`, `IS_HOST`, `HOST_UUID`, and the internal `CUSTOM_GAME_PRIVATE` flag connect the Paper plugin to Velocity orchestration.

## State and cleanup

The explicit game states are `WAITING`, `STARTING`, `PLAYING`, `ENDING`, and `ENDED`. Transitions publish corresponding `ServerInstance` states to Redis. The retained game-tick task advances both match time and per-player sheep deadlines and is cancelled on transition or shutdown. Late events cannot restart an ended match.

After the result delay, SheepWars writes replay markers and sends `PROXY:FINISH_GAME:<instanceId>`. Velocity transfers players, retries lobby selection when required, unregisters the backend, removes the container and its labelled ephemeral data volume, and clears Redis state.

See [Deployment](DEPLOYMENT.md) and [Configuration](CONFIGURATION.md) for maps, images, templates, and operational setup.
