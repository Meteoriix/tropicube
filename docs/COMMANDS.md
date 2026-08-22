# Commandes Minecraft et permissions

Les commandes Paper sont disponibles uniquement sur le backend qui héberge le plugin. Les commandes Velocity sont traitées par le proxy et restent disponibles pendant les changements de serveur. Les arguments entre `<...>` sont obligatoires ; ceux entre `[...]` sont facultatifs.

## TropicubeCore — Paper

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/money` | `/balance` | aucune | Affiche uniquement son propre solde |
| `/eco top` | — | `tropicube.eco.admin` | Affiche les dix meilleurs soldes |
| `/eco set <joueur> <montant>` | — | `tropicube.eco.admin` | Fixe un solde |
| `/eco add <joueur> <montant>` | — | `tropicube.eco.admin` | Crédite un compte |
| `/eco remove <joueur> <montant>` | — | `tropicube.eco.admin` | Débite un compte si le solde suffit |
| `/rank list` | `/grade` | `tropicube.grade.admin` | Liste les grades |
| `/rank info <joueur>` | `/grade` | `tropicube.grade.admin` | Affiche le grade d'un joueur |
| `/rank set <joueur> <grade>` | `/grade` | `tropicube.grade.admin` | Attribue un grade existant |
| `/lang [fr|en|es|de]` | `/language`, `/langue` | aucune | Affiche ou change la langue |
| `/help [general|games|profile|staff]` | — | aucune ; catégorie staff réservée au personnel | Résume les commandes disponibles par catégorie |
| `/friend add|accept|deny|remove <joueur>` | — | aucune | Gère les relations d'amitié persistantes |
| `/friend list|requests` | — | aucune | Liste les amis ou les demandes reçues |
| `/friend join <joueur>` | — | aucune | Rejoint l'instance d'un ami ; devient spectateur si la partie a commencé |
| `/party invite|accept|deny <joueur>` | — | aucune | Crée ou rejoint une party réseau |
| `/party list|leave|kick|promote|disband` | — | aucune | Consulte ou administre la party ; certaines actions sont réservées au chef |
| `/party follow on|off` | — | aucune | Active ou désactive le suivi personnel du chef |
| `/party warp` | `/party tp` | aucune | Le chef transfère vers son serveur tous les membres dont le suivi est actif |
| `/party chat <message>` | `/pc <message>` | aucune | Envoie un message à la party |
| `/mute <joueur> <durée> [raison]` | — | `tropicube.mute` | Met un joueur en sourdine |
| `/unmute <joueur>` | — | `tropicube.mute` | Lève la sourdine |
| `/kick <joueur> [raison]` | — | `tropicube.kick` | Expulse et journalise l'action |
| `/warn <joueur> <raison>` | — | `tropicube.warn` | Ajoute un avertissement |
| `/history <joueur>` | — | `tropicube.history` | Affiche l'historique de sanctions |
| `/coreadmin reload` | `/tropiadmin`, `/ca` | `tropicube.admin` | Recharge la configuration Core et les langues |
| `/coreadmin info` | `/tropiadmin`, `/ca` | `tropicube.admin` | Affiche l'état des services Core |

Durées acceptées : une valeur comprise par `DurationParser`, par exemple `30s`, `10m`, `2h`, `7d`. Une permission sans durée est permanente.

Les amis sont persistants en MySQL. Une party est temporaire dans Redis, limitée par la configuration Core et créée lors de la première invitation. Accepter une invitation depuis une autre party retire atomiquement le joueur de l'ancienne ; s'il en était chef, un autre membre est promu, ou la party vide est dissoute. Le suivi est activé par défaut à l'entrée, puis reste un choix individuel. Les transferts sociaux respectent la whitelist et la capacité de l'instance cible.

### Gestion détaillée des permissions

Toutes les formes ci-dessous exigent `tropicube.admin.perm` :

| Commande | Effet |
|---|---|
| `/permissions add <joueur> <permission> [durée]` | Ajoute une permission individuelle |
| `/permissions remove <joueur> <permission>` | Retire une permission individuelle |
| `/permissions list <joueur>` | Affiche le grade et les permissions effectives stockées |
| `/permissions grade add <grade> <permission>` | Ajoute une permission à un grade |
| `/permissions grade remove <grade> <permission>` | Retire une permission d'un grade |
| `/permissions grade list <grade>` | Liste les permissions d'un grade |

Attention : les permissions de grade présentes dans `TropicubeCore/config.yml` sont resynchronisées au démarrage. Il faut modifier la configuration de déploiement pour rendre un changement durable.

## TropicubeLobby — Paper

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/spawn` | — | aucune | Téléporte au spawn configuré du lobby |
| `/play` | `/servers`, `/sv` | aucune | Ouvre le sélecteur de modes et serveurs |
| `/languages` | — | aucune | Ouvre le sélecteur de langue |
| `/vip` | `/boutique`, `/shop` | aucune | Ouvre la boutique de grades VIP |
| `/fly` | `/flymode`, `/fm` | `tropicube.lobby.fly` | Bascule entre vol permanent et sauts aériens |
| `/replay` | `/playnext`, `/playagain`, `/rejouer` | aucune | Rejoint la partie suivante ou attend sa création avec connexion automatique |
| `/replayconfirm` | — | aucune | Confirme une nouvelle série de cinq replays automatiques |
| `/rejoin` | — | aucune | Rejoint une partie SheepWars quittée volontairement si elle est encore active ; aucun argument n'est accepté |

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

