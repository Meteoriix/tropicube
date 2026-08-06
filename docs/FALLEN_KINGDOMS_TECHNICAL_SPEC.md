# Fallen Kingdoms — spécification technique

## Statut du document

Cette page transforme le [game design historique](FALLEN_KINGDOMS_GAME_DESIGN.md) en contrat technique pour la première version Tropicube de Fallen Kingdoms. Elle décrit le comportement à implémenter ; elle ne signifie pas que le module, les commandes ou les intégrations cités existent déjà.

Les règles marquées **confirmées** sont approuvées. Les valeurs marquées **configurables** ont une valeur publique par défaut, mais peuvent être modifiées dans les limites indiquées. Les éléments marqués **HYPOTHÈSE TEMPORAIRE** doivent rester faciles à changer et devront être validés en jeu avant une publication.

En cas de divergence, cette spécification prévaut pour la V1 Tropicube. Le game design historique reste la source de contexte et d'intention.

## Périmètre de la première version

La V1 comprend :

- une partie automatisée sur une instance Paper 26.2 dédiée et éphémère ;
- de deux à cinq royaumes actifs, choisis dynamiquement selon l'effectif ;
- les phases de préparation, combat commun, assaut, mort subite et résultat ;
- les quatre kits historiques : Mineur, Fermier, Éclaireur et Enchanteur ;
- la sélection de carte, de préférence d'équipe et de kit par interface ;
- les territoires de base, la zone commune, les protections et les brèches à la TNT ;
- un cœur par royaume, les dernières vies, les réapparitions et les spectateurs ;
- la ruine contrôlée d'une base éliminée ;
- la réduction de bordure en mort subite ;
- les messages, HUD, scoreboards et bossbars localisés par Core ;
- des commandes d'administration minimales ;
- la persistance asynchrone des préférences et statistiques globales, consultables sur Velocity ;
- l'arrêt propre de l'instance par le flux Docker/Velocity existant.

Ne font pas partie de la V1 : une monnaie ou des récompenses économiques, un éditeur de carte en jeu, une restauration du monde permettant plusieurs parties dans le même conteneur, une reproduction exhaustive du combat Minecraft 1.8, et les cartes de production elles-mêmes.

## Règles confirmées

### Effectif et royaumes

- Une partie publique exige au moins deux royaumes et quatre joueurs par royaume, soit huit joueurs.
- Un royaume accueille au maximum six joueurs en partie publique.
- Le nombre cible de royaumes est `max(2, ceil(effectif / 6))`, plafonné à cinq.
- Les seuils publics naturels sont donc : 8 à 12 joueurs pour deux royaumes, 13 à 18 pour trois, 19 à 24 pour quatre et 25 à 30 pour cinq.
- La répartition finale garantit au moins quatre joueurs par royaume et un écart d'effectif maximal de un.
- Chaque carte activée doit définir un agencement valide pour chacun des nombres de royaumes qu'elle accepte.
- Le joueur choisit une couleur préférée. L'algorithme respecte le plus de préférences possible sans enfreindre l'équilibrage ; une préférence n'est jamais une garantie.

### Chronologie

Toutes les échéances sont mesurées depuis l'entrée en `PREPARATION` :

| Instant | Effet |
|---:|---|
| 00:00 | Début de la préparation, remise du kit et téléportation aux bases |
| 15:00 | Activation du JcJ dans la zone commune |
| 25:00 | Ouverture des bases ennemies et vulnérabilité des cœurs |
| 75:00 | Destruction forcée de tous les cœurs restants et mort subite |
| 90:00 | Résolution forcée selon le nombre de survivants |

La partie se termine avant 90 minutes dès qu'un seul royaume possède encore au moins un joueur vivant.

### Combat

- Le profil public est le combat natif Paper 26.2.
- Une partie personnalisée peut choisir le profil `LEGACY_1_8` : absence de délai d'attaque, dégâts et recul émulés, boucliers et seconde main désactivés.
- L'émulation 1.8 est volontairement limitée à ces mécanismes essentiels.
- Les dégâts directs et indirects entre membres d'un même royaume sont annulés.

### Cœurs, dernières vies et élimination

- Chaque cœur possède 500 points de vie, indépendamment de la carte.
- Avant l'assaut, le cœur est invulnérable et inaccessible aux ennemis.
- Pendant l'assaut, les attaques de mêlée et les projectiles ennemis infligent les dégâts définis par le profil de combat.
- La TNT ne retire jamais directement des points de vie au cœur ; elle sert uniquement aux brèches autorisées.
- Le propriétaire du cœur et ses alliés ne peuvent pas l'endommager.
- La destruction d'un cœur place tous ses survivants en dernière vie. Un joueur déjà mort ou en attente de réapparition au même instant est éliminé définitivement.
- La base reste le territoire du royaume tant qu'au moins un de ses joueurs survit.
- À la mort du dernier survivant, le royaume est éliminé, sa base subit une destruction contrôlée puis devient un territoire neutre en ruine.

