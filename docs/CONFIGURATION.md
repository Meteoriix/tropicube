# Configuration

## Sources et ordre d'application

Le projet contient deux catégories de configuration :

1. `src/main/resources` fournit les valeurs par défaut embarquées dans chaque JAR ;
2. `dockerfiles/configs` contient la configuration effectivement copiée dans les images.

TropicubeCore complète automatiquement les fichiers de langue existants avec les nouvelles clés sans écraser les traductions personnalisées. Les scripts de déploiement font la même opération avant la construction des images pour Core et Velocity, avec refus des clés ou sections YAML dupliquées et des structures imbriquées non prises en charge par le mergeur.

Après restauration des interfaces depuis Redis, Core complète aussi chaque catalogue restauré avec les clés du JAR installé, avant son remplacement atomique sur disque. Une génération de l'éditeur antérieure au plugin conserve ses personnalisations sans supprimer les traductions ajoutées depuis. Les manifestes d'interface restent restaurés à l'identique.

Les variables d'environnement ont priorité sur certaines valeurs Core. Dans les conteneurs `itzg`, le mécanisme `REPLACE_ENV_VARIABLES` remplace également les marqueurs `${CFG_...}` présents dans les fichiers copiés.

Les builds Maven et les scripts de déploiement synchronisent les quatre ressources de langue Core et Velocity vers leurs copies sous `dockerfiles/configs`. Les `config.yml` de déploiement restent distincts, car ils contiennent les marqueurs de secrets et l'adresse du proxy Docker.

L'éditeur local utilise `LIBRETRANSLATE_URL` (défaut `http://127.0.0.1:5000`), `LIBRETRANSLATE_API_KEY` facultative, `TROPICUBE_LANGUAGE_EDITOR_PORT` (défaut `8765`), `TROPICUBE_DOCKER_COMMAND` (défaut `docker`) et éventuellement `TROPICUBE_MINECRAFT_CLIENT_JAR` pour les textures Minecraft 26.3. La clé API reste exclusivement dans l'environnement local.

Les fichiers `menus.yml`, `scoreboards.yml` et `tablists.yml` utilisent le schéma versionné `version: 1`. Les menus déclarent titre, nombre de lignes, cadrage, boutons statiques et régions dynamiques ; les scoreboards déclarent un titre et des variantes de 1 à 15 lignes ; les tablists déclarent, pour chaque état, une clé d'en-tête et une clé de pied. Les ressources embarquées et leurs miroirs Docker sont synchronisés au build.

Les tablists du Lobby, de SheepWars et de Fallen Kingdoms partagent un en-tête Tropicube, le nom du contexte courant et `play.tropicube.fr` en pied. Les informations de jeu détaillées restent dans les scoreboards afin d'éviter leur répétition dans la liste des joueurs.

Pour un menu, `frame` vaut `network`, `neutral` ou `none`. Chaque région dynamique déclare des cases uniques et valides pour la taille de l'inventaire ; les boutons fixes peuvent volontairement servir d'état de chargement ou vide à une région. Le registre rejette toute autre valeur de cadre ou toute disposition invalide au démarrage.

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

Ces trois valeurs doivent être strictement positives et le seuil d'expiration doit être supérieur ou égal à l'intervalle. Les instances `STARTING` ne sont pas concernées : leur initialisation dispose séparément de 120 secondes avant nettoyage. Le flux Docker qui observe ce démarrage reste ouvert 150 secondes afin qu'un Paper silencieux pendant son initialisation ne soit pas supprimé prématurément. Une instance prête expirée est retirée de Docker, Velocity et Redis ; le maintien de `min-instances` peut ensuite recréer un lobby ou un serveur classique si nécessaire.

### Templates

Chaque entrée de `templates` décrit :

- `enabled`, `name`, `image`, `type` ;
- plage de ports, `max-players` et `spectator-slots` ;
- mémoire minimale/maximale en Mio ;
- `auto-start`, `min-instances` et `max-instances` ;
- `auto-stop`, `auto-stop-delay` ;
- variables d'environnement passées à l'image Paper.

Les templates Paper fournis fixent `VERSION: "26.3"` et `PAPER_BUILD: "8"`. Ce build alpha explicitement qualifié garantit que le runtime sélectionné au démarrage correspond aux artefacts préchauffés dans les trois Dockerfiles Paper. Toute mise à jour doit modifier ensemble ces valeurs, les arguments par défaut des Dockerfiles et la dépendance Paper du POM parent, puis reconstruire et requalifier les quatre images du lot.

