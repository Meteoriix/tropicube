# Changelog

This document records functional, technical, and operational changes. Entries are grouped under **Unreleased** until a version is published.

## Unreleased

### 2026-09-03

#### Added

- The global `{instance_name}` placeholder exposes the visible current Paper instance name, or the server a player is connected to for Velocity messages, in every localized text.
- The editor now provides a Tablists view to edit and preview Lobby and SheepWars-state headers and footers in all four languages; each scoreboard line can now be edited alongside its layout.
- The editor now provides a Placeholders tab that groups names used throughout language resources and lists their consuming keys and modules.
- Every placeholder now displays a description of its dynamic value; a compact picker can search and insert them at the cursor while editing text.

#### Changed

- The 291 placeholders produced by the positional migration have been consolidated into 139 canonical business names; the editor provides a precise description and readable example for each one.
- French input now regenerates EN, DE, and ES automatically after a short debounce in text, scoreboard, and tablist editors; proposal and intermediate approval buttons were removed.
- The editor now communicates automatic translation state more clearly, improves focus feedback, and adapts to narrower windows.
- The Lobby scoreboard now displays `{instance_name}` instead of the instance's technical identifier.
- Lobby now resolves its scoreboard `{instance_number}` placeholder instead of displaying it literally; automated previews also verify multiple-placeholder replacement.

### 2026-09-02

#### Added

- The editor now groups Core, Lobby, and SheepWars scoreboards and menus with full previews, add/remove/reorder controls, history, and local Minecraft 26.2 textures.
- A shared named-placeholder API protects plain MiniMessage values while temporarily accepting existing positional placeholders.
- Live configurations are stored as hashed Redis generations so new instances inherit the latest approved version.
- The language editor now synchronizes every validated save with active Core or Velocity containers and reloads in-game text without rebuilding an image.
- Added a local web language editor with contextual MiniMessage previews, reviewed LibreTranslate workflow, glossary, and atomic four-language writes.
- The language editor can now search either by key or by text across all four translations.
- The Windows and Linux editor launchers now start an independent process and provide `start`, `status`, `stop`, and `foreground` actions without occupying the terminal.

#### Changed

- The remaining positional placeholders in Core and Velocity resources now use stable names shared by all four languages and their Docker copies.
- Scoreboard titles can now be edited in all four languages, and their preview applies the actual MiniMessage formatting and sample placeholders.
- The language editor's structured view now accepts natural line breaks with Enter and preserves them as MiniMessage `<br>` tags.
- Lore previously split across numbered keys now uses one key with `<br>` line breaks; the language selector configuration follows the same convention.

#### Fixed

- Lore line breaks are now converted into actual item rows in Core, Lobby, and SheepWars, including blank rows, instead of displaying an unknown control glyph.
- The editor and resource tests now recognize every standard MiniMessage tag from the configured Adventure version, including `<br>` and `<newline>`.
- The editor no longer wraps long YAML strings across physical lines, keeping them compatible with deployment-time configuration merging.
- Decorative Unicode symbols, including `▶`, are now preserved unchanged in the language editor's proposed English, German, and Spanish translations.
- The editor server now completes static-file responses correctly, so the interface loads without hanging.
- Approving an English translation now retains both the German and Spanish proposals instead of overwriting German.
- The profile hotbar name now correctly separates its icon from its label in all four languages.

### 2026-08-28

#### Fixed

- The German `/level` translation now uses natural wording for the access category and moderator hierarchy limit.

### 2026-08-27

#### Changed

- Replaced grade and individual-permission authorization with cumulative `vipLevel` (0–3) and `modLevel` (0–4), `/level`, SQL auditing, and a revisioned Redis cache shared with Velocity.
- Grades are now cosmetic and atomically apply their configured default levels; administrator UUIDs, Docker operators, and `/tropiperm` were removed.
- Match and mission XP now contributes automatically to the player's guild within the configured weekly cap.
- Global chat applies `tropicube.chat.color` only to safe MiniMessage colors and decorations; online and offline private messages use the recipient's language.
- Unconsumed classes, methods, permissions, SheepWars configuration, and 85 language keys were removed together with their Docker copies and obsolete tests.

#### Fixed

- MySQL schema preparation is now serialized across backends; V008 uses MySQL 9.7-compatible syntax and resumes an interrupted run without duplicate audit entries.
- `/level` is published in Velocity suggestions again, while stale `/permissions` and `/tropiperm` entries are gone.
- `/maintenance` now reads and validates `maintenance.default-deadline-minutes` when no duration is supplied.

### 2026-08-25

#### Changed

- Maven builds and Windows/Linux deployments now synchronize Core and Velocity translations exactly from their resources to `dockerfiles/configs`, with automatic parity verification.

