# Éditeur de langues

L'éditeur de langues Tropicube est une application web locale destinée aux ressources MiniMessage de Core, Velocity et des futurs modules. Il découvre les dossiers `src/main/resources/languages`, utilise le français comme source et écrit ensemble les quatre langues supportées ainsi que les copies Docker associées.

## Démarrage

Java 25, Node.js 24 et npm sont requis. Sous Windows, lancer `.\language-editor.ps1`. Sous Linux ou macOS, lancer `./language-editor.sh`.

L'application écoute uniquement sur `http://127.0.0.1:8765`. `TROPICUBE_LANGUAGE_EDITOR_PORT` permet de remplacer ce port.

## Traduction

L'éditeur accepte tout endpoint compatible LibreTranslate. Le service local optionnel se démarre avec :

```bash
docker compose --env-file .env.example --profile language-editor up -d libretranslate
```

`LIBRETRANSLATE_URL` vaut `http://127.0.0.1:5000` par défaut. `LIBRETRANSLATE_API_KEY` permet d'utiliser une instance externe protégée et ne doit jamais être ajoutée au dépôt.

Le français est traduit vers l'anglais pour revue. Après approbation, l'allemand et l'espagnol sont générés directement depuis le français. Les balises MiniMessage, placeholders, commandes, termes du glossaire et symboles Unicode décoratifs tels que `▶`, `⚠` ou `🌴` ne sont pas envoyés comme texte traduisible et sont conservés à leur position d'origine. Une indisponibilité du service n'empêche pas de préparer un brouillon.

## Édition et sécurité

- la vue structurée permet de chercher par clé ou dans le texte des quatre langues, puis de créer, renommer et supprimer une clé ;
- le mode YAML français convient aux changements groupés ;
- les aperçus couvrent chat, titres, actionbar, inventaires, lores, scoreboard et tablist ;
- la validation contrôle YAML, parité des clés, placeholders et balises autorisées ;
- une écriture est refusée si un fichier a changé depuis son ouverture ;
- les quatre sources et leurs miroirs Docker sont remplacés ensemble, avec rollback sur erreur.

Le catalogue versionné `tools/language-editor/catalog.yml` contient le glossaire, les corrections de contexte et les exemples de placeholders. Les traductions automatiques restent des propositions : relire au minimum l'anglais et vérifier en jeu les interfaces sensibles à la largeur du texte.

## Validation

```bash
npm --prefix tools/language-editor/frontend ci
npm --prefix tools/language-editor/frontend test
npm --prefix tools/language-editor/frontend run build
./mvnw -f tools/language-editor/backend/pom.xml verify
```