SheepWars utilise trois recettes : `sheepwars` (`GAME_MODE=QUICK_PLAY`, ports 25625–25639), `sheepwars-ranked-4v4` (25640–25649) et `sheepwars-ranked-8v8` (25650–25659). Les deux templates classés sont créés seulement lorsqu'un groupe compatible atteint respectivement 8 ou 16 joueurs. `matchmaking.ranked.initial-rating-range`, `growth-per-step`, `step-seconds` et `maximum-rating-range` règlent l'élargissement progressif de leur fenêtre de cote.

La catégorie `BETA` publie `fallenkingdoms-beta-1v1` sur les ports 25670–25679 et `fallenkingdoms-beta-2v2` sur 25680–25689. Ces templates démarrent à la demande et partagent l'image ainsi que la carte Cactus de Fallen Kingdoms. Le premier exige au moins un joueur dans chacun d'au moins deux royaumes et démarre donc dès 2 joueurs ; le second exige au moins deux joueurs par royaume et démarre dès 4 joueurs. Les deux conservent la capacité publique de six joueurs par royaume et peuvent employer de deux à cinq royaumes, jusqu'à 30 participants. Aucun plancher d'instance n'est maintenu pour ces files expérimentales.

Les fichiers `dockerfiles/configs/bukkit.yml` et `dockerfiles/configs/paper-world-defaults.yml` sont embarqués dans chaque image de backend. Le second fixe seulement la version du schéma et laisse Paper appliquer ses valeurs par défaut aux options absentes. Ils évitent les téléchargements de configurations réalisés par l'entrypoint sur chaque volume neuf ; leur version doit donc être vérifiée lors d'une migration Paper.

Le lobby est une dépendance de routage essentielle et reste activé. Pour ajouter un mode de jeu, fournir une image, un plugin capable de publier son état, une plage de ports et une entrée correspondante dans `server-types` du lobby.

`max-players` borne les participants avant le démarrage. `spectator-slots`, nul par défaut et fixé à `8` pour SheepWars, ajoute une réserve physique uniquement quand l'instance est `GAME_PLAYING`. La variable Docker `MAX_PLAYERS` reçoit la somme afin que `/friend join` puisse connecter un spectateur sans réduire la capacité de jeu nominale.

`min-instances` est le plancher créé dès l'initialisation de Velocity puis maintenu toutes les 30 secondes par l'autoscaler. Les ressources embarquée et Docker utilisent un plancher de `1` pour Lobby et de `0` pour SheepWars Quick Play et Fallen Kingdoms Quick Play : aucun serveur de jeu ne démarre donc avec le proxy. Le matchmaking crée une instance à la demande lorsqu'aucun serveur compatible en attente ou en démarrage n'est encore joignable. `max-instances` borne toutes les créations simultanées, qu'elles viennent du matchmaking, d'une partie personnalisée ou de `/tropi start`.

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
- `announcements.interval-seconds` et `announcements.entries[].message-key/target` définissent la rotation localisée. Une cible vaut `network`, un type, un template ou une instance. La liste livrée est vide : le message `proxy.announcement-welcome` reste traduisible pour `/announce`, mais n'est plus diffusé périodiquement.
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

Les deux plugins sont installés uniquement sur Velocity. `Dockerfile.velocity` télécharge les artefacts officiels Geyser-Velocity `2.11.3-b1245` et Floodgate-Velocity `2.2.5-b140`, avec URL de build et SHA-256 épinglés. Aucun JAR tiers n'est versionné. Avant une mise à jour, vérifier la prise en charge de Java `26.3` et des versions Bedrock courantes, puis modifier ensemble URL, empreinte, commentaires de configuration, tests et documentation.

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

- `economy` : nom, symbole, solde initial et bornes de transfert. Le solde d'un joueur est plafonné à 100 milliards de TropiCoins ; les affichages utilisent les suffixes `K`, `M` et `B` ;
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
- `locations` décrit le lobby, la limite du vide et les cartes activées. Chaque carte accepte `hazards.void-kill-enabled` ainsi que `hazards.water-poison.enabled`, `duration-ticks` et `amplifier` ; les anciennes cartes conservent par défaut la mort sous `void_limit` et aucun poison aquatique. Galions désactive cette mort et applique Poison I pendant l'immersion. Une carte peut aussi fournir un nombre quelconque de centres candidats numérotés `powerups.target1`, `target2`, etc. ; un seul est actif et la réapparition évite le candidat précédent. Sans centre, la carte reste jouable mais n'affiche aucune cible ;
- chaque carte doit fournir des points d'apparition utilisables pour les équipes rouge et bleue.