### Mort, déconnexion et arrivée tardive

- Tant que son cœur existe, un joueur mort laisse tomber son inventaire selon les règles Minecraft, observe pendant dix secondes, puis réapparaît sans équipement à sa base.
- Le kit n'est distribué qu'une fois, au début de la partie.
- Si le cœur est détruit durant ce délai, la réapparition est annulée et le joueur reste spectateur.
- Une déconnexion pendant une phase active compte immédiatement comme une mort.
- À son retour, le joueur ne peut reprendre que si sa session appartenait déjà à la partie et si son cœur est encore vivant ; sinon il reste spectateur.
- Toute arrivée sans session verrouillée après le début de la préparation est placée en spectateur.

### Mort subite et résultat à 90 minutes

- À 75 minutes, tous les cœurs encore vivants sont détruits avec la cause `FORCED_SUDDEN_DEATH` et aucune réapparition supplémentaire n'est possible.
- La bordure se resserre autour du centre configuré pour atteindre exactement 50 × 50 blocs à 90 minutes.
- Si un seul royaume conserve des survivants avant cette échéance, il gagne immédiatement.
- À 90 minutes, gagne le ou les royaumes ayant le plus grand nombre de survivants.
- En cas d'égalité au maximum, le résultat est un match nul et chaque royaume ex æquo reçoit à la fois une victoire et un match nul dans ses statistiques.

## Règles configurables

Les valeurs de gameplay sont validées au chargement et regroupées dans `config.yml`. Les réglages publics forment le contrat par défaut. Une partie personnalisée ne peut surcharger que les clés explicitement autorisées.

| Règle | Défaut public | Surcharge personnalisée |
|---|---:|---|
| Compte à rebours | 30 s | oui |
| Joueurs minimum par royaume | 4 | non |
| Joueurs maximum par royaume | 6 | oui, dans la capacité de la carte |
| Royaumes maximum | 5 | oui, de 2 à 5 |
| Début du JcJ | 15 min | oui |
| Début de l'assaut | 25 min | oui |
| Mort subite | 75 min | oui |
| Fin forcée | 90 min | oui |
| Points de vie du cœur | 500 | oui |
| Délai de réapparition | 10 s | oui |
| Taille finale de bordure | 50 blocs | non |
| Profil de combat | `PAPER_26_2` | oui |
| Kits activés | quatre kits | oui |
| Carte | vote | choix de l'hôte |
| Rayon et proportion de ruine | selon configuration | oui |

Les validations imposent `pvp-at < assault-at < sudden-death-at < force-end-at`, au moins deux royaumes, quatre joueurs minimum par royaume, une taille finale de bordure de 50, un cœur strictement positif et au moins un kit actif. Les protections techniques, l'absence de dégâts alliés, la préservation des conteneurs et l'absence d'accès réseau bloquant ne sont jamais surchargeables.

## Hypothèses temporaires

- **HYPOTHÈSE TEMPORAIRE — compte à rebours :** 30 secondes offrent assez de temps pour annuler si l'effectif devient invalide.
- **HYPOTHÈSE TEMPORAIRE — écran de résultat :** les résultats restent affichés dix secondes avant transfert par Velocity.
- **HYPOTHÈSE TEMPORAIRE — combat 1.8 :** l'émulation essentielle décrite plus haut suffit ; les écarts restants seront évalués après tests en jeu.
- **HYPOTHÈSE TEMPORAIRE — kits :** lorsqu'une durabilité historique n'est pas connue, l'objet commence à sa durabilité maximale.
- **HYPOTHÈSE TEMPORAIRE — ruine :** trois vagues visuelles espacées de quelques ticks, un rayon de huit blocs et 35 % de blocs destructibles sont les valeurs initiales d'équilibrage.
- **HYPOTHÈSE TEMPORAIRE — récompenses :** aucune monnaie n'est attribuée en V1.

Ces hypothèses doivent être configurables lorsqu'elles influencent le gameplay et couvertes par des critères d'acceptation avant d'être déclarées définitives.

## Machine à états

