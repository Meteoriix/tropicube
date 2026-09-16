# Historique des changements

Ce document conserve les évolutions fonctionnelles, techniques et opérationnelles visibles du projet. Les modifications en cours restent sous `Non publié` jusqu'à la création d'une version.

## Non publié

### 2026-09-16

- Corrige SheepWars : toutes les maps sont proposées au vote dans tous les modes, le leader apparaît dans le scoreboard, l'XP réseau et les menus d'attente sont nettoyés, Galions empoisonne dans l'eau sans limite basse, les flèches bonus durent cinq secondes, le mouton Échange ne dash plus sans cible et les lignes vides du scoreboard sont rendues.
- Supprime le démarrage automatique des serveurs SheepWars et Fallen Kingdoms en fixant leur plancher d'instances à zéro ; seul le Lobby reste préchauffé.
- Active le combat 1.8 par défaut dans Fallen Kingdoms, affiche les PV du cœur allié dans l'actionbar et abaisse le départ Quick Play à six joueurs sans modifier les seuils bêta.
- Simplifie et harmonise les tablists Lobby, SheepWars et Fallen Kingdoms autour de la marque Tropicube et de l'adresse du serveur, puis allège le scoreboard du Lobby.
- Corrige et ajuste Fallen Kingdoms : conteneurs utilisables, construction territoriale et portillons cohérents, JcJ à 5 minutes, assaut à 15 minutes, mort subite à 45 minutes, fin à 60 minutes, Alchimiste, 32 steaks et suppression de l'XP réseau à l'arrivée.
- Réorganise le HUD Fallen Kingdoms : équipes actives sans PV de cœur dans le scoreboard, prochaine phase nommée, tablist colorée dès l'attente, actionbar contextuelle et bossbar temporaire du cœur ennemi frappé.
- Abrège les soldes en milliards avec le suffixe `B` et plafonne chaque solde TropiCoins à 100 milliards, y compris pour les transferts, missions et récompenses de saison.
- Préserve le format MiniMessage du nom de file bêta dans le message de connexion et retire les balises visibles de sa description d'annulation.
- Libère dans le profil Docker de développement la capacité nécessaire aux files Fallen Kingdoms bêta en lançant aussi le template classique à la demande.

### 2026-09-15

- Ajoute une catégorie Bêta au sélecteur de jeux avec deux files Fallen Kingdoms créées à la demande : seuil d'un ou deux joueurs par royaume, sur deux à cinq royaumes.
- Aligne la salle d'attente Fallen Kingdoms sur SheepWars : hotbar complète, choix courants explicites, menus manifestes, descriptions de kits, effectifs de préférences, vote visible, contrôle hôte, Profil et retour au lobby.
- Corrige l'ouverture du sélecteur Fallen Kingdoms au premier tick de connexion lorsque le grade joueur est encore en cours de chargement asynchrone.
- Termine le contrat Fallen Kingdoms V1 : vote de carte, préférences persistantes, kits historiques complets, combat 1.8 limité, dégâts indirects alliés neutralisés, événements Paper, décompte de réapparition, ruines neutralisées, HUD manifeste, fin atomique et reprise d'instance protégée.
- Ajoute l'écran Lobby des parties Fallen Kingdoms personnalisées et transmet à Velocity une liste blanche de capacités, chronologie, cœurs, réapparition, combat, kits et ruines.
- Supprime le menu intermédiaire de choix du mode : le clic gauche sur un jeu lance de nouveau directement sa partie Quick Play, tandis que les raccourcis Classé et Parties publiques restent disponibles.
- Nettoie les sauvegardes, données joueur, fichiers de session, données temporaires de mod et régions vides générés lors de l'édition du monde Fallen Kingdoms, puis renforce leur exclusion de Git.
- Démarre automatiquement une instance Fallen Kingdoms Quick Play avec le proxy et maintient ce plancher comme pour SheepWars.

### 2026-09-14

- Supprime l'enveloppe Redis `NetworkEvent` restée sans consommateur, les régions Minecraft strictement vides et les reliquats de mods ou d'anciens formats présents dans les mondes modèles ; retire aussi les descriptions obsolètes du module Fallen Kingdoms.
- Remplace l'appel Paper déprécié de santé maximale dans Fallen Kingdoms, élimine l'avertissement varargs de la configuration Redis Velocity et aligne le test d'intégration sur l'index réel des migrations.
- Distribue les plugins Paper tiers vérifiés vers Fallen Kingdoms afin que la dépendance HeadDatabase de Core soit satisfaite au démarrage d'une instance.
- Ajuste l'enveloppe mémoire Fallen Kingdoms du profil de développement à 1–2 Gio afin qu'une instance puisse démarrer avec Lobby et SheepWars dans le budget local de 8 Gio.
- Aligne les métadonnées Paper de l'image Fallen Kingdoms sur les autres backends afin que le contrôle de préchauffage autorise son déploiement.
- Livre Fallen Kingdoms V1 jouable : session générique indépendante des cartes, sélecteurs de royaume et kit, protections territoriales, cœurs, réapparitions/reconnexions, ruines, mort subite, HUD multilingue, résultat persistant idempotent et nettoyage Velocity.
- Active le template Velocity Fallen Kingdoms avec la carte Cactus immuable et ajoute la migration MySQL générique `V010__game_statistics.sql`.