Les types désactivés sont exclus avant normalisation. Le menu affiche le pourcentage effectif, reconstruit immédiatement le sélecteur pondéré après une modification et interdit de ramener à zéro le dernier poids actif. Chaque remise est un tirage indépendant utilisant exactement ces probabilités. Une échéance rencontrant le plafond de stock reste due et est retentée chaque seconde ; le délai complet redémarre uniquement après insertion réussie. Si tous les poids activés proviennent néanmoins d'une configuration externe à zéro, le premier type actif devient le secours visible à 100 %. Une configuration qui désactive tous les types est réparée au chargement en réactivant TNT avec un avertissement.

La section `gameplay-balance` est validée au démarrage. Une valeur manquante, non numérique, négative, ou nulle lorsqu'un rayon, une durée ou une cadence doit être strictement positif empêche le plugin de démarrer avec un message indiquant la clé fautive. `ConfigUpdater` complète les anciennes configurations avant cette validation.

## Tropicube Fallen Kingdoms

Le fichier `dockerfiles/configs/TropicubeFallenKingdoms/config.yml` déclare la carte `cactus`. Sa zone jouable est comprise entre `(-947, 0, -898)` et `(-468, 150, -433)`. Les cinq bases, cœurs et spawns sont configurés pour les royaumes bleu, rouge, vert, jaune et orange. La bordure initiale de `490` blocs est centrée en `(-702, -653)` : elle couvre la distance maximale de 245 blocs entre le centre et la zone jouable. La bordure de mort subite reste fixée à 50 blocs.

`game.default-map` choisit la carte locale et la variable d'instance `MAP_ID` la remplace en production. Chaque entrée activée sous `locations.maps` fournit sa propre traduction, région jouable, bordure, agencements et bases : ajouter Apocalypse ou Yeti ne demande donc aucun changement Java. Une entrée `layouts.<nombre>` contient le pool de couleurs éligibles pour ce format et doit compter au moins `<nombre>` bases distinctes. L'allocateur choisit dans ce pool la combinaison qui conserve le plus de préférences, puis équilibre les tailles ; les solutions équivalentes sont départagées aléatoirement. Le démarrage est refusé si le format correspondant à l'effectif manque, si deux bases se chevauchent ou si une position sort des régions déclarées.

`game.auto-start`, les quatre échéances de phase, la vie des cœurs, le délai de réapparition, les matériaux interdits, la bordure finale, les paramètres de ruine et tous les objets de kits sont validés au chargement. Le profil public ouvre le JcJ à 10 minutes, l'assaut à 20 minutes, la mort subite à 40 minutes et force le résultat à 60 minutes. Le Lobby propose ces quatre défauts, dont l'option exacte de 40 minutes. `world-cycle.day-duration-seconds` et `night-duration-seconds` valent chacun `300`. `spawns.natural-hostile-night-retention` vaut `0.50`. `drops.flint-base-chance` vaut `0.25` et `creeper-gunpowder-multiplier` vaut `2.0`. `protections.enemy-base-barrier` rend toutes les cinq ticks, à 32 blocs, une grille espacée de deux blocs dans un rayon vertical de huit blocs. `protections.forbidden-placement-materials` réserve les blocs techniques ; les blocs ordinaires sont autorisés en zone commune et en base alliée, tandis que seule la TNT est posable en base ennemie pendant l'assaut. Les kits se trouvent sous `kits.definitions`; `kits.default` doit référencer un kit activé et le kit `alchemist` fournit l'atelier de brassage approuvé.

`heart-alert` règle le son, son volume et sa hauteur, le délai sonore, la durée totale et la cadence du clignotement blanc/rouge après un dégât de cœur appliqué. Les valeurs livrées sont `minecraft:entity.blaze.hurt`, `1.0`, `1.0`, `40`, `120` et `10` ticks. `progressive-loot.rolls-per-chest` vaut `3`; `progressive-loot.tables` déclare une table pondérée validée pour chaque jour 2 à 6. Chaque entrée fournit `material`, `min`, `max` et `weight`. La carte Cactus déclare dix `loot-chests` aux positions `(-686,70,-670)`, `(-705,71,-686)`, `(-721,71,-675)`, `(-728,70,-656)`, `(-732,70,-630)`, `(-716,70,-620)`, `(-694,70,-616)`, `(-679,71,-627)`, `(-674,71,-651)` et `(-698,71,-652)`. Toutes doivent pointer vers des coffres préplacés uniques, dans la zone jouable et hors des bases.

La salle d'attente est située en `(-712, 59, -653)`, orientée à `-90°`. Les trois templates Fallen Kingdoms utilisent une distance de rendu de 12 chunks et conservent une distance de simulation de 5 chunks.

