# Git, intégration continue et versionnement

Ce guide explique comment versionner Tropicube, proposer une modification et utiliser les contrôles automatiques ajoutés au projet.

## Ce qui est fourni par le projet

Le dépôt contient désormais :

- `.gitignore`, qui exclut les secrets, sorties Maven, logs, fichiers d'IDE et artefacts Docker ;
- `.gitattributes`, qui stabilise les fins de ligne entre Windows et Linux ;
- `.editorconfig`, qui harmonise encodage et indentation dans les IDE ;
- `mvnw` et `mvnw.cmd`, qui utilisent Maven 3.9.11 sans installation globale ;
- `.github/workflows/ci.yml`, qui valide chaque pull request et chaque push sur `main` ;
- `.github/dependabot.yml`, qui recherche automatiquement les mises à jour Maven et GitHub Actions ;
- `.github/pull_request_template.md`, qui fournit la liste des vérifications attendues ;
- JaCoCo, qui produit des rapports de couverture pendant la phase Maven `verify`.
- Git LFS, qui stocke les fichiers de régions Minecraft `.mca` sans alourdir l'historique Git classique.

## Publier le dépôt pour la première fois

Installer Git LFS sur chaque poste qui clone le projet, puis l'activer une fois dans le dépôt :

```bash
git lfs install
```

Après avoir créé un dépôt vide sur GitHub, GitLab ou un autre hébergeur, vérifier l'identité utilisée pour signer les commits :

```bash
git config user.name
git config user.email
```

Puis enregistrer l'état initial et publier `main` :

```bash
git add .
git status
git commit -m "chore(project): initialise le dépôt Tropicube"
git remote add origin <URL_DU_DEPOT>
git push -u origin main
```

Ne jamais ajouter `.env`, une clé privée, un mot de passe, un secret de forwarding ou un export de base de données. Vérifier systématiquement le contenu préparé avec `git diff --cached` avant de valider un commit.

Les JAR de plugins tiers sont également ignorés : chaque développeur doit déposer les versions requises dans `dockerfiles/plugins/lobby/` et `dockerfiles/plugins/sheepwars/` comme indiqué dans le guide de déploiement. Les données joueur, statistiques et verrous de session présents dans les mondes modèles ne sont jamais versionnés.

## Cycle quotidien recommandé

Mettre à jour `main`, puis créer une branche courte depuis celui-ci :

```bash
git switch main
git pull --ff-only
git switch -c feat/nom-court
```

Préfixes de branches conseillés :

| Préfixe | Usage | Exemple |
|---|---|---|
| `feat/` | Nouvelle fonctionnalité ou mini-jeu | `feat/map-voting` |
| `fix/` | Correction de bug | `fix/lobby-language-scoreboard` |
| `refactor/` | Restructuration sans changement fonctionnel | `refactor/redis-events` |
| `docs/` | Documentation uniquement | `docs/linux-installation` |
| `chore/` | Infrastructure, dépendances ou outillage | `chore/update-paper` |

Pendant le développement, utiliser fréquemment :

```bash
git status
git diff
git diff --cached
git log --oneline --decorate -10
```

Éviter les commandes destructrices telles que `git reset --hard` sur un espace de travail contenant des modifications non enregistrées. Pour retirer un fichier de l'index sans supprimer son contenu, utiliser `git restore --staged <fichier>`.

## Construire un bon commit

Un commit doit représenter une modification cohérente, compilable et vérifiée. Le projet suit une forme inspirée de Conventional Commits :

```text
type(portée): résumé court à l'impératif

Pourquoi le changement était nécessaire.
Description des choix importants et de leurs conséquences.
Tests automatiques et scénarios manuels exécutés.
Documentation ou migration éventuellement requise.
```

Types principaux : `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci` et `chore`.

Exemple :

```text
fix(lobby): empêche la création de plusieurs serveurs personnalisés

Vérifie l'instance déjà possédée avant d'envoyer la demande de création.
Harmonise le message de refus dans les quatre langues et actualise la
documentation du menu SheepWars.

Tests : mvnw verify, création simple et tentative de doublon en jeu.
```

Le résumé doit décrire le résultat, rester concis et ne pas se limiter à `modifications`, `mise à jour` ou `fix`. Le corps est requis pour un changement important, une migration, une correction subtile ou toute décision d'architecture.

## Tests avant le push

Sous Windows :

```powershell
.\mvnw.cmd clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
.\deploy.ps1 -OnlyImages -ValidateOnly
```

Sous Linux :

```bash
bash ./mvnw clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
./deploy.sh --only-images --validate-only
```

Les rapports de couverture sont générés dans `target/site/jacoco/` pour les modules possédant des tests. La couverture aide à repérer les zones non testées, mais ne remplace pas des assertions métier pertinentes ni les essais en jeu.