- Carte Cactus Fallen Kingdoms : intégration du monde Git LFS, de ses cinq bases, cœurs, spawns, zone jouable et bordure initiale de 490 blocs.

- Socle Fallen Kingdoms : module Paper, modèle de phases et de cœurs testable, équilibrage public des royaumes, configuration validée, commande d'administration et image Docker.

- Documentation Fallen Kingdoms actualisée : cycle d'instances, interfaces localisées, Geyser/Floodgate et déploiement.
- Le classement Fallen Kingdoms est prévu après le socle V1 avec des formats fixes propres au jeu et une cote Elo indépendante de SheepWars.

### 2026-09-11

- Simplifie le redéploiement de développement : `deploy.ps1` et `deploy.sh` recréent directement Velocity avec les images vérifiées, sans maintenance, sauvegarde ni délai de drain ; les parties dynamiques en cours sont arrêtées. La livraison de production reste une activation explicite d'un lot préparé.

### 2026-09-10

- Harmonise les menus Core, Lobby et SheepWars avec le cadrage déclaré par leurs manifestes, y compris les choix rapides neutres. Les manifestes valident maintenant cadres et régions dynamiques au chargement.
- Ajoute une confirmation claire avant l'achat d'un grade et la suppression définitive des notifications lues, dans les quatre langues.

- Corrige le démarrage des backends dynamiques : le flux de logs Docker reste ouvert au-delà de la fenêtre de disponibilité Paper, ce qui évite de supprimer un Lobby ou SheepWars encore en initialisation.

- Migre la disposition Profil antérieure qui plaçait Guildes en case 24, désormais réservée au Vestiaire, avant validation de la configuration restaurée.

- Intègre les cosmétiques à la Boutique avec confirmation et achat atomique sans double débit ; vérifie concurrence et rollback MySQL, relie les déblocages aux récompenses existantes et complète les protections de navigation/rafraîchissement après `/lang`.

- Ajoute le vestiaire à filtres et aperçus privés, les sélections persistantes par catégorie, le rendu Lobby borné et la suspension des accès VIP perdus ; inclut export/anonymisation et tests MySQL isolés.

- Rend le choix des modes de jeu accessible au clic gauche, ajoute Guide et Progression, recharge les compteurs du Profil et protège les lectures tardives ; corrige les icônes Profil Bedrock. Ajoute le catalogue configurable et validé des futurs déblocages.

### 2026-09-09

- Supprime les apparitions de terminal des tâches Windows : lancement direct par `pythonw.exe`, sous-processus sans console et conservation des journaux et codes de retour du diagnostic et des sauvegardes.

### 2026-09-08

- Ajoute un profil de développement Windows avec sauvegardes locales Restic chiffrées, répertoire privé protégé par ACL, tâches planifiées et commande de déploiement réutilisable ; le mode de production exige toujours SFTP.
- Fixe le budget dynamique de la configuration Docker de développement à 8192 Mio, distinct du défaut embarqué de 16384 Mio.

### 2026-09-07

- Fiabilise le démarrage Core/Lobby/SheepWars avec préparation réseau asynchrone et admission après disponibilité applicative ; borne la file SQL et les délais des pools, et retire les accès SQL/Redis de la déconnexion Paper.
- Sépare heap Java et limite Docker, ajoute un budget mémoire dynamique et un propriétaire aux ressources ; la file classique attend lorsque le budget est épuisé.
- Vérifie les empreintes des migrations, fournit sauvegardes chiffrées SFTP, diagnostic léger, rotation des logs et activation de lots vérifiés avec maintenance et sauvegarde préalable.
- Ajoute tests MySQL/Redis isolés, tests d'échec de l'outillage, validation native Windows et suivi Dependabot des images et de l'éditeur.

### 2026-09-06

- La restauration des interfaces Redis complète désormais les langues avec les nouvelles clés du JAR avant leur chargement, en conservant les personnalisations. Une ancienne génération pouvait supprimer 79 clés par langue, notamment dans Social et les guildes, malgré des catalogues embarqués complets.
- Un audit automatique vérifie les références de traduction de tous les plugins dans les quatre langues, y compris les familles dynamiques connues. Les clés des résultats de guilde, du solde insuffisant et du filtre de partie rapide sont corrigées ; les copies Docker restent synchronisées.
- Social accueille la gestion complète des guildes : invitations, membres, défis, classement et saisie privée annulable ; le bouton quitte le Profil. Les mutations revalident les droits et la guilde affichée sous verrou SQL ; rôles, défis et résultats sont localisés.

### 2026-09-04

#### Ajouté

- Le proxy Velocity accepte désormais les clients Bedrock via Geyser et Floodgate sur un port UDP configurable, avec artefacts vérifiés, clé privée persistante et identités compatibles avec Social et les whitelists SheepWars.
- SheepWars affiche désormais des cibles de laine lumineuse entre les bases : une flèche leur fait accorder à toute l'équipe un soin, des flèches empoisonnées ou un bonus de vitesse, avec effets, poids, positions et réapparition configurables.

