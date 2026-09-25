# Message style guide

This guide keeps Core, Lobby, Velocity, and game messages visually consistent. Every text uses MiniMessage, including technical messages before console rendering.

## Identity

- network notifications start with `<gold><bold>TROPICUBE</bold></gold> <dark_gray>></dark_gray>` through the `<tc>` tag;
- SheepWars notifications start with `<aqua><bold>SHEEPWARS</bold></aqua> <dark_gray>></dark_gray>` through the `<sw>` tag;
- decorative prefixes never use brackets; functional command syntax such as `[reason]` remains unchanged;
- gray is neutral, white or gold highlights values, green indicates success, yellow warns, and red reports errors.

Bold is limited to branding, short headings, grades, and important actions. Icons remain occasional and never replace textual information.
The `<tc>` and `<sw>` tags explicitly reset bold after the brand: message bodies remain regular unless a portion has its own `<bold>` tag.

## Prefix usage

The prefix appears on command results and errors, system notifications, and confirmations or errors triggered by a menu. A multi-line response prefixes its heading only and keeps detail lines compact.

Narrative events directly caused by a player remain unprefixed: connecting, leaving, joining a game, or leaving a game. They use a restrained contextual symbol and keep the player name as the focal point.

Player chat, inventory titles and contents, scoreboards, tab lists, boss bars, titles, subtitles, and action bars do not repeat the brand. They still follow the same palette.

Scoreboards group contextual information with whitespace and short dotted `•` separators. The lobby prioritizes visible identity, balance, network activity, accessible games, and destination. A mini-game prioritizes its state, objectives, and immediately useful personal information without exceeding the readable sidebar height.

Grades use a bold colored label without brackets. Chat follows `VIP Player > message`, while the tab list ends after the player name.

## Inventories and menus

Every Core, Lobby, and game inventory reuses the `NetworkMenuStyle` frame: neutral gray background without a blue glass line, main actions in the center, back on the left, and close on the right of the last row. The same action keeps its icon, color, wording, and logical position across screens.

Manifests explicitly declare the `network`, `neutral`, or `none` frame. Both `network` and `neutral` keep the sand-gray surface without a header; `none` is reserved for native inventories such as anvils. Irreversible operations and balance debits open a confirmation that states the effect, cost when applicable, and offers confirm or cancel.

Player-facing labels never expose enum names, template identifiers, or underscored values. Each domain value has a natural translation in all four languages. Settings show their current state and explain their actual effect; missions show objective, progress, rewards, status, and available actions. Buttons name the exact click and its result. Profile entries use the relevant player's head whenever available.

## Technical logs

Logs are authored in MiniMessage and rendered as `TROPICUBE > CONTEXT > message` or `SHEEPWARS > CONTEXT > message`. Adventure emits ANSI colors when supported and readable plain text otherwise. Logger levels, structured parameters, and stack traces must be preserved.

Dynamic values must not expose secrets or sensitive data. New contexts stay short and stable, such as `REDIS`, `DOCKER`, `LANG`, `GAME`, or `NICK`.

## Locales and validation

Every key exists in `fr`, `en`, `de`, and `es` with identical positional placeholders. Bundled resources and Docker copies remain identical. Tests validate YAML, key and placeholder parity, MiniMessage syntax, and the absence of legacy decorative prefixes.

Angle brackets that describe command arguments are not tags: inside a double-quoted YAML string, write `\\<player>` to render `<player>`. Legacy `§` color codes are forbidden; all colors and decorations must use MiniMessage.

Multi-line text, especially lore, stays in one key and uses `<br>` without spaces between lines. `<newline>` is accepted as the explicit alias, and `<br><br>` represents a blank line. Before item assignment, the shared `ComponentLines` utility expands these breaks into distinct lore components because the Minecraft client does not render a newline control character correctly inside one lore entry. Validation follows the tags exposed by `StandardTags.defaults()` in the project's Adventure version, plus `<tc>` and `<sw>`.
