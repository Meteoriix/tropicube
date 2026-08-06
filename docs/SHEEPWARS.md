# SheepWars

## Présentation

SheepWars est un mini-jeu PvP en équipes dans lequel les joueurs combattent sur des îles opposées en lançant des moutons aux effets spéciaux. La version Tropicube oppose l'équipe rouge à l'équipe bleue dans une manche unique à élimination : une équipe gagne lorsque plus aucun adversaire n'est vivant. À l'expiration du temps, l'équipe qui conserve le plus de joueurs gagne ; une égalité de survivants produit un match nul.

Le mode mêle visée, gestion du recul et du vide, combat classique à l'épée et à l'arc, composition d'équipe et utilisation tactique d'effets de zone. Chaque joueur reçoit régulièrement un nouveau mouton, ce qui maintient la pression et oblige les équipes à adapter leur positionnement.

## Origine et adaptation Tropicube

Le concept SheepWars a été popularisé dans la communauté Minecraft francophone par le serveur Epicube au milieu des années 2010. Un article d'époque consacré à Epicube rapporte que le nom est né d'un jeu de mots autour d'un prototype appelé « ShipWars », devenu « SheepWars ». Des cartes communautaires de 2015 décrivaient déjà le principe comme un affrontement PvP visant à éliminer les joueurs de l'île adverse.

La présente implémentation est une adaptation propre à Tropicube. Elle conserve le principe des moutons-projectiles et des deux îles, puis l'étend avec quinze types de moutons, trois classes, neuf kits spécialisés, des réglages de partie personnalisée, un vote de carte, une orchestration Docker et une interface traduite.

