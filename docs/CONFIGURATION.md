# Configuration

## Sources et ordre d'application

Le projet contient deux catégories de configuration :

1. `src/main/resources` fournit les valeurs par défaut embarquées dans chaque JAR ;
2. `dockerfiles/configs` contient la configuration effectivement copiée dans les images.

TropicubeCore complète automatiquement les fichiers de langue existants avec les nouvelles clés sans écraser les traductions personnalisées. Les scripts de déploiement font la même opération avant la construction des images pour Core et Velocity, avec refus des clés ou sections YAML dupliquées et des structures imbriquées non prises en charge par le mergeur.

Après restauration des interfaces depuis Redis, Core complète aussi chaque catalogue restauré avec les clés du JAR installé, avant son remplacement atomique sur disque. Une génération de l'éditeur antérieure au plugin conserve ses personnalisations sans supprimer les traductions ajoutées depuis. Les manifestes d'interface restent restaurés à l'identique.

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
| `BEDROCK_PORT` | Port UDP public de Geyser, entier de `1` à `65535` (`19132` par défaut) |
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

Le service Compose `velocity` monte `/server` en `tmpfs` avec une limite de 512 Mio. Cette donnée d'exécution est réinitialisée depuis l'image à chaque démarrage et libérée automatiquement dès l'arrêt du conteneur. Seul le sous-dossier `/server/plugins/floodgate` utilise le volume nommé `floodgate-data`, afin de conserver la clé privée et les éventuelles données de liaison de comptes.

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

## Geyser et Floodgate

Les deux plugins sont installés uniquement sur Velocity. `Dockerfile.velocity` télécharge les artefacts officiels Geyser-Velocity `2.11.2-b1234` et Floodgate-Velocity `2.2.5-b140`, avec URL de build et SHA-256 épinglés. Aucun JAR tiers n'est versionné. Avant une mise à jour, vérifier la prise en charge de Java `26.2` et des versions Bedrock courantes, puis modifier ensemble URL, empreinte, commentaires de configuration, tests et documentation.

`dockerfiles/configs/Geyser-Velocity/config.yml`, au schéma Geyser `config-version: 7`, écoute sur `0.0.0.0:${CFG_BEDROCK_PORT}`, annonce le même port et impose `auth-type: floodgate`. La connexion directe est conservée pour éviter un second trajet TCP. Les suggestions de commandes sont désactivées côté Geyser et l'échafaudage propre à Bedrock est bloqué pour préserver l'équité SheepWars.

Le contenu personnalisé et le pack intégré Geyser sont activés et obligatoires. Le pack améliore le rendu des inventaires Bedrock ; `custom_mappings/tropicube-heads.json` préenregistre les onze textures HeadDatabase statiques utilisées par les menus afin d'éviter leur remplacement par une tête de Steve. Toute nouvelle icône HeadDatabase doit ajouter son hash de texture à ce fichier, puis requiert une reconstruction et un redémarrage de Velocity pour régénérer le pack Bedrock. Les skins de joueurs résolus après connexion ne peuvent pas rejoindre ce pack déjà construit : les interfaces emploient donc des icônes vanilla explicites pour ces entrées dynamiques sur Bedrock et conservent les têtes sur Java.

`dockerfiles/configs/floodgate/config.yml` autorise les comptes Bedrock sans liaison Java obligatoire. Le préfixe `.` et le remplacement des espaces par `_` évitent les collisions avec les pseudos Java ; les invitations sociales et la whitelist privée acceptent explicitement ce format. `key.pem` est généré dans `floodgate-data`, n'est jamais copié dans l'image ni dans Git, et doit être sauvegardé avec les autres secrets durables. Changer ou perdre cette clé peut invalider les identités transmises pendant la transition.

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
- `team-powerups.enabled`, `respawn-seconds` et `hit-radius` contrôlent la cible unique de laine lumineuse. `effects.healing`, `poison-arrows` et `speed` définissent leurs poids et leurs valeurs ; au moins un poids doit rester positif et toutes les bornes sont validées au démarrage ;
- `force-settings` désactive des classes, kits ou moutons ;
- `locations` décrit le lobby, la limite du vide et les cartes activées. Une carte peut fournir un nombre quelconque de centres candidats numérotés `powerups.target1`, `target2`, etc. ; un seul est actif et la réapparition évite le candidat précédent. Sans centre, la carte reste jouable mais n'affiche aucune cible ;
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

## Saisie privée des guildes dans le Lobby

Dans `TropicubeLobby/config.yml`, `guilds.input-timeout-seconds` définit le délai de chaque étape de saisie privée (nom, tag ou pseudo). Valeur par défaut : `120` secondes ; un entier entre `10` et `600` est requis. Les valeurs fractionnaires, textuelles ou hors limites sont refusées au démarrage avec la clé et la valeur reçue. La ressource embarquée et sa copie Docker sont synchronisées ; les anciennes configurations reçoivent la valeur par défaut via le mécanisme existant. Les limites de membres, d'officiers et de contribution restent celles de Core.

## Fiabilité avant ouverture

Les versions Minecraft 26.2, Java 25 et Maven 3.9.11 restent inchangées. Les nouvelles options sont lues au démarrage ; un changement nécessite la recréation du backend ou du proxy concerné.

