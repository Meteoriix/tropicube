# Commands

## Velocity commands

| Command | Alias | Permission | Purpose |
|---|---|---|---|
| `/server [name]` | — | none | Lists known instances or connects to one |
| `/queue <server>` | `/file` | none | Joins the queue for a full instance; Premium and staff grades have priority |
| `/hub` | `/lobby` | none | Connects to the least loaded lobby |
| `/whitelist add <player>` | — | private-game host | Adds a known player to the private custom game |
| `/whitelist remove <player>` | — | private-game host | Removes a player, except the host, from the private game |
| `/nick` | — | configured grade | Generates and applies a random name and signed skin |
| `/nick off` | — | none | Restores the original identity, even after losing the required grade |
| `/find <player>` | — | `tropicube.admin.find` | Locates a connected player |
| `/send <player|*> <server>` | — | `tropicube.admin.send` | Transfers one or all players |
| `/pull <player>` | — | `tropicube.admin.pull` | Brings a player to the sender's joinable server |
| `/tropicube ...` | `/tropi`, `/cm` | `tropicube.admin` | Administers dynamic instances |
| `/maintenance <network\|type> <on\|off\|status> [minutes] [reason]` | — | `tropicube.admin.maintenance` | Starts a network or game-type drain with a deadline |
| `/announce <network\|type\|instance> <language.key>` | — | `tropicube.admin.announce` | Broadcasts a configured localized announcement |
| `/networkdiag` | `/netdiag` | `tropicube.admin.diagnostic` | Displays Redis, instance, and connection-protection health |

`nick.allowed-grades` controls who may enable a nick. Disabling is always allowed, cancels an outstanding skin request, and can be retried while a backend has not restored the profile. The nick and original-profile Redis entries remain available until restoration succeeds, so a lost event cannot lock the player into the visual identity. The Redis identity keeps the fake `PREMIUM` display grade for 24 hours after disconnecting and restores it in the tablist after reconnecting, without changing real permissions. Concurrent generations for the same player are rejected.

Velocity validates `/whitelist`: the sender must own an active private custom game. Names resolve among players currently or previously seen by the proxy, and UUIDs are accepted directly. The SheepWars host hotbar item uses the same proxy-owned mutation path.

## Core commands

| Command | Permission | Purpose |
|---|---|---|
| `/money` | none | Displays only the sender's TropiCoin balance |
| `/eco <set|add|remove|top> ...` | economy administration | Changes balances or displays the ranking |
| `/rank <set|info|list> ...` | grade administration | Reads and assigns grades, including temporary grades |
| `/tropiperm ...` | permission administration | Manages individual and grade permissions |
| `/lang [fr|en|de|es]` | none | Reads or changes the persistent language |
| `/help [general|games|profile|staff]` | none; staff section is restricted | Summarizes available commands by category |
| `/friend add|accept|deny|remove <player>` | none | Manages persistent friendships |
| `/friend list|requests|join <player>` | none | Lists social state or joins a friend's current game, as spectator after start |
| `/party invite|accept|deny <player>` | none | Creates or joins a network party |
| `/party list|leave|kick|promote|disband` | none | Reads or administers party membership |
| `/party follow on|off` | none | Controls whether this member follows the leader |
| `/party warp` (`/party tp`) | party leader | Moves every online, follow-enabled member to the leader's server |
| `/party chat <message>` (`/pc`) | none | Sends a party-only message |
| `/mute`, `/unmute`, `/kick`, `/warn`, `/history` | moderation permissions | Performs and audits moderation actions |
| `/ban`, `/tempban`, `/unban` | ban permission + TOTP session | Applies network-wide bans at the proxy |
| `/report <player> <category> [#message] [details]` | none | Creates a report and optionally captures available chat evidence |
| `/reports list|claim|resolve ...` | report management + TOTP session | Processes the staff report queue |
| `/msg`, `/reply`, `/ignore`, `/globalchat` | none | Cross-server communication and persistent ignores |
| `/2fa issue|enroll|confirm|verify ...` | staff/enrollment permissions | Enrolls TOTP and validates a fifteen-minute staff session |
| `/staff`, `/staffchat <message>` | staff + TOTP session | Invisible controlled spectator mode and cross-server staff chat |
| `/profile [player]` | none | Displays the network profile according to its visibility |
| `/settings profile|messages|global|entities|hints <value>` | none | Updates persistent preferences |
| `/missions [reroll|claim] ...` | none | Displays and manages personal daily/weekly missions |
| `/notifications [read <id>]` | none | Reads the complete notification center |
| `/coreadmin reload` | Core administration | Reloads supported Core configuration (`/tropiadmin` remains an alias) |

## Lobby commands

| Command | Purpose |
|---|---|
| `/play` | Opens the server selector (`/servers` remains an alias) |
| `/rejoin` | Rejoins the remembered active SheepWars instance as a spectator; no arguments are accepted |
| `/replay` | Joins or waits for the suggested replay instance (`/playnext` remains an alias) |
| `/replayconfirm` | Confirms a fresh batch of five automatic replays |
| `/lang` | Opens or updates language selection |
| `/fly` | Toggles authorized lobby flight |
| `/vip` | Opens the VIP presentation interface |

## SheepWars controls

SheepWars registers no standalone Paper command. Team, class, kit, map, host settings, start/cancel, and return-to-lobby actions use hotbar items and inventory menus. Late arrivals and reconnecting players enter spectator mode when the game is already in progress.

## Administration principles

Command handlers validate arguments and permissions at the boundary. Player-facing text comes from the four language files. Operations involving SQL, Redis, Docker, or disk must not block the Paper or Velocity event thread.

After `/` is entered, clients receive only commands and aliases provided by the Tropicube infrastructure. External commands and namespaces stay hidden; `/?`, Bukkit/Minecraft namespaces, and vanilla commands are rejected network-wide. Accepting a party invitation while already grouped atomically leaves the old party, promotes a successor when needed, and joins the new one.
