# Historique des changements

Ce document conserve les évolutions fonctionnelles, techniques et opérationnelles visibles du projet. Les modifications en cours restent sous `Non publié` jusqu'à la création d'une version.

## Non publié

### Ajouté

- Socle d'exploitation Velocity : migrations SQL versionnées, modes d'instance typés, événements Redis versionnés, maintenance avec drain, MOTD bilingue, annonces ciblées, diagnostic réseau et protection adaptative des connexions.
- Communication et modération réseau : chat global, messages privés hors ligne, réglages d'ignorance, bannissements appliqués au proxy, file de signalements et preuves de chat conservées 90 jours.
- Outils staff sécurisés par TOTP : sessions secondaires de quinze minutes, codes de récupération, chat interserveurs et mode spectateur invisible sans interaction.

- Menu Paramètres du lobby avec choix de langue et rejeu automatique par séries confirmées de cinq parties.
- Système social réseau complet : amis persistants, parties Redis, chat de groupe, suivi individuel du chef, rassemblement `/party warp`, `/friend join` avec arrivée en spectateur et menu Social dans la hotbar du lobby.
- Commandes réseau `/pull <joueur>` et aide localisée `/help`, avec autocomplétion proxy étendue pour `/send`.
- Profil PvP SheepWars inspiré de Minecraft 1.8 : attaques sans recharge ni balayage, dégâts d'épée et recul configurables.
- Gestion complète des parties personnalisées privées : commande proxy `/whitelist`, item hôte SheepWars, saisie enclume, retrait par menu, persistance Redis et filtrage du lobby par joueur.
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

### Modifié

- Les membres déconnectés sont retirés de leur party après une minute ; le rôle de chef passe alors à un membre en ligne et une party entièrement hors ligne est supprimée immédiatement. Le menu Social affiche désormais la tête de profil de chaque ami.
- Les instances Lobby et SheepWars embarquent désormais les configurations Bukkit/Paper par défaut ; leur démarrage sur un volume neuf évite deux téléchargements séquentiels et gagne environ quatre secondes sur la pile de référence.
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
- Arrêt de Velocity : les serveurs dynamiques et leurs volumes Docker anonymes sont désormais supprimés par défaut, sans toucher aux volumes persistants MySQL et Redis.
- Site documentaire aligné sur la charte graphique Tropicube : palette officielle, identité de marque, composants, navigation et affichage mobile.
- Site documentaire enrichi avec les pages Développement, Git/CI et Historique des changements.
- README complété avec le wrapper Maven et les contrôles automatiques disponibles.
- Consignes de création de mini-jeux renforcées : game design Markdown obligatoire, analyse comparative des jeux existants, machine à états, configuration, sécurité Paper et validation incrémentale.
- Fin des parties SheepWars : transfert confirmé de tous les joueurs au lobby, puis destruction immédiate du conteneur et purge de son état Redis.

### Corrigé

- Les têtes du menu Social initialisent désormais leur résolution dynamique avec le seul UUID, puis embarquent la texture obtenue côté serveur ; elles n'affichent plus un skin par défaut causé par un profil statique incomplet et ne dépendent pas d'une reconnexion récente de l'ami.
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
- Les instances SheepWars en cours sont affichées avec le statut bleu `PLAYING` et acceptent les nouvelles connexions en mode spectateur, sans équipe ni impact sur les conditions de victoire.
- Accès à `/nick` refusé aux grades autorisés à cause d'une clé Redis incohérente ; le contrat de grade est désormais partagé avec Core, stable pendant les transferts et actualisé lors des changements de grade. `/nick off`, les arguments invalides, les requêtes concurrentes et la purge multi-backend sont également sécurisés.
- Distribution SheepWars trop rapide : la cadence standard passe de 10 à 20 secondes et le tirage indépendant conserve les probabilités annoncées sur toutes les durées de partie.
- Instances SheepWars actives affichées hors ligne après l'échec d'un auto-stop : Velocity restaure désormais leur statut précédent dans Redis, et le Lobby exclut les états arrêtés ou en erreur de ses listes et totaux.
- Faux échec de `/tropi stop` lorsque le proxy de socket Docker perdait la réponse HTTP après avoir effectivement arrêté le conteneur ; l'état réel est maintenant vérifié et la commande retentée une fois si nécessaire.
- Configuration d'autoscaling rendue explicite : les templates Lobby et SheepWars autorisent chacun jusqu'à cinq instances, et la documentation distingue maintien du minimum et création SheepWars à la demande.
- Cache persistant de l'ancienne interface documentaire après déploiement : les pages référencent désormais `styles.css` avec une empreinte de contenu automatiquement validée.
- URL de téléchargement du Maven Wrapper épinglée sur Maven 3.9.11.
- Déconnexion des clients à la mort d'un joueur SheepWars causée par un conflit entre les équipes de scoreboard et les équipes temporaires de surlignage.
- Démarrage automatique des parties SheepWars classiques dès que deux joueurs sont présents, tout en conservant un lancement manuel par défaut pour les parties personnalisées.
- Suppression des serveurs fantômes : une instance prête sans réponse depuis 60 secondes est retirée de Docker, Velocity et de toutes ses références Redis connues.
- Créations SheepWars multiples lors de clics répétés : une seule instance classique est désormais créée par template et tous les joueurs attendent son démarrage avant connexion automatique.