#### Corrigé

- Les clients Bedrock reçoivent des menus allégés sans vitres décoratives ainsi que le pack intégré Geyser obligatoire ; les onze icônes HeadDatabase employées par Tropicube sont préenregistrées et ne retombent plus sur une tête de Steve.
- Les faux enchantements utilisés comme scintillement décoratif dans les menus et la hotbar ne sont plus ajoutés pour les clients Bedrock ; les enchantements de gameplay restent inchangés.
- Les têtes de joueur dynamiques du Profil et des écrans Social utilisent désormais des icônes d'état vanilla sur Bedrock au lieu de retomber sur une tête de Steve ; Java conserve les skins résolus.
- SheepWars n'affiche plus qu'une cible de power-up à la fois, alterne parmi cinq emplacements par carte sans plafond logiciel, vérifie automatiquement ses clés de langue et rend correctement le statut coloré du résumé sans balise MiniMessage visible.

### 2026-09-03

#### Ajouté

- Le placeholder global `{instance_name}` expose dans tous les textes le nom visible de l'instance Paper courante ou du serveur auquel le joueur est connecté côté Velocity.
- L'éditeur propose une vue Tablists pour modifier en quatre langues et prévisualiser les en-têtes et pieds du Lobby et des états SheepWars ; chaque ligne de scoreboard est désormais éditable au même endroit que sa disposition.
- L'éditeur propose un onglet Placeholders qui regroupe les noms utilisés dans toutes les langues et indique leurs clés et modules consommateurs.
- Chaque placeholder affiche désormais une description de sa valeur dynamique ; un sélecteur compact permet de les rechercher et de les insérer au curseur pendant l'édition d'un texte.

#### Modifié

- Le scoreboard du Lobby présente désormais séparément le grade du joueur, la file, le réseau et l'instance courante ; le scoreboard d'attente SheepWars masque la ligne redondante du minimum de joueurs.
- Le placeholder global `{player_grade}` expose désormais le grade MiniMessage du joueur destinataire sans inclure son pseudo, dans les rendus Core comme Velocity et dans le catalogue de l'éditeur.
- Les 291 placeholders issus de la migration positionnelle ont été regroupés en 139 noms métier canoniques ; l'éditeur fournit une description précise et un exemple lisible pour chacun.
- La saisie française régénère automatiquement EN, DE et ES après une courte temporisation dans les éditeurs de textes, scoreboards et tablists ; les boutons de proposition et d'approbation intermédiaire ont été supprimés.
- L'interface de l'éditeur clarifie l'état de la traduction automatique, améliore les retours de focus et s'adapte aux fenêtres plus étroites.
- Le scoreboard du Lobby affiche désormais `{instance_name}` plutôt que l'identifiant technique de l'instance.
- Le Lobby renseigne désormais le placeholder `{instance_number}` de son scoreboard au lieu de l'afficher littéralement ; les aperçus automatisés vérifient également le remplacement de plusieurs placeholders.

### 2026-09-02

#### Ajouté

- L'éditeur regroupe désormais les scoreboards et menus de Core, Lobby et SheepWars, avec aperçus complets, ajout, suppression, réorganisation, historique et textures Minecraft 26.2 locales.
- Une API partagée de placeholders nommés protège les valeurs texte MiniMessage tout en acceptant temporairement les placeholders positionnels existants.
- Les configurations live sont conservées sous forme de générations hashées dans Redis afin que les nouvelles instances récupèrent la dernière version approuvée.
- L'éditeur de langues synchronise désormais chaque enregistrement validé avec les conteneurs Core ou Velocity actifs et recharge les textes en jeu sans rebuild d'image.
- Ajout d'un éditeur web local des langues avec aperçu MiniMessage contextuel, traduction LibreTranslate contrôlée, glossaire et écritures atomiques des quatre langues.
- L'éditeur de langues permet désormais de choisir une recherche par clé ou par texte dans les quatre traductions.
- Les lanceurs Windows et Linux de l'éditeur le démarrent désormais dans un processus indépendant et proposent les actions `start`, `status`, `stop` et `foreground` sans monopoliser le terminal.

#### Modifié

- Les derniers placeholders positionnels des ressources Core et Velocity ont été migrés vers des noms stables, identiques dans les quatre langues et leurs copies Docker.
- Les titres des scoreboards sont désormais éditables dans les quatre langues et leur aperçu applique réellement le formatage MiniMessage et les placeholders d'exemple.
- La vue structurée de l'éditeur de langues permet désormais d'insérer naturellement des sauts de ligne avec Entrée, puis les conserve sous forme de balises MiniMessage `<br>`.
- Les lores auparavant répartis entre des clés numérotées sont regroupés dans une clé unique utilisant `<br>` ; le sélecteur de langue suit la même convention dans sa configuration.

#### Corrigé

