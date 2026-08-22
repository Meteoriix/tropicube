# SheepWars

## Présentation

SheepWars est un mini-jeu PvP en équipes dans lequel les joueurs combattent sur des îles opposées en lançant des moutons aux effets spéciaux. La version Tropicube oppose l'équipe rouge à l'équipe bleue dans une manche unique à élimination : une équipe gagne lorsque plus aucun adversaire n'est vivant. À l'expiration du temps, l'équipe qui conserve le plus de joueurs gagne ; une égalité de survivants produit un match nul.

Le mode mêle visée, gestion du recul et du vide, combat classique à l'épée et à l'arc, composition d'équipe et utilisation tactique d'effets de zone. Chaque joueur reçoit régulièrement un nouveau mouton, ce qui maintient la pression et oblige les équipes à adapter leur positionnement.

## Origine et adaptation Tropicube

Le concept SheepWars a été popularisé dans la communauté Minecraft francophone par le serveur Epicube au milieu des années 2010. Un article d'époque consacré à Epicube rapporte que le nom est né d'un jeu de mots autour d'un prototype appelé « ShipWars », devenu « SheepWars ». Des cartes communautaires de 2015 décrivaient déjà le principe comme un affrontement PvP visant à éliminer les joueurs de l'île adverse.

La présente implémentation est une adaptation propre à Tropicube. Elle conserve le principe des moutons-projectiles et des deux îles, puis l'étend avec quinze types de moutons, trois classes, neuf kits spécialisés, des réglages de partie personnalisée, un vote de carte, une orchestration Docker et une interface traduite.