## Fonctionnement de la CI

Le workflow GitHub Actions s'exécute automatiquement :

- lors d'un push sur `main` ;
- lors de l'ouverture ou de la mise à jour d'une pull request ;
- manuellement avec `workflow_dispatch` depuis l'onglet Actions.

Le job `Build, tests and static validation` utilise Ubuntu, Java 25, Maven Wrapper et Node.js 24. Il effectue successivement :

1. la vérification du wrapper Maven ;
2. le build Maven `verify` de tous les modules et les tests JUnit ;
3. la génération des rapports JaCoCo ;
4. la reconstruction du site puis la vérification qu'il correspond aux Markdown ;
5. la validation de `docker-compose.yml` avec `.env.example` ;
6. l'analyse syntaxique des scripts Bash et PowerShell.

Un échec doit être corrigé dans la même branche. Ouvrir le job concerné dans GitHub Actions, identifier la première étape en erreur, reproduire sa commande localement puis pousser le correctif. Ne pas neutraliser un test ou une validation uniquement pour rendre la CI verte.

## Pull requests et protection de `main`

Après les validations locales :

```bash
git push -u origin feat/nom-court
```

Créer ensuite une pull request vers `main` et remplir son modèle : objectif, validations automatiques, essais manuels et éventuelles migrations. Après publication sur GitHub, configurer une règle de protection de `main` avec :

- pull request obligatoire ;
- contrôle `Build, tests and static validation` obligatoire ;
- branche à jour avant fusion ;
- au moins une approbation lorsque plusieurs personnes contribuent ;
- interdiction du force-push et de la suppression de `main`.

Préférer `Squash and merge` pour une branche contenant des commits temporaires, ou `Rebase and merge` si chaque commit est déjà propre et autonome.

## Dependabot et dépendances

Dependabot inspecte Maven chaque semaine et GitHub Actions chaque mois. Pour chaque proposition :

1. lire les notes de version et migrations ;
2. vérifier la compatibilité avec Java 25 et Minecraft 26.2 ;
3. laisser la CI terminer ;
4. démarrer Velocity, le lobby et une partie SheepWars si Paper ou Velocity change ;
5. mettre à jour la documentation si le comportement ou les prérequis évoluent.

Une mise à jour majeure ne doit pas être fusionnée automatiquement, même si elle compile.

## Documentation obligatoire

Les Markdown sont la source de vérité. Toute évolution fonctionnelle ou opérationnelle doit :

1. mettre à jour les pages concernées sous `docs/` et, si nécessaire, le `README.md` ;
2. ajouter une entrée dans [l'historique des changements](CHANGELOG.md) ;
3. reconstruire `docs-site/` ;
4. valider les liens ;
5. inclure Markdown et HTML généré dans le même commit que le code.

Une nouvelle commande met à jour `COMMANDS.md`, une nouvelle option met à jour `CONFIGURATION.md`, une modification de flux met à jour `ARCHITECTURE.md`, et toute modification de déploiement met à jour `DEPLOYMENT.md`.

## Versionner une livraison

Tropicube suit le versionnement sémantique `MAJEUR.MINEUR.CORRECTIF` :

- `CORRECTIF` pour une correction compatible ;
- `MINEUR` pour une fonctionnalité compatible ;
- `MAJEUR` pour une migration incompatible de configuration, données, commandes ou API.

Avant de créer une version, déplacer les entrées pertinentes de `Non publié` dans une section datée de `CHANGELOG.md`, mettre à jour la version Maven, exécuter toutes les validations puis créer un tag annoté :

```bash
git tag -a v1.1.0 -m "Tropicube 1.1.0"
git push origin main v1.1.0
```

## Ajouter une fonctionnalité ou un mini-jeu

Une fonctionnalité issue d'un document de game design doit utiliser le fichier `.md` fourni comme source fonctionnelle principale. Avant de coder, analyser également SheepWars et les autres mini-jeux présents, puis produire un tableau séparant composants réutilisables tels quels, composants à généraliser, composants propres aux jeux existants et composants nouveaux. Une généralisation ne doit jamais créer de dépendance du nouveau jeu vers le métier d'un jeu existant.

L'analyse couvre règles, machine à états, données persistantes, commandes, permissions, textes, paramètres configurables et interactions entre modules. Les ambiguïtés structurantes sont signalées avant implémentation. Le développement est découpé en commits cohérents : socle métier, intégrations, interface joueur, tests et documentation.

Pour un nouveau mini-jeu, consulter également `AGENTS.md` à la racine. Il contient la checklist d'architecture, de performance, de localisation, de Docker et de documentation à appliquer lors des développements futurs.