- Les sauts de ligne des lores sont désormais convertis en véritables lignes d'item dans Core, Lobby et SheepWars, y compris les lignes vides, au lieu d'afficher un glyphe de contrôle inconnu.
- L'éditeur et les tests de ressources reconnaissent désormais toutes les balises MiniMessage standard de la version Adventure configurée, notamment `<br>` et `<newline>`.
- L'éditeur ne replie plus les longues chaînes YAML sur plusieurs lignes physiques, ce qui préserve leur compatibilité avec la fusion de configuration au déploiement.
- Les symboles Unicode décoratifs, notamment `▶`, sont désormais conservés à l'identique dans les traductions anglaises, allemandes et espagnoles proposées par l'éditeur de langues.
- Le serveur de l'éditeur termine désormais correctement les réponses des fichiers statiques, afin que l'interface charge sans rester en attente.
- La validation d'une traduction anglaise conserve désormais simultanément les propositions allemande et espagnole au lieu d'écraser l'allemand.
- Le nom du profil dans la hotbar sépare désormais correctement son icône de son libellé dans les quatre langues.

### 2026-08-28

#### Corrigé

- La traduction allemande de `/level` emploie désormais une terminologie naturelle pour la catégorie d’accès et la limite hiérarchique des modérateurs.

### 2026-08-27

#### Modifié

- Refonte complète des autorisations autour de `vipLevel` (0–3) et `modLevel` (0–4) : grades cosmétiques, commande `/level`, audit SQL, cache Redis révisionné et suppression des permissions individuelles, des UUID administrateurs et des opérateurs Docker.
- Les achats, commandes et expirations de grade appliquent désormais atomiquement les niveaux configurés ; Velocity utilise les mêmes niveaux pour `/nick`, les files prioritaires et les commandes d'exploitation.
- L'XP gagnée en partie ou via une mission contribue désormais automatiquement à la guilde, dans la limite hebdomadaire configurée.
- Le chat global applique `tropicube.chat.color` aux seules couleurs et décorations MiniMessage sûres ; les messages privés en ligne et hors ligne utilisent la langue du destinataire.
- Les classes, méthodes, permissions, configurations SheepWars et 85 clés de langue sans consommateur ont été retirées, ainsi que leurs copies Docker et tests obsolètes.

#### Corrigé

- La préparation MySQL est désormais sérialisée entre les backends ; V008 utilise une syntaxe compatible MySQL 9.7 et reprend sans doublon une exécution interrompue.
- `/level` est à nouveau publié dans les suggestions Velocity ; les anciennes entrées `/permissions` et `/tropiperm` ont été retirées.
- `/maintenance` utilise et valide désormais `maintenance.default-deadline-minutes` lorsqu'aucune durée n'est fournie.

### 2026-08-25

#### Modifié

- Les builds Maven et les déploiements Windows/Linux synchronisent désormais exactement les traductions Core et Velocity depuis leurs ressources vers `dockerfiles/configs`, avec vérification automatique de parité.

### 2026-08-24

#### Modifié

- Les menus Core, Lobby et SheepWars partagent désormais le même cadrage ; Profil utilise la tête du joueur, les missions et paramètres expliquent leur contenu, et les actions ainsi que valeurs métier sont localisées sans identifiants techniques.
- Profil adopte désormais la grille de 54 cases et les indications de clic explicites des sélecteurs ; Social affiche « Social • Amis » et les demandes de party reprennent les colonnes reçues/envoyées du menu Amis, avec annulation au clic droit.
- La sélection du type de partie personnalisée indique désormais « Clic gauche : sélectionner ce type et choisir un jeu ».
- Le niveau réseau et la progression vers le niveau suivant sont désormais affichés dans la barre d'expérience et actualisés après chaque gain d'XP.

#### Corrigé

- Le grand titre « Bienvenue sur Tropicube » est désormais réservé à l'arrivée initiale au lobby après connexion au proxy ; les retours depuis un mini-jeu conservent uniquement l'actionbar discrète.
- Les têtes Profil de la hotbar et du menu complet conservent désormais systématiquement leur libellé localisé au lieu de reprendre « Player's Head ».

### 2026-08-23

#### Ajouté

- Navigation Social en deux onglets Amis/Party, gestion visuelle des membres et invitations de party, et transfert ciblé avec `/party warp <joueur>`.
- Sous-menu Social paginé pour les demandes d'amis reçues et envoyées : têtes de profil, acceptation au clic gauche, refus ou annulation au clic droit.
- Expérience lobby immersive et persistante : accueil adaptatif selon l'origine de connexion, préférence d'effets, scoreboard orienté profil/action et état de file publié en temps réel.
- Sélecteur de jeux à trois actions : clic gauche Quick Play, clic droit choix Ranked 4v4/8v8, `Maj + clic gauche` navigateur unifié filtrable des instances Quick Play et personnalisées publiques.
- Boutique restructurée avec accueil puis onglet Grades, distinction fiable entre avantages actifs et « Bientôt », déduction du prix des grades déjà achetés et achat transactionnel économie/grade.
- Outils staff sécurisés par TOTP : sessions secondaires liées à la connexion réseau, codes de récupération, chat interserveurs et mode spectateur invisible sans interaction.

