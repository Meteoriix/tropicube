# Configuration

## Sources et ordre d'application

Le projet contient deux catégories de configuration :

1. `src/main/resources` fournit les valeurs par défaut embarquées dans chaque JAR ;
2. `dockerfiles/configs` contient la configuration effectivement copiée dans les images.

TropicubeCore complète automatiquement les fichiers de langue existants avec les nouvelles clés sans écraser les traductions personnalisées. Les scripts de déploiement font la même opération avant la construction des images pour Core et Velocity, avec refus des clés ou sections YAML dupliquées et des structures imbriquées non prises en charge par le mergeur.

Les variables d'environnement ont priorité sur certaines valeurs Core. Dans les conteneurs `itzg`, le mécanisme `REPLACE_ENV_VARIABLES` remplace également les marqueurs `${CFG_...}` présents dans les fichiers copiés.

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
| `DOCKER_SOCKET_PATH` | Socket Docker de l'hôte, y compris rootless |

Génération recommandée d'un secret sous PowerShell :

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLower()
```

Sous Linux :

```bash
openssl rand -hex 32
```

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

### Templates

Chaque entrée de `templates` décrit :

- `enabled`, `name`, `image`, `type` ;
- plage de ports et `max-players` ;
- mémoire minimale/maximale en Mio ;
- `auto-start`, `min-instances` ;
- `auto-stop`, `auto-stop-delay` ;
- variables d'environnement passées à l'image Paper.

Le lobby est une dépendance de routage essentielle et reste activé. Pour ajouter un mode de jeu, fournir une image, un plugin capable de publier son état, une plage de ports et une entrée correspondante dans `server-types` du lobby.

`shutdown.stop-dynamic-servers` doit normalement rester à `false`. Le passer à `true` signifie qu'un arrêt de Velocity doit également arrêter toutes les instances dynamiques.

### Nick et administration

- `admin-uuids` donne `tropicube.admin`, `tropicube.admin.find`,
  `tropicube.admin.send` et `tropicube.bypass.whitelist` aux UUID approuvés ;
- `nick.allowed-grades` contrôle les grades pouvant utiliser `/nick` ;
- `nick.skin-uuids` complète le pool de profils Mojang utilisés comme skins.

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
- `language.default` : langue utilisée avant chargement du profil ;
- `grades` : présentation MiniMessage, priorité, statut VIP/staff et permissions.

Les noms de grades sont utilisés comme identifiants stables dans la boutique, le nick et les permissions. Une modification doit donc être répercutée dans tous les fichiers concernés.

## TropicubeLobby

Fichier : `dockerfiles/configs/TropicubeLobby/config.yml`.

- `lobby.spawn` définit monde, coordonnées et orientation ;
- `lobby.double-jump` active globalement les sauts aériens ;
- `server-types` définit les icônes Material ou HeadDatabase ;
- `vip-shop.entries` associe grade, icône, nom et prix ;
- `lang-selector.languages` configure codes, têtes et textes de présentation.

Le `grade-key` d'une entrée VIP doit exister dans Core. Le prix doit être positif et son avantage traduit sous la clé `lobby.vip-perks-<grade>` dans les quatre langues.

## TropicubeSheepwars

Fichier : `dockerfiles/configs/TropicubeSheepwars/config.yml`.

- `default-settings` fixe capacité, démarrage, durées, kits, vote et fréquence des moutons ;
- `default-settings.auto-start` vaut `true` par défaut pour les parties classiques et lance le compte à rebours dès que `min-players` est atteint ;
- `custom-game-default-settings.auto-start` vaut `false` par défaut et remplace cette valeur à l'initialisation d'une instance possédant un `HOST_UUID` ; l'hôte peut ensuite la modifier pour la partie courante ;
- `sheep-probabilities` contient des poids relatifs, pas nécessairement un total de 100 ;
- `force-settings` désactive des classes, kits ou moutons ;
- `locations` décrit le lobby, la limite du vide et les cartes activées ;
- chaque carte doit fournir des points d'apparition utilisables pour les équipes rouge et bleue.

Si tous les poids activés valent zéro, le gestionnaire sélectionne un type de repli afin de conserver une distribution valide.

## Langues

Core et Velocity prennent en charge `fr`, `en`, `es` et `de`. Les textes utilisent MiniMessage et les paramètres positionnels `{0}`, `{1}`, etc. Pour ajouter une clé :

1. l'ajouter dans les quatre ressources du module ;
2. ajouter les traductions correspondantes sous `dockerfiles/configs` ;
3. lancer `mvn test` pour vérifier les ressources Core ;
4. exécuter un déploiement ou `--validate-only` pour valider le merge.

Éviter les clés YAML dupliquées. Le merge de déploiement accepte les sections de premier niveau et leurs feuilles indentées de deux espaces ; une structure plus profonde doit être migrée explicitement plutôt qu'ignorée silencieusement.
