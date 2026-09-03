import { describe, expect, it } from 'vitest';
import { collectPlaceholders, describePlaceholder, documents, editableValue, filterKeys, flatten, inferContext, PLACEHOLDER_DESCRIPTIONS, rename, serialize, setValue, value, valueFromEditor, withTranslations } from './model';
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

  it('edits MiniMessage line breaks as natural textarea line breaks', () => {
    expect(editableValue('Première ligne<br><br>Troisième ligne')).toBe('Première ligne\n\nTroisième ligne');
    expect(editableValue('Première ligne<newline>Seconde ligne')).toBe('Première ligne\nSeconde ligne');
    expect(valueFromEditor('Texte', 'Première ligne\r\n\r\nTroisième ligne'))
      .toBe('Première ligne<br><br>Troisième ligne');
  });

  it('keeps each textarea line as a distinct YAML list item', () => {
    expect(valueFromEditor(['Première ligne'], 'Première ligne\nSeconde ligne'))
      .toEqual(['Première ligne', 'Seconde ligne']);
  });

  it('lists and deduplicates named placeholders across language sets', () => {
    const files = (content: string) => Object.fromEntries(['fr', 'en', 'de', 'es']
      .map(locale => [locale, { content, hash: locale }])) as any;
    const placeholders = collectPlaceholders([
      { set: { id: 'core', sourceDirectory: 'core' }, files: files('one: "{player} {balance} {player}"\n') },
      { set: { id: 'velocity', sourceDirectory: 'velocity' }, files: files('two: "{player}"\n') },
    ]);

    expect(placeholders.map(entry => entry.name)).toEqual(['balance', 'player']);
    expect(placeholders.find(entry => entry.name === 'player')?.references).toEqual([
      { set: 'core', key: 'one' }, { set: 'velocity', key: 'two' },
    ]);
    expect(placeholders.find(entry => entry.name === 'balance')?.description).toContain('Solde actuel');
    expect(describePlaceholder('instance_name', [])).toContain('Nom visible');
    expect(Object.keys(PLACEHOLDER_DESCRIPTIONS)).toHaveLength(139);
    expect(Object.values(PLACEHOLDER_DESCRIPTIONS).every(description =>
      !description.includes('Contenu dynamique associé au champ'))).toBe(true);
    expect(describePlaceholder('custom_value', [{ set: 'core', key: 'one' }]))
      .toBe('Description métier manquante pour {custom_value}, utilisé par « one ».');
  });
});
