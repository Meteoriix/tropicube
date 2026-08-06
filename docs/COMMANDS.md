# Commandes Minecraft et permissions

Les commandes Paper sont disponibles uniquement sur le backend qui héberge le plugin. Les commandes Velocity sont traitées par le proxy et restent disponibles pendant les changements de serveur. Les arguments entre `<...>` sont obligatoires ; ceux entre `[...]` sont facultatifs.

## TropicubeCore — Paper

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/money [joueur]` | `/balance` | aucune pour soi, `tropicube.eco.others` pour autrui | Affiche un solde |
| `/eco top` | — | `tropicube.eco.admin` | Affiche les dix meilleurs soldes |
| `/eco set <joueur> <montant>` | — | `tropicube.eco.admin` | Fixe un solde |
| `/eco add <joueur> <montant>` | — | `tropicube.eco.admin` | Crédite un compte |
| `/eco remove <joueur> <montant>` | — | `tropicube.eco.admin` | Débite un compte si le solde suffit |
| `/rank list` | `/grade` | `tropicube.grade.admin` | Liste les grades |
| `/rank info <joueur>` | `/grade` | `tropicube.grade.admin` | Affiche le grade d'un joueur |
| `/rank set <joueur> <grade>` | `/grade` | `tropicube.grade.admin` | Attribue un grade existant |
| `/lang [fr|en|es|de]` | `/language`, `/langue` | aucune | Affiche ou change la langue |
| `/mute <joueur> <durée> [raison]` | — | `tropicube.mute` | Met un joueur en sourdine |
| `/unmute <joueur>` | — | `tropicube.mute` | Lève la sourdine |
| `/kick <joueur> [raison]` | — | `tropicube.kick` | Expulse et journalise l'action |
| `/warn <joueur> <raison>` | — | `tropicube.warn` | Ajoute un avertissement |
| `/history <joueur>` | — | `tropicube.history` | Affiche l'historique de sanctions |
| `/tropiadmin reload` | `/ca` | `tropicube.admin` | Recharge la configuration Core et les langues |
| `/tropiadmin info` | `/ca` | `tropicube.admin` | Affiche l'état des services Core |

Durées acceptées : une valeur comprise par `DurationParser`, par exemple `30s`, `10m`, `2h`, `7d`. Une permission sans durée est permanente.

### Gestion détaillée des permissions

Toutes les formes ci-dessous exigent `tropicube.admin.perm` :

| Commande | Effet |
|---|---|
| `/tropiperm add <joueur> <permission> [durée]` | Ajoute une permission individuelle |
| `/tropiperm remove <joueur> <permission>` | Retire une permission individuelle |
| `/tropiperm list <joueur>` | Affiche le grade et les permissions effectives stockées |
| `/tropiperm grade add <grade> <permission>` | Ajoute une permission à un grade |
| `/tropiperm grade remove <grade> <permission>` | Retire une permission d'un grade |
| `/tropiperm grade list <grade>` | Liste les permissions d'un grade |

Attention : les permissions de grade présentes dans `TropicubeCore/config.yml` sont resynchronisées au démarrage. Il faut modifier la configuration de déploiement pour rendre un changement durable.

## TropicubeLobby — Paper

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/spawn` | — | aucune | Téléporte au spawn configuré du lobby |
| `/servers` | `/sv`, `/play` | aucune | Ouvre le sélecteur de modes et serveurs |
| `/languages` | — | aucune | Ouvre le sélecteur de langue |
| `/vip` | `/boutique`, `/shop` | aucune | Ouvre la boutique de grades VIP |
| `/flymode` | `/fm` | `tropicube.lobby.fly` | Bascule entre vol permanent et sauts aériens |
| `/playnext` | `/playagain`, `/rejouer` | aucune | Rejoint la partie suivante proposée après un match |
| `/sw join` | — | aucune | Rejoint une partie SheepWars quittée volontairement si elle est encore active |

Permissions fonctionnelles du lobby :

