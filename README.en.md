# Tropicube

[Version française](README.md)

Tropicube is a modular Minecraft 26.2 network built around Velocity, dynamically created Paper servers, Redis, MySQL, and Docker Compose. The reference development environment is Java 25 with Maven 3.9.11.

## Modules

| Module | Responsibility |
|---|---|
| `tropicube-docker-api` | Shared server models plus Docker and Redis access |
| `tropicube-velocity` | Proxy routing, queues, dynamic instance lifecycle, and `/nick` |
| `tropicube-core` | Player data, economy, grades, permissions, languages, and moderation |
| `tropicube-lobby` | Lobby menus, server selection, language selection, and custom games |
| `tropicube-sheepwars` | Complete SheepWars minigame |
| `tropicube-fallenkingdoms` | Reserved module for the future Fallen Kingdoms implementation |

## Runtime architecture

Players enter through Velocity. The proxy creates and registers Paper containers from templates, stores shared state in Redis, and routes players without exposing backend ports publicly. Paper plugins use MySQL for durable player data and Redis for short-lived coordination.

```text
Player -> Velocity -> Lobby / SheepWars Paper instance
                    |-> Redis (state, events, queues)
                    |-> Docker socket proxy (instance lifecycle)
Paper plugins ------|-> MySQL (profiles and statistics)
```

## Requirements

- Java 25;
- Maven 3.9.11 through the included wrapper;
- Docker Engine with Compose;
- Git LFS for Minecraft region files;
- Node.js for the static documentation site.

Copy `.env.example` to `.env`, fill in local secrets without committing them, then validate the stack before deployment.

## Common commands

```powershell
.\mvnw.cmd clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
.\deploy.ps1 -OnlyImages -ValidateOnly
```

```bash
bash ./mvnw clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
./deploy.sh --only-images --validate-only
```

## Documentation

- [Architecture](docs/en/ARCHITECTURE.md)
- [Commands](docs/en/COMMANDS.md)
- [Configuration](docs/en/CONFIGURATION.md)
- [Deployment](docs/en/DEPLOYMENT.md)
- [SheepWars](docs/en/SHEEPWARS.md)
- [Fallen Kingdoms game design](docs/en/FALLEN_KINGDOMS_GAME_DESIGN.md)
- [Fallen Kingdoms technical specification](docs/en/FALLEN_KINGDOMS_TECHNICAL_SPEC.md)
- [Development](docs/en/DEVELOPMENT.md)
- [Git and CI](docs/en/GIT_CI.md)
- [Changelog](docs/en/CHANGELOG.md)

The French documentation remains canonical for existing operations, while the English pages mirror the same contracts and are generated under `docs-site/en/`.
