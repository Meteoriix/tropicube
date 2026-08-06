# Historique des changements

Ce document conserve les évolutions fonctionnelles, techniques et opérationnelles visibles du projet. Les modifications en cours restent sous `Non publié` jusqu'à la création d'une version.

## Non publié

### Ajouté

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

- Site documentaire enrichi avec les pages Développement, Git/CI et Historique des changements.
- README complété avec le wrapper Maven et les contrôles automatiques disponibles.
- Consignes de création de mini-jeux renforcées : game design Markdown obligatoire, analyse comparative des jeux existants, machine à états, configuration, sécurité Paper et validation incrémentale.
- Fin des parties SheepWars : transfert confirmé de tous les joueurs au lobby, puis destruction immédiate du conteneur et purge de son état Redis.

### Corrigé

- URL de téléchargement du Maven Wrapper épinglée sur Maven 3.9.11.
- Déconnexion des clients à la mort d'un joueur SheepWars causée par un conflit entre les équipes de scoreboard et les équipes temporaires de surlignage.
- Démarrage automatique des parties SheepWars classiques dès que deux joueurs sont présents, tout en conservant un lancement manuel par défaut pour les parties personnalisées.
- Suppression des serveurs fantômes : une instance prête sans réponse depuis 60 secondes est retirée de Docker, Velocity et de toutes ses références Redis connues.
- Créations SheepWars multiples lors de clics répétés : une seule instance classique est désormais créée par template et tous les joueurs attendent son démarrage avant connexion automatique.