### 2026-08-24

#### Changed

- Core, Lobby, and SheepWars menus now share one frame; Profile uses the player's head, missions and settings explain their contents, and actions and domain values are localized without technical identifiers.
- Profile now uses the selectors' 54-slot grid and explicit click guidance; Social displays “Social • Friends”, while party requests mirror the Friends incoming/sent columns and allow right-click cancellation.
- Custom-game type selection now says “Left click: select this type and choose a game”.
- The experience bar now displays the network level and progress toward the next level, refreshing after each XP gain.

#### Fixed

- The full “Welcome to Tropicube” title is now reserved for the initial lobby arrival after proxy login; returns from a mini-game keep only the discreet action bar.
- Profile heads in the hotbar and full Profile menu now consistently retain their localized labels instead of reverting to “Player's Head”.

### 2026-08-23

#### Added

- Two-tab Friends/Party Social navigation, visual party-member and invitation management, and targeted `/party warp <player>` transfers.
- Paginated Social submenu for incoming and sent friend requests, with profile heads, left-click acceptance, and right-click denial or cancellation.
- Persistent immersive lobby experience with origin-aware welcome effects, a profile/action sidebar, and reliable live queue state.
- Three game-selector actions: left click for Quick Play, right click for Ranked 4v4/8v8, and `Shift + left click` for the filtered unified public-instance browser.
- Shop home and Grades tab with honest “Available now”/“Coming soon” sections, previous-grade price deduction, and transactional grade purchases.
- TOTP-protected staff tools: network-connection-scoped sessions, recovery codes, cross-server staff chat, and controlled invisible spectator mode.

#### Changed

- Staff 2FA remains active for the whole network connection and Velocity revokes it on the actual disconnect.
- Unreleased changelog entries are now dated and sorted from newest to oldest.
- The Social menu now uses left click to join a friend's server and right click to invite them to the party; incoming friend and party invitations can be accepted directly from chat.
- The lobby hotbar is now `Games 0`, `Social 2`, `Profile 4`, and `Shop 8`; custom-game creation moved into the game selector and visibly explains its VIP+ requirement.
- Tropicube Center is now Profile, uses the player's head, embeds Settings, and shares one visual inventory style across Core and Lobby.
- Each player has at most one matchmaking queue; choosing another format replaces it and the Ranked menu exposes an explicit leave action.
- The `/help` catalog now displays one primary command per line, keeps only its actual aliases on that line, and details arguments and subcommands in all four languages.

#### Fixed

- The Profile hotbar item now retains its localized label instead of showing Minecraft's generated English head name.
- The public-instance browser now opens with `Shift + left click` in the game selector, an interaction available in Adventure mode unlike middle click.
- Velocity administrator UUIDs now receive the missing operations permissions; `/netdiag`, `/maintenance`, and `/announce` work after reconnecting.
- Command help and usage parameters in angle brackets now render literally in all four languages, including 2FA and SheepWars; the remaining `§` color codes were replaced with MiniMessage, and tests now validate palettes, tags, placeholders, Velocity parity, and Docker copies.

### 2026-08-22

#### Added

- Registered Tropicube commands and aliases are now all suggested after `/`, subject to permissions; Lobby and SheepWars share a localized player-center hotbar item.
- Velocity operations foundation: versioned SQL migrations, typed instance modes, versioned Redis events, draining maintenance, bilingual MOTD, targeted announcements, network diagnostics, and adaptive connection protection.
- Network communication and moderation: global chat, offline private messages, ignores, proxy-enforced bans, reports, and 90-day chat evidence.
- Visibility-aware network profiles, network levels, a versioned mission catalog, personal 5-daily/3-weekly rotations, rerolls, and atomic rewards.
- Lobby contextual help, persistent entity visibility, and fill-oriented smart match selection.
- Complete persistent guilds with 50-member capacity, bounded roles, invites, audit, capped contributions, weekly challenges, aggregate competitive ranking, and inactive-owner succession.

#### Fixed

- Staff 2FA now reports enrollment/session state, completes subcommands, provides copyable enrollment data, uses consistent four-language colors, and consumes codes transactionally to prevent concurrent replay.

### 2026-08-21

#### Changed

- Party members are removed after one offline minute; leadership then moves to an online member and fully offline parties are deleted immediately. The Social menu now displays every friend's profile head.

#### Fixed

- Social-menu heads now start dynamic resolution with the UUID alone and embed the server-resolved texture; incomplete static profiles no longer produce an incorrect default skin, and friends do not need to reconnect recently.

### 2026-08-15

#### Added

- Lobby Settings menu with language selection and confirmed five-game automatic replay batches.
- Complete network social system: durable friends, Redis parties, party chat, individual leader following, `/party warp`, spectator-capable `/friend join`, and the lobby Social hotbar menu.