```text
WAITING ──effectif et carte valides──> COUNTDOWN
   ^                                      │
   └──effectif/carte invalides────────────┘
                                          │ compte à rebours terminé
                                          v
PREPARATION ──15:00──> PVP ──25:00──> ASSAULT ──75:00──> SUDDEN_DEATH
     │                    │               │                    │
     └──────────── abandon administrateur / condition de victoire ───────┐
                                                                         v
                                                                      ENDING
                                                                         │
                                                            résultat traité/nettoyage
                                                                         v
                                                                       ENDED
```

### États

| État | Responsabilité et invariants |
|---|---|
| `WAITING` | Accueil, vote de carte, préférences d'équipe et kits. Aucun roster verrouillé. |
| `COUNTDOWN` | Vérification continue de l'effectif et de la carte. Aucune règle active de partie. |
| `PREPARATION` | Roster et carte verrouillés, équipes assignées, kits remis, JcJ et bases ennemies fermés. |
| `PVP` | JcJ autorisé uniquement en zone commune ; bases ennemies et cœurs protégés. |
| `ASSAULT` | Bases ouvertes, cœurs vulnérables, JcJ et règles de siège actifs. |
| `SUDDEN_DEATH` | Tous les cœurs détruits, aucune réapparition, bordure mobile. |
| `ENDING` | Jeu figé, résultat immuable, statistiques et transfert en cours. |
| `ENDED` | Tâches annulées, listeners inactifs, ressources fermées ; aucun événement tardif n'agit. |

### Transitions et événements déclencheurs

| Origine | Destination | Déclencheur | Effets atomiques |
|---|---|---|---|
| `WAITING` | `COUNTDOWN` | démarrage automatique ou administrateur, effectif ≥ 8 et carte/agencement valides | démarre le minuteur et publie l'état |
| `COUNTDOWN` | `WAITING` | effectif insuffisant, carte/agencement invalide ou annulation | annule le minuteur et déverrouille les sélections |
| `COUNTDOWN` | `PREPARATION` | minuteur à zéro et préconditions toujours valides | verrouille le roster, résout le vote, calcule les royaumes, assigne joueurs/kits, crée les cœurs et téléporte |
| `PREPARATION` | `PVP` | horloge à 15:00 | active le JcJ dans la zone commune |
| `PVP` | `ASSAULT` | horloge à 25:00 | ouvre les bases et rend les cœurs vulnérables |
| `ASSAULT` | `SUDDEN_DEATH` | horloge à 75:00 | détruit les cœurs restants, annule les réapparitions et démarre la bordure |
| `ASSAULT` | `ENDING` | un seul royaume vivant | fige le vainqueur |
| `SUDDEN_DEATH` | `ENDING` | un seul royaume vivant | fige le vainqueur |
| `SUDDEN_DEATH` | `ENDING` | horloge à 90:00 | compte les survivants et applique l'égalité multi-vainqueur |
| état actif | `ENDING` | arrêt/abandon administrateur ou désactivation du plugin | résultat `ADMIN_ABORT`, aucune statistique compétitive |
| `ENDING` | `ENDED` | persistance terminée ou expirée, affichage terminé, joueurs transférés | annule tâches et abonnements, ferme les ressources et demande l'arrêt d'instance |

Chaque transition est idempotente et exécutée sur le thread serveur pour les mutations Paper. Une transition refusée ne produit aucun effet partiel. Les tâches capturent l'identifiant de session attendu ; elles ignorent un callback tardif si la session ou l'état ne correspond plus.

## Responsabilités des composants

| Composant | Responsabilité |
|---|---|
| `GameSessionManager` | Compose les services pour une unique session d'instance ; ne contient pas les règles détaillées. |
| `GameStateMachine` | Valide et applique les transitions pures, conserve l'état et le motif de fin. |
| `PhaseService` | Convertit l'horloge de partie en échéances et demande les transitions une seule fois. |
| `PlayerSessionService` | Gère roster, présence, reconnexion, état vivant/mort/spectateur et arrivée tardive. |
| `KingdomService` | Calcule le nombre de royaumes, équilibre les préférences et détermine survivants/éliminations. |
| `MapService` | Charge et valide cartes, agencements, positions, régions et bornes. Résout le vote. |
| `RegionProtectionService` | Décide chaque interaction à partir de la phase, du territoire, du joueur et du matériau. |
| `HeartService` | Crée et identifie les cœurs, valide les attaquants, applique les dégâts et publie leur destruction. |
| `KitService` | Charge le catalogue, mémorise le choix et remet une seule fois le contenu configuré. |
| `RespawnService` | Programme les dix secondes, annule à la destruction du cœur et réapparaît sans kit. |
| `RuinService` | Exécute les vagues visuelles et la destruction filtrée, puis neutralise la région. |
| `BorderService` | Initialise la bordure et garantit son interpolation vers 50 × 50 à 90 minutes. |
| `HudService` | Produit scoreboard, bossbars, titres, hotbar et rafraîchissement après changement de langue. |
| `TaskRegistry` | Enregistre toutes les tâches Paper et les annule de façon idempotente à la fin. |
| `StatisticsService` | Construit un résultat immuable, orchestre l'écriture durable et la mise à jour du cache. |
| `StatisticsRepository` | Persiste les détails et agrégats dans MySQL hors thread Paper. |
| `GameNetworkGateway` | Publie les snapshots d'instance et demandes de transfert Redis avec identifiant/propriétaire validés. |

