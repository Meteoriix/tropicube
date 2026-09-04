# Configuration

## Sources et ordre d'application

Le projet contient deux catégories de configuration :

1. `src/main/resources` fournit les valeurs par défaut embarquées dans chaque JAR ;
2. `dockerfiles/configs` contient la configuration effectivement copiée dans les images.

TropicubeCore complète automatiquement les fichiers de langue existants avec les nouvelles clés sans écraser les traductions personnalisées. Les scripts de déploiement font la même opération avant la construction des images pour Core et Velocity, avec refus des clés ou sections YAML dupliquées et des structures imbriquées non prises en charge par le mergeur.

Les variables d'environnement ont priorité sur certaines valeurs Core. Dans les conteneurs `itzg`, le mécanisme `REPLACE_ENV_VARIABLES` remplace également les marqueurs `${CFG_...}` présents dans les fichiers copiés.

Les builds Maven et les scripts de déploiement synchronisent les quatre ressources de langue Core et Velocity vers leurs copies sous `dockerfiles/configs`. Les `config.yml` de déploiement restent distincts, car ils contiennent les marqueurs de secrets et l'adresse du proxy Docker.

L'éditeur local utilise `LIBRETRANSLATE_URL` (défaut `http://127.0.0.1:5000`), `LIBRETRANSLATE_API_KEY` facultative, `TROPICUBE_LANGUAGE_EDITOR_PORT` (défaut `8765`), `TROPICUBE_DOCKER_COMMAND` (défaut `docker`) et éventuellement `TROPICUBE_MINECRAFT_CLIENT_JAR` pour les textures Minecraft 26.2. La clé API reste exclusivement dans l'environnement local.

Les fichiers `menus.yml`, `scoreboards.yml` et `tablists.yml` utilisent le schéma versionné `version: 1`. Les menus déclarent titre, nombre de lignes, cadrage, boutons statiques et régions dynamiques ; les scoreboards déclarent un titre et des variantes de 1 à 15 lignes ; les tablists déclarent, pour chaque état, une clé d'en-tête et une clé de pied. Les ressources embarquées et leurs miroirs Docker sont synchronisés au build.

### Grades et niveaux d'accès

Chaque entrée `grades.<nom>` contient uniquement son affichage, sa priorité cosmétique et `default-vip-level` (0–3) / `default-mod-level` (0–4). Appliquer ou faire expirer un grade remplace toujours les niveaux courants par ces valeurs. `access.audit-retention-days`, fixé à 365 par défaut, contrôle la purge quotidienne du journal SQL.

La configuration Velocity ne contient plus `admin-uuids`, `nick.allowed-grades` ni `OPS`. Core publie les niveaux avec une révision dans Redis ; Velocity échoue fermé à `0/0` tant qu'un profil valide n'est pas disponible.

## `.env`

Créer `.env` depuis `.env.example` et remplacer chaque valeur :

| Variable | Utilisation |
|---|---|
| `REDIS_PASSWORD` | Authentification Redis et clients Java |
| `MYSQL_ROOT_PASSWORD` | Administration initiale du conteneur MySQL |
| `MYSQL_DATABASE` | Base créée pour Tropicube |
| `MYSQL_USER` | Compte applicatif MySQL |
| `MYSQL_PASSWORD` | Mot de passe du compte applicatif |
| `FORWARDING_SECRET` | Authentification Velocity → Paper |
| `RCON_PASSWORD` | RCON des instances dynamiques |
| `TOTP_MASTER_KEY` | Clé AES-256 encodée en Base64 pour chiffrer les secrets TOTP du personnel |
| `DOCKER_SOCKET_PATH` | Socket Docker de l'hôte, y compris rootless |

