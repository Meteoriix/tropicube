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
| `/rank list` | `/grade` | `modLevel ≥ 3` | Liste les grades cosmétiques et leurs niveaux par défaut |
| `/rank info <joueur>` | `/grade` | `modLevel ≥ 3` | Affiche le grade et ses niveaux par défaut |
| `/rank set <joueur> <grade> [durée]` | `/grade` | `modLevel ≥ 3` | Attribue un grade et remplace les deux niveaux du joueur |
| `/level <joueur>` | — | `modLevel ≥ 3` | Affiche le grade cosmétique, le `vipLevel` et le `modLevel` |
| `/level <joueur> <vip\|mod> <niveau>` | — | `modLevel ≥ 3` | Modifie définitivement un niveau, y compris hors ligne |
| `/lang [fr|en|es|de]` | `/language`, `/langue` | aucune | Affiche ou change la langue |
| `/help [general|games|social|profile|staff]` | — | aucune ; catégorie staff réservée au personnel | Présente toutes les commandes et fonctionnalités disponibles par catégorie |
| `/friend add|accept|deny|cancel|remove <joueur>` | — | aucune | Gère les relations d'amitié persistantes et permet d'annuler une demande envoyée |
| `/friend list|requests` | — | aucune | Liste les amis ou les demandes reçues |
| `/friend join <joueur>` | — | aucune | Rejoint l'instance d'un ami ; devient spectateur si la partie a commencé |
| `/party invite|accept|deny <joueur>` | — | aucune | Crée ou rejoint une party réseau |
| `/party list|leave|kick|promote|disband` | — | aucune | Consulte ou administre la party ; certaines actions sont réservées au chef |
| `/party follow on|off` | — | aucune | Active ou désactive le suivi personnel du chef |
| `/party warp [joueur]` | `/party tp` | chef de party | Sans joueur, transfère les membres dont le suivi est actif ; avec un joueur, déplace uniquement ce membre vers le serveur du chef |
| `/party chat <message>` | `/pc <message>` | aucune | Envoie un message à la party |
| `/mute <joueur> <durée> [raison]` | — | `tropicube.mute` | Met un joueur en sourdine |
| `/unmute <joueur>` | — | `tropicube.mute` | Lève la sourdine |
| `/kick <joueur> [raison]` | — | `tropicube.kick` | Expulse et journalise l'action |
| `/warn <joueur> <raison>` | — | `tropicube.warn` | Ajoute un avertissement |
| `/history <joueur>` | — | `tropicube.history` | Affiche l'historique de sanctions |
| `/ban <joueur> [raison]` | — | `tropicube.ban` + session TOTP | Bannit du réseau et expulse immédiatement |
| `/tempban <joueur> <durée> [raison]` | — | `tropicube.ban` + session TOTP | Bannit temporairement du réseau |
| `/unban <joueur>` | — | `tropicube.ban` + session TOTP | Lève un bannissement réseau |
| `/report <joueur> <chat\|cheat\|behavior\|other> [#message] [détails]` | — | aucune | Crée un signalement et joint, si indiqué, une preuve de chat encore disponible |
| `/reports list\|claim\|resolve ...` | — | `tropicube.reports.manage` + session TOTP | Traite la file de signalements |
| `/msg <joueur> <message>` | `/tell`, `/w` | aucune | Message privé interserveurs, conservé sept jours hors ligne |
| `/reply <message>` | `/r` | aucune | Répond au dernier interlocuteur |
| `/ignore <joueur>` | — | aucune | Active ou désactive l'ignorance persistante |
| `/globalchat <message>` | `/g` | aucune | Envoie explicitement un message global |
| `/profile [joueur]` | `/profil` | aucune | Sans argument, ouvre l'accueil Profil ; avec un joueur, affiche son profil selon sa visibilité |
| `/profile title <id>` | `/profil` | aucune | Sélectionne un titre saisonnier débloqué ; le centre joueur permet aussi de cliquer dessus |
| `/settings profile\|messages\|global\|entities\|hints\|effects <valeur>` | `/preferences` | aucune | Modifie les préférences persistantes, dont l'accueil immersif du lobby |
| `/missions` | — | aucune | Affiche les 5 missions quotidiennes et 3 hebdomadaires personnelles |
| `/missions reroll <1..5>` | — | aucune | Remplace une mission quotidienne (2/jour, 4 avec la permission bonus) |
| `/missions claim <daily\|weekly> <slot>` | — | aucune | Réclame monnaie et XP réseau d'une mission terminée |
| `/notifications [read <id>]` | `/inbox` | aucune | Consulte le centre de notifications ou marque une entrée comme lue |
| `/center` | `/centre` | aucune | Ouvre le centre joueur et sa boîte de notifications paginée |
| `/guild info` | `/guilde` | aucune | Affiche membres, rôles, niveau, contributions et défis hebdomadaires |
| `/guild create <nom> <tag>` | `/guilde` | aucune | Crée une guilde dont la capacité initiale est 50 membres |
| `/guild invite <joueur>` puis `/guild accept <tag>` | `/guilde` | officier/chef pour inviter | Gère les invitations conservées sept jours |
| `/guild leave\|kick\|promote\|demote\|transfer ...` | `/guilde` | selon le rôle | Gère les membres, officiers et la propriété |
| `/guild ranking` | `/guilde classement` | aucune | Affiche le classement agrégé de la saison classée active |
| `/privacy export\|erase\|status <joueur>` | — | `tropicube.privacy.manage` + session TOTP | Exporte, programme l'anonymisation ou consulte les demandes RGPD |
| `/privacy cancel <id>` | — | `tropicube.privacy.manage` + session TOTP | Annule une demande en attente ou placée sous gel légal |
| `/2fa issue <joueur>` | — | `tropicube.2fa.issue` | Émet un jeton d'inscription TOTP à usage court |
| `/2fa status` | — | `tropicube.staff` | Distingue compte non inscrit, compte inscrit et session active |
| `/2fa enroll\|confirm\|verify ...` | — | `tropicube.staff` | Inscrit l'application TOTP ou valide la session jusqu'à la déconnexion du réseau ; liens et codes de secours sont copiables |
| `/staff` | — | `tropicube.staff` + session TOTP | Active le spectateur invisible sans interaction |
| `/staffchat <message>` | `/sc` | `tropicube.staff` + session TOTP | Chat staff interserveurs |
| `/coreadmin reload` | `/tropiadmin`, `/ca` | `tropicube.admin` | Recharge la configuration Core et les langues |
| `/languageeditorreload` | — | console uniquement | Recharge uniquement les langues Core ou Velocity après une synchronisation de l'éditeur local |
| `/coreadmin info` | `/tropiadmin`, `/ca` | `tropicube.admin` | Affiche l'état des services Core |