Les modèles et règles de transition, d'équilibrage, de victoire et de validation restent en Java pur. Les listeners, commandes et adaptateurs Paper/Redis/SQL restent minces. Aucun composant Fallen Kingdoms ne dépend d'une classe métier SheepWars.

## Modèle de domaine

### Partie

`GameSession` possède un identifiant UUID immuable, l'identifiant de l'instance, la carte résolue, le profil de règles, l'instant de départ, l'état courant, les royaumes actifs et le résultat éventuel. Une instance ne conserve jamais l'état d'une ancienne session.

### Royaumes et bases

- `KingdomId` appartient au catalogue `BLUE`, `RED`, `GREEN`, `YELLOW`, `ORANGE`.
- `KingdomRuntime` contient les joueurs affectés, les survivants, le cœur et l'état `ACTIVE`, `LAST_LIFE` ou `ELIMINATED`.
- `BaseDefinition` contient la région cuboïde, les points de réapparition, la position du cœur, les blocs/régions techniques protégés et les paramètres de ruine.
- `BaseRuntime` évolue de `INTACT` vers `LAST_LIFE`, puis `RUINED`. Une base `RUINED` est un territoire `NEUTRAL`.

### Joueurs

`PlayerSession` contient UUID, préférence de royaume, royaume affecté, kit, présence, statistiques de partie, échéance de réapparition et l'un des états :

- `ACTIVE` : vivant et participant ;
- `RESPAWNING` : mort avec une réapparition possible ;
- `LAST_LIFE` : vivant sans réapparition future ;
- `OFFLINE` : session connue, décès de déconnexion déjà appliqué ;
- `ELIMINATED` : définitivement hors jeu ;
- `SPECTATOR` : arrivé tardivement ou éliminé, sans interaction.

Le service de session conserve uniquement les données nécessaires à une reconnexion. Il ne duplique pas durablement un inventaire déjà tombé au sol.

### Cœurs

`HeartRuntime` contient le royaume propriétaire, les points de vie maximum et courants, l'état `PROTECTED`, `VULNERABLE` ou `DESTROYED`, l'identifiant de l'entité/bloc Paper et une clé PDC propre à la session. Les dégâts portent un auteur, une cause, une valeur initiale et une valeur finale validée.

### Kits

`KitDefinition` est immuable : identifiant, icône, traduction, équipement, inventaire, effets et activation. `PlayerKitPreference` est persistée par joueur et par jeu. Les doublons de kit dans un royaume sont permis.

### Cartes

`MapDefinition` contient identifiant, nom localisable, monde, région jouable, accueil spectateur, centre et taille initiale de bordure, agencements par nombre de royaumes et définitions de bases. Une carte n'est activable que si sa validation complète réussit.

## Configuration YAML

Fallen Kingdoms suit le modèle SheepWars : toutes les cartes sont déclarées dans une section dédiée du `config.yml` du plugin. Les durées sont exprimées en secondes, les positions sous forme `x/y/z/yaw/pitch` et les cuboïdes par deux coins inclusifs.