#### Modifié

- La session 2FA du staff reste active pendant toute la connexion au réseau et est révoquée par Velocity à la déconnexion réelle.
- Les entrées de la section `Non publié` sont désormais datées et classées de la plus récente à la plus ancienne.
- Le menu Social distingue désormais le clic gauche pour rejoindre le serveur d'un ami et le clic droit pour l'inviter dans la party ; les invitations d'ami et de party reçues peuvent être acceptées directement depuis le chat.
- La hotbar du lobby est désormais `Jeux 0`, `Social 2`, `Profil 4` et `Boutique 8`. Les parties personnalisées quittent la hotbar et restent visibles, avec explication du grade VIP+ requis, dans le sélecteur de jeux.
- Le Centre Tropicube devient Profil, utilise la tête du joueur et intègre Paramètres. Les inventaires Core et Lobby partagent désormais le même cadrage, la même palette et les mêmes contrôles.
- Une seule file de matchmaking peut être active par joueur : rejoindre un autre format remplace la précédente et le menu Ranked permet de la quitter explicitement.
- Le catalogue `/help` affiche désormais une commande principale par ligne, conserve uniquement ses véritables alias sur cette ligne et détaille les arguments et sous-commandes dans les quatre langues.

#### Corrigé

- L'item Profil de la hotbar conserve désormais son libellé localisé au lieu d'afficher le nom de tête anglais généré par Minecraft.
- Le navigateur des instances publiques s'ouvre désormais avec `Maj + clic gauche` dans le sélecteur de jeux, une interaction disponible en mode Aventure contrairement au clic molette.
- Les UUID administrateurs Velocity reçoivent désormais les permissions d'exploitation manquantes ; `/netdiag`, `/maintenance` et `/announce` sont utilisables après reconnexion.
- Les paramètres entre chevrons des aides et usages de commandes s'affichent désormais littéralement dans les quatre langues, y compris pour la 2FA et SheepWars ; les derniers codes couleur `§` ont été remplacés par MiniMessage et les tests vérifient désormais palettes, balises, placeholders, parité Velocity et copies Docker.

### 2026-08-22

#### Ajouté

- Toutes les commandes Tropicube enregistrées par le proxy et les backends sont désormais proposées après `/`, en respectant les permissions ; les hotbars Lobby et SheepWars partagent un accès localisé au centre joueur.
- Chaîne de migrations renforcée : V003 est désormais indexée et un test interdit toute ressource SQL oubliée ; V004 prépare les archives, récompenses de profil, aides contextuelles et demandes de confidentialité.
- SheepWars Quick Play et compétition : files 4v4/8v8 à cote partagée, incertitude individuelle, fenêtre progressive, saisons trimestrielles archivées, placements et reset souple.
- Progression SheepWars : XP propre à chaque kit en Quick Play, deux branches exclusives réversibles débloquées au niveau 5 et effets équilibrés pilotés par YAML, missions/niveau réseau, historique détaillé et distinctions de fin.
- Garde-fous compétitifs : taille de party limitée à une demi-équipe, limites de rôles, vote court pondéré sur trois cartes et sanctions d'abandon graduées.
- Socle d'exploitation Velocity : migrations SQL versionnées, modes d'instance typés, événements Redis versionnés, maintenance avec drain, MOTD bilingue, annonces ciblées, diagnostic réseau et protection adaptative des connexions.
- Communication et modération réseau : chat global, messages privés hors ligne, réglages d'ignorance, bannissements appliqués au proxy, file de signalements et preuves de chat conservées 90 jours.
- Profils réseau à détails réglables, niveaux réseau, catalogue YAML de missions versionné, rotations personnelles de 5 quotidiennes et 3 hebdomadaires, rerolls et récompenses atomiques.
- Lobby enrichi avec aide contextuelle désactivable, visibilité persistante tous/amis/party/personne et sélection de partie intelligente orientée remplissage.
- Guildes persistantes complètes : 50 membres, rôles bornés, invitations, audit, contributions plafonnées, défis hebdomadaires, classement compétitif agrégé et succession automatique du chef inactif.
- Centre joueur localisé : profil enrichi, archives saisonnières, titres/badges, missions avec jetons de reroll, classement des guildes et boîte de notifications filtrable conservée sept jours.
- Outillage de confidentialité TOTP : exports JSON exhaustifs supprimés après sept jours, demandes d'anonymisation différées de trente jours, annulation, suivi et gel légal des preuves de modération.
- Reconnexion classée SheepWars : grâce de trois minutes, forfait après trente secondes lorsqu'une équipe entière est hors ligne, sanctions graduées uniquement à expiration ou lors d'un abandon explicite.
- Récompenses de saison SheepWars : archivage trimestriel idempotent, reset souple, titre, badge et monnaie sans avantage de jeu pour les joueurs ayant terminé leurs placements.

#### Modifié

- `/help` couvre désormais toutes les commandes et fonctionnalités réseau dans cinq rubriques localisées, dont une rubrique sociale dédiée et une rubrique staff masquée sans permission.
- Le MOTD public affiche désormais une phrase d'accroche et la liste dédupliquée des jeux activés, sans indicateur FR/EN ni compteur de joueurs.

