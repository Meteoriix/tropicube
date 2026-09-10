import { Document, isMap, isScalar, isSeq, parseDocument } from 'yaml';

export type Locale = 'fr' | 'en' | 'de' | 'es';
export type SearchMode = 'key' | 'text';
export const locales: Locale[] = ['fr', 'en', 'de', 'es'];

export interface FileSnapshot { content: string; hash: string }
export interface LanguageSet { id: string; sourceDirectory: string; mirrorDirectory?: string }
export interface StateSet { set: LanguageSet; files: Record<Locale, FileSnapshot> }
export interface Diagnostic { language: Locale; key: string; code: string; message: string }
export interface PlaceholderReference { set: string; key: string }
export interface PlaceholderSummary { name: string; description: string; references: PlaceholderReference[] }
export type LanguageDocuments = Record<Locale, Document>;
const GLOBAL_PLACEHOLDERS = ['player_grade'] as const;

export function documents(files: Record<Locale, FileSnapshot>): LanguageDocuments {
  return Object.fromEntries(locales.map(locale => [locale, parseDocument(files[locale].content)])) as LanguageDocuments;
}

export function withTranslations(current: LanguageDocuments, key: string,
                                 translations: Partial<Record<Locale, string | string[]>>): LanguageDocuments {
  const copy = Object.fromEntries(locales.map(locale => [locale, current[locale].clone()])) as LanguageDocuments;
  for (const locale of locales) {
    const translation = translations[locale];
    if (translation !== undefined) setValue(copy[locale], key, translation);
  }
  return copy;
}

export function flatten(document: Document): string[] {
  const result: string[] = [];
  const visit = (node: unknown, path: string[]) => {
    if (isMap(node)) {
      for (const pair of node.items) {
        const key = String(isScalar(pair.key) ? pair.key.value : pair.key);
        visit(pair.value, [...path, key]);
      }
    } else result.push(path.join('.'));
  };
  visit(document.contents, []);
  return result;
}

export function value(document: Document, key: string): string | string[] {
  const found = document.getIn(key.split('.'), true);
  if (isSeq(found)) return found.items.map(item => String(isScalar(item) ? item.value ?? '' : item));
  return String(isScalar(found) ? found.value ?? '' : found ?? '');
}

export function editableValue(current: string | string[]): string {
  if (Array.isArray(current)) return current.join('\n');
  return current.replace(/<(?:br|newline)>/gi, '\n');
}

export function valueFromEditor(current: string | string[], edited: string): string | string[] {
  const normalized = edited.replace(/\r\n?/g, '\n');
  if (Array.isArray(current)) return normalized.split('\n');
  return normalized.replace(/\n/g, '<br>');
}

export function serialize(document: Document): string {
  return document.toString({ lineWidth: 0, doubleQuotedAsJSON: true });
}

export function filterKeys(documents: LanguageDocuments, search: string, mode: SearchMode): string[] {
  const keys = flatten(documents.fr);
  const query = normalizeSearch(search);
  if (!query) return keys;

  return keys.filter(key => {
    if (mode === 'key') return normalizeSearch(key).includes(query);
    return locales.some(locale => {
      const translation = value(documents[locale], key);
      const text = Array.isArray(translation) ? translation.join('\n') : translation;
      return normalizeSearch(text).includes(query);
    });
  });
}

/** Collects every named placeholder once per translation key across all language sets. */
export function collectPlaceholders(sets: StateSet[]): PlaceholderSummary[] {
  const found = new Map<string, PlaceholderReference[]>(GLOBAL_PLACEHOLDERS.map(name => [name, []]));
  for (const state of sets) {
    const french = parseDocument(state.files.fr.content);
    for (const key of flatten(french)) {
      const translated = value(french, key);
      const text = Array.isArray(translated) ? translated.join('\n') : translated;
      const names = new Set([...text.matchAll(/\{([a-z][a-z0-9_]*)}/g)].map(match => match[1]));
      for (const name of names) {
        const references = found.get(name) || [];
        references.push({ set: state.set.id, key });
        found.set(name, references);
      }
    }
  }
  return [...found.entries()]
    .map(([name, references]) => {
      const sortedReferences = references.sort((left, right) =>
        left.set.localeCompare(right.set) || left.key.localeCompare(right.key));
      return { name, description: describePlaceholder(name, sortedReferences), references: sortedReferences };
    })
    .sort((left, right) => left.name.localeCompare(right.name));
}