Les variables internes `FK_MIN_PLAYERS_PER_KINGDOM`, `FK_MAX_PLAYERS_PER_KINGDOM` et `FK_MAX_KINGDOMS` permettent aux templates bêta d'abaisser de manière contrôlée la taille des royaumes. Sans surcharge, le Quick Play accepte 3–6 joueurs par royaume et 2–5 royaumes, soit un départ à six joueurs. Les instances hôte conservent automatiquement le minimum historique de quatre joueurs par royaume. Les files bêta restent respectivement à un et deux joueurs minimum par royaume.

Une partie personnalisée passe par l'écran de réglages FK du Lobby. Velocity accepte uniquement `FK_AUTO_START`, `FK_COMBAT_PROFILE`, `FK_COUNTDOWN_SECONDS`, `FK_MAX_PLAYERS_PER_KINGDOM`, `FK_MAX_KINGDOMS`, `FK_PVP_AT_SECONDS`, `FK_ASSAULT_AT_SECONDS`, `FK_SUDDEN_DEATH_AT_SECONDS`, `FK_FORCE_END_AT_SECONDS`, `FK_HEART_HEALTH`, `FK_RESPAWN_DELAY_SECONDS`, `FK_ENABLED_KITS`, `FK_RUIN_WAVES`, `FK_RUIN_RADIUS` et `FK_RUIN_DESTRUCTION_RATIO`. Le proxy refuse toute autre clé ou valeur contenant des caractères de commande. Le plugin revalide ensuite les bornes, l'ordre des phases, les kits et les protections au démarrage. Une instance hôte désactive le démarrage automatique par défaut ; son propriétaire peut utiliser `status`, `start` et `cancel`.

`scoreboards.yml` et `tablists.yml` définissent les variantes `waiting`, `countdown`, `active` et `ending`. Le scoreboard actif affiche le jour 1 à 6, la phase actuelle, la prochaine échéance et uniquement les équipes affectées, sans PV de cœur. La tablist et les équipes personnelles de scoreboard colorent les pseudonymes selon la préférence puis l'affectation. Pendant une phase active, l'actionbar affiche les PV actuels et maximaux du cœur allié ; une alerte de réapparition ou de territoire prend temporairement la priorité sur l'alerte de cœur. Ces ressources rejoignent le bundle UI de Core et se rechargent avec les langues. La migration `V011__fallenkingdoms_preferences.sql` conserve le dernier kit et la dernière couleur préférée de chaque joueur.

La même configuration est embarquée dans le module. Toute modification doit être effectuée dans les deux fichiers jusqu'à l'ajout de la synchronisation automatique des ressources FK.

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

Les versions Minecraft 26.3, Java 25 et Maven 3.9.11 constituent l'environnement de référence. Les nouvelles options sont lues au démarrage ; un changement nécessite la recréation du backend ou du proxy concerné.

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

Le lot d'infrastructure épingle Redis `8.10.1-alpine`, MySQL `9.7.2`, Adminer `5.5.1`, LibreTranslate `1.9.6` et docker-socket-proxy `0.3` avec leurs digests. Redis Commander utilise désormais l'image maintenue `ghcr.io/joeferner/redis-commander`, également épinglée par digest. Les tags ne sont jamais suffisants seuls : une mise à jour d'image doit actualiser le digest, Compose, la pile d'intégration lorsqu'elle emploie le même service, puis passer sauvegarde, restauration et healthchecks.

### Poste de développement Windows

La configuration Docker livrée fixe `docker.memory-budget-mib` à `8192` Mio pour le poste de développement ; la ressource embarquée conserve `16384` Mio pour les installations autonomes. Seul Lobby réserve 2560 Mio au démarrage, marge native comprise. SheepWars et Fallen Kingdoms conservent `min-instances: 0` et réservent leur mémoire à la création d'une partie. Le budget local permet ainsi de démarrer à la demande plusieurs instances de jeu dans la limite de leurs enveloppes et de `max-instances`.

`TROPICUBE_BACKUP_MODE` vaut `off-host` par défaut et impose toujours un dépôt SFTP. La valeur explicite `local-development` autorise uniquement `RESTIC_REPOSITORY=local:<chemin absolu>` hors du dépôt source. Le chiffrement, la vérification et la rétention Restic restent actifs ; le diagnostic indique le mode de la dernière sauvegarde. Une copie locale ne satisfait pas l'objectif de sauvegarde hors hôte.

