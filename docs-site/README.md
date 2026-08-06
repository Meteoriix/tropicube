# Site de documentation Tropicube

Ouvrir `index.html` directement dans un navigateur. Le site est autonome et ne nécessite ni serveur HTTP, ni connexion Internet.

Après une modification de `README.md` ou d'un fichier sous `docs/`, reconstruire les pages depuis la racine du projet :

```powershell
node docs-site/build.mjs
node docs-site/validate.mjs
```

Le générateur utilise uniquement les API natives de Node.js et ne nécessite donc aucun `npm install`.
La feuille de style applique la palette officielle Tropicube (Violet Nuit, Soleil Doré, Sable et accents tropicaux) et conserve des polices de repli locales afin que le site reste entièrement consultable hors ligne.
