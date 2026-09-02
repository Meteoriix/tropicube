import { describe, expect, it } from 'vitest';
import { documents, flatten, inferContext, rename, setValue, value, withTranslations } from './model';
import { parseDocument } from 'yaml';

describe('language model', () => {
  it('edits and renames nested keys without dropping comments', () => {
    const document = parseDocument('# heading\nmenu:\n  title: "Bonjour"\n');
    setValue(document, 'menu.title', '<gold>Salut');
    rename(document, 'menu.title', 'menu.heading');
    expect(flatten(document)).toEqual(['menu.heading']);
    expect(value(document, 'menu.heading')).toBe('<gold>Salut');
    expect(document.toString()).toContain('# heading');
  });

  it('infers common preview contexts', () => {
    expect(inferContext('lobby.scoreboard-title')).toBe('scoreboard');
    expect(inferContext('menu.item-lore')).toBe('lore');
  });

  it('applies German and Spanish translations in one immutable update', () => {
    const current = documents(Object.fromEntries(['fr', 'en', 'de', 'es'].map(locale => [locale, {
      content: `menu:\n  title: "${locale}"\n`, hash: locale,
    }])) as Parameters<typeof documents>[0]);

    const translated = withTranslations(current, 'menu.title', { de: 'Deutsch', es: 'Español' });

    expect(value(translated.de, 'menu.title')).toBe('Deutsch');
    expect(value(translated.es, 'menu.title')).toBe('Español');
    expect(value(translated.en, 'menu.title')).toBe('en');
    expect(value(current.de, 'menu.title')).toBe('de');
  });
});