/** Canonical business meaning of every placeholder exposed by the language resources. */
export const PLACEHOLDER_DESCRIPTIONS: Record<string, string> = {
    action: 'Type d’action de modération enregistrée dans l’historique.',
    amount: 'Montant de TropiCoins ajouté, retiré ou transféré.',
    auto_replay: 'État actuel du rejeu automatique dans les préférences du joueur.',
    badge: 'Nom localisé du badge affiché sur le profil.',
    action: 'Action localisée du bouton, annoncée après le geste de clic.',
    requirement: 'Niveau, seuil VIP ou prix requis pour débloquer un cosmétique.',
    remaining: 'Solde de monnaie restant après un achat cosmétique.',
    balance: 'Solde actuel du joueur en TropiCoins.',
    best_killer: 'Pseudo du joueur ayant réalisé le plus d’éliminations.',
    best_thrower: 'Pseudo du joueur ayant lancé le plus de moutons.',
    blue_players: 'Nombre de joueurs encore actifs dans l’équipe bleue.',
    branch: 'Nom localisé de la branche de maîtrise sélectionnée.',
    category: 'Catégorie fonctionnelle de la notification.',
    challenge: 'Nom ou numéro du défi en cours.',
    coins: 'Nombre de TropiCoins accordés par la récompense.',
    command: 'Commande préparée lorsque le joueur clique sur la notification.',
    container_id: 'Nom ou identifiant du conteneur Docker de l’instance.',
    contextual_help: 'État activé ou désactivé de l’aide contextuelle.',
    countdown: 'Nombre de secondes restantes avant le démarrage.',
    current_players: 'Nombre de joueurs actuellement présents dans la partie ou la file.',
    current_setting: 'Valeur actuellement sélectionnée pour le réglage affiché.',
    database_status: 'État de la connexion à la base de données.',
    date: 'Date et heure formatées de l’événement affiché.',
    days: 'Nombre de jours d’une durée formatée.',
    duration: 'Durée formatée appliquée à l’action.',
    entity_visibility: 'État de visibilité des autres joueurs et entités.',
    error_message: 'Détail technique lisible de l’erreur rencontrée.',
    experience: 'Quantité d’expérience réseau ou de maîtrise.',
    expiration: 'Date ou durée restante avant la fin de la sanction.',
    filter: 'Filtre actuellement appliqué à la liste de serveurs.',
    flags: 'Options complémentaires activées sur le template serveur.',
    follow_enabled: 'État indiquant si le membre suit automatiquement le chef de party.',
    friend_count: 'Nombre total d’amis du joueur, dans les listes comme dans son profil.',
    game_mode: 'Nom localisé du mode de jeu ou du format classé.',
    game_type: 'Type de partie demandé pour la création ou le routage.',
    games: 'Nombre total de parties jouées.',
    global_chat: 'État activé ou désactivé du chat global.',
    grace_seconds: 'Nombre de secondes de grâce restantes pour se reconnecter.',
    grade: 'Nom ou composant formaté du grade du joueur.',
    grade_count: 'Nombre de grades chargés dans le catalogue.',
    groups: 'Nombre de groupes actuellement présents dans la file classée.',
    guild: 'Nom visible de la guilde du joueur.',
    host: 'Adresse réseau de l’hôte de l’instance.',
    hours: 'Nombre d’heures d’une durée formatée.',
    image: 'Nom de l’image Docker utilisée par le template.',
    instance_id: 'Identifiant UUID complet de l’instance serveur.',
    instance_name: 'Nom visible de l’instance serveur sur laquelle se trouve le joueur.',
    instance_number: 'Numéro court utilisé pour distinguer deux instances du même type.',
    invalid_value: 'Valeur invalide saisie par le joueur et refusée par la commande.',
    kills: 'Nombre d’éliminations réalisées par le joueur.',
    kit: 'Nom localisé du kit ou de la classe sélectionnée.',
    language: 'Nom de la langue actuellement sélectionnée.',
    language_count: 'Nombre de langues chargées par Core.',
    level: 'Niveau réseau ou niveau de maîtrise atteint.',
    map: 'Nom de la carte sélectionnée pour la partie.',
    match_result: 'Résultat personnel de la partie, par exemple victoire ou défaite.',
    max_players: 'Capacité maximale de joueurs de la partie ou de la file.',
    max_wait_seconds: 'Plus longue durée d’attente actuelle dans la file, en secondes.',
    maximum_memory: 'Limite maximale de mémoire du serveur, en mégaoctets.',
    maximum_warnings: 'Nombre d’avertissements entraînant une expulsion.',
    message: 'Contenu du message privé, de party ou de staff envoyé.',
    message_privacy: 'Règle actuelle de réception des messages privés.',
    min_players: 'Nombre minimal de joueurs requis pour démarrer.',
    minimum_memory: 'Mémoire minimale réservée au serveur, en mégaoctets.',
    minutes: 'Nombre de minutes d’une durée formatée.',
    mission_description: 'Objectif localisé détaillant l’action demandée par la mission.',
    mission_number: 'Numéro de la mission dans sa série quotidienne ou hebdomadaire.',
    mission_status: 'État de la mission : en cours, terminée ou récompense récupérée.',
    mission_type: 'Type localisé de la mission, par exemple quotidienne ou hebdomadaire.',
    moderation_level: 'Niveau de modération attribué au joueur.',
    moderator: 'Pseudo du membre du staff ayant appliqué la sanction.',
    nickname: 'Pseudonyme temporaire appliqué par le système de nick.',
    notification_id: 'Identifiant numérique de la notification.',
    notification_status: 'État lu ou non lu de la notification.',
    online_players: 'Nombre de joueurs actuellement connectés au réseau.',
    online_servers: 'Nombre d’instances actuellement disponibles pour ce type de jeu.',
    operation_result: 'Résultat lisible de la récupération ou du renouvellement demandé.',
    page: 'Numéro de la page actuellement affichée.',
    party_size: 'Nombre de membres actuellement présents dans la party.',
    percentage: 'Pourcentage effectif associé au poids configuré.',
    placements: 'Nombre de parties de placement restantes ou réalisées.',
    player: 'Pseudo ou identité visible du joueur concerné.',
    player_grade: 'Grade MiniMessage formaté du joueur destinataire, sans son pseudo.',
    player_class: 'Nom localisé de la classe choisie par le joueur.',
    port: 'Port interne attribué à l’instance serveur.',
    position: 'Position actuelle du joueur dans la file d’attente.',
    prefix: 'Préfixe MiniMessage formaté du grade.',
    price: 'Prix requis en TropiCoins pour effectuer l’achat.',
    priority: 'Priorité numérique du grade dans la hiérarchie.',
    profile: 'Profil formaté du joueur, avec son grade visible.',
    profile_visibility: 'Niveau de visibilité actuel du profil.',
    progress: 'Progression actuelle vers l’objectif de la mission ou du défi.',
    powerup: 'Nom localisé du bonus d’équipe déclenché par la cible de laine.',
    queue: 'Nom localisé de la file d’attente active.',
    rank: 'Rang ou position du joueur dans le classement.',
    ranked_games: 'Nombre de parties classées jouées pendant la saison.',
    rating: 'Cote compétitive actuelle du joueur.',
    rcon_status: 'Disponibilité de la connexion RCON interne de l’instance.',
    reason: 'Motif fourni pour la sanction, le refus ou l’indisponibilité.',
    recovery_codes: 'Liste des codes de récupération 2FA générés pour le joueur.',
    red_players: 'Nombre de joueurs encore actifs dans l’équipe rouge.',
    remaining_games: 'Nombre de parties restantes avant la prochaine confirmation de rejeu.',
    request_count: 'Nombre de demandes ou invitations reçues.',
    request_deadline: 'Date limite de traitement de la demande de données.',
    request_id: 'Identifiant numérique de la demande ou du signalement.',
    request_status: 'État de traitement de la demande de données.',
    request_type: 'Type de demande relative aux données personnelles.',
    required_level: 'Niveau minimal requis pour débloquer la fonctionnalité.',
    reserved_players: 'Nombre de joueurs dont la place est réservée dans la file.',
    reward: 'Récompense formatée accordée par le défi.',
    role: 'Rôle du membre dans la guilde.',
    season: 'Nom ou numéro de la saison compétitive.',
    seconds: 'Nombre de secondes d’une durée formatée.',
    sent_request_count: 'Nombre de demandes ou invitations envoyées.',
    server: 'Nom visible du serveur ciblé par l’action.',
    server_status: 'État de cycle de vie actuel de l’instance serveur.',
    server_type: 'Nom du type de serveur ou identifiant de file utilisé par la commande.',
    sheep_interval_seconds: 'Délai en secondes entre deux distributions de moutons.',
    sheep_thrown: 'Nombre de moutons lancés par le joueur.',
    tag: 'Tag court visible de la guilde.',
    target: 'Valeur cible à atteindre pour terminer la mission ou le défi.',
    team: 'Nom localisé de l’équipe du joueur.',
    team_members: 'Nombre de membres actuellement présents dans l’équipe.',
    template: 'Identifiant du template de serveur concerné.',
    template_count: 'Nombre de templates serveur disponibles.',
    template_status: 'État activé, désactivé ou en maintenance du template.',
    templates: 'Liste des templates serveur disponibles.',
    time: 'Temps restant ou écoulé, déjà formaté pour l’affichage.',
    title: 'Titre cosmétique actuellement équipé sur le profil.',
    token: 'Jeton d’inscription ou de sécurité généré.',
    tokens: 'Nombre de jetons compétitifs accordés par la récompense.',
    total_servers: 'Nombre total d’instances configurées pour ce type de jeu.',
    uncertainty: 'Marge d’incertitude associée à la cote compétitive.',
    unread_count: 'Nombre de notifications qui n’ont pas encore été lues.',
    vip_level: 'Niveau VIP attribué au joueur.',
    visibility: 'Valeur de visibilité choisie pour les détails du résumé.',
    visible_games: 'Nombre de parties visibles et accessibles au joueur.',
    votes: 'Nombre de votes reçus par la carte.',
    wait_seconds: 'Temps d’attente écoulé dans la file, en secondes.',
    warnings: 'Nombre actuel d’avertissements du joueur.',
    weekly_experience: 'Expérience de guilde apportée par le membre cette semaine.',
    weight: 'Poids brut configuré pour la probabilité de sélection.',
    whitelist_status: 'État activé ou désactivé de la whitelist de l’instance.',
    wins: 'Nombre de parties remportées par le joueur.',
};