#### Corrigé

- Le parcours 2FA staff distingue inscription, session inactive et session active, propose ses sous-commandes, rend les secrets copiables et harmonise les couleurs dans les quatre langues ; la consommation des codes est désormais transactionnelle contre les validations concurrentes.
- Les migrations V002 et V004 ajoutent désormais leurs colonnes de façon idempotente avec une syntaxe compatible MySQL ; Core, Lobby et SheepWars ne se désactivent plus en cascade au démarrage.
- Les libellés de récompenses saisonnières utilisent de nouveau des clés plates compatibles avec la fusion de langues au déploiement ; V006 convertit les éventuelles récompenses déjà enregistrées.
- Le précontrôle des scripts de déploiement diagnostique désormais une clé `TOTP_MASTER_KEY` absente ou invalide et indique comment générer une clé AES-256 sans la versionner.
- Toutes les migrations SQL présentes sont désormais indexées ; la contrainte qui empêchait plusieurs résultats SheepWars pour une même instance a été remplacée par un index d'historique non unique.
- Le mode staff restaure le mode de jeu antérieur, y compris après un transfert interserveurs, au lieu d'imposer systématiquement le mode Aventure.

### 2026-08-21

#### Modifié

- Les membres déconnectés sont retirés de leur party après une minute ; le rôle de chef passe alors à un membre en ligne et une party entièrement hors ligne est supprimée immédiatement. Le menu Social affiche désormais la tête de profil de chaque ami.
- Les instances Lobby et SheepWars embarquent désormais les configurations Bukkit/Paper par défaut ; leur démarrage sur un volume neuf évite deux téléchargements séquentiels et gagne environ quatre secondes sur la pile de référence.

#### Corrigé

- Les têtes du menu Social initialisent désormais leur résolution dynamique avec le seul UUID, puis embarquent la texture obtenue côté serveur ; elles n'affichent plus un skin par défaut causé par un profil statique incomplet et ne dépendent pas d'une reconnexion récente de l'ami.

### 2026-08-15

#### Ajouté

- Menu Paramètres du lobby avec choix de langue et rejeu automatique par séries confirmées de cinq parties.
- Système social réseau complet : amis persistants, parties Redis, chat de groupe, suivi individuel du chef, rassemblement `/party warp`, `/friend join` avec arrivée en spectateur et menu Social dans la hotbar du lobby.
- Commandes réseau `/pull <joueur>` et aide localisée `/help`, avec autocomplétion proxy étendue pour `/send`.
- Profil PvP SheepWars inspiré de Minecraft 1.8 : attaques sans recharge ni balayage, dégâts d'épée et recul configurables.

#### Modifié

- Le bouton du sélecteur de parties utilise la tête HeadDatabase 52706, le réglage de langue s'intitule désormais « Langues / Language » et un nouveau profil reprend la langue du client Minecraft avec repli anglais.
- Le bouton Langue du menu Paramètres reprend la tête HeadDatabase 71786, le bouton Paramètres de la hotbar utilise la tête 89489 et l'autocomplétion après `/` publie uniquement les commandes Tropicube.
- Le proxy masque les commandes étrangères à Tropicube, refuse les commandes Bukkit/vanilla et les backends bloquent les interfaces de panneaux, fours, tables de craft et coffres. `/money` n'affiche plus que le solde personnel.
- Une invitation de party peut désormais être acceptée depuis une autre party : le départ, la promotion éventuelle d'un nouveau chef et l'entrée dans la nouvelle party sont atomiques. SheepWars rapproche les membres d'une party dans la même équipe tant que l'écart d'effectif reste au plus égal à un.
- La hotbar du lobby utilise les têtes HeadDatabase 35309 et 78804 pour les parties personnalisées et Social, puis se rafraîchit après un changement de grade.
- Les limites de joueurs choisies par l'hôte SheepWars sont bornées à deux, ne peuvent plus passer sous l'effectif présent et sont publiées immédiatement à Velocity.
- Les images Lobby et SheepWars préchauffent désormais Paper 26.2, le JAR Mojang et le runtime patché pendant le déploiement ; les instances dynamiques ne retéléchargent plus le serveur à chaque création.
- Le menu enclume de whitelist privée ne demande plus d'expérience, valide strictement pseudo/UUID et attend désormais la confirmation de sauvegarde de Velocity avant de rafraîchir ses membres.
- `/rejoin` devient l'unique forme de reconnexion à une partie SheepWars : l'ancienne commande `/sw join` est supprimée et tout argument est désormais refusé.
- Cadence SheepWars ramenée à 15 secondes par défaut et adaptée à l'effectif des parties classiques (10/12/15 secondes) ; les parties personnalisées conservent le réglage de l'hôte.
- Dégâts des moutons TNT, chercheur, fragmentation, feu et météore augmentés ; portée du mouton feu étendue et dégâts d'arc légèrement réduits.
- Commandes harmonisées autour de `/play`, `/rejoin`, `/replay`, `/lobby`, `/permissions`, `/coreadmin` et `/tropicube`, avec maintien des anciens noms comme alias.
- Sélecteur de langue modernisé avec des drapeaux en carrés colorés, sans symboles Unicode régionaux.
- Noms et descriptions des classes, kits et moutons des menus SheepWars désormais localisés dans les quatre langues.
- Les advancements sont désactivés sur tous les backends Paper, et une partie personnalisée terminée ne précrée plus de serveur suivant.
- Le lancement SheepWars annule désormais la vélocité et la distance de chute résiduelles des joueurs téléportés depuis le vide de la salle d'attente.
- En partie SheepWars personnalisée sans vote de carte, la carte choisie par l'hôte apparaît désormais immédiatement dans le scoreboard d'attente de tous les joueurs.
- Scoreboards Lobby et SheepWars enrichis et aérés par des séparateurs tropicaux : profil, solde, fréquentation réseau et parties visibles au lobby ; carte, capacité, minimum requis, équipe, classe et statistiques personnelles en jeu. Les données d'économie restent chargées hors du thread principal.
- Tablists et scoreboards Lobby/SheepWars harmonisés avec une identité tropicale plus lisible, des titres localisés et des états de partie structurés ; les connexions et arrivées/départs de partie deviennent des annonces narratives sans préfixe.
- Messages et locales entièrement harmonisés : identité `TROPICUBE >` ou `SHEEPWARS >` sans crochets décoratifs, grades modernisés, préfixes limités aux commandes et notifications, et logs MiniMessage rendus en ANSI avec fallback texte.
- Probabilités SheepWars affinées : les moutons polyvalents et lisibles deviennent plus fréquents, tandis que Distorsion, Mécha et Météore restent rares ; le menu affiche désormais le pourcentage effectif après désactivation et renormalisation.
- Équilibrage SheepWars revu : dégâts d'explosion déterministes sans cumul natif, moutons offensifs et de contrôle ajustés, Fragmentation plafonnée, kits normalisés, cadence stabilisée à 20 secondes, stock limité à cinq moutons et compensation de sous-effectif proportionnelle. Toutes les valeurs sensibles sont désormais regroupées et validées sous `gameplay-balance`.

