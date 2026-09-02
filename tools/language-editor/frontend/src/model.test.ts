import { describe, expect, it } from 'vitest';
import { documents, filterKeys, flatten, inferContext, rename, serialize, setValue, value, withTranslations } from './model';
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

  it('searches either keys or text across every language', () => {
    const current = documents(Object.fromEntries([
      ['fr', { content: 'menu:\n  title: "Équipe"\n  lore:\n    - "Première ligne"\n    - "Seconde ligne"\n', hash: 'fr' }],
      ['en', { content: 'menu:\n  title: "Team"\n  lore:\n    - "First line"\n    - "Second line"\n', hash: 'en' }],
      ['de', { content: 'menu:\n  title: "Mannschaft"\n  lore:\n    - "Erste Zeile"\n    - "Zweite Zeile"\n', hash: 'de' }],
      ['es', { content: 'menu:\n  title: "Equipo"\n  lore:\n    - "Primera línea"\n    - "Segunda línea"\n', hash: 'es' }],
    ]) as Parameters<typeof documents>[0]);

    expect(filterKeys(current, 'TITLE', 'key')).toEqual(['menu.title']);
    expect(filterKeys(current, 'equipe', 'text')).toEqual(['menu.title']);
    expect(filterKeys(current, 'second line', 'text')).toEqual(['menu.lore']);
    expect(filterKeys(current, 'title', 'text')).toEqual([]);
    expect(filterKeys(current, '', 'text')).toEqual(['menu.title', 'menu.lore']);
  });

  it('serializes long quoted values without wrapping them across physical lines', () => {
    const document = parseDocument('# comment\nmessage: "Une phrase volontairement longue qui doit rester sur une seule ligne afin de rester compatible avec les outils de déploiement\\nSeconde ligne logique"\n');

    const yaml = serialize(document);

    expect(yaml).toContain('# comment');
    expect(yaml).toContain('message: "Une phrase volontairement longue qui doit rester sur une seule ligne afin de rester compatible avec les outils de déploiement\\nSeconde ligne logique"');
  });
});
