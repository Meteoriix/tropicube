# Development

## Reference environment

- Java 25;
- Maven 3.9.11 through the repository wrapper;
- Minecraft 26.2 with the Paper and Velocity versions declared in the parent POM;
- UTF-8 and four-space Java indentation;
- Windows and Linux compatibility.

Do not replace Minecraft 26.2 with a 1.21.x example version and do not introduce a `paper-mojangapi` dependency.

## Module boundaries

Shared network contracts belong in Docker API or Core only when they are genuinely used across modules. Game rules, teams, kits, abilities, match state, and game data remain inside their game module. A new game must never depend on SheepWars business classes.

## Code design

- inject dependencies explicitly through constructors;
- keep mutable global state out of static fields;
- model game lifecycles with explicit, idempotent state transitions;
- keep Paper and Velocity adapters thin and move pure rules into testable Java classes;
- validate configuration at startup with actionable error messages;
- expose immutable collections where callers must not mutate internal state;
- use Adventure Components and MiniMessage for player text and technical logs, following the [Tropicube message style guide](MESSAGING_STYLE.md);
- keep code comments and JavaDoc in English.

## Threading

Never block the Paper or Velocity event thread with SQL, Redis, Docker, network, or disk work. Perform I/O asynchronously, then return to the server scheduler before changing worlds, entities, inventories, player profiles, or other non-thread-safe Bukkit state.

Track every scheduled task and cancel it when a match ends or a plugin stops. Close executors, connection pools, subscriptions, and clients during shutdown.

## Tests

Use JUnit for pure rules, parsers, configuration validation, resource parity, state transitions, and regressions. Avoid mocking the entire Paper or Velocity API. Extract business logic when a server runtime is unnecessary.

```powershell
.\mvnw.cmd -o -pl tropicube-sheepwars -am test --batch-mode --no-transfer-progress
.\mvnw.cmd clean verify
```

JaCoCo reports are generated under each module's `target/site/jacoco/` directory. Coverage helps locate missing behavior, but assertions must remain meaningful.

## Languages

Player-facing messages are available in French, English, German, and Spanish. Every new key must exist in all four files with the same placeholder structure. Embedded resources and deployment copies must stay synchronized. Cached interfaces must refresh after `/lang` without requiring a reconnect.

The `tropicube-core/src/main/resources/languages/*.yml` and
`tropicube-velocity/src/main/resources/languages/*.yml` files are the translation sources of truth.
Each module's Maven `process-resources` phase copies them without filtering to the matching
directory under `dockerfiles/configs`. Do not customize the Docker copies directly: the next build
or deployment replaces them.

## Documentation site

French Markdown lives in `docs/`; English equivalents live in `docs/en/`. `README.md` and `README.en.md` provide the two home pages. Build and validate both languages with:

```powershell
node docs-site/build.mjs
node docs-site/validate.mjs
```

Generated HTML under `docs-site/` and `docs-site/en/` is committed with its Markdown sources.

## Change workflow

1. Inspect `git status`, the affected POM, resources, documentation, and consumers.
2. Identify lifecycle, threading, Redis, SQL, Docker, and compatibility constraints.
3. Implement the smallest coherent change with tests.
4. Run targeted tests after each coherent step.
5. Run the full reactor for cross-module changes.
6. Rebuild the documentation site.
7. Inspect the full diff, status, LFS state when relevant, and secrets.
8. Commit with a conventional French commit subject as defined by project policy.
