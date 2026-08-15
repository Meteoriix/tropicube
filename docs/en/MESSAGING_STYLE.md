# Message style guide

This guide keeps Core, Lobby, Velocity, and game messages visually consistent. Every text uses MiniMessage, including technical messages before console rendering.

## Identity

- network notifications start with `<gold><bold>TROPICUBE</bold></gold> <dark_gray>></dark_gray>` through the `<tc>` tag;
- SheepWars notifications start with `<aqua><bold>SHEEPWARS</bold></aqua> <dark_gray>></dark_gray>` through the `<sw>` tag;
- decorative prefixes never use brackets; functional command syntax such as `[reason]` remains unchanged;
- gray is neutral, white or gold highlights values, green indicates success, yellow warns, and red reports errors.

Bold is limited to branding, short headings, grades, and important actions. Icons remain occasional and never replace textual information.

## Prefix usage

The prefix appears on command results and errors, system notifications, and confirmations or errors triggered by a menu. A multi-line response prefixes its heading only and keeps detail lines compact.

Player chat, inventory titles and contents, scoreboards, tab lists, boss bars, titles, subtitles, and action bars do not repeat the brand. They still follow the same palette.

Grades use a bold colored label without brackets. Chat follows `VIP Player > message`, while the tab list ends after the player name.

## Technical logs

Logs are authored in MiniMessage and rendered as `TROPICUBE > CONTEXT > message` or `SHEEPWARS > CONTEXT > message`. Adventure emits ANSI colors when supported and readable plain text otherwise. Logger levels, structured parameters, and stack traces must be preserved.

Dynamic values must not expose secrets or sensitive data. New contexts stay short and stable, such as `REDIS`, `DOCKER`, `LANG`, `GAME`, or `NICK`.

## Locales and validation

Every key exists in `fr`, `en`, `de`, and `es` with identical positional placeholders. Bundled resources and Docker copies remain identical. Tests validate YAML, key and placeholder parity, MiniMessage syntax, and the absence of legacy decorative prefixes.
