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
  const found = new Map<string, PlaceholderReference[]>();
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

/** Provides a durable French explanation for known and newly introduced placeholder names. */
export function describePlaceholder(name: string, references: PlaceholderReference[]): string {
  const exact: Record<string, string> = {
    player: 'Pseudo ou identité visible du joueur concerné.',
    profile: 'Profil formaté du joueur, avec son grade visible.',
    balance: 'Solde actuel du joueur en TropiCoins.',
    amount: 'Montant concerné par l’action.',
    online_players: 'Nombre de joueurs actuellement connectés au réseau.',
    current_players: 'Nombre de joueurs présents dans la partie ou la file.',
    max_players: 'Capacité maximale de joueurs.',
    min_players: 'Nombre minimal de joueurs requis.',
    visible_games: 'Nombre de parties visibles et accessibles au joueur.',
    instance_number: 'Identifiant de l’instance serveur actuelle.',
    instance_name: 'Nom visible de l’instance serveur sur laquelle se trouve le joueur.',
    map: 'Nom de la carte actuellement sélectionnée.',
    team: 'Nom localisé de l’équipe du joueur.',
    player_class: 'Nom localisé de la classe choisie par le joueur.',
    queue: 'Nom localisé de la file d’attente active.',
    reserved_players: 'Nombre de places actuellement réservées dans la file.',
    capacity: 'Capacité totale de la partie ou de la file.',
    wait_seconds: 'Temps d’attente écoulé, en secondes.',
    countdown: 'Temps restant avant le démarrage.',
    time: 'Durée ou temps restant, déjà formaté pour l’affichage.',
    red_players: 'Nombre de joueurs encore actifs dans l’équipe rouge.',
    blue_players: 'Nombre de joueurs encore actifs dans l’équipe bleue.',
    kills: 'Nombre d’éliminations réalisées par le joueur.',
    sheep_thrown: 'Nombre de moutons lancés par le joueur.',
  };
  if (exact[name]) return exact[name];
  const readable = name.replaceAll('_', ' ');
  if (/(count|total|number|players|warnings|votes|pages|requests)/.test(name)) return `Nombre dynamique correspondant à « ${readable} ».`;
  if (/(player|member|owner|leader|moderator|sender|target|friend)/.test(name)) return `Identité de joueur associée à « ${readable} ».`;
  if (/(time|duration|delay|seconds|minutes|hours|days|expiration|cooldown)/.test(name)) return `Durée dynamique correspondant à « ${readable} ».`;
  if (/(amount|balance|price|cost|coins|reward|rating|experience|xp)/.test(name)) return `Valeur numérique correspondant à « ${readable} ».`;
  if (/(map|team|class|kit|game|server|instance|queue|rank|grade|level|status|state|reason|message|command)/.test(name)) return `Libellé ou valeur dynamique correspondant à « ${readable} ».`;
  const firstReference = references[0];
  return firstReference
    ? `Contenu dynamique associé au champ « ${readable} » dans la traduction « ${firstReference.key} ».`
    : `Valeur dynamique correspondant à « ${readable} ».`;
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
