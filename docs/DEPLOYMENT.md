# Installation, déploiement et exploitation

## Prérequis

### Communs

- machine x86-64 ou ARM64 supportée par les images utilisées ;
- Java JDK 25 sur le `PATH` ;
- Maven 3.9 ou plus récent ;
- Docker Engine récent et daemon démarré ;
- plugin Docker Compose v2 (`docker compose`, pas l'ancien binaire `docker-compose`) ;
- ports hôte `25565/tcp`, `3306`, `6379`, et éventuellement `8080`/`8081`, disponibles ;
- mémoire suffisante pour Velocity, MySQL, Redis et au moins deux backends Paper. Prévoir au minimum 6 à 8 Gio pour un environnement de test confortable, davantage en production.

Les versions Java et Maven sont aussi contrôlées par Maven Enforcer. Les images Minecraft utilisées sont des images Linux : même sous Windows, Docker doit fonctionner en mode conteneurs Linux.

### Windows

- Windows 10/11 ou Windows Server compatible Docker ;
- PowerShell 7 (`pwsh`) recommandé ;
- Docker Desktop avec moteur Linux/WSL2, ou un Docker Engine distant compatible ;
- exécution autorisée pour le script local si la stratégie PowerShell la bloque.

Vérification :

```powershell
java -version
mvn -version
docker info
docker compose version
```

Pour autoriser uniquement la session courante si nécessaire :

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

### Linux

- distribution 64 bits avec Docker Engine ;
- Bash 4 ou ultérieur ;
- Python 3, utilisé par le mergeur sûr de traductions ;
- utilisateur autorisé à accéder au socket Docker.

Vérification :

```bash
java -version
mvn -version
docker info
docker compose version
python3 --version
```

Pour Docker rootless, régler `DOCKER_SOCKET_PATH`, par exemple `/run/user/1000/docker.sock`. Éviter d'exécuter tout le déploiement en root uniquement pour contourner des permissions Docker : configurer proprement le groupe Docker ou le mode rootless.

## Première installation

1. Placer le projet sur la machine de déploiement.
2. Vérifier que les mondes existent sous `dockerfiles/worlds/lobby` et `dockerfiles/worlds/sheepwars`.
3. Vérifier les plugins tiers dans `dockerfiles/plugins/lobby` et `dockerfiles/plugins/sheepwars`, notamment HeadDatabase.
4. Créer `.env` et remplacer tous les secrets.
5. Adapter les UUID administrateurs, OPS, cartes et coordonnées.
6. Valider sans construire d'image.
7. Exécuter le déploiement complet.

Windows :

```powershell
Copy-Item .env.example .env
# Éditer .env
./deploy.ps1 -ValidateOnly
./deploy.ps1
```

Linux :

```bash
cp .env.example .env
# Éditer .env
chmod +x deploy.sh
./deploy.sh --validate-only
./deploy.sh
```

Le premier `docker compose up` télécharge MySQL, Redis, le proxy de socket et les images de base. Il peut donc prendre plusieurs minutes.

## Parité des scripts Windows et Linux

`deploy.ps1` et `deploy.sh` offrent les mêmes étapes et garanties fonctionnelles. Leur implémentation diffère uniquement pour utiliser les primitives naturelles de chaque plateforme.

| Fonction ou contrôle | PowerShell | Bash | Parité |
|---|---:|---:|---:|
| Se place dans le dossier du script | `$PSScriptRoot` | `BASH_SOURCE` | oui |
| Vérifie Maven quand une compilation est demandée | oui | oui | oui |
| Vérifie Docker, le daemon et Compose v2 | oui | oui | oui |
| Valide Compose et les variables `.env` obligatoires | oui | oui | oui |
| `mvn clean package -T 1C` | oui | oui | oui |
| Tests actifs par défaut | oui | oui | oui |
| Exige exactement un JAR ombré par module | oui | oui | oui |
| Refuse un artefact périmé avec `OnlyImages` | oui | oui | oui |
| Vérifie l'intégrité après copie | SHA-256 | comparaison binaire | équivalent |
| Nettoie l'ancien dossier de langues doublement imbriqué | oui | oui | oui |
| Fusionne uniquement les clés de langue absentes | natif PowerShell/.NET | Python 3 | oui |
| Refuse sections/clés dupliquées et feuilles trop imbriquées | oui | oui | oui |
| Construit trois images en parallèle avec `--pull` | jobs PowerShell | processus Bash | oui |
| Crée les tags `latest` et UTC horodaté | oui | oui | oui |
| Attend tous les builds et restitue leurs logs | oui | oui | oui |
| Recrée Velocity, sauf option contraire | oui | oui | oui |
| Affiche le tag utilisable pour un rollback | oui | oui | oui |

Différence de prérequis : Bash délègue la lecture des ZIP et le merge YAML à Python 3. PowerShell utilise directement .NET et n'a donc pas ce prérequis. Le résultat et les validations sont identiques.

## Options des scripts

| Windows | Linux | Effet |
|---|---|---|
| `-SkipTests` | `--skip-tests` | Compile et package sans exécuter les tests |
| `-OnlyImages` | `--only-images` | Réutilise les JAR de `target` après contrôle de fraîcheur |
| `-SkipRestart` | `--skip-restart` | Construit les images mais ne recrée pas Velocity |
| `-ValidateOnly` | `--validate-only` | Compile si nécessaire, distribue/vérifie les JAR et fusionne les langues, sans image ni conteneur |

`ValidateOnly` ne modifie aucun conteneur ni image, mais peut copier des JAR sous `dockerfiles/plugins` et compléter les traductions de déploiement. `OnlyImages` n'est accepté que si aucun POM ou fichier source dépendant n'est plus récent que son artefact.

Exemples :

```powershell
./deploy.ps1 -SkipRestart
./deploy.ps1 -OnlyImages -ValidateOnly
```

```bash
./deploy.sh --skip-restart
./deploy.sh --only-images --validate-only
```

## Ce que fait un déploiement complet

1. validation des outils, du daemon, de Compose et de `.env` ;
2. compilation Maven de tout le réacteur ;
3. copie des JAR ombrés vers les contextes Docker avec contrôle d'intégrité ;
4. fusion des nouvelles traductions dans les configurations persistantes ;
5. construction parallèle de `tropicube-lobby`, `tropicube-sheepwars` et `tropicube-velocity` ;
6. double tag `latest` et `YYYYMMDD-HHMMSS` UTC ;
7. `docker compose up -d --force-recreate velocity`.

Compose démarre ou vérifie automatiquement Redis, MySQL et `docker-proxy` grâce aux dépendances de santé. Le nouveau Velocity restaure les backends encore actifs. Les futures instances utilisent les nouvelles images `latest`; un serveur de jeu déjà lancé ne change pas en plein match.

Le build compile aussi le squelette `tropicube-fallenkingdoms`, mais aucun artefact de ce module n'est distribué ou incorporé à une image tant qu'il ne constitue pas un plugin complet.

## Contrôles après déploiement

```bash
docker compose ps
docker compose logs --tail 200 velocity
docker compose exec redis redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping
```

Sous PowerShell, remplacer la dernière commande par une valeur lue de manière sûre ou utiliser directement les healthchecks de `docker compose ps`; éviter de placer un secret dans l'historique du terminal.

Vérifier ensuite :

- statut `healthy` de Redis, MySQL et docker-proxy ;
- absence d'erreur d'initialisation du plugin Velocity ;
- création d'au moins un lobby ;
- connexion d'un compte Minecraft officiel sur `hôte:25565` ;
- `/server`, transfert vers SheepWars et retour `/hub` ;
- fin d'une partie SheepWars, retour de tous les joueurs au lobby puis disparition immédiate du conteneur avec `docker compose ps` ;
- chargement des profils, soldes, langues et permissions.

Velocity sonde les backends prêts toutes les 10 secondes. Pour valider la protection contre les serveurs fantômes, interrompre un backend de test sans le retirer de Redis, attendre au moins 60 secondes depuis sa dernière réponse, puis vérifier sa disparition de Docker, de `/tropi list` et des clés `instance:*`/index Redis. Un template avec `min-instances` peut être recréé automatiquement après cette purge.

Les interfaces de développement sont optionnelles :

```bash
docker compose --profile dev up -d adminer redis-commander
```

Adminer écoute alors sur `127.0.0.1:8080` et Redis Commander sur `127.0.0.1:8081`. Ne pas modifier ce binding pour les exposer publiquement sans authentification et filtrage supplémentaires.

## Arrêt, redémarrage et mise à jour

Arrêt gracieux de la stack statique :

```bash
docker compose down
```

Les volumes nommés ne sont pas supprimés. Ne pas ajouter `-v` sauf si la suppression définitive de MySQL et Redis est réellement voulue.

Redémarrage du proxy uniquement :

```bash
docker compose up -d --force-recreate velocity
```

Pour un arrêt complet incluant les serveurs dynamiques, les arrêter d'abord avec `/tropi stop` ou activer temporairement `shutdown.stop-dynamic-servers`, puis arrêter la stack après vérification. Un arrêt brutal de Docker peut interrompre une sauvegarde de monde.

## Rollback

Chaque déploiement affiche un tag UTC. Pour revenir au lot précédent :

```bash
docker tag tropicube-velocity:YYYYMMDD-HHMMSS tropicube-velocity:latest
docker tag tropicube-lobby:YYYYMMDD-HHMMSS tropicube-lobby:latest
docker tag tropicube-sheepwars:YYYYMMDD-HHMMSS tropicube-sheepwars:latest
docker compose up -d --force-recreate velocity
```

Les backends existants gardent leur image actuelle. Les arrêter proprement puis les recréer si le rollback doit également s'appliquer aux instances de jeu. Un rollback de code n'annule pas automatiquement une migration de données ; restaurer les sauvegardes compatibles si le schéma a changé.

## Sauvegardes

Sauvegarder régulièrement :

- le volume Docker `mysql-data` ou un dump SQL cohérent ;
- le volume `redis-data` si la restauration des instances/états est requise ;
- les mondes sources sous `dockerfiles/worlds` ;
- les configurations sous `dockerfiles/configs` ;
- `.env` dans un coffre à secrets séparé du dépôt.

Tester les restaurations sur une machine isolée. Une sauvegarde jamais restaurée ne constitue pas une garantie exploitable.

## Dépannage

### « Unable to verify player details »

Contrôler simultanément :

1. `online-mode = true` et forwarding `modern` dans Velocity ;
2. `ONLINE_MODE=false` sur Paper ;
3. forwarding Velocity activé dans `paper-global.yml` ;
4. même valeur réelle de `FORWARDING_SECRET` dans les deux conteneurs ;
5. remplacement effectif de `${CFG_FORWARDING_SECRET}` ;
6. connexion du joueur au port Velocity `25565`, jamais directement au backend.

Après modification du secret, reconstruire les images Paper et recréer Velocity ainsi que les backends concernés.

### Aucun lobby disponible

- consulter `docker compose logs velocity` ;
- vérifier la santé du docker-proxy ;
- contrôler l'image `tropicube-lobby:latest` et le template `lobby` ;
- vérifier les plages de ports et `tropicube-net` ;
- lancer `/tropi templates`, `/tropi list`, puis `/tropi start lobby` si nécessaire.

Une partie terminée n'est jamais détruite tant que ses joueurs ne peuvent pas rejoindre un lobby. Velocity réessaie le transfert chaque seconde ; restaurer au moins un lobby permet alors de terminer automatiquement la destruction en attente.

### Redis ou MySQL inaccessible

- vérifier les healthchecks et les mots de passe `.env` ;
- confirmer que les conteneurs sont sur `tropicube-net` ;
- comparer les variables `TROPICUBE_REDIS_*` et `TROPICUBE_DB_*` injectées aux backends ;
- ne pas utiliser `localhost` depuis un conteneur pour joindre un autre service : utiliser `redis` ou `mysql`.

### Artefact périmé avec `OnlyImages`

Relancer sans cette option. Le contrôle est intentionnel : il empêche de déployer un JAR antérieur au code ou à un POM dont il dépend.

### Docker rootless

Définir dans `.env` :

```dotenv
DOCKER_SOCKET_PATH=/run/user/1000/docker.sock
```

Puis vérifier que Compose peut monter ce chemin et que le daemon correspondant est actif pour l'utilisateur du déploiement.
