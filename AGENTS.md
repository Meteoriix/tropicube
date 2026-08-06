# Instructions durables pour les agents Tropicube

Ce fichier s'applique à l'ensemble du dépôt. Il sert de contexte et de définition de qualité pour toute intervention future, y compris dans un nouveau chat.

## Objectif du projet

Tropicube est un réseau Minecraft 26.2 composé d'un proxy Velocity, de serveurs Paper dynamiques, de Redis, de MySQL et d'une orchestration Docker. Java 25 et Maven 3.9.11 sont les versions de référence.

Modules Maven :

- `tropicube-docker-api` : modèles partagés, accès Docker et Redis ;
- `tropicube-velocity` : routage proxy, files d'attente et cycle de vie des instances ;
- `tropicube-core` : données joueur, économie, grades, permissions, langues et modération ;
- `tropicube-lobby` : accueil, menus, sélection de serveurs et fonctionnalités lobby ;
- `tropicube-sheepwars` : mini-jeu SheepWars ;
- `tropicube-fallenkingdoms` : emplacement réservé à un futur mini-jeu.

Les fichiers Markdown sous `docs/` sont la documentation source. `docs-site/` est une sortie HTML statique générée par `docs-site/build.mjs`.

## Priorités obligatoires

1. Préserver la correction fonctionnelle, la sécurité des données et la compatibilité Windows/Linux.
2. Privilégier un code lisible, simple, testable et mesurable avant une abstraction prématurée.
3. Éviter toute opération réseau, SQL, Redis, Docker ou disque bloquante sur le thread principal Paper/Velocity.
4. Documenter et tester chaque changement dans la même intervention.
5. Ne jamais inclure de secret, `.env`, mot de passe, token, export SQL ou secret de forwarding dans Git.
6. Préserver les modifications utilisateur déjà présentes et ne jamais employer une commande Git destructive sans demande explicite.
7. Conserver les régions Minecraft `.mca` sous Git LFS et ne jamais versionner les données joueur ou les JAR tiers locaux.

## Méthode de travail

Avant de modifier :

- lire `git status`, le POM du module, ses ressources et les pages documentaires associées ;
- rechercher les consommateurs dans les autres modules, Redis, Docker et les fichiers de langues ;
- identifier les contraintes de thread, de cycle de vie et de compatibilité de configuration ;
- pour un bug, déterminer sa cause puis ajouter si possible un test de non-régression.

Pendant l'implémentation :

- garder une responsabilité claire par classe et une dépendance explicite par constructeur ;
- préférer les objets immuables, les validations aux frontières et les retours précoces ;
- fermer correctement tâches planifiées, pools, connexions et abonnements lors de l'arrêt d'un plugin ;
- journaliser les erreurs avec leur contexte sans exposer de secret ;
- éviter les allocations répétées dans les tâches exécutées chaque tick et mettre en cache les objets immuables coûteux ;
- ne pas optimiser sans mesure, mais corriger immédiatement les appels bloquants ou boucles non bornées sur le thread serveur.

Après l'implémentation :

- exécuter les tests proportionnels au changement, puis `mvnw verify` pour un changement transversal ;
- valider les ressources, Docker Compose et les scripts concernés ;
- reconstruire et valider `docs-site/` ;
- contrôler `git diff`, `git status` et l'absence de secret avant le commit ;
- résumer les validations réellement exécutées et signaler toute vérification manuelle restante.

## Style Java et documentation du code

- Utiliser Java 25 et les API Paper/Velocity ciblées par le POM parent.
- Respecter une indentation de quatre espaces, UTF-8 et les règles `.editorconfig`.
- Employer des noms métier explicites ; éviter les abréviations opaques, nombres magiques et booléens ambigus.
- Limiter les méthodes, extraire les règles métier réutilisées et éviter les classes gestionnaires omniscientes.
- Exposer des collections non modifiables lorsque l'appelant ne doit pas muter l'état.
- Vérifier les valeurs de configuration au chargement avec une erreur exploitable plutôt qu'une défaillance tardive.
- Utiliser Adventure Components et MiniMessage pour le texte joueur ; ne pas réintroduire les anciens codes couleur `§`.
- Utiliser `ItemBuilder` selon son API actuelle et centraliser la construction d'items répétés.