```yaml
game:
  countdown-seconds: 30
  result-display-seconds: 10
  min-players-per-kingdom: 4
  max-players-per-kingdom: 6
  max-kingdoms: 5

phases:
  pvp-at-seconds: 900
  assault-at-seconds: 1500
  sudden-death-at-seconds: 4500
  force-end-at-seconds: 5400

combat:
  default-profile: PAPER_26_2
  allowed-custom-profiles:
    - PAPER_26_2
    - LEGACY_1_8
  friendly-fire: false

hearts:
  max-health: 500.0
  tnt-direct-damage: false

respawn:
  delay-seconds: 10
  drop-inventory: true
  reissue-kit: false
  disconnect-counts-as-death: true

sudden-death:
  final-border-size: 50.0

ruins:
  waves: 3
  ticks-between-waves: 10
  radius: 8.0
  destruction-ratio: 0.35
  preserve-containers: true

protections:
  forbidden-base-materials:
    - OBSIDIAN
    - BEDROCK
  common-placement-whitelist:
    - COBBLESTONE
    - OAK_PLANKS
    - LADDER
    - TORCH
  tnt-breaches-enabled: true
  block-portal-bypass: true
  block-teleport-bypass: true
  block-piston-crossing: true
  block-fluid-crossing: true

kits:
  miner:
    enabled: true
    icon: IRON_PICKAXE
    items:
      - material: IRON_PICKAXE
        amount: 1
        slot: 0
        durability: FULL
  farmer:
    enabled: true
    icon: WHEAT
    items: []
  scout:
    enabled: true
    icon: FEATHER
    items: []
  enchanter:
    enabled: true
    icon: ENCHANTING_TABLE
    items: []

custom-game:
  allowed-overrides:
    - map
    - combat-profile
    - phase-timings
    - heart-health
    - kingdom-count
    - max-players-per-kingdom
    - enabled-kits
    - respawn-delay

locations:
  world: world
  lobby: { x: 0.5, y: 100.0, z: 0.5, yaw: 0.0, pitch: 0.0 }
  spectator: { x: 0.5, y: 120.0, z: 0.5, yaw: 0.0, pitch: 30.0 }
  maps:
    citadelles:
      enabled: false
      display-name-key: fallenkingdoms.maps.citadelles
      playable-region:
        min: { x: -300, y: -64, z: -300 }
        max: { x: 300, y: 320, z: 300 }
      border:
        center: { x: 0.5, z: 0.5 }
        initial-size: 600.0
      layouts:
        "2": [BLUE, RED]
        "3": [BLUE, RED, GREEN]
        "4": [BLUE, RED, GREEN, YELLOW]
        "5": [BLUE, RED, GREEN, YELLOW, ORANGE]
      kingdoms:
        blue:
          spawn: { x: -200.5, y: 70.0, z: 0.5, yaw: -90.0, pitch: 0.0 }
          heart: { x: -210, y: 72, z: 0 }
          base-region:
            min: { x: -250, y: -64, z: -70 }
            max: { x: -150, y: 180, z: 70 }
          ruin:
            center: { x: -210.5, y: 72.0, z: 0.5 }
            protected-regions: []
        red:
          spawn: { x: 200.5, y: 70.0, z: 0.5, yaw: 90.0, pitch: 0.0 }
          heart: { x: 210, y: 72, z: 0 }
          base-region:
            min: { x: 150, y: -64, z: -70 }
            max: { x: 250, y: 180, z: 70 }
          ruin:
            center: { x: 210.5, y: 72.0, z: 0.5 }
            protected-regions: []
```

L'exemple est un schéma cible, pas une carte prête à jouer. `enabled: false` empêche son vote tant que toutes les bases référencées par ses agencements ne sont pas définies. Le chargeur doit signaler précisément la clé, la valeur reçue et la valeur attendue. Il refuse les régions inversées ou chevauchées de manière incohérente, les positions hors bornes, les couleurs absentes, les matériaux inconnus et les agencements incapables de respecter la capacité.

## Commandes et permissions cibles

| Commande | Permission | Effet |
|---|---|---|
| `/fkadmin status` | `fallenkingdoms.admin` | Affiche session, état, carte, effectif et minuteurs. |
| `/fkadmin start` | `fallenkingdoms.admin` | Tente un démarrage en appliquant toutes les préconditions. |
| `/fkadmin cancel` | `fallenkingdoms.admin` | Annule le compte à rebours et revient à l'attente. |
| `/fkadmin stop` | `fallenkingdoms.admin` | Termine administrativement sans statistiques compétitives. |
| `/fkadmin reload` | `fallenkingdoms.admin` | Recharge seulement en `WAITING`, après validation complète. |
| `/stats` | `tropicube.stats` | Affiche sur Velocity les agrégats globaux puis le détail par jeu. |
| `/stats <joueur>` | `tropicube.stats.others` | Affiche les statistiques globales d'un autre joueur. |

La sélection de carte, royaume et kit reste graphique. Une commande refusée ne modifie jamais partiellement la session. Les textes sont fournis en `fr`, `en`, `de` et `es` via Adventure/MiniMessage.

## Protections par phase

| Action | `PREPARATION` | `PVP` | `ASSAULT` | `SUDDEN_DEATH` |
|---|---|---|---|---|
| JcJ en zone commune | interdit | autorisé | autorisé | autorisé |
| Entrée physique dans une base ennemie | repoussée | repoussée | autorisée | autorisée |
| Dégâts au cœur ennemi | interdits | interdits | autorisés | sans objet |
| Construction dans sa base | autorisée sauf interdits | identique | identique | identique |
| Construction en zone commune | liste blanche | liste blanche | liste blanche | liste blanche |
| Casse/pose manuelle en base ennemie | interdite | interdite | interdite | interdite |
| Brèche TNT configurée | inactive | inactive | autorisée | autorisée |

