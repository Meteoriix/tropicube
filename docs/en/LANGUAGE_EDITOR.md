# Language editor

The Tropicube language editor is a local web application for Core, Velocity, and future modules' MiniMessage resources. It discovers `src/main/resources/languages` directories, treats French as the source, and writes all four supported languages together with their Docker mirrors.

## Starting the editor

Java 25, Node.js 24, and npm are required. Run `.\language-editor.ps1` on Windows or `./language-editor.sh` on Linux and macOS. The script builds the application, starts it as an independent process, opens the browser, and then releases the terminal. Closing the terminal therefore does not stop the editor.

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

French is first translated to English for review. Once approved, German and Spanish are generated directly from French. MiniMessage tags, placeholders, commands, glossary terms, and decorative Unicode symbols such as `▶`, `⚠`, or `🌴` are kept out of translatable text and preserved in their original positions. The editor remains available for drafting when the service is offline.

Validation recognizes every tag supplied by `StandardTags.defaults()` in the project's Adventure version, plus the internal `<tc>` and `<sw>` tags. Write `<br>` without spaces to insert a line break (`<newline>` is its long alias). Multi-line lore uses one key with lines separated by `<br>`; two consecutive tags, `<br><br>`, preserve a blank line.

Serialization keeps each YAML string on one physical line regardless of length so that it remains compatible with the deployment scripts' configuration merge.

## Editing and safety

- the structured view can search by key or within all four languages' text, then create, rename, and delete keys;
- raw French YAML mode supports bulk changes;
- previews cover chat, titles, actionbar, inventories, lore, scoreboard, and tablist;
- validation checks YAML, key parity, placeholders, and allowed tags;
- writes are rejected when a file changed after it was opened;
- four sources and their Docker mirrors are replaced together, with rollback on failure.

The versioned `tools/language-editor/catalog.yml` stores the glossary, context overrides, and placeholder samples. Machine translations remain proposals: review at least English and verify width-sensitive interfaces in game.

## Validation

```bash
npm --prefix tools/language-editor/frontend ci
npm --prefix tools/language-editor/frontend test
npm --prefix tools/language-editor/frontend run build
./mvnw -f tools/language-editor/backend/pom.xml verify
```