Sources historiques : [présentation d'Epicube et origine du nom](https://www.minecraft-france.fr/epicube/), [carte communautaire SheepWars de 2015](https://www.minecraft-france.fr/map-sheepwars-vanilla-1-8-3/).

## Boucle de jeu

1. L'instance attend les joueurs et charge leur profil, leur langue, leur classe et leur kit.
2. Les joueurs choisissent une équipe, une classe, un kit et, si le vote est actif, une carte.
3. En partie classique, le démarrage automatique déclenche le compte à rebours lorsque le minimum configuré est atteint. En partie personnalisée, l'hôte lance le compte à rebours depuis son menu de réglages ou active explicitement le démarrage automatique.
4. Au début de la manche, chaque joueur rejoint un spawn libre de son équipe avec une armure en cuir colorée, une épée, un arc Infinité, une flèche et un mouton aléatoire.
5. Un mouton spécial supplémentaire est distribué périodiquement à chaque survivant. L'équipe en sous-nombre reçoit aussi trois moutons par joueur au lancement.
6. Une mort est définitive pour la manche et place le joueur en spectateur. La disparition de tous les survivants d'une équipe termine immédiatement la partie.
7. Après l'écran de résultat, les joueurs sont renvoyés progressivement au lobby. Une prochaine instance est précréée lorsque possible et le lobby propose une revanche.

La durée par défaut est de 600 secondes, le compte à rebours de 10 secondes et la distribution des moutons de 10 secondes. Ces valeurs sont configurables.

## Classes et kits

Les classes organisent les kits par rôle. La classe elle-même sert de catégorie ; le bonus concret provient du kit sélectionné.

| Classe | Kit | Effet |
|---|---|---|
| DPS | Épéiste | Épée en pierre avec Tranchant I à la place de l'épée en bois |
| DPS | Archer | Arc avec Puissance I en plus d'Infinité |
| DPS | Berger de la Mort | Moutons infligeant 50 % de dégâts supplémentaires |
| Tank | Colosse | 14 cœurs maximum, soit 4 de plus que la base |
| Tank | Ancre | 80 % de résistance au recul |
| Tank | Plume d'Acier | 80 % de dégâts de chute en moins |
| Support | Éleveur | Moutons possédant 40 PV au lieu de 16 |
| Support | Médic | Flèches soignant les alliés touchés |
| Support | Acrobate | Saut amélioré II permanent |

L'hôte peut désactiver des classes ou des kits. Le mode « kits aléatoires » ignore les choix individuels et attribue un kit actif au lancement.

## Moutons spéciaux

| Mouton | Fonction principale |
|---|---|
| Abordage | Transporte son lanceur pour franchir l'espace entre les îles |
| TNT | Produit une forte explosion |
| Distortion | Téléporte les blocs autour de l'impact |
| Ténébreux | Ralentit et aveugle les ennemis touchés |
| Feu | Enflamme les ennemis touchés |
| Poison | Crée une zone persistante de poison |
| Échange | Échange le lanceur avec la cible proche, ou effectue un dash sans cible |
| Météore | Déclenche une pluie de météores |
| Tête chercheuse | Poursuit l'ennemi le plus proche de l'impact |
| Soin | Soigne le lanceur et les alliés dans un rayon de cinq blocs |
| Foudre | Frappe une cible puis enchaîne jusqu'à trois joueurs proches |
| Gravité | Attire les joueurs proches puis les projette en l'air |
| Mécha | Déploie un golem résistant qui attaque l'équipe ennemie |
| Force | Renforce les dégâts du lanceur et des alliés proches |
| Fragmentation | Libère cinq petits moutons explosifs |

Chaque type possède une probabilité configurable dans `default-settings.sheep-probabilities`. Les types peuvent également être désactivés via `force-settings.sheep-disabled` ou depuis le menu de l'hôte.

## Cartes et équipes

Une carte jouable contient un monde, une limite de vide et jusqu'à huit spawns rouges et huit spawns bleus. Le nombre maximal effectif de joueurs est donc limité à 16. Les spawns sont mélangés au début de chaque manche afin d'éviter une attribution prévisible.

Lorsque `map-vote-enabled` vaut `true`, chaque joueur vote et une carte est tirée au hasard parmi celles arrivées en tête. Sinon, l'hôte choisit directement la carte. Une partie ne démarre pas si la carte sélectionnée est incomplète ou désactivée.

Les joueurs peuvent demander une équipe dans le menu d'attente. Le gestionnaire conserve des équipes équilibrées et attribue automatiquement une équipe lorsque nécessaire. Les coéquipiers bénéficient d'un contour coloré visible uniquement par leur équipe.

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

## Interface, langues et commandes

Le backend SheepWars n'enregistre aucune commande Minecraft propre dans son `plugin.yml`. Toutes les actions pendant la partie passent par les objets de la hotbar et les inventaires : équipe, carte, classe/kit, réglages de l'hôte et retour au lobby.

Le scoreboard affiche l'état de la partie, le temps restant, les survivants par équipe, la classe, le kit et les éliminations. La tablist identifie les équipes et les spectateurs. Les textes proviennent de TropicubeCore et sont disponibles en français, anglais, espagnol et allemand.

La commande `/sw join`, fournie par TropicubeLobby, sert uniquement à rejoindre de nouveau une partie quittée mais encore active. Le lit de sortie transmet l'ID de l'instance pendant cinq minutes avant de transférer le joueur au lobby.

## Configuration technique

La configuration source se trouve dans `tropicube-sheepwars/src/main/resources/config.yml`. Les sections principales sont :

- `redis` : connexion à l'état partagé ;
- `default-settings` : règles initiales de la manche ;
- `custom-game-default-settings` : valeurs initiales propres aux instances personnalisées ;
- `force-settings` : fonctionnalités interdites par l'exploitation ;
- `locations` : monde, lobby, limite du vide, cartes et spawns d'équipe.

En déploiement Docker, `INSTANCE_ID`, `SERVER_NAME`, `IS_HOST` et `HOST_UUID` relient le plugin à Velocity. L'état de l'instance passe successivement par attente, démarrage, jeu, fin et arrêt. Redis porte les marqueurs de partie commencée, de reconnexion, de revanche et de propriété du serveur.

Pour l'installation complète, la création des images et la configuration des cartes, consulter [Déploiement](DEPLOYMENT.md) et [Configuration](CONFIGURATION.md).