Les projectiles, explosions, pistons, fluides, portails, perles de l'End, fruits de chorus, véhicules et téléportations doivent passer par la même décision territoriale. Aucun mécanisme indirect ne doit contourner une frontière. Les spectateurs sont en `GameMode.SPECTATOR`, ne peuvent ni interagir ni influencer les entités, peuvent se téléporter entre joueurs et restent confinés aux bornes de la carte.

## Mort et réapparition

1. Le listener valide que la session est active et que le joueur participe.
2. L'inventaire tombe une seule fois ; le joueur devient `RESPAWNING` si son cœur vit, sinon `ELIMINATED`.
3. En `RESPAWNING`, il observe sans interaction et reçoit un décompte localisé.
4. Une tâche enregistrée dans `TaskRegistry` vérifie à l'échéance la session, la phase et l'état du cœur.
5. Si le cœur vit encore, le joueur réapparaît au spawn sûr de sa base, sans kit ni ancien inventaire, puis redevient `ACTIVE`.
6. Si le cœur a été détruit, la tâche est annulée et le joueur devient spectateur définitif.
7. Toute mort définitive déclenche un nouveau calcul des survivants du royaume.

Une déconnexion suit exactement ce pipeline avec la cause `DISCONNECT`, sans second dépôt d'inventaire au retour. La reconnexion réattache la session existante ; elle ne crée jamais une nouvelle vie.

## Élimination et ruine d'un royaume

L'élimination est déclenchée une seule fois lorsque le nombre de survivants atteint zéro. Le royaume passe à `ELIMINATED`, ses joueurs deviennent spectateurs et `RuinService` :

1. fige la liste de blocs candidats dans le rayon configuré ;
2. exclut conteneurs, inventaires, cœur, blocs techniques et régions protégées ;
3. sélectionne de façon bornée la proportion configurée de blocs destructibles ;
4. joue les vagues visuelles et sonores sans dégâts de cœur ni duplication d'objets ;
5. détruit uniquement la sélection autorisée ;
6. marque la base `RUINED` et son territoire `NEUTRAL`.

Les coffres, tonneaux et autres conteneurs conservent leur contenu. Une ruine n'est jamais déclenchée par la seule destruction du cœur.

## Victoire, statistiques et nettoyage

Le résultat immuable contient la cause (`LAST_KINGDOM`, `TIME_LIMIT`, `DRAW` ou `ADMIN_ABORT`), les royaumes gagnants, les survivants et les statistiques individuelles. Pour un résultat compétitif :

1. la machine entre dans `ENDING` et bloque combat/interactions ;
2. le HUD annonce le ou les vainqueurs ;
3. `StatisticsService` persiste le détail de partie puis les agrégats ;
4. Redis est invalidé ou actualisé uniquement après succès MySQL ;
5. après dix secondes par défaut, Velocity transfère les joueurs vers un lobby ;
6. toutes les tâches sont annulées, listeners rendus inactifs et ressources fermées ;
7. l'instance publie son état terminal et Velocity arrête le conteneur.

`ADMIN_ABORT` n'incrémente ni partie jouée, ni victoire, ni défaite, ni nul. Un échec de persistance est journalisé avec l'identifiant de partie, n'immobilise pas le thread serveur et ne transforme pas silencieusement un résultat en succès. Une stratégie de reprise idempotente doit éviter tout double comptage à partir de l'identifiant unique de partie.

## Événements Paper personnalisés

| Événement | Contrat |
|---|---|
| `FallenKingdomsPhaseChangeEvent` | Informatif, ancien/nouvel état et session ; non annulable. |
| `KingdomHeartDamageEvent` | Annulable ; dégâts modifiables avant application. |
| `KingdomHeartDestroyedEvent` | Informatif ; cause `PLAYER` ou `FORCED_SUDDEN_DEATH`. |
| `FallenKingdomsPlayerRespawnEvent` | Informatif après validation, avant téléportation sûre. |
| `KingdomEliminatedEvent` | Informatif, royaume et dernier événement causal. |
| `KingdomBaseRuinedEvent` | Informatif après neutralisation complète. |
| `FallenKingdomsGameEndEvent` | Résultat immuable, plusieurs gagnants possibles, cause de fin. |

Ils sont émis sur le thread Paper. Les observateurs ne doivent pas effectuer de SQL, Redis ou disque bloquant et ne doivent jamais muter le monde de manière asynchrone.

## Sauvegarde et restauration de carte