| Permission | Effet | Valeur Paper par défaut |
|---|---|---|
| `tropicube.lobby.build` | Autorise la modification des blocs | `op` |
| `tropicube.lobby.drop` | Autorise le jet d'objets | `op` |
| `tropicube.lobby.pickup` | Autorise le ramassage | `op` |
| `tropicube.lobby.fly` | Vol permanent et `/flymode` | `op` |
| `tropicube.lobby.jump` | Un saut aérien | `false` |
| `tropicube.lobby.jump.double` | Deux sauts aériens | `false` |
| `tropicube.lobby.infinitejump` | Sauts illimités | `op` |

Les grades Core accordent déjà les permissions adaptées : VIP obtient un saut, VIP+ deux, Premium et le personnel des sauts illimités.

## TropicubeSheepwars — Paper

Le plugin ne déclare aucune commande textuelle. La configuration de partie, la sélection de carte, d'équipe, de classe et de kit passent par les inventaires graphiques. Les commandes Core restent disponibles puisque `TropicubeCore` est chargé sur l'instance.

## Tropicube Fallen Kingdoms

Le module est actuellement vide et n'ajoute donc aucune commande ni permission.

## TropicubeVelocity — proxy

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/hub` | `/lobby` | aucune | Transfère vers le lobby disponible le moins chargé |
| `/server [nom]` | — | aucune | Liste les instances ou se connecte à une instance joignable |
| `/queue <serveur>` | `/file` | aucune | Entre dans la file d'une instance pleine ; VIP+ et Premium sont prioritaires |
| `/nick` | — | grade autorisé | Génère un pseudonyme et un skin aléatoires |
| `/nick off` | — | grade autorisé | Restaure l'identité originale |
| `/find <joueur>` | — | `tropicube.admin.find` | Localise un joueur connecté |
| `/send <joueur|*> <serveur>` | — | `tropicube.admin.send` | Transfère un joueur ou tous les joueurs |
| `/tropi ...` | `/tropicube`, `/cm` | `tropicube.admin` | Administration des instances |

Les grades autorisés pour `/nick` sont configurés dans `nick.allowed-grades`; les grades staff sont destinés à y figurer explicitement. Le changement est propagé aux backends par Redis sans déconnexion volontaire du joueur.

### Administration des instances

| Commande | Description |
|---|---|
| `/tropi list` | Liste les instances actives |
| `/tropi templates` | Liste les templates chargés et leur état de maintenance |
| `/tropi start <template> [nom]` | Démarre une instance et attend sa disponibilité |
| `/tropi stop <id|nom>` | Arrête proprement une instance |
| `/tropi kill <id|nom>` | Force l'arrêt et le nettoyage |
| `/tropi info <id|nom>` | Affiche état, ports, capacité, image et conteneur |
| `/tropi maintenance <template> <on|off>` | Interdit ou réautorise les créations sur un template |
| `/tropi reload` | Recharge les templates et la configuration du proxy |

## Permissions réseau principales

Les permissions Paper sont résolues par TropicubeCore à partir du grade et des attributions individuelles :

- `tropicube.bypass.whitelist` : accès aux parties en liste blanche ;
- `tropicube.bypass.spam` : exemption de l'anti-spam du chat ;
- `tropicube.chat.color` : MiniMessage/couleurs autorisés dans le chat ;
- `tropicube.vip`, `tropicube.premium`, `tropicube.staff` : marqueurs fonctionnels de grade.

Velocity possède son propre fournisseur de permissions. Les UUID inscrits dans `admin-uuids` reçoivent exactement `tropicube.admin`, `tropicube.admin.find`, `tropicube.admin.send` et `tropicube.bypass.whitelist`. Les permissions arbitraires de Core ne sont pas automatiquement importées dans le proxy. La priorité de `/queue` et l'accès à `/nick` lisent séparément le grade publié dans Redis.

`OWNER` possède `*` et `ADMIN` possède `tropicube.*` sur Paper. Les valeurs `default: op` de `plugin.yml` restent utiles en environnement local, mais les grades constituent la source normale des autorisations sur les backends.