| Option | Défaut | Validation / effet |
|---|---|---|
| Velocity `docker.memory-budget-mib` | 16384 | Entier positif ; budget des limites mémoire des conteneurs dynamiques, créations en cours comprises. Ne représente pas la RAM totale de l'hôte. |
| Template `memory-overhead-mib` | 0 | Entier positif ou nul ; 0 calcule `max(512, ceil(ram-max / 4))` Mio hors heap. |
| Core `database.pool.max-size` / `min-idle` | 10 / 2 | Maximum positif ; minimum entre 0 et le maximum. |
| Core `database.connection-timeout-millis` | 30000 | Au moins 250 ms pour obtenir une connexion. |
| Core `database.socket-timeout-millis` | 30000 | Entier positif ; borne les lectures réseau MySQL. |
| Core `database.max-concurrent` | 10 | Entre 1 et la taille maximale du pool. |
| Core `database.queue-capacity` | 100 | Entier positif ; refus asynchrone immédiat au-delà. |
| Core `database.shutdown-timeout-seconds` | 10 | Entier positif ; délai de drainage des tâches SQL avant interruption. |
| Core/Velocity `redis.pool.max-total` / `max-idle` / `min-idle` | 20 / 10 / 2 | `0 <= min-idle <= max-idle <= max-total`, maximum total positif. |
| Core/Velocity `redis.connect-timeout-millis` / `socket-timeout-millis` / `borrow-timeout-millis` | 2000 chacun | Entiers positifs ; connexion, lecture réseau et attente du pool. |

`ram-min` et `ram-max` décrivent toujours le heap Java. La limite Docker vaut `ram-max + marge`. Les variables `MEMORY`, `INIT_MEMORY` et `MAX_MEMORY` sont calculées à partir de ces paramètres, après les autres variables de template, pour éviter une divergence silencieuse. Les anciennes configurations obtiennent la marge automatique. Réserver séparément la mémoire Linux, Docker, Velocity, MySQL, Redis et la marge d'exploitation avant de fixer le budget dynamique : 16384 Mio est un défaut de configuration, pas une recommandation de matériel.

Les clients Redis propres au Lobby et à SheepWars conservent les limites compatibles de RedisOptions : 20 connexions, 10 idle, 2 minimum et délais de 2 secondes. La taille maximale du pool SQL doit être multipliée par le nombre maximal de backends pour vérifier le budget de connexions MySQL.

L'outillage `tools/ops/tropicube_ops.py` requiert Python 3.11 minimum. `TROPICUBE_OPS_STATE` désigne le répertoire privé d'état (défaut `.runtime/ops` du projet, `/var/lib/tropicube-ops` dans les unités systemd). `RESTIC_REPOSITORY` doit être une adresse `sftp:` hors serveur ; `RESTIC_PASSWORD_FILE` désigne le fichier du mot de passe de chiffrement. Copier le modèle `tools/ops/ops.env.example` vers `/etc/tropicube/ops.env` avec des droits 0600. Le compte exécutant le timer doit posséder la clé SSH et une empreinte d'hôte vérifiée ; le mot de passe Restic et la clé de récupération restent également dans un coffre extérieur.

Les services statiques et dynamiques utilisent le pilote Docker `local`, cinq fichiers de 20 Mio au maximum chacun. Ces paramètres n'affectent que les conteneurs recréés. Redis reçoit aussi `REDIS_PASSWORD` dans son environnement afin que l'outil de sauvegarde puisse s'authentifier sans inscrire le mot de passe dans la ligne de commande du client.

### Poste de développement Windows

La configuration Docker livrée fixe `docker.memory-budget-mib` à `8192` Mio pour le poste de développement ; la ressource embarquée conserve `16384` Mio pour les installations autonomes. Cette différence est intentionnelle : adapter la copie Docker à la mémoire réellement disponible avant une installation serveur.

`TROPICUBE_BACKUP_MODE` vaut `off-host` par défaut et impose toujours un dépôt SFTP. La valeur explicite `local-development` autorise uniquement `RESTIC_REPOSITORY=local:<chemin absolu>` hors du dépôt source. Le chiffrement, la vérification et la rétention Restic restent actifs ; le diagnostic indique le mode de la dernière sauvegarde. Une copie locale ne satisfait pas l'objectif de sauvegarde hors hôte.

`tools/ops/setup-windows.ps1` crée `.runtime/windows/settings.json` (ignoré par Git), qui décrit ces variables, `RESTIC_PASSWORD_FILE`, `TROPICUBE_OPS_STATE`, les chemins Python, Docker, Git et le répertoire des outils. Les données et la clé sont placées sous `%LOCALAPPDATA%/Tropicube/ops/<identifiant du dépôt>/`, avec accès limité au compte Windows, à SYSTEM et aux administrateurs. Les relances conservent la clé et les sauvegardes. `windows.ps1` charge ce fichier pour les opérations manuelles, sans modifier l'environnement global Windows.

Le lanceur planifié `windows_task.py` lit le même fichier sous `pythonw.exe` (requis à côté du `python.exe` configuré). Il conserve les journaux et les codes de sortie sans créer de console. Aucune variable supplémentaire n'est nécessaire ; réinstaller les tâches avec `setup-windows.ps1 -InstallTasks` après cette mise à jour.