Tout type public, API partagée et comportement non évident doit avoir une JavaDoc utile. Les commentaires expliquent le pourquoi, les invariants, les contraintes de thread ou de protocole ; ils ne paraphrasent pas une instruction évidente. Lorsqu'un changement modifie un comportement, sa documentation de classe et ses commentaires concernés doivent être actualisés dans le même commit.

## Langues et cohérence visuelle

Les langues supportées sont `fr`, `en`, `de` et `es`, dans :

- `tropicube-core/src/main/resources/languages/` ;
- `tropicube-velocity/src/main/resources/languages/` ;
- leurs copies de déploiement sous `dockerfiles/configs/TropicubeCore/` et `dockerfiles/configs/TropicubeVelocity/`.

Pour chaque nouvelle clé :

1. ajouter exactement la même arborescence dans les quatre langues ;
2. conserver les mêmes placeholders, leur nombre, leur ordre et leur sens ;
3. traduire naturellement, sans laisser une langue de secours masquer une clé manquante ;
4. examiner les clés voisines et harmoniser préfixe, palette MiniMessage, gras, italique, ponctuation et icônes ;
5. conserver les conventions visuelles du contexte : succès, avertissement, erreur, titre, lore ou action ;
6. mettre à jour les copies sous `dockerfiles/configs/` lorsqu'elles sont utilisées au déploiement ;
7. exécuter les tests YAML et de parité des langues.

Une modification de langue doit également actualiser toutes les interfaces mises en cache, par exemple scoreboard, inventaire ou hotbar. Vérifier le comportement après `/lang` sans reconnexion.

## Configuration et compatibilité

- Toute nouvelle option doit avoir une valeur par défaut sûre, une validation et une description dans `docs/CONFIGURATION.md`.
- Préserver la compatibilité des anciennes configurations lorsque cela est raisonnable ; utiliser le mécanisme de mise à jour de configuration existant dans Core.
- Synchroniser les ressources embarquées des plugins avec les fichiers actifs sous `dockerfiles/configs/` lorsque les deux représentent la même configuration.
- Ne jamais publier les ports des serveurs Paper dynamiques directement ; les joueurs passent par Velocity.
- Le secret de forwarding Velocity/Paper doit rester identique et provenir de l'environnement, jamais d'une valeur réelle versionnée.
- Toute modification de Redis doit préciser clés, durée de vie, atomicité et consommateurs ; toute modification SQL doit prévoir migration, index et compatibilité des données existantes.

## Tests et qualité

- Utiliser JUnit pour les règles métier, parseurs, modèles, catalogues et validations de ressources.
- Un correctif de bug doit inclure un test reproduisant le défaut dès que le code peut être isolé des API serveur.
- Tester les cas nominaux, limites, erreurs, appels répétés et nettoyage d'état.
- Ne pas simuler toute l'API Paper ou Velocity : extraire le métier dans des services Java purs et garder les listeners/commandes comme adaptateurs minces.
- Les scénarios dépendant d'un serveur réel doivent être listés dans le commit et la pull request, puis validés sur la pile Docker.
- JaCoCo produit les rapports sous `target/site/jacoco/`. Utiliser la couverture pour identifier les trous, sans écrire de tests sans assertion métier uniquement pour augmenter un pourcentage.

Commandes de référence :

```powershell
.\mvnw.cmd clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
.\deploy.ps1 -OnlyImages -ValidateOnly
```

```bash
bash ./mvnw clean verify
node docs-site/build.mjs
node docs-site/validate.mjs
docker compose --env-file .env.example config --quiet
./deploy.sh --only-images --validate-only
```

## Documentation obligatoire

Chaque changement fonctionnel, de configuration, d'architecture ou d'exploitation doit :

- mettre à jour la page appropriée dans `docs/` et le `README.md` si le point est structurant ;
- ajouter une entrée concise sous `Non publié` dans `docs/CHANGELOG.md` ;
- mettre à jour `docs/COMMANDS.md` pour toute commande, alias ou permission ;
- mettre à jour `docs/CONFIGURATION.md` pour toute clé ou variable ;
- mettre à jour `docs/ARCHITECTURE.md` pour tout flux ou contrat entre modules ;
- mettre à jour `docs/DEPLOYMENT.md` pour tout prérequis, conteneur ou script ;
- mettre à jour `docs/SHEEPWARS.md` si le game design SheepWars évolue ;
- exécuter `node docs-site/build.mjs` et `node docs-site/validate.mjs` ;
- inclure les Markdown et HTML générés dans le même commit que le changement.