/** Provides a durable French explanation and makes missing metadata explicit. */
export function describePlaceholder(name: string, references: PlaceholderReference[]): string {
  if (PLACEHOLDER_DESCRIPTIONS[name]) return PLACEHOLDER_DESCRIPTIONS[name];
  const firstReference = references[0];
  return `Description métier manquante pour {${name}}${firstReference ? `, utilisé par « ${firstReference.key} »` : ''}.`;
}

function normalizeSearch(text: string): string {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase();
}

export function setValue(document: Document, key: string, next: string | string[]) {
  document.setIn(key.split('.'), next);
}

export function rename(document: Document, oldKey: string, newKey: string) {
  const oldPath = oldKey.split('.');
  const current = document.getIn(oldPath);
  if (current === undefined) return;
  document.setIn(newKey.split('.'), current);
  document.deleteIn(oldPath);
}

export function inferContext(key: string): string {
  if (/scoreboard/.test(key)) return 'scoreboard';
  if (/tab(list)?/.test(key)) return 'tablist';
  if (/subtitle/.test(key)) return 'subtitle';
  if (/title/.test(key)) return 'title';
  if (/actionbar/.test(key)) return 'actionbar';
  if (/lore/.test(key)) return 'lore';
  if (/menu|selector|item-name|button/.test(key)) return 'inventory';
  return 'chat';
}
