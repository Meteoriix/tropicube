# Language editor

The Tropicube language editor is a local web application for Core, Lobby, SheepWars, and Velocity MiniMessage resources and interfaces. It discovers languages plus `scoreboards.yml`, `tablists.yml`, and `menus.yml` manifests, treats French as the source, and maintains their Docker mirrors.

## Starting the editor

Java 25, Node.js 24, npm, and the Docker client are required. Run `.\language-editor.ps1` on Windows or `./language-editor.sh` on Linux and macOS. The script builds the application, starts it as an independent process, opens the browser, and then releases the terminal. Closing the terminal therefore does not stop the editor.

The same lifecycle commands are available on both platforms:

```text
.\language-editor.ps1 start       # detached start (the default action)
.\language-editor.ps1 status      # status and PID
.\language-editor.ps1 stop        # stop the detached process
.\language-editor.ps1 foreground  # attached execution for diagnostics
```

Replace the script name with `./language-editor.sh` on Linux or macOS. Use `-NoBrowser` on Windows or `--no-browser` on Linux and macOS to prevent the browser from opening automatically. Runtime PID and log files are kept locally under `tools/language-editor/.runtime/` and ignored by Git.

The application only listens on `http://127.0.0.1:8765`. Override the port with `TROPICUBE_LANGUAGE_EDITOR_PORT`.

## Translation

The editor accepts any LibreTranslate-compatible endpoint. Start the optional local service with:

```bash
docker compose --env-file .env.example --profile language-editor up -d libretranslate
```

`LIBRETRANSLATE_URL` defaults to `http://127.0.0.1:5000`. `LIBRETRANSLATE_API_KEY` selects a protected external instance and must never be committed.

In structured text, scoreboard, and tablist editing, every French change automatically schedules English, German, and Spanish regeneration after 800 ms without typing. All three translations are produced directly from French, remain manually editable, and a stale response cannot overwrite newer input. MiniMessage tags, placeholders, commands, glossary terms, and decorative Unicode symbols such as `▶`, `⚠`, or `🌴` are kept out of translatable text and preserved in their original positions. The editor remains available for drafting while the service is offline, with that state shown below the French field.

Validation recognizes every tag supplied by `StandardTags.defaults()` in the project's Adventure version, plus the internal `<tc>` and `<sw>` tags. In structured mode, Enter inserts a line break directly and the editor stores it as the MiniMessage `<br>` tag (`<newline>` remains accepted as its long alias), including blank lines. In raw YAML mode, write `<br>` without spaces. Multi-line lore uses one key with lines separated by `<br>`. When an item is rendered, Core, Lobby, and SheepWars convert both these tags and line breaks already stored in YAML into distinct Minecraft lore rows; double breaks remain blank rows and are never sent to the client as control glyphs.

Serialization keeps each YAML string on one physical line regardless of length so that it remains compatible with the deployment scripts' configuration merge.

All resources use canonical named placeholders such as `{player}`, `{balance}`, or `{countdown}`. Locations receiving the same information reuse the same name; legacy names derived from translation keys have been consolidated, while distinct data uses explicit names such as `{instance_id}`, `{notification_id}`, or `{request_id}`. The global `{instance_name}` placeholder returns the visible name of the current Paper instance (`SERVER_NAME`) or, for a player-targeted Velocity message, the name of the server the player is connected to. `{player_grade}` returns the recipient player's MiniMessage-formatted grade prefix without their username; Core reads it from its local cache and Velocity from the Redis cache populated on login and grade changes. The internal adapter skips these global placeholders when mapping remaining legacy Java arguments in declaration order, without reintroducing positional placeholders in language files. Plain values are escaped before MiniMessage insertion; only explicitly rich components preserve styling.

## Editing and safety

- the structured view can search by key or within all four languages' text, then create, rename, and delete keys;
- raw French YAML mode supports bulk changes;
- previews cover chat, titles, actionbar, inventories, lore, scoreboard, and tablist;
- validation checks YAML, key parity, placeholders, and allowed tags;
- writes are rejected when a file changed after it was opened;
- four sources and their Docker mirrors are replaced together, with rollback on failure;
- after saving, validated files are copied to Velocity or every active Paper instance, then the internal `languageeditorreload` command reloads the affected languages and interfaces; each container acknowledges completion only after replacing its catalog and refreshing active views;
- the `Apply` button stays disabled during the operation to prevent concurrent writes; conflicts and errors preserve the draft and appear in the status bar;
- the status bar distinguishes saved files from a complete, partial, or failed live reload. A live failure does not roll back sources that were already saved.
- the Scoreboards tab groups variants, edits the title and every line in all four languages, previews their MiniMessage rendering with placeholder sample values, and can add, remove, or reorder all fifteen Minecraft lines;
- the Tablists tab groups Lobby and SheepWars by state variant, edits headers and footers in all four languages, and previews the complete layout around sample players;
- the Menus tab renders the inventory grid, required buttons, dynamic regions, and localized item properties;
- the Placeholders tab automatically inventories every canonical named Core and Velocity placeholder, precisely describes the business information supplied by each one, supports search, and lists every module and translation key that uses it;
- while editing structured text, the compact bottom-right picker searches the same placeholders, displays their descriptions, and inserts the selected token at the cursor;
- manifest drafts support undo/redo, drag and drop, and a summary before applying changes.

Live synchronization exclusively uses the local Docker client, active Tropicube containers, and their internal RCON service. It does not publish an additional port or read a password. `TROPICUBE_DOCKER_COMMAND` overrides the default executable. This version must be delivered once to install the engine. Later applications need no image rebuild: Core publishes a complete hashed generation to Redis and new instances restore it before initializing languages and interfaces.

Item previews look for the local Minecraft 26.3 client. `TROPICUBE_MINECRAFT_CLIENT_JAR` can point to its JAR explicitly; loaded textures are cached only in editor memory.

The versioned `tools/language-editor/catalog.yml` stores the glossary, context overrides, and placeholder samples. Machine translations remain proposals: review at least English and verify width-sensitive interfaces in game.

## Validation

```bash
npm --prefix tools/language-editor/frontend ci
npm --prefix tools/language-editor/frontend test
npm --prefix tools/language-editor/frontend run build
./mvnw -f tools/language-editor/backend/pom.xml verify
```
