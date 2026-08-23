# Changelog

This document records functional, technical, and operational changes. Entries are grouped under **Unreleased** until a version is published.

## Unreleased

### Added

- Registered Tropicube commands and aliases are now all suggested after `/`, subject to permissions; Lobby and SheepWars share a localized player-center hotbar item.

- Velocity operations foundation: versioned SQL migrations, typed instance modes, versioned Redis events, draining maintenance, bilingual MOTD, targeted announcements, network diagnostics, and adaptive connection protection.
- Network communication and moderation: global chat, offline private messages, ignores, proxy-enforced bans, reports, and 90-day chat evidence.
- TOTP-protected staff tools: fifteen-minute sessions, recovery codes, cross-server staff chat, and controlled invisible spectator mode.
- Visibility-aware network profiles, network levels, a versioned mission catalog, personal 5-daily/3-weekly rotations, rerolls, and atomic rewards.
- Lobby contextual help, persistent entity visibility, and fill-oriented smart match selection.
- Complete persistent guilds with 50-member capacity, bounded roles, invites, audit, capped contributions, weekly challenges, aggregate competitive ranking, and inactive-owner succession.

- Lobby Settings menu with language selection and confirmed five-game automatic replay batches.
- Complete network social system: durable friends, Redis parties, party chat, individual leader following, `/party warp`, spectator-capable `/friend join`, and the lobby Social hotbar menu.
- Complete private custom-game access control: proxy `/whitelist`, SheepWars host item and anvil input, removal menu, Redis persistence, and per-player lobby filtering.
- Bilingual French/English Markdown documentation and static site, with a page-preserving language switch.
- Blue `PLAYING` server presentation for active SheepWars matches.
- Late-join SheepWars spectator mode: no team assignment and no effect on victory conditions.

### Changed

- Party members are removed after one offline minute; leadership then moves to an online member and fully offline parties are deleted immediately. The Social menu now displays every friend's profile head.
- The game selector uses HeadDatabase head 52706, the language setting is now labelled “Langues / Language”, and new profiles inherit the Minecraft client language with an English fallback.
- The Settings menu Language button now reuses HeadDatabase head 71786, the hotbar Settings button uses head 89489, and slash completion publishes only Tropicube commands.
- Non-Tropicube command discovery and Bukkit/vanilla commands are blocked network-wide; protected container interfaces are disabled and `/money` is personal-only.
- Party-to-party acceptance is atomic, SheepWars favors party grouping without breaking team balance, and host capacity changes now reach Velocity immediately.
- In a custom SheepWars game without map voting, the host-selected map now appears immediately in every player's waiting sidebar.
- Lobby and SheepWars sidebars now use airy tropical separators and show more useful context: profile, balance, network activity, and visible games in the lobby; map, capacity, required minimum, team, class, and personal statistics in game. Economy data remains loaded away from the main thread.
- Lobby and SheepWars tab lists and sidebars now use a clearer tropical identity, localized titles, and structured game states; connection and game join/leave events are narrative and unprefixed.
- Messages and locales now share one identity: bracket-free `TROPICUBE >` and `SHEEPWARS >` branding, modernized grades, prefixes limited to commands and notifications, and MiniMessage technical logs rendered to ANSI with a plain-text fallback.
- SheepWars uses a twenty-second default delivery interval and direct independent weighted draws, preserving the configured probability on short and long matches.
- SheepWars scoreboard teams now use the profile name actually visible to the client, including active nicknames.
- Team entries are installed before glow metadata, and tablist names are colored explicitly for participants and spectators.

### Fixed

- Command help and usage parameters in angle brackets now render literally in all four languages, including 2FA and SheepWars; the remaining `§` color codes were replaced with MiniMessage, and tests now validate palettes, tags, placeholders, Velocity parity, and Docker copies.

- Staff 2FA now reports enrollment/session state, completes subcommands, provides copyable enrollment data, uses consistent four-language colors, and consumes codes transactionally to prevent concurrent replay.

- Social-menu heads now start dynamic resolution with the UUID alone and embed the server-resolved texture; incomplete static profiles no longer produce an incorrect default skin, and friends do not need to reconnect recently.
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
- Nickname profile changes no longer leave SheepWars tablist entries under the historical Bukkit name, which could produce white glowing and incorrect team colors.
- Lobby recognition now matches the published `GAME_PLAYING` status instead of checking only the unused `PLAYING` spelling.
- `/nick` grade cache, concurrent requests, invalid arguments, `/nick off`, and multi-backend cleanup remain protected by the previous fixes.
- SheepWars distribution, lobby instance visibility, orchestration stop handling, anonymous-volume cleanup, and documentation cache invalidation include their previously released regressions fixes.

### Documentation

- Architecture, commands, configuration, deployment, game design, development, Git/CI, and changelog are available in both languages.