#### Corrigé

- Le menu Social ne provoque plus de `LinkageError` : Core est désormais l'unique fournisseur Paper des modèles `tropicube-docker-api`, qui ne sont plus dupliqués dans les JAR Lobby et SheepWars.
- Le menu Social gère désormais ses erreurs de construction, n'affiche plus la balise littérale `<tc>` et emploie le style de lore commun à la hotbar.
- Les préfixes MiniMessage `<tc>` et `<sw>` ne propagent plus leur gras au corps des messages ; seuls la marque et les segments explicitement balisés restent en gras.
- Cycle de vie Docker de Velocity : `/server` utilise désormais un `tmpfs` initialisé depuis l'image et libéré automatiquement à l'arrêt ; les scripts suppriment l'ancien volume anonyme lors de la migration.
- SheepWars : l'explosion ou les dégâts d'un lanceur ne lui rendent plus son propre mouton ; seuls les moutons détruits par un autre joueur peuvent être récupérés. Les rails détachés par la destruction de leur bloc de support ne génèrent plus d'item.
- Nettoyage Docker : les instances de jeu utilisent des volumes `/data` éphémères étiquetés, supprimés explicitement avec leur conteneur et purgés s'ils deviennent orphelins.
- Distribution SheepWars : un joueur ne peut plus recevoir trois fois de suite le même type de mouton lorsqu'au moins deux types ont un poids positif.
- Distribution SheepWars durcie : aucun type désactivé ne peut servir de secours, le dernier poids actif ne peut plus être mis à zéro depuis le menu, une configuration désactivant tous les moutons est réparée, et le sélecteur pondéré est testé sur ses intervalles, ses fréquences et ses cas extrêmes.
- Délais et probabilités SheepWars corrigés : chaque remise utilise désormais directement les pourcentages configurés, les échéances sont individuelles et ne sont plus perdues lorsque le stock est plein, et une partie standard fournit 29 remises périodiques utiles en plus du mouton initial.
- `/nick off` ne supprime plus son état de récupération avant la restauration effective par le backend et peut réparer une identité visuelle désynchronisée au lieu de répondre à tort qu'aucun nick n'est actif.
- Un changement d'équipe dans la salle d'attente SheepWars actualise désormais immédiatement la couleur du pseudonyme dans la tablist, y compris avec `/nick`.
- Le chat SheepWars actualise désormais le pseudonyme après `/nick off`, et la tablist retire le cœur devant les noms tout en conservant leur couleur d'équipe ou de spectateur.
- La tablist SheepWars masque désormais systématiquement les grades et réapplique, après chaque changement d'identité, le pseudonyme seul dans la couleur de l'équipe ou en gris pour un spectateur.
- `/nick off` restaure désormais aussi le pseudonyme réel dans la tablist du lobby ; un rafraîchissement différé ne peut plus réinjecter l'ancien nom de profil nické.
- Après reconnexion avec `/nick`, le message d'entrée du lobby utilise désormais le pseudonyme et le grade fictif restaurés, sans révéler le grade réel.
- Le rafraîchissement différé de la tablist du lobby conserve désormais le pseudonyme `/nick` en plus du grade fictif, au lieu de réafficher le nom réel du profil.
- `/lobby` affiche désormais un message neutre quand le joueur est déjà au lobby ; hors identité `/nick`, la tablist et le message de connexion conservent le grade réel.
- Persistance du grade d'affichage factice de `/nick` après reconnexion : la déconnexion ne réduit plus son TTL à 30 secondes et le lobby utilise désormais ce grade dans la tablist sans modifier les permissions réelles. Les anciens payloads Redis restent compatibles ; les noms nickés conservent aussi la bonne couleur d'équipe dans SheepWars.
- Distribution SheepWars trop rapide : la cadence standard passe de 10 à 20 secondes et le tirage indépendant conserve les probabilités annoncées sur toutes les durées de partie.