Une instance héberge une seule partie. Le monde propre est intégré à l'image Docker ou monté depuis le modèle d'instance, puis chargé avant que le serveur devienne joignable. La partie est terminale : après `ENDED`, les joueurs sont transférés et le conteneur est supprimé.

La V1 ne tient donc ni journal de blocs ni copie en mémoire pour redémarrer une partie sur le même processus. La restauration consiste à créer une nouvelle instance depuis l'image immuable. Les régions `.mca` restent sous Git LFS ; les données joueur du monde, journaux et fichiers générés ne sont pas versionnés.

Si l'arrêt Docker échoue, Velocity marque l'instance non joignable et le mécanisme d'exploitation existant reprend le nettoyage. Un redémarrage du plugin dans un monde déjà modifié ne tente pas de reprendre la partie : il refuse l'ouverture publique et demande le remplacement de l'instance.

## Interactions avec Core, Redis et Velocity

### Core

Fallen Kingdoms réutilise les services génériques de Core pour profils joueur, langues, Adventure/MiniMessage, grades et permissions. Le choix de kit préféré est une préférence par jeu. Le domaine Fallen Kingdoms reste dans son module.

Les statistiques deviennent un contrat réseau commun : parties jouées, victoires, nuls, éliminations, morts et objectifs/cœurs détruits sont agrégés globalement et détaillés par mini-jeu. SheepWars devra alimenter le même contrat lors de l'implémentation, avec tests de non-régression.

### MySQL et Redis

MySQL est la source durable. Une écriture atomique et idempotente persiste le résultat détaillé et met à jour les agrégats avec index sur identifiant de partie, UUID et jeu. Toutes les opérations sont hors thread Paper/Velocity.

Redis ne remplace pas MySQL. Il transporte les snapshots d'instance, événements de fin/transfert et caches de statistiques. Chaque message comporte version de schéma, identifiant de session, identifiant et propriétaire d'instance ; le consommateur les valide. Les clés éphémères ont une durée de vie explicite. Un cache n'est actualisé ou invalidé qu'après commit SQL réussi.

### Velocity

Velocity route les joueurs, expose `/stats`, déclenche une lecture asynchrone contrôlée en cas de cache absent et affiche une erreur localisée en cas de délai dépassé. Aucun appel SQL/Redis ne bloque un event loop Velocity. La vue présente d'abord l'agrégat tous jeux, puis le détail Fallen Kingdoms, SheepWars et des futurs jeux.

## Cas limites obligatoires

- Départ d'un joueur pendant le compte à rebours : recalculer les préconditions et revenir à `WAITING` si nécessaire.
- Vote gagnant pour une carte sans agencement compatible : l'exclure avant le vote ; refuser le départ si aucune carte ne convient.
- Événements exactement à 15:00, 25:00, 75:00 ou 90:00 : une seule transition, dans cet ordre, sans tick ambigu.
- Destruction d'un cœur pendant une réapparition : annuler la tâche et éliminer le joueur.
- Plusieurs dégâts de cœur dans le même tick : une seule destruction et une seule publication.
- Éliminations simultanées : calculer tous les effets du tick avant de figer le résultat ; le résultat peut être nul.
- Déconnexion du dernier survivant : appliquer le décès et l'élimination immédiatement.
- Reconnexion après destruction du cœur : spectateur, jamais de vie recréée.
- Arrivée tardive : spectateur sans affectation compétitive ni kit.
- Égalité à 90 minutes : tous les royaumes au maximum reçoivent victoire et nul.
- Aucun survivant à 90 minutes : tous les royaumes à zéro sont ex æquo ; le résultat nul les inclut uniquement si leur élimination était simultanée au traitement terminal, sinon seuls les royaumes encore admissibles au début du tick terminal.
- Bordure : atteindre exactement 50 × 50, même après retard scheduler, sans descendre sous cette taille.
- TNT à cheval sur plusieurs régions : ne modifier que les blocs autorisés, sans dégâts directs au cœur.
- Pistons, fluides, portails, perles et chorus : aucun franchissement interdit.
- Ruine : préserver tous les conteneurs et ne toucher aucun bloc hors sélection.
- Transition, fin ou nettoyage répétés : résultat stable et aucune tâche/listener restant actif.
- Arrêt administrateur : aucun agrégat compétitif.
- Changement de langue en partie : rafraîchir HUD, hotbar et inventaires mis en cache sans reconnexion.
- Panne MySQL/Redis : jeu et thread serveur restent disponibles, cache jamais annoncé à tort comme durable, reprise sans doublon.

## Critères d'acceptation testables

### Tests unitaires sans serveur

