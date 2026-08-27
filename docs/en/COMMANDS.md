# Commands

## Velocity commands

| Command | Alias | Permission | Purpose |
|---|---|---|---|
| `/server [name]` | — | none | Lists known instances or connects to one |
| `/queue <server>` | `/file` | none | Joins the queue for a full instance; `vipLevel ≥ 2` has priority |
| `/hub` | `/lobby` | none | Connects to the least loaded lobby |
| `/whitelist add <player>` | — | private-game host | Adds a known player to the private custom game |
| `/whitelist remove <player>` | — | private-game host | Removes a player, except the host, from the private game |
| `/nick` | — | `vipLevel ≥ 3` | Generates and applies a random name and signed skin |
| `/nick off` | — | none | Restores the original identity, even after losing the required grade |
| `/find <player>` | — | `tropicube.admin.find` | Locates a connected player |
| `/send <player|*> <server>` | — | `tropicube.admin.send` | Transfers one or all players |
| `/pull <player>` | — | `tropicube.admin.pull` | Brings a player to the sender's joinable server |
| `/tropicube ...` | `/tropi`, `/cm` | `tropicube.admin` | Administers dynamic instances |
| `/maintenance <network\|type> <on\|off\|status> [minutes] [reason]` | — | `tropicube.admin.maintenance` | Starts a network or game-type drain with the configured default deadline |
| `/announce <network\|type\|instance> <language.key>` | — | `tropicube.admin.announce` | Broadcasts a configured localized announcement |
| `/networkdiag` | `/netdiag` | `tropicube.admin.diagnostic` | Displays Redis, instance, and connection-protection health |

`vipLevel ≥ 3` controls who may enable a nick. Disabling is always allowed, cancels an outstanding skin request, and can be retried while a backend has not restored the profile. The visual grade remains independent from access levels.

Velocity validates `/whitelist`: the sender must own an active private custom game. Names resolve among players currently or previously seen by the proxy, and UUIDs are accepted directly. The SheepWars host hotbar item uses the same proxy-owned mutation path.

## Core commands

| Command | Permission | Purpose |
|---|---|---|
| `/money` | none | Displays only the sender's TropiCoin balance |
| `/eco <set|add|remove|top> ...` | economy administration | Changes balances or displays the ranking |
| `/rank <set|info|list> ...` | `modLevel ≥ 3` | Reads and assigns cosmetic grades, including temporary grades; assignment replaces both levels |
| `/level <player> [vip|mod] [level]` | `modLevel ≥ 3` | Reads or permanently changes cumulative access levels |
| `/lang [fr|en|de|es]` | none | Reads or changes the persistent language |
| `/help [general|games|profile|staff]` | none; staff section is restricted | Summarizes available commands by category |
| `/friend add|accept|deny|cancel|remove <player>` | none | Manages persistent friendships and cancels sent requests |
| `/friend list|requests|join <player>` | none | Lists social state or joins a friend's current game, as spectator after start |
| `/party invite|accept|deny <player>` | none | Creates or joins a network party |
| `/party list|leave|kick|promote|disband` | none | Reads or administers party membership |
| `/party follow on|off` | none | Controls whether this member follows the leader |
| `/party warp [player]` (`/party tp`) | party leader | Moves all follow-enabled members, or only the selected member, to the leader's server |
| `/party chat <message>` (`/pc`) | none | Sends a party-only message |
| `/mute`, `/unmute`, `/kick`, `/warn`, `/history` | moderation permissions | Performs and audits moderation actions |
| `/ban`, `/tempban`, `/unban` | ban permission + TOTP session | Applies network-wide bans at the proxy |
| `/report <player> <category> [#message] [details]` | none | Creates a report and optionally captures available chat evidence |
| `/reports list|claim|resolve ...` | report management + TOTP session | Processes the staff report queue |
| `/msg`, `/reply`, `/ignore`, `/globalchat` | none | Cross-server communication and persistent ignores |
| `/2fa issue|enroll|confirm|verify|status ...` | staff/enrollment permissions | Enrolls TOTP, reports enrollment/session state, and validates the staff session until network disconnect |
| `/staff`, `/staffchat <message>` | staff + TOTP session | Invisible controlled spectator mode and cross-server staff chat |
| `/profile [player]` | none | Opens Profile without arguments or displays a visible player profile |
| `/settings profile|messages|global|entities|hints|effects <value>` | none | Updates persistent preferences, including immersive lobby effects |
| `/missions [reroll|claim] ...` | none | Displays and manages personal daily/weekly missions |
| `/notifications [read <id>]` | none | Reads the complete notification center |
| `/guild info|create|invite|accept|leave|kick|promote|demote|transfer ...` | role-dependent | Manages a persistent 50-member guild |
| `/coreadmin reload` | Core administration | Reloads supported Core configuration (`/tropiadmin` remains an alias) |

## Lobby commands

| Command | Purpose |
|---|---|
| `/play` | Opens the selector: left Quick Play, right Ranked, middle public instances (`/servers` remains an alias) |
| `/rejoin` | Rejoins the remembered active SheepWars instance as a spectator; no arguments are accepted |
| `/replay` | Joins or waits for the suggested replay instance (`/playnext` remains an alias) |
| `/replayconfirm` | Confirms a fresh batch of five automatic replays |
| `/quickplay` | Joins the SheepWars Quick Play queue and replaces any active queue |
| `/competitive <4v4|8v8>` | Joins a shared-rating Ranked format and replaces any active queue |
| `/lang` | Opens or updates language selection |
| `/fly` | Toggles authorized lobby flight |
| `/vip` | Opens the Shop home and its Grades tab |

The in-game `/help` catalog uses one line per primary command and shows its complete syntax. Only actual aliases remain grouped in parentheses on the same line. Commands are split between `general`, `games`, `social`, `profile`, and the permission-protected `staff` category.

## SheepWars controls

Team, class, kit, map, host settings, start/cancel, and return-to-lobby actions use hotbar items and inventory menus. Waiting players also receive the shared player-center item in slot 7; it is removed before combat. Late arrivals and reconnecting players enter spectator mode when the game is already in progress.

The lobby hotbar keeps stable positions: Games in slot 0, Social in slot 2, the player's Profile head in slot 4, and Shop in slot 8. Custom-game creation and Settings live in the game selector and Profile respectively.

## Administration principles

Command handlers validate arguments and permissions at the boundary. Player-facing text comes from the four language files. Operations involving SQL, Redis, Docker, or disk must not block the Paper or Velocity event thread.

`tropicube.chat.color` allows safe MiniMessage colors and decorations in global chat. Interactive tags such as click and hover actions remain literal text.

`vipLevel` ranges from 0 to 3 and `modLevel` from 0 to 4. Players cannot change themselves and may only assign a moderator level strictly below their own; level 4 is console-only. Arbitrary individual permissions no longer exist. Sensitive staff actions still require TOTP.

After `/` is entered, clients receive every registered Tropicube command and alias from Velocity, Core, Lobby, and the current game, while permission checks continue to hide inaccessible staff commands. External commands and namespaces stay hidden; `/?`, Bukkit/Minecraft namespaces, and vanilla commands are rejected network-wide. Accepting a party invitation while already grouped atomically leaves the old party, promotes a successor when needed, and joins the new one.
