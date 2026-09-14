# Tropicube

[English version](README.en.md)

Tropicube est une infrastructure Minecraft multi-serveurs pour **Minecraft 26.2**, accessible depuis Java Edition et Bedrock Edition grâce à Geyser/Floodgate. Elle associe un proxy Velocity, des serveurs Paper créés dynamiquement dans Docker, Redis pour l'état partagé et les événements, et MySQL pour les données persistantes.

Le Profil donne accès aux missions, notifications, à la progression et au guide. Au Lobby, le vestiaire permet de prévisualiser et choisir des traînées et sons personnels ; la Boutique propose des acquisitions confirmées et persistantes. Le choix des modes de jeu est accessible au clic gauche, avec la hotbar habituelle. Voir les [parcours et contrats](docs/ARCHITECTURE.md) et le [catalogue configurable](docs/CONFIGURATION.md).

## Documentation

- [Ouvrir le site de documentation HTML](docs-site/index.html)
- [Ouvrir la documentation anglaise](docs-site/en/index.html)
- [Éditer et prévisualiser les langues](docs/LANGUAGE_EDITOR.md)
- [Architecture et fonctionnement](docs/ARCHITECTURE.md)
- [Commandes Minecraft et permissions](docs/COMMANDS.md)
- [Configuration](docs/CONFIGURATION.md)
- [Installation, déploiement et exploitation](docs/DEPLOYMENT.md)
- [SheepWars : game design et fonctionnalités](docs/SHEEPWARS.md)
- [Fallen Kingdoms : game design de référence](docs/FALLEN_KINGDOMS_GAME_DESIGN.md)
- [Fallen Kingdoms : spécification technique](docs/FALLEN_KINGDOMS_TECHNICAL_SPEC.md)
- [Développement, tests et contribution](docs/DEVELOPMENT.md)
- [Git, CI et versionnement](docs/GIT_CI.md)
- [Historique des changements](docs/CHANGELOG.md)

## Modules

| Module | Environnement | Rôle |
|---|---|---|
| `tropicube-docker-api` | Java partagé | Modèles d'instances/templates, client Docker et accès Redis |
| `tropicube-language-api` | Java partagé | Rendu sûr des placeholders MiniMessage nommés et compatibilité positionnelle |
| `tropicube-velocity` | Velocity | Routage, files d'attente, création/arrêt des instances et commandes proxy |
| `tropicube-core` | Paper | Joueurs, économie, grades cosmétiques, niveaux VIP/modération, langues, amis et parties |
| `tropicube-lobby` | Paper | Accueil immersif, hotbar Jeux/Social/Profil/Boutique, gestion des guildes dans Social, Quick Play/Ranked, navigateur d'instances et menus harmonisés |
| `tropicube-sheepwars` | Paper | SheepWars Quick/classé 4v4-8v8, saisons, cote, progression de kits, cartes, équipes et moutons spéciaux |
| `tropicube-fallenkingdoms` | Paper dynamique | Fallen Kingdoms public de 2 à 5 royaumes, cartes configurables, protections par phase, cœurs, kits, réapparitions et mort subite |

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

Pendant le développement, relancer cette même commande redéploie directement les images vérifiées : elle recrée Velocity sans mode maintenance, sauvegarde ni délai de drain, et interrompt les parties dynamiques en cours. Pour une livraison de production, construire d'abord un lot avec `-SkipRestart` / `--skip-restart`, puis utiliser la procédure d'activation sécurisée décrite dans le [guide de déploiement](docs/DEPLOYMENT.md).

Le proxy écoute sur `25565/tcp` pour Java et sur `${BEDROCK_PORT:-19132}/udp` pour Bedrock. MySQL (`3306`), Redis (`6379`) et les interfaces de développement optionnelles sont publiés uniquement sur `127.0.0.1`.

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

## Avertissements essentiels

- Ne versionnez jamais `.env` ni un secret réel. Le fichier est ignoré par Git.
- Ne publiez pas directement les ports des serveurs Paper dynamiques : seul Velocity doit être accessible aux joueurs.
- Ouvrez le port `BEDROCK_PORT` en **UDP**, jamais uniquement en TCP, et conservez le volume `floodgate-data` qui contient la clé privée d'identité Bedrock.
- Le secret de forwarding doit être identique côté Velocity et Paper.
- L'accès au daemon Docker est limité au conteneur `docker-proxy`; seules les API nécessaires aux conteneurs, images, réseaux et volumes éphémères sont exposées. Cet accès reste néanmoins sensible et doit être réservé à une machine de confiance.
- Sauvegardez les volumes `mysql-data` et `redis-data` avant toute opération d'infrastructure importante.

## Préparation de la première production

Le réseau dispose de limites mémoire distinctes du heap Java, d'une file SQL bornée, d'un signal de disponibilité applicative, de sauvegardes SFTP chiffrées et de lots d'images activés après validation. Consulter [le guide d'exploitation](docs/DEPLOYMENT.md#procédure-de-livraison-fiable-avant-ouverture) et [les paramètres](docs/CONFIGURATION.md#fiabilité-avant-ouverture). L'objectif initial de 50 joueurs doit être validé sur le matériel cible avant ouverture.

Pour le poste de développement Windows, `tools/ops/setup-windows.ps1` configure des sauvegardes locales chiffrées et `tools/ops/windows.ps1 -Command deploy` charge leur configuration avant le déploiement. L'installation des tâches Windows est décrite dans le guide d'exploitation ; une sauvegarde hors du PC reste nécessaire pour couvrir sa perte.
