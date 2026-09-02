# Éditeur de langues

L'éditeur de langues Tropicube est une application web locale destinée aux ressources MiniMessage et aux interfaces de Core, Lobby, SheepWars et Velocity. Il découvre les langues ainsi que les manifestes `scoreboards.yml` et `menus.yml`, utilise le français comme source et maintient leurs copies Docker.

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

Le français est traduit vers l'anglais pour revue. Après approbation, l'allemand et l'espagnol sont générés directement depuis le français. Les balises MiniMessage, placeholders, commandes, termes du glossaire et symboles Unicode décoratifs tels que `▶`, `⚠` ou `🌴` ne sont pas envoyés comme texte traduisible et sont conservés à leur position d'origine. Une indisponibilité du service n'empêche pas de préparer un brouillon.

La validation reconnaît toutes les balises fournies par `StandardTags.defaults()` dans la version Adventure du projet, ainsi que les balises internes `<tc>` et `<sw>`. Dans la vue structurée, la touche Entrée insère directement un saut de ligne et l'éditeur l'enregistre sous la forme MiniMessage `<br>` (`<newline>` reste accepté comme alias long). Une ligne vide est donc conservée. Dans le mode YAML brut, écrire `<br>` sans espaces. Un lore composé de plusieurs lignes doit utiliser une seule clé et séparer ses lignes avec `<br>`. Lors de l'affichage d'un item, Core, Lobby et SheepWars convertissent aussi bien ces balises que les retours déjà enregistrés dans YAML en lignes de lore Minecraft distinctes ; les doubles retours restent des lignes vides et ne sont jamais envoyés au client comme des glyphes de contrôle.

La sérialisation conserve chaque chaîne YAML sur une seule ligne physique, quelle que soit sa longueur, afin de rester compatible avec la fusion de configuration des scripts de déploiement.

Les nouvelles API acceptent des placeholders nommés comme `{player}`, `{balance}` ou `{countdown}`. Les anciennes formes positionnelles `{0}` restent lisibles pendant la migration. Une valeur texte est échappée avant son insertion MiniMessage ; seul un composant explicitement riche conserve son style.

## Édition et sécurité

- la vue structurée permet de chercher par clé ou dans le texte des quatre langues, puis de créer, renommer et supprimer une clé ;
- le mode YAML français convient aux changements groupés ;
- les aperçus couvrent chat, titres, actionbar, inventaires, lores, scoreboard et tablist ;
- la validation contrôle YAML, parité des clés, placeholders et balises autorisées ;
- une écriture est refusée si un fichier a changé depuis son ouverture ;
- les quatre sources et leurs miroirs Docker sont remplacés ensemble, avec rollback sur erreur ;
- après l'enregistrement, les fichiers validés sont copiés dans Velocity ou dans toutes les instances Paper actives, puis la commande interne `languageeditorreload` recharge uniquement les langues ;
- la barre d'état distingue Docker disponible d'une synchronisation live partielle ou impossible, sans annuler les fichiers déjà enregistrés.
- l'onglet Scoreboards regroupe les variantes d'un même scoreboard, affiche les quinze lignes Minecraft et permet de les ajouter, retirer ou réordonner ;
- l'onglet Menus affiche la grille de l'inventaire, les boutons requis, les zones dynamiques et les propriétés localisées des items ;
- les brouillons de manifestes disposent d'un historique annuler/rétablir, du glisser-déposer et d'un récapitulatif avant application.

La synchronisation live utilise exclusivement le client Docker local, des conteneurs Tropicube actifs et leur RCON interne. Elle ne publie aucun port supplémentaire et ne lit aucun mot de passe. `TROPICUBE_DOCKER_COMMAND` permet de remplacer le nom de l'exécutable `docker`. Une première livraison de cette version reste nécessaire pour installer le moteur. Les applications suivantes ne nécessitent plus de rebuild : Core publie une génération complète et hashée dans Redis, et toute nouvelle instance la restaure avant d'initialiser ses langues et interfaces.

L'aperçu des items cherche le client Minecraft 26.2 local. `TROPICUBE_MINECRAFT_CLIENT_JAR` permet d'indiquer explicitement son JAR ; les textures lues sont conservées uniquement en mémoire par l'éditeur.

Le catalogue versionné `tools/language-editor/catalog.yml` contient le glossaire, les corrections de contexte et les exemples de placeholders. Les traductions automatiques restent des propositions : relire au minimum l'anglais et vérifier en jeu les interfaces sensibles à la largeur du texte.

## Validation

```bash
npm --prefix tools/language-editor/frontend ci
npm --prefix tools/language-editor/frontend test
npm --prefix tools/language-editor/frontend run build
./mvnw -f tools/language-editor/backend/pom.xml verify
```
