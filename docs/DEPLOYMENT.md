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

Les images Paper Lobby et SheepWars copient aussi `dockerfiles/configs/spigot.yml`. Ce fichier désactive l'enregistrement et le chargement de tous les advancements (`*`) ; il doit rester présent dans les deux images pour éviter les notifications et la progression vanilla sur l'ensemble des backends.

Le build de ces deux images télécharge Paper, le JAR serveur Mojang et produit le runtime patché avec `paperclip.patchonly`. Ces artefacts, leurs bibliothèques et les fichiers `bukkit.yml` et `paper-world-defaults.yml` sont stockés dans une couche Docker commune et amorcent ensuite chaque nouveau volume `/data`. Le premier build ou un changement de version nécessite donc un accès à PaperMC et Mojang, tandis que la création d'une instance Lobby ou SheepWars ne télécharge plus aucun de ces éléments. Sur la pile de référence, cela retire deux appels HTTP séquentiels qui ajoutaient environ quatre secondes avant le lancement de la JVM.
7. Exécuter le déploiement complet.

Windows :

```powershell
Copy-Item .env.example .env
# Éditer .env et remplacer toutes les valeurs de démonstration.
$key = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
# Remplacer manuellement la valeur TOTP_MASTER_KEY par $key.
./deploy.ps1 -ValidateOnly
./deploy.ps1
```

Linux :

```bash
cp .env.example .env
# Éditer .env, remplacer toutes les valeurs de démonstration et utiliser
# `openssl rand -base64 32` comme valeur de TOTP_MASTER_KEY.
chmod +x deploy.sh
./deploy.sh --validate-only
./deploy.sh
```

Lorsqu'un `.env` existant est antérieur à l'authentification TOTP, ajouter la variable sans afficher sa valeur :

```powershell
$key = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
Add-Content -LiteralPath .env -Value "TOTP_MASTER_KEY=$key"
```

```bash
printf '\nTOTP_MASTER_KEY=%s\n' "$(openssl rand -base64 32)" >> .env
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
| Vérifie Paper, le JAR Mojang, le runtime patché et les configurations de démarrage dans les images | oui | oui | oui |
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
6. vérification des caches Paper/Mojang et des configurations de démarrage dans les deux images de backend ;
7. double tag `latest` et `YYYYMMDD-HHMMSS` UTC ;
8. `docker compose up -d --force-recreate velocity` puis suppression contrôlée de l'éventuel ancien volume anonyme `/server`.

Compose démarre ou vérifie automatiquement Redis, MySQL et `docker-proxy` grâce aux dépendances de santé. Par défaut, l'arrêt de l'ancien Velocity supprime les backends dynamiques et leurs volumes de données éphémères avant le redémarrage. Le `/server` de Velocity est un `tmpfs` initialisé depuis l'image : son contenu disparaît à l'arrêt et le JAR fraîchement construit ne peut pas être masqué par un ancien volume. Lors de la première migration, les scripts suppriment précisément l'ancien volume anonyme détecté sur ce chemin. Les futures instances utilisent les nouvelles images `latest`.

Pour conserver exceptionnellement les parties actives pendant un redéploiement, régler auparavant `shutdown.stop-dynamic-servers: false` dans la configuration Velocity déployée. Le nouveau proxy restaurera alors les backends encore actifs. Cette option ne doit pas être utilisée pour un arrêt complet.

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
- `/quickplay`, puis `/competitive 4v4` et `/competitive 8v8` avec les capacités exactes ; vérifier les plages 25625–25639, 25640–25649 et 25650–25659 uniquement depuis l'hôte, jamais publiées aux joueurs ;
- transfert d'une party avec suivi activé, refus au-delà de 2 joueurs en classé 4v4 et 4 joueurs en classé 8v8 ;
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

Avec la configuration par défaut, l'arrêt propre du proxy arrête les serveurs dynamiques puis supprime leurs conteneurs et volumes `/data` éphémères. Au prochain démarrage, Velocity supprime aussi tout volume dynamique étiqueté qui n'est plus relié à une instance connue. Attendre la fin de ce nettoyage avant d'arrêter `docker-proxy` ou le daemon Docker ; un arrêt brutal peut interrompre une sauvegarde de monde. Si `shutdown.stop-dynamic-servers` a été désactivé pour un redéploiement, rétablir la valeur `true` ou arrêter d'abord les instances avec `/tropi stop` avant un arrêt complet.

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

### Whitelist d'une partie privée indisponible

- vérifier que la partie a été créée comme privée depuis le lobby ;
- contrôler la présence de `HOST_UUID` et `CUSTOM_GAME_PRIVATE=true` dans le conteneur dynamique ; ces valeurs sont injectées par Velocity et ne doivent pas être ajoutées au template ;
- vérifier `host:<uuid>` et le champ `whitelistedPlayers` de `instance:<id>` dans Redis ;
- confirmer que le joueur ciblé s'est déjà connecté au réseau, ou utiliser directement son UUID dans `/whitelist add`.

Les parties privées ne publient aucun port supplémentaire et restent accessibles uniquement via Velocity.

### Redis ou MySQL inaccessible

- vérifier les healthchecks et les mots de passe `.env` ;
- confirmer que les conteneurs sont sur `tropicube-net` ;
- comparer les variables `TROPICUBE_REDIS_*` et `TROPICUBE_DB_*` injectées aux backends ;
- ne pas utiliser `localhost` depuis un conteneur pour joindre un autre service : utiliser `redis` ou `mysql`.

### Artefact périmé avec `OnlyImages`

Relancer sans cette option. Le contrôle est intentionnel : il empêche de déployer un JAR antérieur au code ou à un POM dont il dépend.

### Cache Paper préchauffé incomplet

Les scripts refusent le déploiement si `paper-<version>-<build>.jar`, `cache/mojang_<version>.jar`, le runtime patché sous `versions/<version>`, `bukkit.yml` ou `config/paper-world-defaults.yml` manque dans une image Lobby ou SheepWars. Vérifier l'accès réseau de Docker à PaperMC et Mojang, puis reconstruire sans réutiliser une couche de build défectueuse. La version et le build des Dockerfiles doivent rester alignés avec `VERSION` et `PAPER_BUILD` dans les templates Velocity.

### Docker rootless

Définir dans `.env` :

```dotenv
DOCKER_SOCKET_PATH=/run/user/1000/docker.sock
```

Puis vérifier que Compose peut monter ce chemin et que le daemon correspondant est actif pour l'utilisateur du déploiement.