`tools/ops/setup-windows.ps1` crée `.runtime/windows/settings.json` (ignoré par Git), qui décrit ces variables, `RESTIC_PASSWORD_FILE`, `TROPICUBE_OPS_STATE`, les chemins Python, Docker, Git et le répertoire des outils. Les données et la clé sont placées sous `%LOCALAPPDATA%/Tropicube/ops/<identifiant du dépôt>/`, avec accès limité au compte Windows, à SYSTEM et aux administrateurs. Les relances conservent la clé et les sauvegardes. `windows.ps1` charge ce fichier pour les opérations manuelles, sans modifier l'environnement global Windows.

Le lanceur planifié `windows_task.py` lit le même fichier sous `pythonw.exe` (requis à côté du `python.exe` configuré). Il conserve les journaux et les codes de sortie sans créer de console. Aucune variable supplémentaire n'est nécessaire ; réinstaller les tâches avec `setup-windows.ps1 -InstallTasks` après cette mise à jour.

## Catalogue de personnalisation

Core charge `cosmetics.yml` au démarrage, hors du thread Paper pendant son initialisation. Le fichier est livré aussi sous `dockerfiles/configs/TropicubeCore/`. `version` doit être l'entier `1`, `entries` contient de 1 à 100 entrées aux identifiants uniques `[a-z][a-z0-9-]{0,47}`. Chaque entrée définit `category` (`TRAIL` ou `SOUND`), `access` (`FREE`, `LEVEL`, `CURRENCY`, `VIP`), `requirement` entier et `effect` non vide. `FREE` exige 0, les autres règles un entier positif et `VIP` au plus 3. Une valeur textuelle ou fractionnaire n'est pas convertie silencieusement. Chaque identifiant possède un libellé `cosmetics.name-<id>` dans les quatre langues.

Le catalogue initial comprend Brise gratuite, Étincelles niveau 5, Lucioles 500, Cœurs VIP 1, Carillon gratuit, Xylophone niveau 3, Cristal 300 et Mélodie tropicale VIP 2. La Progression n'affiche que les seuils de niveau restant à atteindre. La courbe réseau et les récompenses de missions ne changent pas.

### Rendu Lobby

| Clé `cosmetics.*` dans `TropicubeLobby/config.yml` | Défaut | Bornes |
|---|---|---|
| `render-interval-ticks` | 5 | entier 1–100 |
| `particles-per-emission` | 2 | entier 1–20, par destinataire |
| `preview-seconds` | 5 | entier 1–15, aperçu privé de traînée |
| `range-blocks` | 24 | entier 1–24, rayon maximal |

Les anciens fichiers reçoivent les défauts via la mise à jour habituelle. Les nombres fractionnaires et chaînes sont refusés. Le catalogue accepte uniquement des particules sans données additionnelles pour TRAIL et des clés de sons présentes dans le registre Paper pour SOUND ; Lobby valide les identifiants au démarrage. Les aperçus sonores jouent une fois, avec un intervalle minimal d'une seconde entre lectures. Les menus du vestiaire nécessitent six lignes pour conserver leurs actions et boutons communs. La préférence existante `lobbyEffectsEnabled` contrôle aussi les nouveaux effets.

### Catalogue initial et achats

| Catégorie | Identifiant stable / libellé FR | Accès | Rendu |
|---|---|---|---|
| Traînée | `breeze` / Brise | gratuit | CLOUD |
| Traînée | `sparks` / Étincelles | niveau réseau 5 | CRIT |
| Traînée | `fireflies` / Lucioles | 500 unités | END_ROD |
| Traînée | `hearts` / Cœurs | VIP ≥ 1 | HEART |
| Son | `chime` / Carillon | gratuit | minecraft:block.note_block.chime |
| Son | `xylophone` / Xylophone | niveau réseau 3 | minecraft:block.note_block.xylophone |
| Son | `crystal` / Cristal | 300 unités | minecraft:block.amethyst_block.chime |
| Son | `tropical` / Mélodie tropicale | VIP ≥ 2 | minecraft:block.note_block.bell |

Les prix représentent plusieurs missions quotidiennes de 35 à 50 unités et restent inférieurs au premier grade à 5 000. Un changement de catalogue nécessite un redémarrage de Core et Lobby dans le même lot. Les noms doivent exister dans les quatre langues locales : le démarrage refuse une entrée sans libellé. Garder les identifiants stables ; retirer une entrée suspend son rendu sans effacer les anciennes acquisitions/sélections. Le déséquipement de la catégorie reste possible même après ce retrait. Aucun nouveau pack, plugin tiers, commande ou variable d'environnement n'est requis.
