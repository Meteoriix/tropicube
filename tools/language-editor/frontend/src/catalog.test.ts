import { describe, expect, it } from 'vitest';
import { normalizeProtectedTerms, withProtectedTerms } from './catalog';

describe('protected translation terms', () => {
  it('removes empty and duplicate entries while preserving their order', () => {
    expect(normalizeProtectedTerms([' Tropicube ', '', 'Fallen Kingdoms', 'Tropicube', 42]))
      .toEqual(['Tropicube', 'Fallen Kingdoms']);
  });

  it('preserves the other catalog sections', () => {
    expect(withProtectedTerms({ version: 2, glossary: { TropiCoin: {} } }, ['Clan']).glossary)
      .toEqual({ TropiCoin: {} });
  });
});
