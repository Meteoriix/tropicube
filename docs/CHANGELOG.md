# Historique des changements

Ce document conserve les évolutions fonctionnelles, techniques et opérationnelles visibles du projet. Les modifications en cours restent sous `Non publié` jusqu'à la création d'une version.

## Non publié

### Ajouté

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

- Probabilités SheepWars affinées : les moutons polyvalents et lisibles deviennent plus fréquents, tandis que Distorsion, Mécha et Météore restent rares ; le menu affiche désormais le pourcentage effectif après désactivation et renormalisation.
- Équilibrage SheepWars revu : dégâts d'explosion déterministes sans cumul natif, moutons offensifs et de contrôle ajustés, Fragmentation plafonnée, kits normalisés, cadence stabilisée à 20 secondes, stock limité à cinq moutons et compensation de sous-effectif proportionnelle. Toutes les valeurs sensibles sont désormais regroupées et validées sous `gameplay-balance`.
- Arrêt de Velocity : les serveurs dynamiques et leurs volumes Docker anonymes sont désormais supprimés par défaut, sans toucher aux volumes persistants MySQL et Redis.
- Site documentaire aligné sur la charte graphique Tropicube : palette officielle, identité de marque, composants, navigation et affichage mobile.
- Site documentaire enrichi avec les pages Développement, Git/CI et Historique des changements.
- README complété avec le wrapper Maven et les contrôles automatiques disponibles.
- Consignes de création de mini-jeux renforcées : game design Markdown obligatoire, analyse comparative des jeux existants, machine à états, configuration, sécurité Paper et validation incrémentale.
- Fin des parties SheepWars : transfert confirmé de tous les joueurs au lobby, puis destruction immédiate du conteneur et purge de son état Redis.

### Corrigé

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
