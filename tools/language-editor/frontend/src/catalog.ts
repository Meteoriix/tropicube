export function normalizeProtectedTerms(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.filter((term): term is string => typeof term === 'string')
    .map(term => term.trim()).filter(Boolean))];
}

export function withProtectedTerms(catalog: any, terms: unknown): any {
  return { ...catalog, protectedTerms: normalizeProtectedTerms(terms) };
}
