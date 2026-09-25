# Charte des messages

Cette charte garantit une identité cohérente entre Core, Lobby, Velocity et les mini-jeux. Tous les textes utilisent MiniMessage, y compris les messages techniques avant leur rendu console.

## Identité

- les notifications réseau commencent par `<gold><bold>TROPICUBE</bold></gold> <dark_gray>></dark_gray>` via la balise `<tc>` ;
- les notifications SheepWars commencent par `<aqua><bold>SHEEPWARS</bold></aqua> <dark_gray>></dark_gray>` via la balise `<sw>` ;
- aucun préfixe décoratif ne doit employer de crochets ; les crochets fonctionnels d'une syntaxe de commande, comme `[raison]`, sont conservés ;
- le gris porte le texte neutre, le blanc ou l'or les valeurs, le vert les succès, le jaune les avertissements et le rouge les erreurs.

Le gras est réservé à la marque, aux titres courts, aux grades et aux actions importantes. Les icônes restent ponctuelles et ne remplacent jamais une information textuelle.
Les balises `<tc>` et `<sw>` réinitialisent explicitement le gras après la marque : le corps du message reste normal sauf lorsqu'une portion contient sa propre balise `<bold>`.

## Quand afficher le préfixe

Le préfixe apparaît sur les résultats et erreurs de commandes, les notifications système et les confirmations ou erreurs déclenchées par un menu. Une réponse composée préfixe uniquement son en-tête ; ses lignes de détail restent sobres.

Les événements narratifs directement provoqués par un joueur restent sans préfixe : connexion, départ, arrivée dans une partie ou sortie d'une partie. Ils utilisent un symbole contextuel discret et le nom du joueur comme point focal.

Le chat des joueurs, les titres et contenus d'inventaires, les scoreboards, tablists, bossbars, titles, sous-titres et actionbars ne répètent pas la marque. Ils suivent néanmoins la même palette.

Les scoreboards regroupent les informations par contexte avec des espaces et de courts séparateurs en points `•`. Le lobby privilégie l'identité visible, le solde, la fréquentation du réseau, les parties accessibles et la destination. Un mini-jeu privilégie son état, ses objectifs et les informations personnelles immédiatement utiles, sans dépasser la hauteur lisible de la sidebar.

Les grades utilisent un libellé coloré en gras sans crochets. Le chat suit le rendu `VIP Joueur > message`, tandis que la tablist s'arrête au nom.

## Inventaires et menus

Tous les inventaires Core, Lobby et mini-jeux réutilisent le cadrage de `NetworkMenuStyle` : fond gris neutre sans ligne de vitres bleues, actions principales au centre, retour à gauche et fermeture à droite sur la dernière ligne. Une action conserve son icône, sa couleur, son vocabulaire et sa position logique entre les écrans.

Les manifestes déclarent explicitement le cadrage `network`, `neutral` ou `none`. Les cadres `network` et `neutral` conservent tous deux le fond sable gris sans bandeau ; `none` est réservé aux inventaires natifs comme l'enclume. Les opérations irréversibles ou qui débitent un solde ouvrent une confirmation qui rappelle l'effet, le coût s'il existe, et propose confirmer ou annuler.

Les libellés joueur ne montrent jamais une énumération, un identifiant de template ou une valeur avec underscores. Chaque valeur métier possède une traduction naturelle dans les quatre langues. Les réglages affichent leur état courant et expliquent concrètement leur effet ; les missions indiquent objectif, progression, récompenses, état et actions disponibles. Les boutons nomment le clic exact et son résultat. Une entrée de profil emploie la tête du joueur concerné lorsqu'elle est disponible.

## Logs techniques

Les logs sont écrits en MiniMessage et rendus sous la forme `TROPICUBE > CONTEXTE > message` ou `SHEEPWARS > CONTEXTE > message`. Adventure produit les couleurs ANSI quand le terminal les supporte et un texte lisible sinon. Les niveaux du logger, paramètres structurés et stacktraces doivent être conservés.

Les valeurs dynamiques ne doivent contenir ni secret ni donnée sensible. Un nouveau contexte reste court et stable, par exemple `REDIS`, `DOCKER`, `LANG`, `GAME` ou `NICK`.

## Locales et validation

Chaque clé existe en `fr`, `en`, `de` et `es`, avec les mêmes placeholders positionnels. Les ressources embarquées et leurs copies Docker doivent rester identiques. Les tests contrôlent le YAML, la parité des clés et placeholders, la validité MiniMessage et l'absence des anciens préfixes décoratifs.

Les chevrons qui décrivent un paramètre de commande ne sont pas des balises : dans une chaîne YAML entre guillemets doubles, écrire `\\<joueur>` pour obtenir `<joueur>` à l'écran. Les codes couleur hérités `§` sont interdits ; les couleurs et décorations passent exclusivement par MiniMessage.

Les textes multiligne, notamment les lores, restent dans une clé unique et utilisent `<br>` sans espaces entre leurs lignes. `<newline>` est accepté comme alias explicite et `<br><br>` représente une ligne vide. Avant d'être affectés à un item, ces retours sont développés en composants de lore distincts par l'utilitaire partagé `ComponentLines`, car le client Minecraft ne rend pas correctement un caractère de contrôle de nouvelle ligne dans une entrée de lore unique. Les validations suivent directement les balises de `StandardTags.defaults()` fournies par la version Adventure du projet, auxquelles s'ajoutent `<tc>` et `<sw>`.
