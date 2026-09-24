# Éditeur de langues

L'éditeur de langues Tropicube est une application web locale destinée aux ressources MiniMessage et aux interfaces de Core, Lobby, SheepWars et Velocity. Il découvre les langues ainsi que les manifestes `scoreboards.yml`, `tablists.yml` et `menus.yml`, utilise le français comme source et maintient leurs copies Docker.

## Démarrage

Java 25, Node.js 24, npm et le client Docker sont requis. Sous Windows, lancer `.\language-editor.ps1`. Sous Linux ou macOS, lancer `./language-editor.sh`. Le script construit l'application, la démarre dans un processus indépendant, ouvre le navigateur et rend ensuite le terminal. Fermer le terminal n'arrête donc pas l'éditeur.

Les commandes de cycle de vie sont identiques sur les deux plateformes :

```text
.\language-editor.ps1 start       # démarrage détaché (action par défaut)
.\language-editor.ps1 status      # état et PID
.\language-editor.ps1 stop        # arrêt du processus détaché
.\language-editor.ps1 foreground  # exécution attachée pour le diagnostic
```

Remplacer le nom du script par `./language-editor.sh` sous Linux ou macOS. `-NoBrowser` sous Windows ou `--no-browser` sous Linux et macOS empêche l'ouverture automatique du navigateur. Les PID et journaux d'exécution sont conservés localement sous `tools/language-editor/.runtime/` et ignorés par Git.

L'application écoute uniquement sur `http://127.0.0.1:8765`. `TROPICUBE_LANGUAGE_EDITOR_PORT` permet de remplacer ce port.

## Traduction

L'éditeur accepte tout endpoint compatible LibreTranslate. Le service local optionnel se démarre avec :

```bash
docker compose --env-file .env.example --profile language-editor up -d libretranslate
```

`LIBRETRANSLATE_URL` vaut `http://127.0.0.1:5000` par défaut. `LIBRETRANSLATE_API_KEY` permet d'utiliser une instance externe protégée et ne doit jamais être ajoutée au dépôt.

Dans l'édition structurée des textes, scoreboards et tablists, chaque modification française programme automatiquement la régénération de l'anglais, de l'allemand et de l'espagnol après 800 ms sans frappe. Les trois traductions partent directement du français, restent modifiables manuellement et une réponse devenue obsolète ne peut pas écraser une saisie plus récente. Les balises MiniMessage, placeholders, commandes, termes du glossaire et symboles Unicode décoratifs tels que `▶`, `⚠` ou `🌴` ne sont pas envoyés comme texte traduisible et sont conservés à leur position d'origine. Une indisponibilité du service n'empêche pas de préparer un brouillon et est indiquée sous le champ français.

La validation reconnaît toutes les balises fournies par `StandardTags.defaults()` dans la version Adventure du projet, ainsi que les balises internes `<tc>` et `<sw>`. Dans la vue structurée, la touche Entrée insère directement un saut de ligne et l'éditeur l'enregistre sous la forme MiniMessage `<br>` (`<newline>` reste accepté comme alias long). Une ligne vide est donc conservée. Dans le mode YAML brut, écrire `<br>` sans espaces. Un lore composé de plusieurs lignes doit utiliser une seule clé et séparer ses lignes avec `<br>`. Lors de l'affichage d'un item, Core, Lobby et SheepWars convertissent aussi bien ces balises que les retours déjà enregistrés dans YAML en lignes de lore Minecraft distinctes ; les doubles retours restent des lignes vides et ne sont jamais envoyés au client comme des glyphes de contrôle.

La sérialisation conserve chaque chaîne YAML sur une seule ligne physique, quelle que soit sa longueur, afin de rester compatible avec la fusion de configuration des scripts de déploiement.

Toutes les ressources utilisent des placeholders nommés canoniques comme `{player}`, `{balance}` ou `{countdown}`. Deux emplacements qui reçoivent la même information réutilisent le même nom ; les anciens noms dérivés des clés de traduction ont été regroupés, tandis que les informations différentes disposent de noms explicites comme `{instance_id}`, `{notification_id}` ou `{request_id}`. Le placeholder global `{instance_name}` renvoie le nom visible de l'instance Paper courante (`SERVER_NAME`) ou, pour un message Velocity destiné à un joueur, le nom du serveur auquel il est connecté. `{player_grade}` renvoie le préfixe MiniMessage du grade du joueur destinataire, sans son pseudo ; Core le lit depuis son cache local et Velocity depuis le cache Redis alimenté à la connexion et lors d'un changement de grade. L'adaptateur interne des anciens appels Java ignore ces placeholders globaux lorsqu'il associe les arguments restants dans leur ordre déclaré, sans réintroduire de placeholder positionnel dans les fichiers de langue. Une valeur texte est échappée avant son insertion MiniMessage ; seul un composant explicitement riche conserve son style.

