import { Document, isMap, isScalar, isSeq, parseDocument } from 'yaml';

export type Locale = 'fr' | 'en' | 'de' | 'es';
export const locales: Locale[] = ['fr', 'en', 'de', 'es'];

export interface FileSnapshot { content: string; hash: string }
export interface LanguageSet { id: string; sourceDirectory: string; mirrorDirectory?: string }
export interface StateSet { set: LanguageSet; files: Record<Locale, FileSnapshot> }
export interface Diagnostic { language: Locale; key: string; code: string; message: string }

export function documents(files: Record<Locale, FileSnapshot>): Record<Locale, Document> {
  return Object.fromEntries(locales.map(locale => [locale, parseDocument(files[locale].content)])) as Record<Locale, Document>;
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