Après la saisie de `/`, le client reçoit uniquement les commandes et alias fournis par l'infrastructure Tropicube. Les commandes externes et leurs espaces de noms restent masqués ; `/?`, les espaces de noms `bukkit:` et `minecraft:` ainsi que les commandes vanilla sont refusés sur l'ensemble du réseau.

## TropicubeSheepwars — Paper

Le plugin ne déclare aucune commande textuelle. La configuration de partie, la sélection de carte, d'équipe, de classe et de kit passent par les inventaires graphiques. Les commandes Core restent disponibles puisque `TropicubeCore` est chargé sur l'instance.

## Tropicube Fallen Kingdoms

Le module est actuellement vide et n'ajoute donc aucune commande ni permission.

## TropicubeVelocity — proxy

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/lobby` | `/hub` | aucune | Transfère vers le lobby disponible le moins chargé |
| `/whitelist add <joueur>` | — | hôte d'une partie privée | Ajoute un joueur connu à la partie personnalisée privée |
| `/whitelist remove <joueur>` | — | hôte d'une partie privée | Retire un joueur de la partie, sauf l'hôte lui-même |
| `/server [nom]` | — | aucune | Liste les instances ou se connecte à une instance joignable |
| `/queue <serveur>` | `/file` | aucune | Entre dans la file d'une instance pleine ; VIP+ et Premium sont prioritaires |
| `/nick` | — | grade autorisé | Génère un pseudonyme et un skin aléatoires |
| `/nick off` | — | aucune | Restaure l'identité originale, même après une perte de grade |
| `/find <joueur>` | — | `tropicube.admin.find` | Localise un joueur connecté |
| `/send <joueur|*> <serveur>` | — | `tropicube.admin.send` | Transfère un joueur ou tous les joueurs |
| `/pull <joueur>` | — | `tropicube.admin.pull` | Fait venir un joueur sur le serveur actuel, si celui-ci est joignable |
| `/tropicube ...` | `/tropi`, `/cm` | `tropicube.admin` | Administration des instances |
| `/maintenance <network\|type> <on\|off\|status> [minutes] [motif]` | — | `tropicube.admin.maintenance` | Active un drain réseau ou par type avec échéance |
| `/announce <network\|type\|instance> <clé.langue>` | — | `tropicube.admin.announce` | Diffuse une annonce localisée configurée |
| `/networkdiag` | `/netdiag` | `tropicube.admin.diagnostic` | Affiche l'état Redis, les instances et la protection des connexions |

Les grades autorisés à activer `/nick` sont configurés dans `nick.allowed-grades`; les grades staff sont destinés à y figurer explicitement. `/nick off` reste toujours accessible, annule aussi une génération encore en attente et peut être rejoué si un backend n'a pas encore restauré le profil. Le changement est propagé aux backends par Redis sans déconnexion volontaire du joueur ; l'identité et le profil original restent disponibles jusqu'à la restauration effective afin qu'une perte d'événement ne bloque jamais le joueur. Deux générations simultanées pour le même joueur sont refusées. L'identité Redis conserve également le grade d'affichage factice `PREMIUM` pendant 24 heures après une déconnexion et le restaure dans la tablist à la reconnexion, sans modifier les permissions réelles.

`/whitelist` est validée par Velocity : l'émetteur doit posséder une partie personnalisée active et privée. Un pseudo est résolu parmi les joueurs actuellement ou précédemment vus par le proxy ; un UUID est aussi accepté. L'item de whitelist remis à l'hôte dans la hotbar SheepWars utilise le même flux proxy.

### Administration des instances

| Commande | Description |
|---|---|
| `/tropi list` | Liste les instances actives |
| `/tropi templates` | Liste les templates chargés et leur état de maintenance |
| `/tropi start <template> [nom]` | Démarre une instance et attend sa disponibilité |
| `/tropi stop <id|nom>` | Arrête proprement une instance, confirme son état Docker puis supprime son conteneur et son volume de données éphémère |
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

Velocity possède son propre fournisseur de permissions. Les UUID inscrits dans `admin-uuids` reçoivent exactement `tropicube.admin`, `tropicube.admin.find`, `tropicube.admin.send`, `tropicube.admin.pull` et `tropicube.bypass.whitelist`. Les permissions arbitraires de Core ne sont pas automatiquement importées dans le proxy. La priorité de `/queue` et l'accès à `/nick` lisent séparément le grade publié dans Redis.

`OWNER` possède `*` et `ADMIN` possède `tropicube.*` sur Paper. Les valeurs `default: op` de `plugin.yml` restent utiles en environnement local, mais les grades constituent la source normale des autorisations sur les backends.