## Édition et sécurité

- la vue structurée permet de chercher par clé ou dans le texte des quatre langues, puis de créer, renommer et supprimer une clé ;
- le mode YAML français convient aux changements groupés ;
- les aperçus couvrent chat, titres, actionbar, inventaires, lores, scoreboard et tablist ;
- la validation contrôle YAML, parité des clés, placeholders et balises autorisées ;
- une écriture est refusée si l'un des fichiers effectivement modifiés dans le brouillon a changé depuis son ouverture ; les manifestes non modifiés ne participent pas au contrôle de conflit ;
- les quatre sources et leurs miroirs Docker sont remplacés ensemble, avec rollback sur erreur ;
- après l'enregistrement, les fichiers validés sont copiés dans Velocity ou dans toutes les instances Paper actives, puis la commande interne `languageeditorreload` recharge les langues et interfaces concernées ; chaque conteneur écrit un accusé de réception seulement après le remplacement du catalogue et le rafraîchissement des vues ;
- le bouton `Appliquer` reste désactivé pendant l'opération afin d'éviter deux écritures concurrentes ; un conflit ou une erreur conserve le brouillon et s'affiche dans la barre d'état ;
- la barre d'état distingue les fichiers enregistrés d'un rechargement live complet, partiel ou impossible. Une erreur live n'annule pas les sources déjà enregistrées.
- l'onglet Scoreboards regroupe les variantes d'un même scoreboard, permet d'éditer son titre et chacune de ses lignes dans les quatre langues, affiche leur rendu MiniMessage avec les valeurs d'exemple des placeholders, puis permet d'ajouter, retirer ou réordonner ses quinze lignes Minecraft ;
- l'onglet Tablists regroupe Lobby et SheepWars par variante d'état, édite en-tête et pied dans les quatre langues et prévisualise l'ensemble autour de joueurs d'exemple ;
- l'onglet Menus affiche la grille de l'inventaire, les boutons requis, les zones dynamiques et les propriétés localisées des items ;
- l'onglet Placeholders inventorie automatiquement tous les placeholders nommés canoniques de Core et Velocity, décrit précisément l'information métier fournie par chacun, permet de les rechercher et affiche chaque module et clé de traduction qui les utilise ;
- pendant l'édition structurée d'un texte, le sélecteur compact en bas à droite recherche les mêmes placeholders, affiche leur description et insère le jeton choisi à la position du curseur ;
- les brouillons de manifestes disposent d'un historique annuler/rétablir, du glisser-déposer et d'un récapitulatif avant application.

La synchronisation live utilise exclusivement le client Docker local, des conteneurs Tropicube actifs et leur RCON interne. Elle ne publie aucun port supplémentaire et ne lit aucun mot de passe. `TROPICUBE_DOCKER_COMMAND` permet de remplacer le nom de l'exécutable `docker`. Une première livraison de cette version reste nécessaire pour installer le moteur. Les applications suivantes ne nécessitent plus de rebuild : Core publie une génération complète et hashée dans Redis, et toute nouvelle instance la restaure avant d'initialiser ses langues et interfaces.

L'aperçu des items cherche le client Minecraft 26.3 local. `TROPICUBE_MINECRAFT_CLIENT_JAR` permet d'indiquer explicitement son JAR ; les textures lues sont conservées uniquement en mémoire par l'éditeur.

Le catalogue versionné `tools/language-editor/catalog.yml` contient le glossaire, les corrections de contexte et les exemples de placeholders. Les traductions automatiques restent des propositions : relire au minimum l'anglais et vérifier en jeu les interfaces sensibles à la largeur du texte.

## Validation

```bash
npm --prefix tools/language-editor/frontend ci
npm --prefix tools/language-editor/frontend test
npm --prefix tools/language-editor/frontend run build
./mvnw -f tools/language-editor/backend/pom.xml verify
```
