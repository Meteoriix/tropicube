# Charte des messages

Cette charte garantit une identité cohérente entre Core, Lobby, Velocity et les mini-jeux. Tous les textes utilisent MiniMessage, y compris les messages techniques avant leur rendu console.

## Identité

- les notifications réseau commencent par `<gold><bold>TROPICUBE</bold></gold> <dark_gray>></dark_gray>` via la balise `<tc>` ;
- les notifications SheepWars commencent par `<aqua><bold>SHEEPWARS</bold></aqua> <dark_gray>></dark_gray>` via la balise `<sw>` ;
- aucun préfixe décoratif ne doit employer de crochets ; les crochets fonctionnels d'une syntaxe de commande, comme `[raison]`, sont conservés ;
- le gris porte le texte neutre, le blanc ou l'or les valeurs, le vert les succès, le jaune les avertissements et le rouge les erreurs.

Le gras est réservé à la marque, aux titres courts, aux grades et aux actions importantes. Les icônes restent ponctuelles et ne remplacent jamais une information textuelle.

## Quand afficher le préfixe

Le préfixe apparaît sur les résultats et erreurs de commandes, les notifications système et les confirmations ou erreurs déclenchées par un menu. Une réponse composée préfixe uniquement son en-tête ; ses lignes de détail restent sobres.

Le chat des joueurs, les titres et contenus d'inventaires, les scoreboards, tablists, bossbars, titles, sous-titres et actionbars ne répètent pas la marque. Ils suivent néanmoins la même palette.

Les grades utilisent un libellé coloré en gras sans crochets. Le chat suit le rendu `VIP Joueur > message`, tandis que la tablist s'arrête au nom.

## Logs techniques

Les logs sont écrits en MiniMessage et rendus sous la forme `TROPICUBE > CONTEXTE > message` ou `SHEEPWARS > CONTEXTE > message`. Adventure produit les couleurs ANSI quand le terminal les supporte et un texte lisible sinon. Les niveaux du logger, paramètres structurés et stacktraces doivent être conservés.

Les valeurs dynamiques ne doivent contenir ni secret ni donnée sensible. Un nouveau contexte reste court et stable, par exemple `REDIS`, `DOCKER`, `LANG`, `GAME` ou `NICK`.

## Locales et validation

Chaque clé existe en `fr`, `en`, `de` et `es`, avec les mêmes placeholders positionnels. Les ressources embarquées et leurs copies Docker doivent rester identiques. Les tests contrôlent le YAML, la parité des clés et placeholders, la validité MiniMessage et l'absence des anciens préfixes décoratifs.