Sources historiques : [présentation d'Epicube et origine du nom](https://www.minecraft-france.fr/epicube/), [carte communautaire SheepWars de 2015](https://www.minecraft-france.fr/map-sheepwars-vanilla-1-8-3/).

## Découpage architectural de l'extension

| Catégorie | Décision |
|---|---|
| Réutilisable tel quel | Les profils, niveaux réseau, missions, guildes, parties Redis, instances Docker et préférences de langue restent fournis par Core, Docker API et Velocity. |
| À généraliser | Le mode fonctionnel d'une instance et les événements réseau sont des contrats partagés ; aucune règle de cote ou de kit SheepWars n'est déplacée dans Core. |
| Propre aux jeux existants | Les moutons, cartes, équipes, classes, kits et la machine à états demeurent strictement dans `tropicube-sheepwars`. FallenKingdoms reste un module vide et n'est pas une dépendance. |
| Nouveau | Quick Play, files classées 4v4/8v8, cote et incertitude, saisons, sanctions d'abandon, maîtrise des kits, scrutin court et résumés de partie. |

## Boucle de jeu

1. L'instance attend les joueurs et charge leur profil, leur langue, leur classe et leur kit.
2. Les joueurs choisissent une équipe, une classe, un kit et, si le vote est actif, une carte.
3. En partie classique, le démarrage automatique déclenche le compte à rebours lorsque le minimum configuré est atteint. En partie personnalisée, l'hôte lance le compte à rebours depuis son menu de réglages ou active explicitement le démarrage automatique.
4. Au début de la manche, chaque joueur rejoint un spawn libre de son équipe avec une armure en cuir colorée, une épée, un arc Infinité, une flèche et un mouton aléatoire.
5. Un mouton spécial supplémentaire est distribué périodiquement à chaque survivant qui en stocke moins de cinq. Si le stock est plein à l'échéance, la remise reste en attente jusqu'à ce qu'une place se libère, puis le délai complet repart pour ce joueur. L'équipe en sous-nombre reçoit un pool de trois moutons par joueur manquant, réparti avec un plafond de deux bonus par joueur.
6. Une mort est définitive pour la manche et place le joueur en spectateur. Tout joueur qui rejoint l'instance après le lancement arrive également en spectateur, sans équipe et sans influencer les conditions de victoire. La disparition de tous les survivants d'une équipe termine immédiatement la partie.
7. Après l'écran de résultat, les joueurs sont renvoyés au lobby. Une prochaine instance est précréée uniquement après une partie classique ; une partie personnalisée n'engendre jamais automatiquement un nouveau serveur. Le lobby peut alors proposer une revanche classique et mettre les joueurs en attente pendant sa création.

La durée par défaut est de 600 secondes, le compte à rebours de 10 secondes et la distribution configurée des moutons de 15 secondes. En partie classique, le délai est adapté une fois au lancement : 10 secondes pour 2 à 4 joueurs, 12 secondes pour 5 à 8 joueurs et 15 secondes pour 9 à 16 joueurs. Une partie personnalisée conserve le délai fixé par l'hôte.

Le combat adopte les fondamentaux compétitifs de Minecraft 1.8 : aucune recharge d'attaque, absence d'attaque circulaire, dégâts d'épées en bois et en pierre restaurés à 5 et 6 PV, et recul horizontal/vertical rétabli avec une impulsion renforcée en sprint. L'arc applique un multiplicateur de 0,85, y compris avec le kit Archer dont l'enchantement Puissance reste inchangé. Tous ces paramètres sont centralisés sous `gameplay-balance.pvp`.

## Classes et kits

Les classes organisent les kits par rôle. La classe elle-même sert de catégorie ; le bonus concret provient du kit sélectionné.

| Classe | Kit | Effet |
|---|---|---|
| DPS | Épéiste | Épée en pierre à la place de l'épée en bois |
| DPS | Archer | Arc avec Puissance I en plus d'Infinité |
| DPS | Berger de la Mort | Moutons infligeant 20 % de dégâts supplémentaires, sans agrandir leurs rayons |
| Tank | Colosse | 12 cœurs maximum, soit 2 de plus que la base |
| Tank | Ancre | 50 % de résistance au recul |
| Tank | Plume d'Acier | 65 % de dégâts de chute en moins |
| Support | Éleveur | Moutons destructibles possédant 30 PV au lieu de 20 |
| Support | Médic | Flèches donnant Régénération I pendant 4 secondes, avec 3 secondes de recharge par allié |
| Support | Acrobate | Saut amélioré II permanent |

L'hôte peut désactiver des classes ou des kits. Le mode « kits aléatoires » ignore les choix individuels et attribue un kit actif au lancement.

En Quick Play, chaque kit reçoit sa propre expérience. `/sheepwars mastery` présente les deux branches côte à côte ; le joueur peut aussi utiliser `/sheepwars mastery a|b` et revenir sur son choix. Les branches sont persistées, mais leurs bonus ne sont volontairement pas actifs : leur catalogue YAML et leur équilibrage devront faire l'objet d'une validation de game design dédiée. Une partie personnalisée ne produit aucune progression et le classé utilise uniquement les effets de base des kits.

## Moutons spéciaux

| Mouton | Fonction principale |
|---|---|
| Abordage | Transporte son lanceur pour franchir l'espace entre les îles |
| TNT | Produit une explosion de 9 PV maximum dans un rayon de 6,5 blocs |
| Distortion | Déplace jusqu'à 72 blocs dans un rayon de 4 blocs |
| Ténébreux | Inflige Lenteur II, Cécité I et Fatigue I pendant 4 secondes dans un rayon de 5 blocs |
| Feu | Inflige jusqu'à 4 PV dans un rayon de 5 blocs et enflamme pendant 4 secondes |
| Poison | Crée pendant 4 secondes une zone de Poison II et de faibles dégâts directs |
| Échange | Échange le lanceur avec la cible proche, ou effectue un dash sans cible |
| Météore | Produit un impact modéré puis quatre projectiles contrôlés |
| Tête chercheuse | Cherche à 12 blocs pendant 6 secondes puis poursuit sa cible pendant 6 secondes |
| Soin | Rend jusqu'à 10 PV en 6 secondes dans un rayon de cinq blocs |
| Foudre | Frappe les trois ennemis les plus proches pour 4 PV chacun |
| Gravité | Attire dans un rayon de 7 blocs puis projette modérément les ennemis proches |
| Mécha | Déploie pendant 20 secondes un golem de 50 PV qui attaque l'équipe ennemie |
| Force | Augmente de 20 % les dégâts des alliés présents dans l'aura pendant 7 secondes |
| Fragmentation | Libère quatre charges et plafonne les dégâts cumulés à 11 PV par cible |

Chaque type possède une probabilité configurable dans `default-settings.sheep-probabilities`. La distribution standard utilise les pourcentages suivants :

| Mouton | Pourcentage | Mouton | Pourcentage | Mouton | Pourcentage |
|---|---:|---|---:|---|---:|
| TNT | 10 % | Soin | 10 % | Force | 9 % |
| Abordage | 8 % | Feu | 8 % | Foudre | 8 % |
| Tête chercheuse | 7 % | Échange | 7 % | Ténébreux | 6 % |
| Poison | 6 % | Fragmentation | 6 % | Gravité | 5 % |
| Météore | 4 % | Distortion | 3 % | Mécha | 3 % |

Chaque remise effectue un tirage pondéré selon les probabilités configurées. L'historique est suivi séparément pour chaque joueur : après deux moutons identiques consécutifs, ce type est exclu du tirage suivant et les autres poids sont renormalisés pour cette seule remise. Dès qu'un autre type est reçu, la distribution complète s'applique de nouveau. Si la configuration ne laisse qu'un seul type doté d'un poids positif, il reste nécessairement le seul résultat possible.

Les types désactivés ont toujours une probabilité effective de 0 % et les autres poids sont automatiquement renormalisés. Le menu affiche à la fois le poids brut et le pourcentage effectif. Il empêche de mettre à zéro le dernier poids actif et de désactiver le dernier type. Si une configuration externe fournit malgré tout uniquement des poids nuls, le premier type actif devient explicitement le secours à 100 %. Si elle désactive tous les types, TNT est réactivé au chargement avec un avertissement.

Les explosions de moutons utilisent un calcul linéaire propre à SheepWars pour les dégâts joueurs. L'explosion Minecraft reste responsable des effets visuels et, selon le type, de la destruction des blocs ; ses dégâts natifs sur les joueurs sont annulés afin d'éviter leur cumul avec les dégâts configurés et de préserver l'absence de tir allié.

Chaque mouton lancé mémorise l'UUID de son lanceur. Lorsqu'un autre joueur détruit un mouton destructible, il récupère un exemplaire du même type si son stock n'a pas atteint la limite. Le lanceur ne récupère jamais son propre mouton, notamment quand l'explosion qui lui est attribuée provoque elle-même la mort de l'entité.

## Modes publics et compétition

- `/quickplay` remplit en priorité une instance existante jusqu'à 8v8 ; son minimum configurable permet un départ adaptatif.
- `/competitive 4v4` attend exactement huit joueurs et `/competitive 8v8` exactement seize. Les deux files utilisent la même cote.
- Une party ne peut dépasser la moitié d'une équipe : deux joueurs en 4v4, quatre en 8v8 et quatre en Quick Play. Seuls les membres ayant activé le suivi automatique sont comptés et transférés.
- La file classée part d'une fenêtre de ±75 points, élargie de 25 points toutes les 15 secondes jusqu'à ±500. Elle ne crée l'instance que lorsqu'un groupe compatible atteint la capacité exacte.
- Le résultat d'équipe détermine toujours le sens de variation de la cote. L'incertitude individuelle module son amplitude puis diminue au fil des parties.
- Une saison dure trois mois calendaires. L'ancienne saison est archivée ; la nouvelle applique un reset souple à mi-distance de 1500 et cinq placements.
- Les paliers localisés reprennent les noms Iron, Bronze, Silver, Gold, Platinum, Diamond, Ascendant, Immortal et Radiant. `/sheepwars rank` affiche cote, incertitude et placements restants.
- Les compositions classées sont bornées par rôle. Par défaut, le 4v4 autorise 2 DPS, 1 Tank et 1 Support par équipe ; le 8v8 double ces limites.

Une déconnexion en vie pendant une partie classée élimine le joueur, conserve `/rejoin` en spectateur et applique une interdiction de file graduée de 1, 5, 15 puis 60 minutes. Les récidives sont remises à zéro après sept jours sans abandon. Aucune intégration anti-triche externe n'est ajoutée.

## Cartes et équipes

Une carte jouable contient un monde, une limite de vide et jusqu'à huit spawns rouges et huit spawns bleus. Le nombre maximal effectif de joueurs est donc limité à 16. Les spawns sont mélangés au début de chaque manche afin d'éviter une attribution prévisible.

Les blocs peuvent être détruits pendant la manche sans produire d'objets récupérables. Cette règle couvre aussi les quatre variantes de rails qui se détachent par mise à jour physique lorsque leur bloc de support disparaît.

Lorsque `map-vote-enabled` vaut `true`, trois cartes au maximum sont tirées pour un scrutin court. Chaque joueur vote et une carte est tirée au hasard parmi celles arrivées en tête. La permission `sheepwars.mapvote.weight.2` donne un poids de deux, sans permettre de voter plusieurs fois. Sinon, l'hôte choisit directement la carte ; son choix est aussitôt affiché dans le scoreboard d'attente de tous les joueurs. Une partie ne démarre pas si la carte sélectionnée est incomplète ou désactivée.

Les joueurs peuvent demander une équipe dans le menu d'attente. Le gestionnaire conserve des équipes équilibrées et attribue automatiquement une équipe lorsque nécessaire. Une sélection acceptée actualise immédiatement la tablist de tous les joueurs, y compris les pseudonymes `/nick`, afin que la couleur corresponde à la nouvelle équipe. Les coéquipiers bénéficient d'un contour coloré visible uniquement par leur équipe. Les équipes scoreboard utilisent le nom de profil réellement envoyé au client pour préserver le contour avec `/nick`. La tablist SheepWars masque toujours le grade réseau ou fictif : elle affiche uniquement le pseudonyme dans la couleur de l'équipe, ou en gris pour un spectateur, sans icône devant le nom. Ce rendu est réappliqué après chaque événement de nick ou de grade afin que Core ne puisse pas le remplacer par le format du lobby. Le chat utilise le même nom d'affichage synchronisé afin que `/nick off` restaure immédiatement le pseudonyme réel.

## Partie personnalisée et rôle de l'hôte

Une partie personnalisée est créée depuis le lobby par un joueur autorisé. Velocity réserve atomiquement `host-creation:<uuid>` pendant la création, puis associe le joueur à l'instance avec `host:<uuid>`. Un joueur ne peut ainsi posséder qu'un serveur personnalisé actif ou en cours de création.

Avant la manche, l'hôte dispose d'un menu lui permettant notamment de :

- lancer ou annuler le compte à rebours ;
- régler les nombres minimum et maximum de joueurs ;
- modifier le compte à rebours, la durée et la fréquence de distribution ;
- activer le démarrage automatique, les kits aléatoires et le vote de carte ;
- activer ou désactiver les moutons, classes et kits disponibles.

Le démarrage automatique est désactivé par défaut pour une partie personnalisée, contrairement aux parties classiques où il est activé. Si l'hôte l'active après que le nombre minimum de joueurs a déjà été atteint, le compte à rebours commence immédiatement.

Le lobby permet d'arrêter le serveur tant que la manche n'a pas commencé. Après le lancement, l'association d'hôte est conservée jusqu'à la fin de partie, puis supprimée automatiquement.

À la création, l'hôte choisit une partie publique ou privée. Une partie privée est absente du sélecteur et des compteurs des joueurs non admis. L'hôte est ajouté automatiquement à sa whitelist et reçoit, au slot 6 de la hotbar d'attente, une porte en fer ouvrant la gestion des accès. L'ajout utilise une saisie par enclume gratuite, limitée à un pseudo Minecraft ou un UUID valide ; le texte indicatif ne peut pas être soumis. Les têtes déjà admises permettent leur retrait. Les mêmes mutations sont disponibles avec `/whitelist add|remove <joueur>` sur Velocity. Le proxy valide la propriété de l'instance, interdit le retrait de l'hôte et contrôle de nouveau l'UUID lors de chaque connexion. Après une mutation GUI, SheepWars attend l'accusé de réception émis par Velocity après la sauvegarde Redis avant de recharger la liste ; une absence de réponse est signalée après cinq secondes.

## Interface, langues et commandes

Les actions de partie passent par les objets de hotbar et les inventaires. La commande `/sheepwars` est limitée au profil compétitif, au choix réversible de branche et à la visibilité `public|team|private` des détails de résumé.

Le scoreboard affiche sous le titre tropical `🐑 SHEEPWARS` des sections aérées par de courts séparateurs. Pendant l'attente, il indique l'effectif actuel et maximal, le minimum requis, la carte, l'équipe et la classe du joueur. En partie, il présente le temps restant, la carte, les survivants par équipe, l'équipe ou le statut spectateur, la classe, les éliminations et les moutons lancés. La tablist reprend les identités `🐑 SHEEPWARS` et `🌴 TROPICUBE` sur deux lignes, puis adapte son pied à l'attente, au lancement, au jeu ou à la fin de partie. Les textes proviennent de TropicubeCore et sont disponibles en français, anglais, espagnol et allemand.

Les annonces système autonomes utilisent l'identité `SHEEPWARS >` sans crochets. Les arrivées et départs de joueurs restent narratifs, sans préfixe, et indiquent qu'un joueur « a rejoint la partie ». Les menus, titles, scoreboards et tablists restent sans préfixe afin de préserver leur lisibilité.

La commande `/rejoin`, fournie par TropicubeLobby et utilisée sans argument, permet de rejoindre de nouveau une partie quittée mais encore active ; le joueur revient alors comme spectateur. Le lit de sortie transmet l'ID de l'instance pendant cinq minutes avant de transférer le joueur au lobby. Dans le sélecteur de serveurs, une partie en cours porte le statut bleu `PLAYING` et reste joignable comme spectateur tant que l'instance n'est pas pleine.

## Configuration technique

La configuration source se trouve dans `tropicube-sheepwars/src/main/resources/config.yml`. Les sections principales sont :

- `redis` : connexion à l'état partagé ;
- `default-settings` : règles initiales de la manche ;
- `custom-game-default-settings` : valeurs initiales propres aux instances personnalisées ;
- `force-settings` : fonctionnalités interdites par l'exploitation ;
- `locations` : monde, lobby, limite du vide, cartes et spawns d'équipe.

Le menu hôte couvre toutes les feuilles de `default-settings` : effectifs, compte à rebours, durée, cadence, kits aléatoires, démarrage automatique, vote de carte et poids de chaque mouton. Les paramètres `gameplay-balance` restent des réglages d'exploitation validés au démarrage, car leurs services sont construits une fois par instance. Le minimum et le maximum valent au moins deux ; le maximum ne peut pas descendre sous l'effectif connecté et sa modification met aussi à jour la capacité vue par Velocity.

Lors de l'arrivée d'un joueur en salle d'attente, SheepWars tente de reprendre l'équipe d'un membre de sa party. Ce rapprochement n'est appliqué que si le changement conserve un écart d'effectif rouge/bleu inférieur ou égal à un ; l'équilibrage reste prioritaire.

En déploiement Docker, `INSTANCE_ID`, `SERVER_NAME`, `IS_HOST`, `HOST_UUID` et l'indicateur interne `CUSTOM_GAME_PRIVATE` relient le plugin à Velocity. L'état de l'instance passe successivement par attente, démarrage, jeu, fin et arrêt. Redis porte les marqueurs de partie commencée, de reconnexion, de revanche, de propriété du serveur et les UUID admis aux parties privées.

Après l'écran de fin, SheepWars publie `PROXY:FINISH_GAME:<instanceId>`. Velocity transfère tous les joueurs vers le meilleur lobby disponible, réessaie chaque seconde en cas d'échec, puis tue et supprime immédiatement le conteneur ainsi que son état Redis. La disparition du backend n'est donc plus différée par l'auto-stop générique.

Chaque résultat est enregistré avec mode, carte, équipes, kit, éliminations, morts, moutons lancés et cote avant/après. L'écran final affiche le résultat personnel et les distinctions « éliminations » et « moutons ». Le profil agrégé réutilise ces données sans créer de nouvelle file de guilde. `/sheepwars summary public|team|private` conserve le niveau de détail souhaité pour les présentations publiques futures.

Pour l'installation complète, la création des images et la configuration des cartes, consulter [Déploiement](DEPLOYMENT.md) et [Configuration](CONFIGURATION.md).