Génération recommandée d'un secret sous PowerShell :

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLower()
```

Sous Linux :

```bash
openssl rand -hex 32
```

La clé TOTP demande une représentation Base64 de 32 octets, par exemple
`[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`
sous PowerShell ou `openssl rand -base64 32` sous Linux. Elle doit être sauvegardée comme un secret d'exploitation : sa perte rend les inscriptions TOTP existantes illisibles. Le déploiement Compose de référence refuse de démarrer sans cette variable ou avec une clé mal formée. Un lancement autonome de Core sans cette variable verrouille volontairement les commandes staff protégées.

Pour compléter un ancien `.env` sous PowerShell sans afficher la clé dans le terminal :

```powershell
$key = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
Add-Content -LiteralPath .env -Value "TOTP_MASTER_KEY=$key"
```

### Cadre de protection des données pour les signalements

Le code applique la minimisation et la limitation temporelle, mais cela ne suffit pas à lui seul à rendre l'exploitation conforme au RGPD. Avant l'ouverture au public, le responsable de traitement doit documenter la finalité et la base légale de la modération, inscrire le traitement au registre, informer clairement les joueurs avant la collecte, désigner les destinataires habilités et fournir un canal effectif d'accès, de rectification, d'opposition, de limitation et d'effacement. Il doit aussi vérifier si une AIPD est nécessaire, conclure les contrats utiles avec ses hébergeurs et définir une procédure de violation de données.

La durée uniforme de 90 jours choisie pour les preuves constitue une décision de l'exploitant, pas une durée universellement validée par la CNIL. Elle doit être justifiée et réévaluée selon la finalité ; les preuves arrivées à échéance sont purgées automatiquement. La CNIL rappelle que la durée doit découler de l'objectif du traitement, que seules les données nécessaires doivent être collectées et que les personnes doivent être informées de leurs droits : [durées de conservation](https://www.cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees), [sécurité et minimisation](https://www.cnil.fr/fr/securite-des-donnees-les-regles-essentielles), [droits des personnes](https://www.cnil.fr/fr/preparer-lexercice-des-droits-des-personnes). Les accès staff aux preuves devront également être journalisés et revus ; le schéma actuel conserve les actions de workflow mais ne constitue pas encore un portail autonome d'exercice des droits.

Éviter les espaces et caractères interprétés par les syntaxes `.env`, YAML ou shell. Ne jamais copier les valeurs réelles dans une issue, un log ou un commit.

## TropicubeVelocity

Fichier de déploiement : `dockerfiles/configs/TropicubeVelocity/config.yml`.

### Redis et Docker

- `redis.host`, `port`, `password` : connexion au bus partagé ;
- `docker.host` : `tcp://docker-proxy:2375` en Compose ;
- `docker.network` : réseau auquel rattacher les backends ;
- `docker.container-prefix` : préfixe utilisé pour identifier les conteneurs gérés ;
- `docker.port-range-*` : plages réservées aux ports Minecraft et RCON ;
- `docker.base-path` : chemin absolu de l'hôte pour d'éventuels volumes relatifs.

Les plages doivent être valides, sans chevauchement et assez grandes pour le nombre maximal d'instances. Dans l'architecture Docker actuelle, les connexions proxy → backend utilisent le port interne `25565`; les ports hôte restent utiles pour l'administration et RCON.

### Surveillance des backends

- `health-check.interval-seconds` : fréquence des sondes TCP envoyées par Velocity aux instances prêtes, `10` par défaut ;
- `health-check.stale-timeout-seconds` : durée maximale depuis la dernière réponse avant destruction complète, `60` secondes par défaut ;
- `health-check.connect-timeout-millis` : délai maximal d'une tentative de connexion, `2000` ms par défaut.

Ces trois valeurs doivent être strictement positives et le seuil d'expiration doit être supérieur ou égal à l'intervalle. Les instances `STARTING` ne sont pas concernées : leur initialisation dispose séparément de 120 secondes avant nettoyage. Une instance prête expirée est retirée de Docker, Velocity et Redis ; le maintien de `min-instances` peut ensuite recréer un lobby ou un serveur classique si nécessaire.

### Templates

Chaque entrée de `templates` décrit :

- `enabled`, `name`, `image`, `type` ;
- plage de ports, `max-players` et `spectator-slots` ;
- mémoire minimale/maximale en Mio ;
- `auto-start`, `min-instances` et `max-instances` ;
- `auto-stop`, `auto-stop-delay` ;
- variables d'environnement passées à l'image Paper.

Les templates Paper fournis fixent `VERSION: "26.2"` et `PAPER_BUILD: "97"`. Ce build explicite garantit que le runtime sélectionné au démarrage correspond aux artefacts préchauffés dans `Dockerfile.lobby` et `Dockerfile.sheepwars`. Toute mise à jour doit modifier ensemble ces valeurs, les arguments par défaut des deux Dockerfiles et la dépendance Paper du POM parent, puis reconstruire les images.

SheepWars utilise trois recettes : `sheepwars` (`GAME_MODE=QUICK_PLAY`, ports 25625–25639), `sheepwars-ranked-4v4` (25640–25649) et `sheepwars-ranked-8v8` (25650–25659). Les deux templates classés sont créés seulement lorsqu'un groupe compatible atteint respectivement 8 ou 16 joueurs. `matchmaking.ranked.initial-rating-range`, `growth-per-step`, `step-seconds` et `maximum-rating-range` règlent l'élargissement progressif de leur fenêtre de cote.

Les fichiers `dockerfiles/configs/bukkit.yml` et `dockerfiles/configs/paper-world-defaults.yml` sont embarqués dans chaque image de backend. Le second fixe seulement la version du schéma et laisse Paper appliquer ses valeurs par défaut aux options absentes. Ils évitent les téléchargements de configurations réalisés par l'entrypoint sur chaque volume neuf ; leur version doit donc être vérifiée lors d'une migration Paper.

Le lobby est une dépendance de routage essentielle et reste activé. Pour ajouter un mode de jeu, fournir une image, un plugin capable de publier son état, une plage de ports et une entrée correspondante dans `server-types` du lobby.

`max-players` borne les participants avant le démarrage. `spectator-slots`, nul par défaut et fixé à `8` pour SheepWars, ajoute une réserve physique uniquement quand l'instance est `GAME_PLAYING`. La variable Docker `MAX_PLAYERS` reçoit la somme afin que `/friend join` puisse connecter un spectateur sans réduire la capacité de jeu nominale.

`min-instances` est le plancher maintenu toutes les 30 secondes par l'autoscaler. Pour SheepWars, le matchmaking crée également une nouvelle instance à la demande lorsqu'aucun serveur classique en attente ou en démarrage n'est encore joignable. `max-instances` borne toutes les créations simultanées, qu'elles viennent du matchmaking, d'une partie personnalisée ou de `/tropi start`. Les templates fournis autorisent explicitement cinq instances concurrentes chacun.

`shutdown.stop-dynamic-servers` vaut `true` par défaut. Un arrêt propre de Velocity arrête alors toutes les instances dynamiques, supprime leurs conteneurs et leurs volumes `/data` éphémères étiquetés par Tropicube. Au démarrage, les volumes étiquetés sans conteneur connu sont également purgés. Le proxy de socket autorise donc l'API Docker `VOLUMES` en plus de `CONTAINERS`, mais les volumes persistants `mysql-data` et `redis-data`, non étiquetés comme dynamiques, ne sont jamais concernés.

`party.disconnect-grace-seconds` vaut `60` et doit être strictement positif. Un joueur qui revient avant cette échéance reste dans sa party. Après l'échéance, il est retiré atomiquement et, si c'était le chef, le rôle passe à un membre connecté. Une party devenue entièrement hors ligne est supprimée immédiatement.

Le service Compose `velocity` monte `/server` en `tmpfs` avec une limite de 512 Mio. Cette donnée d'exécution est réinitialisée depuis l'image à chaque démarrage et libérée automatiquement dès l'arrêt du conteneur ; elle ne doit pas être remplacée par un volume persistant.

La valeur `false` conserve les backends et leurs volumes afin qu'un redémarrage de Velocity puisse restaurer les parties actives. Cette dérogation doit être réservée aux redéploiements où cette continuité est explicitement recherchée ; si la clé est absente, le comportement sûr reste la suppression.

### Accès, nick et administration

- `access.permission-thresholds.vip|mod` permet d'ajuster les seuils des permissions connues, validés au démarrage ;
- `vipLevel >= 3` contrôle l'activation de `/nick` ; la désactivation reste accessible à tous ;
- `nick.skin-uuids` complète le pool de profils Mojang utilisés comme skins.

Core publie le grade courant sous `player:grade:<uuid>` avec une durée de vie de 24 heures. La valeur est actualisée au chargement et à chaque changement de grade, et n'est pas supprimée pendant un transfert entre backends.

Une identité active est enregistrée sous `nick:<uuid>` avec le pseudonyme, le skin signé et le grade d'affichage factice. Les anciens payloads sans grade restent compatibles et utilisent `PREMIUM`. La durée de vie de 24 heures est renouvelée à la déconnexion puis à la reconnexion ; le nick complet reste donc disponible pendant cette fenêtre, sans limite spéciale de 30 secondes.

### Exploitation réseau

- `connection-protection.address-limit`, `global-limit`, `window-seconds` et `quarantine-seconds` contrôlent les limites adaptatives avant authentification. Aucun historique d'adresse n'est persisté.
- `motd.line-1`, `line-2` et `maintenance-line` décrivent uniquement l'entrée publique Velocity. `{games}` est remplacé par les types de jeux activés, dédupliqués entre leurs différentes files ; `motd.game-<TYPE>`, `games-separator` et `no-games` contrôlent leurs libellés et le repli. Le MOTD ne publie ni langues ni effectif connecté.
- `announcements.interval-seconds` et `announcements.entries[].message-key/target` définissent la rotation localisée. Une cible vaut `network`, un type, un template ou une instance.
- `maintenance.default-deadline-minutes` fixe l'échéance utilisée par `/maintenance ... on` lorsqu'aucune durée n'est fournie. Elle doit valoir de 1 à 1 440 minutes ; le drain est persisté dans Redis pendant sept jours au plus.

## Velocity natif

Fichiers : `dockerfiles/configs/Velocity/velocity.toml` et `forwarding.secret`.

Paramètres indispensables :

- `bind = "0.0.0.0:25577"` dans le conteneur ;
- `online-mode = true` ;
- `player-info-forwarding-mode = "modern"` ;
- `forwarding-secret-file = "forwarding.secret"` ;
- liste `servers.try` vide, le lobby étant choisi dynamiquement par le plugin.

Le fichier `forwarding.secret` contient le marqueur `${CFG_FORWARDING_SECRET}`, remplacé au démarrage. Le fichier Paper `paper-global.yml` utilise le même secret avec `proxies.velocity.enabled: true` et `online-mode: true`. Une divergence provoque typiquement « Unable to verify player details ».

## TropicubeCore

Fichier : `dockerfiles/configs/TropicubeCore/config.yml`.

Les valeurs suivantes peuvent être surchargées sans modifier YAML :

- `TROPICUBE_REDIS_HOST`, `TROPICUBE_REDIS_PORT`, `TROPICUBE_REDIS_PASSWORD` ;
- `TROPICUBE_DB_HOST`, `TROPICUBE_DB_PORT`, `TROPICUBE_DB_NAME` ;
- `TROPICUBE_DB_USER`, `TROPICUBE_DB_PASSWORD`.

Sections métier :

- `economy` : nom, symbole, solde initial et bornes de transfert ;
- `social.friends.max-count` : nombre maximal d'amis par joueur (`100`) ;
- `social.friends.request-expiry-days` : expiration des demandes en attente (`30`) ;
- `social.party.max-size` : taille maximale d'une party (`8`) ;
- `social.party.invite-expiry-seconds` : validité d'une invitation de party (`60`) ;
- `guilds.max-members` : capacité d'une guilde (`50`) ;
- `guilds.max-officers` : nombre maximal d'officiers (`5`) ;
- `guilds.weekly-contribution-cap` : contribution d'XP hebdomadaire maximale par membre (`5000`), alimentée automatiquement par l'XP des parties et des missions ;
- `language.default` : langue utilisée avant chargement d'un profil existant. À la création d'un joueur, la langue du client Minecraft sélectionne `fr`, `en`, `es` ou `de` ; toute autre locale utilise l'anglais ;
- `grades` : présentation MiniMessage, priorité et niveaux VIP/modérateur appliqués par défaut.

`TropicubeCore/missions.yml` est un catalogue métier versionné. Chaque définition déclare un événement, une cible, de l'XP réseau et de la monnaie. Le catalogue livré contient au moins six missions quotidiennes et quatre hebdomadaires afin que les rotations de 5 + 3 puissent toujours proposer un remplacement sans doublon. Toute modification exige une hausse explicite de `version`, des identifiants stables et des valeurs positives validées au démarrage.

Une mission peut déclarer `reward-reroll-tokens`. Les jetons sont crédités atomiquement avec les autres récompenses, plafonnés à cinq, puis consommés seulement après les deux rerolls quotidiens gratuits — quatre avec `tropicube.missions.reroll.bonus`.

Les noms de grades sont utilisés comme identifiants stables dans la boutique, les affichages et les données persistées. Une modification doit donc être répercutée dans tous les fichiers concernés.

## TropicubeLobby

Fichier : `dockerfiles/configs/TropicubeLobby/config.yml`.

- `lobby.spawn` définit monde, coordonnées et orientation ;
- `lobby.welcome.enabled` active globalement l'accueil immersif : le titre, le son et les particules ne sont joués qu'à l'arrivée initiale au lobby après connexion au proxy, tandis qu'un retour ultérieur affiche seulement l'actionbar discrète ; chaque joueur peut désactiver durablement ces effets depuis Profil > Paramètres ;
- `lobby.double-jump` active globalement les sauts aériens ;
- `auto-replay.batch-size` fixe le nombre de parties automatiques avant une nouvelle confirmation (`5`, borné entre 1 et 100) ;
- `server-types` définit les icônes Material ou HeadDatabase ;
- `vip-shop.entries` associe grade, icône, nom et prix catalogue croissant ; le prix d'une montée en grade est la différence entre le grade ciblé et le grade déjà acheté ;
- `lang-selector.languages` configure codes, têtes et textes de présentation ; chaque entrée utilise une clé `lore` MiniMessage unique et `<br>` pour ses sauts de ligne. Les anciennes entrées `lore1`/`lore2` restent lues et fusionnées en mémoire pendant la migration.

Le `grade-key` d'une entrée doit exister dans Core et ne peut apparaître qu'une fois. Les prix doivent être strictement croissants. L'onglet Grades sépare les avantages réellement actifs (`lobby.shop-active-<grade>`) des promesses non implémentées (`lobby.shop-soon-<grade>`) dans les quatre langues ; une fonctionnalité ne doit passer dans la première section qu'après validation de son comportement effectif.

## TropicubeSheepwars

Fichier : `dockerfiles/configs/TropicubeSheepwars/config.yml`.

Catalogues complémentaires :

- `kit-mastery.yml` doit définir une `version`, le `unlock-level` et exactement `BRANCH_A`/`BRANCH_B` pour chacun des neuf kits. Les effets ne sont appliqués qu'en Quick Play ; le classé et les parties personnalisées utilisent les kits de base ;
- `season-rewards.yml` versionne la monnaie et les récompenses de confort (titre/badge) de chaque rang. Une récompense de saison ne doit jamais accorder d'avantage en partie.

- `default-settings` fixe capacité, démarrage, durées, kits, vote et fréquence des moutons ; `sheep-give-delay` vaut 15 secondes par défaut. En partie classique, la cadence est figée au lancement selon l'effectif : 10 secondes de 2 à 4 joueurs, 12 secondes de 5 à 8, puis 15 secondes de 9 à 16. Une partie personnalisée conserve la valeur choisie par l'hôte ;
- `default-settings.min-players` et `max-players` sont bornés entre 2 et 16. L'hôte ne peut pas réduire le maximum sous l'effectif déjà présent, et chaque modification du maximum est propagée à l'instance Redis afin que Velocity applique immédiatement la capacité ;
- `default-settings.auto-start` vaut `true` par défaut pour les parties classiques et lance le compte à rebours dès que `min-players` est atteint ;
- `custom-game-default-settings.auto-start` vaut `false` par défaut et remplace cette valeur à l'initialisation d'une instance possédant un `HOST_UUID` ; l'hôte peut ensuite la modifier pour la partie courante ;
- `competitive.role-limits.4v4|8v8.dps|tank|support` limite chaque rôle par équipe ; les sommes par défaut valent exactement quatre ou huit ;
- `CUSTOM_GAME_PRIVATE`, injecté automatiquement par Velocity avec `HOST_UUID`, indique au backend si l'item de whitelist doit être remis à l'hôte ; cette variable interne ne doit pas être configurée manuellement dans le template ;
- `sheep-probabilities` contient des poids relatifs, pas nécessairement un total de 100 ; les valeurs par défaut totalisent 100 et privilégient TNT/Soin à 10 %, Force à 9 %, Abordage/Feu/Foudre à 8 %, Recherche/Échange à 7 %, Ténèbres/Poison/Fragmentation à 6 %, Gravité à 5 %, Météore à 4 % et Distorsion/Mécha à 3 % ;
- `gameplay-balance.global` configure le stock maximal, la compensation de sous-effectif, le délai d'armement et les PV des moutons destructibles ;
- `gameplay-balance.pvp` configure la vitesse d'attaque sans recharge, les dégâts 1.8 des épées, le multiplicateur de dégâts d'arc et les composantes horizontale, verticale et de sprint du recul ;
- `gameplay-balance.kits` configure les multiplicateurs, points de vie, résistances et temporisations des kits ;
- `gameplay-balance.sheep` regroupe par type les dégâts, rayons, durées, puissances de destruction, nombres de cibles et plafonds. Les durées nommées `*-seconds` sont exprimées en secondes, les périodes `*-ticks` en ticks et les dégâts/soins en PV ;
- `team-powerups.enabled`, `respawn-seconds` et `hit-radius` contrôlent les cibles de laine lumineuses. `effects.healing`, `poison-arrows` et `speed` définissent leurs poids et leurs valeurs ; au moins un poids doit rester positif et toutes les bornes sont validées au démarrage ;
- `force-settings` désactive des classes, kits ou moutons ;
- `locations` décrit le lobby, la limite du vide et les cartes activées. Une carte peut fournir jusqu'à huit centres `powerups.target1..target8` en plus de ses spawns ; sans centre, elle reste jouable mais n'affiche aucune cible ;
- chaque carte doit fournir des points d'apparition utilisables pour les équipes rouge et bleue.

Les types désactivés sont exclus avant normalisation. Le menu affiche le pourcentage effectif, reconstruit immédiatement le sélecteur pondéré après une modification et interdit de ramener à zéro le dernier poids actif. Chaque remise est un tirage indépendant utilisant exactement ces probabilités. Une échéance rencontrant le plafond de stock reste due et est retentée chaque seconde ; le délai complet redémarre uniquement après insertion réussie. Si tous les poids activés proviennent néanmoins d'une configuration externe à zéro, le premier type actif devient le secours visible à 100 %. Une configuration qui désactive tous les types est réparée au chargement en réactivant TNT avec un avertissement.

La section `gameplay-balance` est validée au démarrage. Une valeur manquante, non numérique, négative, ou nulle lorsqu'un rayon, une durée ou une cadence doit être strictement positif empêche le plugin de démarrer avec un message indiquant la clé fautive. `ConfigUpdater` complète les anciennes configurations avant cette validation.

## Langues

Core et Velocity prennent en charge `fr`, `en`, `es` et `de`. Les textes utilisent MiniMessage et exclusivement des placeholders nommés en `lower_snake_case`, par exemple `{player}`, `{balance}` ou `{countdown}`. `{instance_name}` est fourni automatiquement : Core lit le nom visible injecté dans `SERVER_NAME`, avec repli sur `INSTANCE_ID` puis le nom Paper, tandis que Velocity lit le serveur actuellement associé au joueur. Pour ajouter une clé :

Les balises internes `<tc>` et `<sw>` insèrent respectivement les marques réseau et SheepWars. Leur usage est défini dans la [charte des messages](MESSAGING_STYLE.md) ; elles sont réservées aux notifications autonomes et ne doivent pas être ajoutées aux contenus compacts d'interface.

1. l'ajouter dans les quatre ressources du module ;
2. ajouter les traductions correspondantes sous `dockerfiles/configs` ;
3. lancer `mvn test` pour vérifier les ressources Core ;
4. exécuter un déploiement ou `--validate-only` pour valider le merge.

Éviter les clés YAML dupliquées. Le merge de déploiement accepte les sections de premier niveau et leurs feuilles indentées de deux espaces ; une structure plus profonde doit être migrée explicitement plutôt qu'ignorée silencieusement.
