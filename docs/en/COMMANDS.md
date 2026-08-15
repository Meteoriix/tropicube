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
| `/tropi ...` | `/tropicube`, `/cm` | `tropicube.admin` | Administers dynamic instances |

`nick.allowed-grades` controls who may enable a nick. Disabling is always allowed, cancels an outstanding skin request, and can be retried while a backend has not restored the profile. The nick and original-profile Redis entries remain available until restoration succeeds, so a lost event cannot lock the player into the visual identity. The Redis identity keeps the fake `PREMIUM` display grade for 24 hours after disconnecting and restores it in the tablist after reconnecting, without changing real permissions. Concurrent generations for the same player are rejected.

Velocity validates `/whitelist`: the sender must own an active private custom game. Names resolve among players currently or previously seen by the proxy, and UUIDs are accepted directly. The SheepWars host hotbar item uses the same proxy-owned mutation path.

## Core commands

| Command | Permission | Purpose |
|---|---|---|
| `/money [player]` | own balance: none; other player: administrative | Displays TropiCoin balance |
| `/eco <set|add|remove|top> ...` | economy administration | Changes balances or displays the ranking |
| `/rank <set|info|list> ...` | grade administration | Reads and assigns grades, including temporary grades |
| `/tropiperm ...` | permission administration | Manages individual and grade permissions |
| `/lang [fr|en|de|es]` | none | Reads or changes the persistent language |
| `/mute`, `/unmute`, `/kick`, `/warn`, `/history` | moderation permissions | Performs and audits moderation actions |
| `/tropiadmin reload` | Core administration | Reloads supported Core configuration |

## Lobby commands

| Command | Purpose |
|---|---|
| `/servers` | Opens the server selector |
| `/sw join` | Rejoins the remembered active SheepWars instance as a spectator |
| `/playnext` | Joins or waits for the suggested replay instance |
| `/lang` | Opens or updates language selection |
| `/fly` | Toggles authorized lobby flight |
| `/vip` | Opens the VIP presentation interface |

## SheepWars controls

SheepWars registers no standalone Paper command. Team, class, kit, map, host settings, start/cancel, and return-to-lobby actions use hotbar items and inventory menus. Late arrivals and reconnecting players enter spectator mode when the game is already in progress.

## Administration principles

Command handlers validate arguments and permissions at the boundary. Player-facing text comes from the four language files. Operations involving SQL, Redis, Docker, or disk must not block the Paper or Velocity event thread.
