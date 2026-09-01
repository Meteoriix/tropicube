import { describe, expect, it } from 'vitest';
import { flatten, inferContext, rename, setValue, value } from './model';
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
});