### 2026-08-14

#### Ajouté

- Gestion complète des parties personnalisées privées : commande proxy `/whitelist`, item hôte SheepWars, saisie enclume, retrait par menu, persistance Redis et filtrage du lobby par joueur.

### 2026-08-12

#### Corrigé

- Les instances SheepWars en cours sont affichées avec le statut bleu `PLAYING` et acceptent les nouvelles connexions en mode spectateur, sans équipe ni impact sur les conditions de victoire.
- Accès à `/nick` refusé aux grades autorisés à cause d'une clé Redis incohérente ; le contrat de grade est désormais partagé avec Core, stable pendant les transferts et actualisé lors des changements de grade. `/nick off`, les arguments invalides, les requêtes concurrentes et la purge multi-backend sont également sécurisés.

### 2026-08-06

#### Ajouté

- Dépôt Git local initialisé avec une branche principale `main`.
- Maven Wrapper 3.9.11 pour des builds reproductibles sous Windows et Linux.
- CI GitHub Actions couvrant Maven, JUnit, JaCoCo, le site documentaire, Docker Compose et les scripts de déploiement.
- Dependabot pour la surveillance des dépendances Maven et GitHub Actions.
- Règles EditorConfig, Git attributes et exclusions Git communes.
- Git LFS pour les régions Minecraft, avec exclusion des JAR tiers et des données joueur volatiles.
- Tests d'invariants pour le catalogue SheepWars et rapports de couverture JaCoCo.
- Guides de développement, d'utilisation de Git/CI et consignes durables `AGENTS.md`.
- Game design historique complet du mode Fallen Kingdoms d'Epicube, intégré au site documentaire.
- Spécification technique de Fallen Kingdoms : périmètre V1, machine à états, configurations, protections, persistance réseau et critères d'acceptation.

#### Modifié

- Arrêt de Velocity : les serveurs dynamiques et leurs volumes Docker anonymes sont désormais supprimés par défaut, sans toucher aux volumes persistants MySQL et Redis.
- Site documentaire aligné sur la charte graphique Tropicube : palette officielle, identité de marque, composants, navigation et affichage mobile.
- Site documentaire enrichi avec les pages Développement, Git/CI et Historique des changements.
- README complété avec le wrapper Maven et les contrôles automatiques disponibles.
- Consignes de création de mini-jeux renforcées : game design Markdown obligatoire, analyse comparative des jeux existants, machine à états, configuration, sécurité Paper et validation incrémentale.
- Fin des parties SheepWars : transfert confirmé de tous les joueurs au lobby, puis destruction immédiate du conteneur et purge de son état Redis.

#### Corrigé

- Instances SheepWars actives affichées hors ligne après l'échec d'un auto-stop : Velocity restaure désormais leur statut précédent dans Redis, et le Lobby exclut les états arrêtés ou en erreur de ses listes et totaux.
- Faux échec de `/tropi stop` lorsque le proxy de socket Docker perdait la réponse HTTP après avoir effectivement arrêté le conteneur ; l'état réel est maintenant vérifié et la commande retentée une fois si nécessaire.
- Configuration d'autoscaling rendue explicite : les templates Lobby et SheepWars autorisent chacun jusqu'à cinq instances, et la documentation distingue maintien du minimum et création SheepWars à la demande.
- Cache persistant de l'ancienne interface documentaire après déploiement : les pages référencent désormais `styles.css` avec une empreinte de contenu automatiquement validée.
- URL de téléchargement du Maven Wrapper épinglée sur Maven 3.9.11.
- Déconnexion des clients à la mort d'un joueur SheepWars causée par un conflit entre les équipes de scoreboard et les équipes temporaires de surlignage.
- Démarrage automatique des parties SheepWars classiques dès que deux joueurs sont présents, tout en conservant un lancement manuel par défaut pour les parties personnalisées.
- Suppression des serveurs fantômes : une instance prête sans réponse depuis 60 secondes est retirée de Docker, Velocity et de toutes ses références Redis connues.
- Créations SheepWars multiples lors de clics répétés : une seule instance classique est désormais créée par template et tous les joueurs attendent son démarrage avant connexion automatique.