#### Changed

- The game selector uses HeadDatabase head 52706, the language setting is now labelled “Langues / Language”, and new profiles inherit the Minecraft client language with an English fallback.
- The Settings menu Language button now reuses HeadDatabase head 71786, the hotbar Settings button uses head 89489, and slash completion publishes only Tropicube commands.
- Non-Tropicube command discovery and Bukkit/vanilla commands are blocked network-wide; protected container interfaces are disabled and `/money` is personal-only.
- Party-to-party acceptance is atomic, SheepWars favors party grouping without breaking team balance, and host capacity changes now reach Velocity immediately.
- In a custom SheepWars game without map voting, the host-selected map now appears immediately in every player's waiting sidebar.
- Lobby and SheepWars sidebars now use airy tropical separators and show more useful context: profile, balance, network activity, and visible games in the lobby; map, capacity, required minimum, team, class, and personal statistics in game. Economy data remains loaded away from the main thread.
- Lobby and SheepWars tab lists and sidebars now use a clearer tropical identity, localized titles, and structured game states; connection and game join/leave events are narrative and unprefixed.
- Messages and locales now share one identity: bracket-free `TROPICUBE >` and `SHEEPWARS >` branding, modernized grades, prefixes limited to commands and notifications, and MiniMessage technical logs rendered to ANSI with a plain-text fallback.
- SheepWars uses a twenty-second default delivery interval and direct independent weighted draws, preserving the configured probability on short and long matches.

#### Fixed

- The Social menu no longer raises a `LinkageError`: Core is now the sole Paper provider of `tropicube-docker-api` models, which are no longer duplicated in Lobby and SheepWars JARs.
- The Social menu no longer renders `<tc>`, reports construction failures safely, and follows the common hotbar style.
- The `<tc>` and `<sw>` MiniMessage prefixes no longer leak bold styling into message bodies; only branding and explicitly tagged segments remain bold.
- Sheep delivery deadlines are now tracked per player and remain pending while the five-sheep stock is full; a standard ten-minute match exposes twenty-nine useful periodic deadlines plus the starting sheep.
- `/nick off` no longer deletes its recovery state before the backend has restored the profile and can repair a desynchronized visual identity instead of incorrectly reporting that no nick is active.
- Changing teams in the SheepWars waiting room now immediately refreshes the name color in the tablist, including for nicked players.
- SheepWars chat now refreshes the player name after `/nick off`, and the tablist removes the heart before names while retaining team or spectator colors.
- The SheepWars tablist now consistently hides grades and reapplies the visible name alone in the team color, or gray for spectators, after every identity change.
- `/nick off` now restores the real name in the lobby tablist as well; a deferred refresh can no longer reapply the stale nicked profile name.
- After reconnecting with `/nick`, the lobby join announcement now uses the restored nick name and fake grade without exposing the real grade.
- Deferred lobby tablist refreshes now retain the `/nick` name together with its fake grade instead of restoring the real profile name.
- `/lobby` now reports that the player is already there; outside an active `/nick` identity, the lobby tablist and join announcements retain the real grade.
- The fake `/nick` display grade now survives reconnects: disconnecting no longer shortens its TTL to 30 seconds, and the lobby tablist uses that grade without changing real permissions. Legacy Redis payloads remain compatible and default to `PREMIUM`.

### 2026-08-14

#### Added

- Complete private custom-game access control: proxy `/whitelist`, SheepWars host item and anvil input, removal menu, Redis persistence, and per-player lobby filtering.

### 2026-08-12

#### Added

- Bilingual French/English Markdown documentation and static site, with a page-preserving language switch.
- Blue `PLAYING` server presentation for active SheepWars matches.
- Late-join SheepWars spectator mode: no team assignment and no effect on victory conditions.

#### Changed

- SheepWars scoreboard teams now use the profile name actually visible to the client, including active nicknames.
- Team entries are installed before glow metadata, and tablist names are colored explicitly for participants and spectators.

#### Fixed

- Nickname profile changes no longer leave SheepWars tablist entries under the historical Bukkit name, which could produce white glowing and incorrect team colors.
- Lobby recognition now matches the published `GAME_PLAYING` status instead of checking only the unused `PLAYING` spelling.
- `/nick` grade cache, concurrent requests, invalid arguments, `/nick off`, and multi-backend cleanup remain protected by the previous fixes.
- SheepWars distribution, lobby instance visibility, orchestration stop handling, anonymous-volume cleanup, and documentation cache invalidation include their previously released regressions fixes.

#### Documentation

- Architecture, commands, configuration, deployment, game design, development, Git/CI, and changelog are available in both languages.