Durées de grade acceptées : une valeur comprise par `DurationParser`, par exemple `30s`, `10m`, `2h`, `7d`. Les niveaux attribués par `/level` sont permanents.

Les amis sont persistants en MySQL. Une party est temporaire dans Redis, limitée par la configuration Core et créée lors de la première invitation. Accepter une invitation depuis une autre party retire atomiquement le joueur de l'ancienne ; s'il en était chef, un autre membre est promu, ou la party vide est dissoute. Le suivi est activé par défaut à l'entrée, puis reste un choix individuel. Les transferts sociaux respectent la whitelist et la capacité de l'instance cible.

L'aide en jeu regroupe la navigation et le chat dans `general`, les files et la progression SheepWars dans `games`, les amis/parties/guildes dans `social`, la progression réseau et les préférences dans `profile`, puis les outils sécurisés de modération et d'exploitation dans `staff`. Chaque commande principale occupe exactement une ligne avec sa syntaxe complète ; seuls ses véritables alias restent entre parenthèses sur cette ligne. L'autocomplétion masque la catégorie `staff` aux joueurs non habilités.

### Hiérarchie des niveaux

`vipLevel` est compris entre 0 et 3 et `modLevel` entre 0 et 4. Un joueur ne peut jamais se modifier lui-même. Le niveau modérateur attribué doit être strictement inférieur à celui de l'émetteur ; le niveau 4 est donc réservé à la console. La console n'est pas limitée. Les permissions individuelles et `/permissions` n'existent plus.

Les avantages VIP cumulés sont : un saut au niveau 1 ; deux sauts, parties personnalisées et priorité de file au niveau 2 ; sauts illimités, annonce d'entrée et `/nick` au niveau 3. Les niveaux modérateur donnent Helper au niveau 1, Moderator au niveau 2, toute l'administration Tropicube au niveau 3 et le wildcard global au niveau 4. Les actions sensibles conservent leur exigence TOTP.