- Pour chaque effectif de 0 à 31, le calcul des royaumes accepte uniquement 8 à 30, produit 2 à 5 royaumes et respecte les tailles 4 à 6 avec un écart maximal de un.
- L'affectation est déterministe à graine identique, respecte les capacités et maximise les préférences après le critère d'équilibrage.
- La machine refuse toute transition non déclarée et rend chaque transition répétée sans effet supplémentaire.
- Les bornes 899/900, 1499/1500, 4499/4500 et 5399/5400 secondes déclenchent exactement les phases attendues.
- Les règles de victoire couvrent un survivant unique, plusieurs équipes, égalité au maximum, zéro survivant simultané et arrêt administrateur.
- Les validations YAML couvrent clés manquantes, types, ordre des durées, régions, positions, matériaux, layouts et capacités.
- La décision de protection couvre chaque phase, territoire, type d'action, projectile, explosion et mécanisme indirect.
- Les dégâts de cœur refusent allié, phase protégée et TNT, puis plafonnent les points de vie à zéro sans double destruction.
- La destruction d'un cœur élimine les joueurs en réapparition et place uniquement les survivants en dernière vie.
- La sélection de ruine est bornée, reproductible pour un test, préserve conteneurs/technique et reste dans la région autorisée.
- Le calcul de statistiques attribue victoire et nul à chaque gagnant ex æquo et exclut `ADMIN_ABORT`.
- Le résultat persistant est idempotent par identifiant de partie.

### Tests de ressources et de modules

- Le `config.yml` embarqué se charge et toutes les cartes activées sont valides.
- Les clés `fr`, `en`, `de` et `es` ont la même arborescence et les mêmes placeholders.
- Les permissions déclarées correspondent aux commandes et à la documentation.
- Fallen Kingdoms dépend des API partagées de Core/Docker API, jamais du métier SheepWars.
- Les contrats de statistiques compilent et leurs consommateurs Core, SheepWars, Fallen Kingdoms et Velocity disposent de tests de compatibilité.

### Scénarios sur serveur Paper/Docker

- Une partie à 8, 13, 19 et 25 joueurs crée respectivement 2, 3, 4 et 5 royaumes équilibrés.
- Le JcJ, l'entrée des bases, les cœurs et la construction suivent le tableau de protections à chaque transition.
- Une mort avec cœur vivant réapparaît nue après dix secondes ; sa destruction pendant le décompte rend le joueur spectateur.
- Une base n'explose pas à la destruction du cœur, mais se ruine une seule fois à l'élimination du dernier survivant en conservant les coffres.
- À 75 minutes, les cœurs restants disparaissent, les réapparitions cessent et la bordure commence sa réduction.
- À 90 minutes, la bordure mesure 50 × 50 et le résultat correspond au compte des survivants, y compris une égalité multi-vainqueur.
- Un spectateur tardif ne peut interagir, sortir des limites ni redevenir participant.
- `/lang` rafraîchit immédiatement les surfaces en cache.
- Après résultat ou arrêt administrateur, tous les joueurs sont transférés, toutes les tâches sont annulées et l'instance devient arrêtable.
- Les écritures de statistiques n'apparaissent sur Velocity qu'après succès MySQL ; une panne simulée ne bloque aucun thread serveur.

## Découpage d'implémentation approuvé

Chaque étape doit compiler et disposer de tests ciblés avant la suivante :

1. créer le squelette Paper Fallen Kingdoms, ses ressources et sa configuration validée ;
2. implémenter les modèles Java purs, la machine à états, l'horloge et leurs tests ;
3. charger et valider cartes, agencements, royaumes, équilibrage et votes ;
4. gérer roster, préférences, kits, arrivée tardive et reconnexion ;
5. implémenter les protections territoriales par adaptateurs Paper minces ;
6. ajouter cœurs, profils de dégâts et événements Paper ;
7. ajouter morts, réapparitions, dernière vie, élimination et ruine contrôlée ;
8. ajouter mort subite, bordure, victoire, HUD et localisation ;
9. généraliser dans Core le contrat de statistiques, migrer SheepWars avec tests de non-régression, puis brancher MySQL/Redis/Velocity ;
10. ajouter commandes administratives, arrêt, transfert et nettoyage idempotent ;
11. intégrer template Docker, monde de test, routage et scripts de déploiement ;
12. exécuter les scénarios Paper/Docker, le réacteur complet, les validations documentaires et la revue de sécurité.

Toute modification de Core ou SheepWars doit être motivée par le contrat partagé de l'étape 9 et livrée avec ses tests de non-régression. Aucune étape ne doit masquer une compilation rouge par un stub ou une implémentation vide.
