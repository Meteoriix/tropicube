# Git and CI

## Branches and history

Keep `main` buildable. Use short-lived branches for isolated work when collaboration requires them. Do not rewrite published history, force-push, create a tag, or push to a remote without explicit authorization.

Preserve unrelated user changes in a dirty worktree. Never use destructive commands such as `git reset --hard` or `git checkout --` unless the user explicitly requests them.

## Commits

Use one coherent commit per important change with the format:

```text
type(scope): imperative French summary
```

Examples:

```text
feat(sheepwars): ajoute le mode spectateur
fix(velocity): conserve le grade du nick
docs(site): ajoute la navigation anglaise
```

The body explains motivation, design choices, impact, migrations, tests, documentation, remaining manual validation, and risks. Never commit a red build.

## Before committing

```powershell
git diff --check
git diff
git status --short
git lfs status
```

Check that no secret, `.env`, password, token, SQL export, forwarding secret, runtime player data, generated cache, or local third-party JAR is included.

## Continuous integration

CI must use Java 25 and Maven 3.9.11-compatible tooling. The core verification is:

```powershell
.\mvnw.cmd clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
```

Script or Compose changes also require the relevant Windows and Linux validation paths. World changes require Git LFS checks.

## Dependency updates

Review dependency updates across the complete reactor. Do not change Paper or Velocity mappings, repositories, or Minecraft numbering based only on outdated examples. A Minecraft migration must be explicit, verified against official sources, and validated in every module.

Dependabot or other automated updates should remain narrowly scoped and must pass dependency convergence, compilation, tests, shading, and runtime compatibility checks.

## Releases

Apply semantic versioning according to user-visible compatibility. Update `docs/CHANGELOG.md` and `docs/en/CHANGELOG.md`, rebuild both documentation languages, and ensure artifacts are reproducible before creating any release. Tags and remote publication require explicit authorization.

## Pull request evidence

A change summary should list:

- behavior and architecture impact;
- tests and validation commands actually executed;
- configuration, language, Redis, SQL, Docker, and documentation changes;
- manual in-game scenarios still required;
- known risks or assumptions.

## Reliability checks

Linux CI runs Python operations tests and disposable real MySQL/Redis integration tests; the dedicated Windows job uses the native Maven wrapper, Python tests and PowerShell parser. Dependabot also monitors Docker, Compose and language-editor Maven/npm dependencies. No automatic major-version merge or Minecraft/SQL branch migration is enabled.

The manually dispatched image-lot workflow runs only from main on a private Linux runner labeled `tropicube-staging`, where a verified image lot must already exist. The isolated smoke test uses a separate owner, network, volume set and loopback backend port range. Never grant that runner to untrusted pull requests.