## TropicubeLobby — Paper

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/spawn` | — | aucune | Téléporte au spawn configuré du lobby |
| `/play` | `/servers`, `/sv` | aucune | Ouvre le sélecteur : gauche Quick Play, droite Ranked, molette instances publiques |
| `/languages` | — | aucune | Ouvre le sélecteur de langue |
| `/vip` | `/boutique`, `/shop` | aucune | Ouvre l'accueil Boutique puis son onglet Grades |
| `/fly` | `/flymode`, `/fm` | `tropicube.lobby.fly` | Bascule entre vol permanent et sauts aériens |
| `/replay` | `/playnext`, `/playagain`, `/rejouer` | aucune | Rejoint la partie suivante ou attend sa création avec connexion automatique |
| `/replayconfirm` | — | aucune | Confirme une nouvelle série de cinq replays automatiques |
| `/rejoin` | — | aucune | Rejoint une partie SheepWars quittée volontairement si elle est encore active ; aucun argument n'est accepté |
| `/quickplay` | — | aucune | Rejoint la file SheepWars Quick Play, avec suivi de party activé |
| `/competitive <4v4\|8v8>` | — | aucune | Rejoint l'une des deux files classées à cote partagée et remplace toute file active |

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

Les niveaux appliqués par défaut avec les grades accordent les capacités adaptées : `vipLevel 1` obtient un saut, `vipLevel 2` deux et `vipLevel 3` des sauts illimités. Les niveaux propres au joueur restent indépendants de son grade cosmétique.

La hotbar du lobby conserve une disposition stable : jeux au slot 0, social au slot 2, profil au slot 4 et boutique au slot 8. Dans le pied du sélecteur de jeux, Bêta occupe le slot 30 et Parties personnalisées le slot 32, avec le slot central 31 laissé vide. Les paramètres restent accessibles depuis Profil.

Après la saisie de `/`, le client reçoit toutes les commandes et tous les alias Tropicube réellement enregistrés par Velocity, Core, Lobby et le mini-jeu courant. Les permissions continuent de masquer les commandes staff non accessibles. Les commandes externes et leurs espaces de noms restent masqués ; `/?`, les espaces de noms `bukkit:` et `minecraft:` ainsi que les commandes vanilla sont refusés sur l'ensemble du réseau.

## TropicubeSheepwars — Paper

| Commande | Permission | Effet |
|---|---|---|
| `/sheepwars rank` | aucune | Affiche le rang localisé, la cote partagée, l'incertitude et les placements |
| `/sheepwars mastery [a|b]` | aucune | Ouvre les deux branches horizontales ou sélectionne directement la branche exclusive du kit courant |
| `/sheepwars summary <public|team|private>` | aucune | Règle le détail public des résumés |

`sheepwars.mapvote.weight.2` porte le poids d'un vote de carte à deux.

La configuration de partie, la sélection de carte, d'équipe, de classe et de kit passent par les inventaires graphiques. La hotbar d'attente propose aussi le centre joueur global au slot 7 ; cet objet disparaît au lancement pour ne jamais occuper un emplacement de combat. Les commandes Core restent disponibles puisque `TropicubeCore` est chargé sur l'instance.

## Tropicube Fallen Kingdoms

`/fkadmin <status|start|cancel|stop|reload>` utilise la permission `fallenkingdoms.admin`. `status` affiche identifiant de session, état, carte, effectif et prochaine échéance; `start` valide l'effectif et l'agencement; `reload` applique atomiquement une configuration valide uniquement en attente; `stop` produit un `ADMIN_ABORT` sans statistiques compétitives. Dans sa partie personnalisée, l'hôte peut aussi exécuter `status`, `start` et `cancel` sans obtenir la permission administrative. Les joueurs choisissent royaume, kit et carte avec les objets du lobby d'attente.

## TropicubeVelocity — proxy

| Commande | Alias | Permission | Description |
|---|---|---|---|
| `/lobby` | `/hub` | aucune | Transfère vers le lobby disponible le moins chargé |
| `/whitelist add <joueur>` | — | hôte d'une partie privée | Ajoute un joueur connu à la partie personnalisée privée |
| `/whitelist remove <joueur>` | — | hôte d'une partie privée | Retire un joueur de la partie, sauf l'hôte lui-même |
| `/server [nom]` | — | aucune | Liste les instances ou se connecte à une instance joignable |
| `/queue <serveur>` | `/file` | aucune | Entre dans la file d'une instance pleine ; VIP+ et Premium sont prioritaires |
| `/nick` | — | `vipLevel ≥ 3` | Génère un pseudonyme et un skin aléatoires |
| `/nick off` | — | aucune | Restaure l'identité originale, même après une perte de grade |
| `/find <joueur>` | — | `tropicube.admin.find` | Localise un joueur connecté |
| `/send <joueur|*> <serveur>` | — | `tropicube.admin.send` | Transfère un joueur ou tous les joueurs |
| `/pull <joueur>` | — | `tropicube.admin.pull` | Fait venir un joueur sur le serveur actuel, si celui-ci est joignable |
| `/tropicube ...` | `/tropi`, `/cm` | `tropicube.admin` | Administration des instances |
| `/maintenance <network\|type> <on\|off\|status> [minutes] [motif]` | — | `tropicube.admin.maintenance` | Active un drain réseau ou par type avec l'échéance configurée par défaut |
| `/announce <network\|type\|instance> <clé.langue>` | — | `tropicube.admin.announce` | Diffuse une annonce localisée configurée |
| `/networkdiag` | `/netdiag` | `tropicube.admin.diagnostic` | Affiche l'état Redis, les instances et la protection des connexions |

`vipLevel ≥ 3` autorise l'activation de `/nick`. `/nick off` reste toujours accessible et le grade d'affichage factice ne modifie jamais les niveaux réels.

`/whitelist` est validée par Velocity : l'émetteur doit posséder une partie personnalisée active et privée. Un pseudo Java ou un pseudo Floodgate préfixé par `.` est résolu parmi les joueurs actuellement ou précédemment vus par le proxy ; un UUID est aussi accepté. L'item de whitelist remis à l'hôte dans la hotbar SheepWars utilise le même flux proxy.

Les commandes Geyser/Floodgate ne sont pas publiées aux joueurs par le catalogue Tropicube. L'exploitation peut lancer `geyser connectiontest <hôte> <port>` depuis la console Velocity après déploiement. Les autres commandes exigent leurs permissions proxy `geyser.command.*` ou `floodgate.command.*` et restent hors du modèle de niveaux Tropicube tant qu'elles ne sont pas explicitement intégrées.

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

## Capacités réseau dérivées

Les nœuds Paper et Velocity sont uniquement des adaptateurs calculés depuis `vipLevel` et `modLevel` :

- `tropicube.bypass.whitelist` : accès aux parties en liste blanche ;
- `tropicube.bypass.spam` : exemption de l'anti-spam du chat ;
- `tropicube.chat.color` : couleurs et décorations MiniMessage autorisées dans le chat ; les tags interactifs restent affichés comme texte ;
- `tropicube.vip`, `tropicube.premium`, `tropicube.staff` : marqueurs fonctionnels de niveau.

Velocity utilise le profil révisionné publié par Core dans Redis et le conserve en mémoire. Une valeur absente ou invalide vaut `0/0` ; aucun accès Redis n'est effectué dans le callback de permissions.

`modLevel 3` couvre toute l'administration Tropicube. `modLevel 4` ajoute le wildcard global ; les opérateurs Paper et UUID configurés ne donnent plus de droits.

## Parcours graphique des guildes

Dans la hotbar du Lobby, **Social → Guildes** propose création, invitations reçues, membres, défis hebdomadaires et top 20 saisonnier. Le Profil ne comporte plus de bouton Guildes. La création demande le nom puis le tag dans une saisie privée ; inviter demande le pseudo. Saisir `!` annule et revient au menu. Chaque étape expire après le délai configuré.

Les officiers et le chef peuvent inviter ; un officier peut exclure un membre, et le chef peut aussi gérer les officiers, promouvoir, rétrograder et transférer la propriété. Départ, exclusion et transfert nécessitent une confirmation dans les menus. Un chef doit transférer sa guilde avant de partir si d'autres membres restent ; le départ de son dernier membre la supprime avec un avertissement explicite. Les commandes `/guild` et `/guilde` ainsi que leurs permissions restent inchangées. Les libellés de rôle, de défi et de résultat sont localisés dans les quatre langues.

## Commandes locales d'exploitation

Ces commandes sont réservées au compte système d'exploitation, sans nouvelle permission Minecraft :

- `python3 tools/ops/tropicube_ops.py backup` : sauvegarde chiffrée vers le dépôt SFTP configuré ; nécessite MySQL, Redis, Velocity et Restic.
- `python3 tools/ops/tropicube_ops.py verify-backup <répertoire>` : contrôle le manifeste et les empreintes avant restauration, sans importer de données.
- `python3 tools/ops/tropicube_ops.py diagnose` : diagnostic JSON et code de retour non nul en cas d'alerte.
- `python3 tools/ops/tropicube_ops.py activate <tag-UTC>` : active un lot d'images déjà vérifié, avec maintenance et sauvegarde si le réseau tourne.
- `python3 tools/ops/integration_tests.py` : crée puis supprime uniquement une pile MySQL/Redis temporaire et exécute les tests d'intégration.

Sous Windows, utiliser `python` au lieu de `python3`. Le déploiement emploie la commande console existante `/maintenance network on|off` ; ses permissions joueur restent inchangées.

`python3 tools/ops/smoke_test.py <tag-UTC>` vérifie un lot dans une pile séparée (préfixe propriétaire, réseaux et volumes dédiés, ports backend décalés de 10000). Les logs restent privés sous `.runtime/smoke`.
