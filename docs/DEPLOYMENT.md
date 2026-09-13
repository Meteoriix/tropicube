# Installation, déploiement et exploitation

## Migration des niveaux d'accès

La migration V008 supprime définitivement `tropicube_permissions` et les anciens champs de permissions des grades. Effectuer un dump MySQL avant le premier déploiement. Déployer et recréer Velocity ainsi que tous les backends Paper dans la même fenêtre : les versions précédentes ne comprennent pas `player:access:<uuid>`. Core sérialise la préparation du schéma entre les backends et V008 reprend une exécution interrompue à partir de son audit `MIGRATION`, sans réincrémenter les révisions ni dupliquer ces lignes. Après démarrage, vérifier que V008 figure dans `tropicube_schema_migrations`, puis contrôler les huit grades, une modification `/level`, la priorité de file et `/nick`. Un retour arrière nécessite la sauvegarde SQL réalisée avant V008.

## Prérequis

Le profil Compose facultatif `language-editor` démarre LibreTranslate sur localhost et conserve ses modèles dans `libretranslate-models`. Il n'est pas requis pour exécuter le réseau Minecraft et ne doit pas être exposé publiquement.

### Communs

- machine x86-64 ou ARM64 supportée par les images utilisées ;
- Java JDK 25 sur le `PATH` ;
- Maven 3.9 ou plus récent ;
- Docker Engine récent et daemon démarré ;
- plugin Docker Compose v2 (`docker compose`, pas l'ancien binaire `docker-compose`) ;
- ports hôte `25565/tcp`, `${BEDROCK_PORT:-19132}/udp`, `3306`, `6379`, et éventuellement `8080`/`8081`, disponibles ;
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

Le build de ces deux images télécharge Paper, le JAR serveur Mojang et produit le runtime patché avec `paperclip.patchonly`. Le build Velocity télécharge pour sa part les builds officiels épinglés de Geyser et Floodgate, puis BuildKit vérifie leurs SHA-256. Ces artefacts, leurs bibliothèques et les fichiers `bukkit.yml` et `paper-world-defaults.yml` sont stockés dans les couches Docker ; aucun JAR tiers n'entre dans Git. Le premier build ou un changement de version nécessite donc un accès à PaperMC, Mojang et `download.geysermc.org`, tandis que la création d'une instance Lobby ou SheepWars ne télécharge plus aucun de ces éléments.
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
| Valide puis synchronise exactement les langues sources | .NET et SHA-256 | Python 3 et comparaison binaire | oui |
| Refuse sections/clés dupliquées et feuilles trop imbriquées | oui | oui | oui |
| Construit trois images en parallèle avec `--pull` | jobs PowerShell | processus Bash | oui |
| Vérifie Paper, le JAR Mojang, le runtime patché et les configurations de démarrage dans les images | oui | oui | oui |
| Crée les tags `latest` et UTC horodaté | oui | oui | oui |
| Attend tous les builds et restitue leurs logs | oui | oui | oui |
| Redéploie directement Velocity en développement, sauf option contraire | oui | oui | oui |
| Affiche le tag utilisable pour un rollback | oui | oui | oui |

Différence de prérequis : Bash délègue la lecture des ZIP et la validation YAML à Python 3. PowerShell utilise directement .NET et n'a donc pas ce prérequis. Le résultat et les validations sont identiques.

## Options des scripts

| Windows | Linux | Effet |
|---|---|---|
| `-SkipTests` | `--skip-tests` | Compile et package sans exécuter les tests |
| `-OnlyImages` | `--only-images` | Réutilise les JAR de `target` après contrôle de fraîcheur |
| `-SkipRestart` | `--skip-restart` | Construit et vérifie un lot UTC sans changer les tags `latest` ni recréer Velocity |
| `-ValidateOnly` | `--validate-only` | Compile si nécessaire, distribue/vérifie les JAR et synchronise les langues, sans image ni conteneur |

`ValidateOnly` ne modifie aucun conteneur ni image, mais peut copier des JAR sous `dockerfiles/plugins` et remplacer les traductions de déploiement par leurs sources. `OnlyImages` n'est accepté que si aucun POM ou fichier source dépendant n'est plus récent que son artefact.

Les traductions embarquées de Core et Velocity sont les sources de vérité. Un build Maven les
recopie pendant `process-resources`, et chaque déploiement répète la copie avec un contrôle
d'intégrité, y compris en mode `OnlyImages`. Toute modification faite uniquement sous
`dockerfiles/configs/*/languages` sera donc écrasée ; modifier le fichier correspondant sous
`src/main/resources/languages`.

Exemples :

```powershell
./deploy.ps1 -SkipRestart
./deploy.ps1 -OnlyImages -ValidateOnly
```

```bash
./deploy.sh --skip-restart
./deploy.sh --only-images --validate-only
```

## Redéploiement de développement

La commande habituelle `./deploy.ps1` ou `./deploy.sh` est le chemin de développement : elle ne lance ni `/maintenance`, ni sauvegarde Restic, ni attente de drain. Après les vérifications de build, elle applique les tags `latest` aux trois images puis recrée Velocity avec Compose. L'arrêt propre du proxy interrompt donc immédiatement les parties et instances dynamiques selon `shutdown.stop-dynamic-servers: true`.

## Ce que fait le redéploiement de développement

1. validation des outils, du daemon, de Compose et de `.env` ;
2. compilation Maven de tout le réacteur ;
3. copie des JAR ombrés vers les contextes Docker avec contrôle d'intégrité ;
4. synchronisation exacte des traductions sources vers les configurations Docker ;
5. construction parallèle de `tropicube-lobby`, `tropicube-sheepwars` et `tropicube-velocity` ;
6. vérification des caches Paper/Mojang et des configurations de démarrage dans les deux images de backend ;
7. tag UTC `YYYYMMDD-HHMMSS`, puis mise à jour des trois tags `latest` ;
8. `docker compose up -d --no-build --force-recreate --wait --wait-timeout 180 velocity`.

Compose démarre ou vérifie automatiquement Redis, MySQL et `docker-proxy` grâce aux dépendances de santé. Par défaut, l'arrêt de l'ancien Velocity supprime les backends dynamiques et leurs volumes de données éphémères avant le redémarrage. Le `/server` de Velocity est un `tmpfs` initialisé depuis l'image : son contenu disparaît à l'arrêt et le JAR fraîchement construit ne peut pas être masqué par un ancien volume. Le sous-dossier Floodgate est un volume nommé imbriqué : sa clé privée `key.pem` survit aux recréations et ne doit jamais être supprimée lors d'un déploiement normal. Lors de la première migration, les scripts suppriment précisément l'ancien volume anonyme détecté sur `/server`, sans toucher à `floodgate-data`. Les futures instances utilisent les nouvelles images `latest`.

Le RCON de Velocity est activé uniquement dans son conteneur pour permettre le rechargement des langues par l'éditeur local ; son port n'est pas publié sur l'hôte. Les commandes dédiées `languageeditorreload` de Core et Velocity refusent les joueurs. Après une première livraison de ces commandes, l'éditeur copie les YAML validés et les recharge sans reconstruire les images.

Le redéploiement quotidien de développement interrompt volontairement les parties. La conservation exceptionnelle de parties actives reste réservée à une opération d'exploitation explicitement préparée : régler auparavant `shutdown.stop-dynamic-servers: false` dans la configuration Velocity déployée. Le nouveau proxy restaurera alors les backends encore actifs. Cette option ne doit pas être utilisée pour un arrêt complet.

Le build distribue aussi le plugin `tropicube-fallenkingdoms` et construit son image. Son template Velocity est désactivé par défaut : ajouter une carte FK immutable sous `dockerfiles/worlds/fallenkingdoms/` via Git LFS, renseigner et valider ses positions dans la configuration, puis activer le template lors de la livraison de jeu.

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
- connexion d'un compte Bedrock/Xbox sur `hôte:BEDROCK_PORT` en UDP, avec pseudo Floodgate préfixé par `.` côté Java ;
- acceptation automatique du pack obligatoire Geyser, menus sans vitres décoratives, affichage des onze icônes HeadDatabase et remplacement des têtes de joueur dynamiques par leurs icônes d'état sans tête de Steve ;
- depuis la console Velocity, `geyser connectiontest <hôte-public> <BEDROCK_PORT>` après ouverture du pare-feu et de la redirection UDP ;
- `/server`, transfert vers SheepWars et retour `/hub` ;
- en partie SheepWars, présence d'un seul bloc power-up, changement d'emplacement après activation et résumé final sans balise MiniMessage littérale ;
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

## Vérification des menus de guilde

Après mise à jour conjointe de Core et Lobby, vérifier sur Java et Bedrock : Social aux trois onglets, absence du bouton Guildes dans Profil, création par nom/tag, invitations, pagination de plus de 21 membres, défis et classement vide ou rempli. Vérifier les rôles membre/officier/chef, les confirmations d'exclusion/transfert/départ et l'avertissement de suppression de la dernière guilde. Une saisie privée ne doit jamais apparaître dans le chat des autres joueurs. Tester `!`, l'expiration, la déconnexion, une navigation pendant chargement, les doubles clics et `/lang` dans les quatre langues. Simuler un échec SQL puis utiliser Actualiser. Aucun changement de ports ni de volume de production n'est requis.

## Procédure de livraison fiable avant ouverture

### Développement sur Windows avec Docker Desktop

PowerShell 7 et Python 3.11+ sont nécessaires. Depuis la racine du dépôt :

```powershell
.\tools\ops\setup-windows.ps1
# Une seule fois pour un ancien Redis, hors session de jeu :
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 60 redis
.\tools\ops\windows.ps1 -Command backup
.\tools\ops\windows.ps1 -Command deploy
.\tools\ops\setup-windows.ps1 -InstallTasks
.\tools\ops\windows.ps1 -Command diagnose
```

L'installation télécharge Restic 0.19.1 depuis sa publication officielle et vérifie le SHA-256 avant exécution. Elle initialise une sauvegarde locale chiffrée hors du dépôt source, protège ses fichiers par ACL et génère une clé aléatoire sans l'afficher. La relancer conserve la clé et les snapshots. Ce profil explicite de développement ne remplace pas une sauvegarde sur un stockage externe : conserver une copie indépendante du dépôt Restic et de sa clé de récupération hors du PC.

Les tâches `Tropicube-<identifiant>-backup` et `Tropicube-<identifiant>-diagnose` utilisent le compte Windows connecté, sans stocker son mot de passe. La sauvegarde est quotidienne à 4 h avec rattrapage des échéances manquées ; le diagnostic s'exécute chaque minute. Le PC, la session et Docker Desktop doivent être disponibles. Une sauvegarde planifiée est ignorée si Velocity est arrêté ; elle ne démarre pas la pile et n'avance pas l'horodatage de succès. Les journaux de dernière exécution et `diagnostic.json` sont dans le répertoire privé indiqué par `.runtime/windows/settings.json`. Le Planificateur de tâches conserve le code de sortie. Il n'y a pas de notification externe ni de garantie de sauvegarde quotidienne lorsque le PC est éteint.

Les tâches lancent directement `pythonw.exe` avec `tools/ops/windows_task.py`, sans PowerShell ni console. L'exécutable `pythonw.exe` doit être présent à côté du `python.exe` configuré. Tous les sous-processus Docker, Git et Restic, y compris la connexion de verrouillage SQL, utilisent `CREATE_NO_WINDOW` sous Windows ; masquer une fenêtre PowerShell après son démarrage ne suffit pas à éviter un flash de terminal. Les sorties restent dans `backup-task.log` et `diagnose-task.log`, et les erreurs conservent un code de retour non nul. Pour migrer les tâches existantes, relancer `.\tools\ops\setup-windows.ps1 -InstallTasks` ; les sauvegardes, la clé et les identifiants des tâches sont conservés. Les déploiements manuels utilisent toujours `windows.ps1` dans le terminal ouvert par le développeur.

Pour un lot déjà construit, remplacer `deploy` par `activate -Tag YYYYMMDD-HHMMSS`. Pour charger les variables Restic dans le terminal sans lancer une opération : `. .\tools\ops\windows.ps1 -Command environment`. Pour restaurer sous Windows, repérer le répertoire `backup-...` contenant `manifest.json` avec `restic ls latest`, puis utiliser `restic restore "latest:/C/.../backup-..." --tag tropicube --target <répertoire privé>` en remplaçant le chemin par celui affiché. Restaurer ce sous-répertoire évite de réappliquer les métadonnées des répertoires système parents de Windows. Valider ensuite avec `python tools/ops/tropicube_ops.py verify-backup <répertoire privé>` avant tout import et tester sur une pile isolée. Ne pas restaurer directement par-dessus les données actives.

### Serveur Linux avec sauvegarde externe

La cible initiale reste un seul hôte Linux avec Compose et un objectif de 50 joueurs à mesurer. Python 3.11+, Restic, OpenSSH et systemd complètent les prérequis d'exploitation Linux. Le build et les vérifications Python fonctionnent également sous Windows.

Pour une livraison de production, utiliser `-SkipRestart` / `--skip-restart` afin de construire uniquement les tags UTC candidats et de vérifier les trois images sans toucher la pile. Pour activer ensuite un lot vérifié :

```bash
python3 tools/ops/tropicube_ops.py activate YYYYMMDD-HHMMSS
```

L'activation résout les trois identifiants d'images avant modification, enregistre le lot et les anciens identifiants sous `.runtime/ops/releases`, puis conserve les références précédentes avec les tags locaux `previous`. Sur une pile démarrée : maintenance réseau d'une minute, attente du drain, sauvegarde hors serveur obligatoire, arrêt de Velocity et vérification qu'aucun backend dynamique ne reste actif. L'option `shutdown.stop-dynamic-servers` doit donc être activée. Après remplacement, Compose attend la santé du proxy et l'outil attend un lobby prêt avant de lever la maintenance. Une erreur bloque l'opération ; consulter le manifeste local et les logs privés avant toute reprise. Aucun push ni tag Git n'est créé.

Au premier déploiement, les clients attendent la disponibilité du lobby. Lors d'une mise à jour depuis une ancienne version, recréer Redis avec sa nouvelle variable d'environnement avant la première sauvegarde automatisée ; le volume Redis doit être conservé. Ne pas exposer de port backend supplémentaire.

### Installation des sauvegardes quotidiennes

Configurer et initialiser le dépôt SFTP Restic depuis le compte d'exploitation, installer les quatre unités de `tools/ops/systemd` sous `/etc/systemd/system`, adapter `/opt/tropicube` si nécessaire, puis :

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tropicube-backup.timer tropicube-diagnostic.timer
sudo systemctl start tropicube-backup.service
sudo systemctl status tropicube-backup.service
```

La sauvegarde utilise le même verrou MySQL que les migrations et un dump InnoDB `--single-transaction`, puis un snapshot Redis RDB, les données Floodgate, les configurations, les mondes sources et `.env`. Les données joueur des mondes et les volumes des parties éphémères sont exclus. Les copies temporaires résident dans un répertoire privé et sont supprimées après l'opération. Le manifeste contient date, révision Git, identifiants d'images et hashes des fichiers. Les images elles-mêmes ne sont pas sauvegardées : conserver les tags des lots et les JAR tiers autorisés dans un stockage privé séparé.

Restic chiffre et conserve 7 quotidiennes, 4 hebdomadaires et 3 mensuelles, regroupées par hôte et tag `tropicube`. Une vérification du dépôt suit la rétention. L'horodatage de succès n'est actualisé qu'à la réussite complète ; un échec préserve celui de la sauvegarde précédente. La sauvegarde quotidienne vise une perte maximale de 24 heures ; une panne du stockage doit être traitée immédiatement pour respecter cet objectif.

### Exercice de restauration isolé

1. Préparer un hôte isolé, les versions d'images du manifeste et un environnement privé avec des volumes MySQL/Redis neufs. Fermer l'entrée des joueurs.
2. Exécuter `restic restore latest --tag tropicube --target /srv/tropicube-restore` avec les accès de récupération. Retrouver le répertoire contenant `manifest.json`, puis `python3 tools/ops/tropicube_ops.py verify-backup <répertoire>` avant tout import.
3. Restaurer `environment.env` comme `.env` avec des droits 0600, les archives de configurations/mondes et les données Floodgate. Importer `mysql.sql` dans la base vide avec le client MySQL, en fournissant le mot de passe via un fichier privé ou l'environnement, jamais dans la commande.
4. Pour Redis, charger `redis.rdb` en `dump.rdb` dans le volume neuf avec AOF temporairement désactivé. Après chargement et contrôle des clés, activer AOF avec `CONFIG SET appendonly yes`, attendre la fin de la réécriture, puis redémarrer avec la configuration Compose normale. Ne jamais remettre un ancien AOF à côté du snapshot restauré.
5. Démarrer Velocity puis les backends ; vérifier réconciliation des instances, profils, économie, permissions, quatre langues, Floodgate et une partie complète. Une ancienne partie éphémère n'est pas récupérable.
6. Mesurer le temps entre le début de la récupération et le réseau fonctionnel : objectif inférieur à deux heures. Répéter l'exercice après une migration incompatible et au moins mensuellement.

Un retour au code précédent ne restaure pas les données : en cas de migration incompatible, utiliser ensemble les images du manifeste antérieur et sa sauvegarde. Ne pas activer arbitrairement `previous` contre un schéma non vérifié.

### Diagnostic et essais de capacité

`python3 tools/ops/tropicube_ops.py diagnose` produit un JSON filtré et un code non nul en cas d'alerte. Le timer le lance chaque minute ; consulter `journalctl -u tropicube-diagnostic.service` et le fichier `diagnostic.json`. Les alertes couvrent service indisponible, sauvegarde absente/échouée/âgée de plus de 26 heures, disque occupé à plus de 80 %, télémétrie absente et nombre d'instances inférieur au minimum. Les temps des sondes incluent l'appel Docker CLI ; les logs Core donnent également la mesure Redis côté JVM. Aucun système de notification externe n'est configuré automatiquement.

Avant ouverture, mesurer avec Spark et les diagnostics une heure de jeu représentatif à 10, 25 puis 50 joueurs, avec vingt cycles création/partie/fin. Critères : aucune terminaison mémoire, pas de croissance durable après nettoyage, temps de tick p95 inférieur à 50 ms. Noter CPU/RAM/stockage, nombre d'instances, temps de démarrage et de transfert. Les essais impliquant de vrais clients Java/Bedrock et le matériel cible restent indispensables ; les tests unitaires ne garantissent pas cette capacité.

Références : [profilage Paper](https://docs.papermc.io/paper/profiling/), [journaux Docker](https://docs.docker.com/engine/logging/drivers/local/), [snapshot Redis](https://redis.io/docs/latest/develop/tools/cli/), [rétention Restic](https://github.com/restic/restic/blob/master/doc/060_forget.rst).

## Lot Profil et personnalisation

Livrer Core et Lobby ensemble après `mvnw verify`. V009 crée les tables de cosmétiques sans attribuer d'équipement ni toucher aux soldes existants. Les migrations historiques restent inchangées ; conserver les tables d'empreintes. Les langues utilisent des feuilles à deux niveaux (`cosmetics.name-breeze`, etc.), compatibles avec les merges PowerShell/Bash. Ne pas créer un sous-arbre `names` dans une langue locale. `cosmetics.yml` est copié avec les ressources Core ; les réglages de rendu sont dans la configuration Lobby. Aucun nouveau conteneur, port, secret, JAR tiers ou pack n'est requis.
