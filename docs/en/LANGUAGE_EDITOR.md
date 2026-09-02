# Language editor

The Tropicube language editor is a local web application for Core, Velocity, and future modules' MiniMessage resources. It discovers `src/main/resources/languages` directories, treats French as the source, and writes all four supported languages together with their Docker mirrors.

## Starting the editor

Java 25, Node.js 24, and npm are required. Run `.\language-editor.ps1` on Windows or `./language-editor.sh` on Linux and macOS.

The application only listens on `http://127.0.0.1:8765`. Override the port with `TROPICUBE_LANGUAGE_EDITOR_PORT`.

## Translation

The editor accepts any LibreTranslate-compatible endpoint. Start the optional local service with:

```bash
docker compose --env-file .env.example --profile language-editor up -d libretranslate
```

`LIBRETRANSLATE_URL` defaults to `http://127.0.0.1:5000`. `LIBRETRANSLATE_API_KEY` selects a protected external instance and must never be committed.

French is first translated to English for review. Once approved, German and Spanish are generated directly from French. MiniMessage tags, placeholders, commands, and glossary terms are not sent as translatable text. The editor remains available for drafting when the service is offline.

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
