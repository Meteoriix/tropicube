# Tropicube

Tropicube est une infrastructure Minecraft multi-serveurs pour **Minecraft 26.2**. Elle associe un proxy Velocity, des serveurs Paper créés dynamiquement dans Docker, Redis pour l'état partagé et les événements, et MySQL pour les données persistantes.

## Documentation

- [Ouvrir le site de documentation HTML](docs-site/index.html)
- [Architecture et fonctionnement](docs/ARCHITECTURE.md)
- [Commandes Minecraft et permissions](docs/COMMANDS.md)
- [Configuration](docs/CONFIGURATION.md)
- [Installation, déploiement et exploitation](docs/DEPLOYMENT.md)
- [SheepWars : game design et fonctionnalités](docs/SHEEPWARS.md)
- [Développement, tests et contribution](docs/DEVELOPMENT.md)
- [Git, CI et versionnement](docs/GIT_CI.md)
- [Historique des changements](docs/CHANGELOG.md)

## Modules

| Module | Environnement | Rôle |
|---|---|---|
| `tropicube-docker-api` | Java partagé | Modèles d'instances/templates, client Docker et accès Redis |
| `tropicube-velocity` | Velocity | Routage, files d'attente, création/arrêt des instances et commandes proxy |
| `tropicube-core` | Paper | Joueurs, économie, grades, permissions, langues et modération |
| `tropicube-lobby` | Paper | Accueil, menus, sélection de serveur, double-saut et boutique VIP |
| `tropicube-sheepwars` | Paper | Mini-jeu SheepWars, cartes, équipes, kits, classes et moutons spéciaux |
| `tropicube-fallenkingdoms` | Non déployé | Squelette Maven réservé au futur mini-jeu Fallen Kingdoms |

## Démarrage rapide

Prérequis communs : Java 25, Maven 3.9+, Docker Engine actif et Docker Compose v2. Sous Windows, PowerShell 7 et Docker Desktop en mode conteneurs Linux sont recommandés. Sous Linux, Bash 4+ et Python 3 sont également requis.

```text
1. Copier .env.example vers .env
2. Remplacer tous les secrets de démonstration
3. Lancer le script correspondant au système
```

Windows :

```powershell
Copy-Item .env.example .env
./deploy.ps1
```

Linux :

```bash
cp .env.example .env
chmod +x deploy.sh
./deploy.sh
```

Le proxy écoute sur le port TCP `25565`. MySQL (`3306`), Redis (`6379`) et les interfaces de développement optionnelles sont publiés uniquement sur `127.0.0.1`.

## Développement

Windows :

```powershell
.\mvnw.cmd clean verify
```

Linux :

```bash
bash ./mvnw clean verify
```

Le wrapper télécharge Maven 3.9.11 si nécessaire. Le parent Maven impose Java `[25, 26)`, Maven 3.9+ et la convergence des dépendances. La phase `verify` exécute les tests et produit les rapports de couverture JaCoCo sous `target/site/jacoco/`. Les JAR déployables sont les artefacts ombrés `*-all.jar`; les scripts les localisent, contrôlent leur fraîcheur et vérifient chaque copie avant de construire les images.

La CI GitHub Actions reproduit ces contrôles sous Linux, valide Docker Compose, les scripts Windows/Linux et le site documentaire. Dependabot surveille les dépendances Maven et les actions GitHub. Le processus complet est décrit dans [le guide de développement](docs/DEVELOPMENT.md).

Le module Fallen Kingdoms est actuellement vide : il compile dans le réacteur mais ne contient encore ni plugin Paper, ni ressource, ni image Docker. Les scripts ne tentent donc pas de le déployer.

## Avertissements essentiels

- Ne versionnez jamais `.env` ni un secret réel. Le fichier est ignoré par Git.
- Ne publiez pas directement les ports des serveurs Paper dynamiques : seul Velocity doit être accessible aux joueurs.
- Le secret de forwarding doit être identique côté Velocity et Paper.
- L'accès au daemon Docker est limité au conteneur `docker-proxy`; il reste néanmoins sensible et doit être réservé à une machine de confiance.
- Sauvegardez les volumes `mysql-data` et `redis-data` avant toute opération d'infrastructure importante.