Un refactoring interne sans effet utilisateur doit au minimum être expliqué dans le corps du commit. S'il introduit une convention ou change la structure du projet, documenter cette convention dans `docs/DEVELOPMENT.md` ou `AGENTS.md`.

## Git, commits et versions

Suivre `docs/GIT_CI.md`. Lorsqu'une demande autorise des modifications, créer un commit pour chaque changement important et cohérent avant la remise finale, sauf si l'utilisateur demande explicitement de ne pas committer, si des changements sans rapport empêchent un commit sûr, ou si l'identité Git n'est pas configurée.

- Ne jamais committer un build rouge.
- Ne jamais mélanger une fonctionnalité et un refactoring sans rapport.
- Utiliser `type(portée): résumé à l'impératif`, par exemple `feat(sheepwars): ajoute le vote de carte`.
- Renseigner le corps pour expliquer motivation, choix, impacts, migrations, tests et documentation.
- Inspecter le diff préparé et l'absence de secret avant chaque commit.
- Ne pas réécrire l'historique publié, forcer un push, créer un tag ou pousser vers un remote sans autorisation explicite.
- Appliquer le versionnement sémantique et tenir `docs/CHANGELOG.md` à jour.
- Vérifier `git lfs status` lorsqu'une carte ou une région Minecraft est ajoutée ou modifiée.

## Ajouter une fonctionnalité depuis un game design

Lire intégralement le document de game design et transformer son contenu en éléments vérifiables :

- boucle de jeu et conditions de victoire/défaite ;
- états et transitions ;
- équipes, rôles, classes, kits, objets et capacités ;
- règles de combat, temporisations et équilibrage configurable ;
- cartes, spawns, limites du monde et restauration ;
- interface joueur, commandes, permissions, messages et sons ;
- données persistantes, statistiques, reconnexion et abandon ;
- interactions avec lobby, Velocity, Redis, Docker et Core ;
- critères d'acceptation, tests automatiques et scénarios en jeu.

Signaler les ambiguïtés qui changeraient fortement le résultat. Pour les détails réversibles, choisir une valeur par défaut configurable et documenter l'hypothèse.

Séparer le domaine du framework : modèles et règles métier Java purs, services applicatifs, puis adaptateurs Paper/Velocity/Redis. Définir explicitement le cycle de vie, rendre les transitions idempotentes et empêcher qu'un événement tardif agisse sur une partie terminée.

## Créer un nouveau mini-jeu

Pour un mini-jeu autonome :

1. créer un module Maven `tropicube-<nom>` et l'ajouter au POM parent ;
2. réutiliser Core et Docker API sans dupliquer leurs responsabilités ;
3. créer le descripteur Paper, la configuration par défaut et les ressources nécessaires ;
4. modéliser états, règles et catalogue dans des classes testables indépendantes de Bukkit ;
5. ajouter commandes, permissions et textes localisés ;
6. gérer proprement démarrage, compte à rebours, partie, fin, reconnexion, arrêt et nettoyage ;
7. ajouter template Docker, configuration, monde ou mécanisme de chargement de carte ;
8. intégrer la création/routage Velocity, Redis, Compose et les deux scripts de déploiement ;
9. ajouter tests unitaires et scénarios d'intégration ;
10. créer une page complète de game design dans `docs/`, l'ajouter à `docs-site/build.mjs`, puis documenter commandes, configuration, architecture et déploiement ;
11. vérifier Windows et Linux, reconstruire le site, exécuter le réacteur complet et committer par étapes cohérentes.

Le module ne doit pas supposer une seule instance, conserver un état global entre deux parties ou faire confiance à un événement Redis sans valider l'identifiant et le propriétaire de l'instance.

## Définition de terminé

Une tâche n'est terminée que si :

- le comportement demandé fonctionne et les interactions entre modules ont été examinées ;
- le code compile, les tests ciblés et globaux appropriés réussissent ;
- ressources, langues, configuration et copies Docker sont cohérentes ;
- Windows et Linux restent compatibles lorsque des scripts ou chemins changent ;
- les commentaires utiles, JavaDoc, Markdown, changelog et site HTML sont à jour ;
- le diff ne contient ni secret, ni artefact, ni changement utilisateur écrasé ;
- un commit clair et complet a été créé pour tout changement important autorisé ;
- les limites ou validations manuelles restantes sont explicitement signalées.
