# Développement et contribution

Ce guide décrit le cycle de développement recommandé pour Tropicube, depuis la préparation du poste jusqu'à la validation d'une modification.

## Prérequis

- Git 2.40 ou plus récent ;
- JDK 25, avec `JAVA_HOME` correctement défini ;
- Docker Engine et Docker Compose v2 pour les validations d'infrastructure ;
- Node.js 24 pour reconstruire le site documentaire ;
- un IDE compatible EditorConfig, IntelliJ IDEA étant recommandé pour les modules Java.

Maven n'a pas besoin d'être installé globalement : `mvnw.cmd` et `mvnw` téléchargent la version 3.9.11 contrôlée par le projet.

## Organisation Git

Le dépôt local utilise `main` comme branche principale. Une modification isolée devrait être développée dans une branche courte :

```bash
git switch -c feature/description-courte
```

Avant chaque commit, vérifier `git status` afin d'exclure les secrets, les répertoires `target/` et les JAR copiés vers Docker. Le fichier `.env` ne doit jamais être versionné ; seul `.env.example` sert de modèle.

Les commits doivent rester atomiques et suivre la convention détaillée dans [Git, CI et versionnement](GIT_CI.md), par exemple `fix(lobby): synchronise le scoreboard après un changement de langue`. Une pull request doit préciser les tests automatisés et les scénarios validés en jeu.

## Compiler et tester

Sous Windows :

```powershell
.\mvnw.cmd clean verify
```

Sous Linux ou macOS :

```bash
bash ./mvnw clean verify
```

La commande compile tous les modules, applique les règles Maven Enforcer, exécute JUnit et génère un rapport JaCoCo dans le répertoire `target/site/jacoco/` de chaque module testé. Pour itérer sur un module tout en construisant ses dépendances :

```bash
bash ./mvnw -pl tropicube-sheepwars -am test
```

Les tests existants couvrent notamment les modèles Docker/Redis, la conversion des durées, la validité des ressources YAML et les invariants du catalogue SheepWars. Toute correction de bug devrait ajouter un test de non-régression lorsque le comportement peut être isolé de Paper ou Velocity.

Les interactions nécessitant un serveur réel — inventaires Bukkit, événements réseau, démarrage d'instances et routage Velocity — restent à valider sur une pile Docker de développement. Si leur volume augmente, l'étape suivante recommandée est de créer des tests d'intégration avec des adaptateurs Paper/Velocity plutôt que de simuler toute l'API serveur.

## Contrôles d'infrastructure

Créer au préalable un `.env` local à partir du modèle. Les contrôles sans déploiement complet sont :

```bash
docker compose --env-file .env.example config --quiet
node docs-site/build.mjs
node docs-site/validate.mjs
bash -n deploy.sh
```

Sous PowerShell, la validation complète non destructive du déploiement est disponible avec :

```powershell
.\deploy.ps1 -OnlyImages -ValidateOnly
```

Le script Bash propose l'équivalent :

```bash
./deploy.sh --only-images --validate-only
```

## Intégration continue

Le workflow `.github/workflows/ci.yml` s'exécute sur chaque pull request et chaque push vers `main`. Il contrôle :

- le wrapper Maven et le build `verify` sous Java 25 ;
- tous les tests JUnit et la génération des rapports JaCoCo ;
- la reconstruction déterministe et les liens du site HTML ;
- la résolution de `docker-compose.yml` avec `.env.example` ;
- la syntaxe de `deploy.sh` et de `deploy.ps1`.

Une pull request ne devrait pas être fusionnée tant que ce workflow échoue. Après publication du dépôt sur GitHub, activer une règle de protection de `main` exigeant le contrôle `Build, tests and static validation` et au moins une revue si plusieurs personnes contribuent.

## Mise à jour des dépendances

Dependabot vérifie chaque semaine les dépendances Maven et chaque mois les actions GitHub. Ses pull requests doivent être validées par la CI puis, pour Paper et Velocity, par un démarrage réel du proxy, du lobby et d'une partie SheepWars.

Une vérification manuelle complète reste possible avec :

```bash
bash ./mvnw org.codehaus.mojo:versions-maven-plugin:2.19.1:display-dependency-updates
```

Ne pas appliquer aveuglément une nouvelle version majeure : vérifier les notes de migration, les changements d'API et la compatibilité avec Minecraft 26.2.

## Documentation et qualité

Les fichiers Markdown sont la source de vérité. Après leur modification, exécuter le générateur puis versionner les pages HTML mises à jour :

```bash
node docs-site/build.mjs
node docs-site/validate.mjs
```

`.editorconfig` harmonise l'UTF-8, l'indentation et les fins de ligne. `.gitattributes` force les scripts Bash et fichiers de configuration en LF, et les scripts Windows en CRLF. Les commentaires Java doivent expliquer une contrainte métier ou une décision non évidente, sans paraphraser le code.

Tout changement fonctionnel, de configuration ou d'exploitation doit mettre à jour la page documentaire concernée et [l'historique des changements](CHANGELOG.md). Les changements internes doivent au minimum être expliqués dans le corps du commit ; s'ils modifient une convention ou l'architecture, ils doivent également être documentés.

## Avant une pull request

1. Vérifier qu'aucun secret ni artefact compilé n'apparaît dans `git status`.
2. Lancer `clean verify` sur l'ensemble du réacteur Maven.
3. Reconstruire et valider le site si la documentation a changé.
4. Valider Docker Compose et les scripts touchés.
5. Tester en jeu tout flux dépendant de Paper, Velocity, Redis ou Docker.
6. Décrire précisément les vérifications dans la pull request.
