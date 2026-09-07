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

Les fichiers `tropicube-core/src/main/resources/languages/*.yml` et
`tropicube-velocity/src/main/resources/languages/*.yml` sont les sources de vérité des traductions.
La phase Maven `process-resources` de chaque module recopie automatiquement ces fichiers, sans
filtrage, vers `dockerfiles/configs/TropicubeCore/languages` et
`dockerfiles/configs/TropicubeVelocity/languages`. Il ne faut donc pas personnaliser directement
les copies Docker : elles sont remplacées au prochain build ou déploiement.

L'[éditeur de langues](LANGUAGE_EDITOR.md) fournit une vue structurée, un mode YAML brut, des aperçus MiniMessage et un workflow LibreTranslate. Il se lance avec `language-editor.ps1` ou `language-editor.sh` ; ses tests frontend et backend font partie de la CI.

Les tests existants couvrent notamment les modèles Docker/Redis, la conversion des durées, la validité des ressources YAML et les invariants du catalogue SheepWars. Toute correction de bug devrait ajouter un test de non-régression lorsque le comportement peut être isolé de Paper ou Velocity.

Les interactions nécessitant un serveur réel — inventaires Bukkit, événements réseau, démarrage d'instances et routage Velocity — restent à valider sur une pile Docker de développement. Si leur volume augmente, l'étape suivante recommandée est de créer des tests d'intégration avec des adaptateurs Paper/Velocity plutôt que de simuler toute l'API serveur.

Tout nouveau texte joueur ou log technique doit respecter la [charte des messages Tropicube](MESSAGING_STYLE.md). Les textes sont écrits en MiniMessage ; les logs sont convertis en ANSI uniquement lorsque la console le permet.

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

## Tests SQL des guildes

`GuildSqlTest` utilise les migrations réelles sur une base jetable dédiée. Sans `TROPICUBE_GUILD_TEST_URL`, les quatre scénarios SQL sont ignorés ; les tests métier et ressources restent actifs. Pour les exécuter, démarrer un conteneur MySQL de test avec la même version que Compose, une base `guild_test`, un port publié uniquement sur `127.0.0.1` et un compte root sans mot de passe limité à ce conteneur jetable. Définir `TROPICUBE_GUILD_TEST_URL=jdbc:mysql://127.0.0.1:<port>/guild_test?allowPublicKeyRetrieval=true&useSSL=false`, puis lancer le réacteur Maven. Ne jamais utiliser la base du réseau. Supprimer le conteneur temporaire après les essais.

Les assertions couvrent invitations expirées, acceptations simultanées face à la capacité, permissions et transfert, écrans périmés après changement de guilde, départ du dernier membre et contribution hebdomadaire affichée après changement de semaine. Les tests purs couvrent également les identités de guilde, le délai de saisie, les droits graphiques, la pagination et la consommation atomique d'une saisie.

## Audit des références de langues

`PluginLanguageReferencesTest` analyse les sources Java de tous les modules Maven et les références des ressources, puis exige leur présence dans les quatre catalogues du fournisseur concerné (Core ou Velocity), sans secours de langue. Il contrôle aussi les arguments des appels de traduction directs et les familles dynamiques connues, notamment les énumérations, missions et entrées VIP configurées. Toute nouvelle famille de clés calculées doit compléter son registre explicite.

`YamlResourcesTest` complète cet audit avec la parité des arborescences, des placeholders et des styles MiniMessage, les doublons YAML et la synchronisation des copies Docker. Ces contrôles statiques ne remplacent pas la relecture linguistique ni les essais en jeu des textes calculés et des changements de langue.

`RuntimeUiBundleTest` restaure une ancienne génération sur des catalogues déjà à jour et vérifie toutes les clés des quatre langues, la préservation des personnalisations, les appels répétés et le nettoyage des fichiers temporaires. Pour diagnostiquer un serveur actif, comparer également ses fichiers de langue aux ressources embarquées : une validation du dépôt seule ne prouve pas que la génération Redis restaurée est complète. Vérifier en jeu Social (amis, groupes, guildes), les autres inventaires, le HUD et leur rafraîchissement après `/lang` sans reconnexion.

## Contrôles avant ouverture

Exécuter `python -m unittest discover -s tools/ops -p test_ops.py -v` pour les erreurs d'exploitation et `python tools/ops/integration_tests.py` avec Docker disponible pour les migrations/verrous/Redis réels. Les identifiants `integration-only` appartiennent exclusivement à cette pile jetable, jamais à la production. Les sources SQL historiques sont regroupées dans DatabaseSchema pour que le démarrage et les tests utilisent les mêmes tables de base.

L'objectif de charge initial est 50 joueurs sur un hôte Linux. Conserver les rapports Spark et diagnostics dans un stockage privé ; relever le p95 des ticks et les ressources après vingt cycles. Ne pas optimiser des requêtes ou des fréquences de rafraîchissement sans comparer un scénario identique avant/après.
