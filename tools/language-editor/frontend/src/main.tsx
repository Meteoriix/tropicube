import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { parse, stringify } from 'yaml';
import { collectPlaceholders, Diagnostic, documents, editableValue, FileSnapshot, filterKeys, flatten, inferContext, Locale, locales, PlaceholderSummary, rename, SearchMode, serialize, setValue, StateSet, value, valueFromEditor, withTranslations } from './model';
import './styles.css';

type Docs = ReturnType<typeof documents>;
type ComponentNode = { text?: string; color?: string; bold?: boolean; italic?: boolean; underlined?: boolean; strikethrough?: boolean; extra?: ComponentNode[] };
type LiveStatus = { available: boolean; message: string };
type LiveResult = { available: boolean; updatedContainers: number; containers: string[]; errors: string[] };
type UiSnapshot = { id: string; module: string; type: 'scoreboards' | 'tablists' | 'menus'; sourcePath: string; mirrorPath?: string; content: string; hash: string };
type EditorMode = 'texts' | 'scoreboards' | 'tablists' | 'menus' | 'placeholders';

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
  return body;
}

function App() {
  const [sets, setSets] = useState<StateSet[]>([]);
  const [uiSnapshots, setUiSnapshots] = useState<UiSnapshot[]>([]);
  const [uiDocuments, setUiDocuments] = useState<Record<string, any>>({});
  const [uiHistory, setUiHistory] = useState<Record<string, any>[]>([]);
  const [uiFuture, setUiFuture] = useState<Record<string, any>[]>([]);
  const [mode, setMode] = useState<EditorMode>('texts');
  const [selectedUi, setSelectedUi] = useState('');
  const [selectedVariant, setSelectedVariant] = useState('');
  const [surfacePreview, setSurfacePreview] = useState<ComponentNode[]>([]);
  const [scoreboardTitlePreview, setScoreboardTitlePreview] = useState<ComponentNode | null>(null);
  const [tablistPreview, setTablistPreview] = useState<{ header: ComponentNode; footer: ComponentNode } | null>(null);
  const [selectedButton, setSelectedButton] = useState('');
  const [selectedPlaceholder, setSelectedPlaceholder] = useState('');
  const [placeholderPickerOpen, setPlaceholderPickerOpen] = useState(false);
  const [placeholderPickerSearch, setPlaceholderPickerSearch] = useState('');
  const [setIndex, setSetIndex] = useState(0);
  const [docs, setDocs] = useState<Docs | null>(null);
  const [snapshots, setSnapshots] = useState<Record<Locale, FileSnapshot> | null>(null);
  const [selected, setSelected] = useState('');
  const [search, setSearch] = useState('');
  const [searchMode, setSearchMode] = useState<SearchMode>('key');
  const [raw, setRaw] = useState(false);
  const [context, setContext] = useState('chat');
  const [preview, setPreview] = useState<ComponentNode | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [provider, setProvider] = useState(false);
  const [live, setLive] = useState<LiveStatus>({ available: false, message: 'Jeu hors ligne' });
  const [englishApproved, setEnglishApproved] = useState(false);
  const [catalog, setCatalog] = useState<any>({ glossary: {}, entries: {} });
  const [catalogText, setCatalogText] = useState('');
  const [usages, setUsages] = useState<any[]>([]);
  const [notice, setNotice] = useState('Chargement…');
  const frenchEditor = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    Promise.all([api<{sets: StateSet[]; ui: UiSnapshot[]; catalog: string; live: LiveStatus}>('/api/state'), api<{available: boolean}>('/api/translation/status')])
      .then(([state, status]) => { setSets(state.sets); setUiSnapshots(state.ui); setUiDocuments(Object.fromEntries(state.ui.map(file => [file.id, parse(file.content)]))); setCatalog(parse(state.catalog)); setCatalogText(state.catalog); setProvider(status.available); setLive(state.live); setNotice('Prêt'); })
      .catch(error => setNotice(error.message));
  }, []);

  useEffect(() => {
    if (!sets[setIndex]) return;
    setSnapshots(sets[setIndex].files);
    const parsed = documents(sets[setIndex].files);
    setDocs(parsed);
    const availableKeys = flatten(parsed.fr);
    setSelected(currentKey => availableKeys.includes(currentKey) ? currentKey : availableKeys[0] || '');
  }, [sets, setIndex]);

  const keys = useMemo(() => docs ? filterKeys(docs, search, searchMode) : [], [docs, search, searchMode]);
  const placeholders = useMemo(() => collectPlaceholders(sets), [sets]);
  const filteredPlaceholders = useMemo(() => placeholders.filter(entry =>
    entry.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())
    || entry.description.toLocaleLowerCase().includes(search.toLocaleLowerCase())
    || entry.references.some(reference => `${reference.set} ${reference.key}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()))), [placeholders, search]);
  const pickerPlaceholders = useMemo(() => {
    const query = placeholderPickerSearch.toLocaleLowerCase();
    return placeholders.filter(entry => entry.name.toLocaleLowerCase().includes(query)
      || entry.description.toLocaleLowerCase().includes(query)
      || entry.references.some(reference => `${reference.set} ${reference.key}`.toLocaleLowerCase().includes(query)));
  }, [placeholders, placeholderPickerSearch]);
  const surfaces = useMemo(() => uiSnapshots.flatMap(file => Object.keys(uiDocuments[file.id]?.[file.type] || {})
    .map(id => ({ file, id, identity: `${file.id}/${id}`, definition: uiDocuments[file.id][file.type][id] })))
    .filter(surface => mode === 'texts' || surface.file.type === mode), [uiSnapshots, uiDocuments, mode]);
  const current = docs && selected ? value(docs.fr, selected) : '';
  const placeholderIds = useMemo(() => Array.from(new Set((Array.isArray(current) ? current.join('\n') : current).match(/\{(?:[a-z][a-z0-9_]*|\d+)}/g) || [])).map(token => token.slice(1, -1)), [current]);

  useEffect(() => {
    if (!selected) return;
    setContext(catalog.entries?.[`${sets[setIndex]?.set.id}:${selected}`]?.context || inferContext(selected));
    api<{usages: any[]}>(`/api/usages?key=${encodeURIComponent(selected)}`).then(result => setUsages(result.usages)).catch(() => setUsages([]));
  }, [selected, catalog, setIndex, sets]);

  useEffect(() => {
    if (!docs || !selected) return;
    const source = value(docs.fr, selected);
    const message = Array.isArray(source) ? source.join('\n') : source;
    const placeholders: Record<string, string> = {};
    for (const match of message.matchAll(/\{([a-z][a-z0-9_]*|\d+)}/g)) placeholders[match[1]] = catalog.entries?.[`${sets[setIndex]?.set.id}:${selected}`]?.placeholders?.[match[1]] || placeholderSample(match[1]);
    api<ComponentNode>('/api/preview', { method: 'POST', body: JSON.stringify({ message, placeholders }) })
      .then(setPreview).catch(error => setNotice(error.message));
  }, [docs, selected, catalog, setIndex, sets]);

  useEffect(() => {
    if (mode === 'texts' || mode === 'placeholders') return;
    const coreIndex = sets.findIndex(entry => entry.set.id === 'tropicube-core');
    if (coreIndex >= 0 && coreIndex !== setIndex) setSetIndex(coreIndex);
    if (!surfaces.some(surface => surface.identity === selectedUi)) {
      const first = surfaces[0];
      setSelectedUi(first?.identity || '');
      setSelectedVariant(first ? Object.keys(first.definition.variants || {})[0] || '' : '');
    }
  }, [mode, surfaces, selectedUi, sets, setIndex]);

  useEffect(() => {
    if (mode !== 'placeholders') return;
    if (!filteredPlaceholders.some(entry => entry.name === selectedPlaceholder)) {
      setSelectedPlaceholder(filteredPlaceholders[0]?.name || '');
    }
  }, [mode, filteredPlaceholders, selectedPlaceholder]);

  const selectedSurface = surfaces.find(surface => surface.identity === selectedUi);
  const placeholder = placeholders.find(entry => entry.name === selectedPlaceholder);

  useEffect(() => {
    if (!docs || !selectedSurface || mode !== 'scoreboards') {
      setScoreboardTitlePreview(null); setSurfacePreview([]); return;
    }
    const variant = selectedSurface.definition.variants?.[selectedVariant];
    if (!variant) return;
    const render = (message: string) => {
      const placeholders: Record<string, string> = {};
      for (const match of message.matchAll(/\{([a-z][a-z0-9_]*|\d+)}/g)) placeholders[match[1]] = placeholderSample(match[1]);
      return api<ComponentNode>('/api/preview', { method: 'POST', body: JSON.stringify({ message, placeholders }) });
    };
    const title = editableValue(value(docs.fr, selectedSurface.definition['title-key']));
    Promise.all([render(title), Promise.all((variant.lines || []).map(async (line: any) => {
      if (line.blank) return { text: '\n' } as ComponentNode;
      const translated = value(docs.fr, line.key);
      const message = Array.isArray(translated) ? translated.join('\n') : translated;
      return render(message);
    }))]).then(([renderedTitle, renderedLines]) => {
      setScoreboardTitlePreview(renderedTitle); setSurfacePreview(renderedLines);
    }).catch(error => setNotice(error.message));
  }, [docs, selectedSurface, selectedVariant, mode]);

  useEffect(() => {
    if (!docs || !selectedSurface || mode !== 'tablists') {
      setTablistPreview(null);
      return;
    }
    const variant = selectedSurface.definition.variants?.[selectedVariant];
    if (!variant) return;
    const renderKey = (key: string) => {
      const translated = value(docs.fr, key);
      const message = Array.isArray(translated) ? translated.join('\n') : translated;
      const placeholders: Record<string, string> = {};
      for (const match of message.matchAll(/\{([a-z][a-z0-9_]*|\d+)}/g)) {
        placeholders[match[1]] = placeholderSample(match[1]);
      }
      return api<ComponentNode>('/api/preview', { method: 'POST', body: JSON.stringify({ message, placeholders }) });
    };
    Promise.all([renderKey(variant['header-key']), renderKey(variant['footer-key'])])
      .then(([header, footer]) => setTablistPreview({ header, footer }))
      .catch(error => setNotice(error.message));
  }, [docs, selectedSurface, selectedVariant, mode]);

  const mutate = (fn: (copy: Docs) => void) => {
    if (!docs) return;
    const copy = Object.fromEntries(locales.map(locale => [locale, docs[locale].clone()])) as Docs;
    fn(copy); setDocs(copy); setEnglishApproved(false);
  };

  const editFrench = (text: string) => mutate(copy => setValue(copy.fr, selected, valueFromEditor(current, text)));

  const insertPlaceholder = (name: string) => {
    const editor = frenchEditor.current;
    const source = editableValue(current);
    const start = editor?.selectionStart ?? source.length;
    const end = editor?.selectionEnd ?? start;
    const token = `{${name}}`;
    editFrench(source.slice(0, start) + token + source.slice(end));
    requestAnimationFrame(() => {
      frenchEditor.current?.focus();
      frenchEditor.current?.setSelectionRange(start + token.length, start + token.length);
    });
    setNotice(`${token} inséré`);
  };

  const requestTranslation = async (target: Locale): Promise<string | string[] | undefined> => {
    if (!docs || target === 'fr') return;
    const source = value(docs.fr, selected);
    const items = Array.isArray(source) ? source : [source];
    const glossary: Record<string, string> = {};
    for (const [term, translations] of Object.entries<any>(catalog.glossary || {})) glossary[term] = translations[target] || term;
    const translated: string[] = [];
    for (const text of items) {
      const result = await api<{translatedText: string}>('/api/translate', { method: 'POST', body: JSON.stringify({ text, target, glossary }) });
      translated.push(result.translatedText);
    }
    return Array.isArray(source) ? translated : translated[0];
  };

  const translateLocale = async (target: Locale) => {
    const translated = await requestTranslation(target);
    if (translated === undefined) return;
    setDocs(currentDocs => currentDocs ? withTranslations(currentDocs, selected, { [target]: translated }) : currentDocs);
    setEnglishApproved(false);
    setNotice(`${target.toUpperCase()} généré`);
  };

  const approveEnglish = async () => {
    const [german, spanish] = await Promise.all([requestTranslation('de'), requestTranslation('es')]);
    if (german === undefined || spanish === undefined) return;
    setDocs(currentDocs => currentDocs ? withTranslations(currentDocs, selected, { de: german, es: spanish }) : currentDocs);
    setEnglishApproved(true);
    setNotice('Anglais approuvé, allemand et espagnol générés');
  };

  const validate = async () => {
    if (!docs) return false;
    const payload = { documents: Object.fromEntries(locales.map(locale => [locale, serialize(docs[locale])])) };
    const result = await api<{diagnostics: Diagnostic[]}>('/api/validate', { method: 'POST', body: JSON.stringify(payload) });
    setDiagnostics(result.diagnostics); setNotice(result.diagnostics.length ? `${result.diagnostics.length} erreur(s)` : 'Validation réussie');
    return result.diagnostics.length === 0;
  };

  const apply = async () => {
    if (!docs || !snapshots || !(await validate())) return;
    const result = await api<{files: Record<Locale, FileSnapshot>; live: LiveResult}>('/api/apply', { method: 'POST', body: JSON.stringify({
      set: sets[setIndex].set,
      expectedHashes: Object.fromEntries(locales.map(locale => [locale, snapshots[locale].hash])),
      documents: Object.fromEntries(locales.map(locale => [locale, serialize(docs[locale])])),
    }) });
    setSnapshots(result.files); setDocs(documents(result.files));
    setSets(currentSets => currentSets.map((entry, index) => index === setIndex ? { ...entry, files: result.files } : entry));
    setLive({ available: result.live.available && !result.live.errors.length, message: result.live.errors.length ? result.live.errors.join(' · ') : 'Jeu synchronisé' });
    if (result.live.updatedContainers > 0 && !result.live.errors.length) {
      setNotice(`Enregistré et rechargé dans ${result.live.updatedContainers} serveur(s)`);
    } else if (result.live.errors.length) {
      setNotice(`Enregistré, mise à jour en jeu incomplète : ${result.live.errors.join(' · ')}`);
    } else setNotice('Enregistré ; aucun serveur actif à recharger');
  };

  const mutateUi = (mutation: (copy: Record<string, any>) => void) => {
    const copy = structuredClone(uiDocuments);
    mutation(copy);
    setUiHistory(history => [...history.slice(-49), structuredClone(uiDocuments)]);
    setUiFuture([]);
    setUiDocuments(copy);
  };

  const undoUi = () => {
    const previous = uiHistory.at(-1); if (!previous) return;
    setUiFuture(future => [structuredClone(uiDocuments), ...future]);
    setUiDocuments(previous); setUiHistory(history => history.slice(0, -1));
  };

  const redoUi = () => {
    const next = uiFuture[0]; if (!next) return;
    setUiHistory(history => [...history, structuredClone(uiDocuments)]);
    setUiDocuments(next); setUiFuture(future => future.slice(1));
  };

  const applyUi = async () => {
    if (!docs || !snapshots) return;
    const uiSerialized = Object.fromEntries(uiSnapshots.map(file => [file.id, stringify(uiDocuments[file.id], { lineWidth: 0 })]));
    for (const file of uiSnapshots) {
      const validation = await api<{errors: string[]}>('/api/ui/validate', { method: 'POST', body: JSON.stringify({ type: file.type, content: uiSerialized[file.id] }) });
      if (validation.errors.length) { setNotice(validation.errors.join(' · ')); return; }
    }
    const changed = uiSnapshots.filter(file => JSON.stringify(parse(file.content)) !== JSON.stringify(uiDocuments[file.id]));
    const languagesChanged = locales.some(locale => JSON.stringify(docs[locale].toJSON()) !== JSON.stringify(parse(snapshots[locale].content)));
    if (!changed.length && !languagesChanged) { setNotice('Aucune modification à appliquer'); return; }
    const summary = [...changed.map(file => `• ${file.id}`), ...(languagesChanged ? ['• titres traduits'] : [])];
    if (!window.confirm(`Appliquer et recharger ${summary.length} modification(s) ?\n\n${summary.join('\n')}`)) return;
    let updatedContainers = 0;
    let liveErrors: string[] = [];
    let liveAvailable = false;
    if (languagesChanged) {
      if (!(await validate())) return;
      const languageResult = await api<{files: Record<Locale, FileSnapshot>; live: LiveResult}>('/api/apply', { method: 'POST', body: JSON.stringify({
        set: sets[setIndex].set,
        expectedHashes: Object.fromEntries(locales.map(locale => [locale, snapshots[locale].hash])),
        documents: Object.fromEntries(locales.map(locale => [locale, serialize(docs[locale])])),
      }) });
      setSnapshots(languageResult.files); setDocs(documents(languageResult.files));
      setSets(currentSets => currentSets.map((entry, index) => index === setIndex ? { ...entry, files: languageResult.files } : entry));
      liveAvailable ||= languageResult.live.available;
      updatedContainers = Math.max(updatedContainers, languageResult.live.updatedContainers);
      liveErrors.push(...languageResult.live.errors);
    }
    if (changed.length) {
      const result = await api<{ui: UiSnapshot[]; live: LiveResult}>('/api/ui/apply', { method: 'POST', body: JSON.stringify({
        expectedHashes: Object.fromEntries(uiSnapshots.map(file => [file.id, file.hash])), documents: uiSerialized,
      }) });
      setUiSnapshots(result.ui); setUiDocuments(Object.fromEntries(result.ui.map(file => [file.id, parse(file.content)])));
      liveAvailable ||= result.live.available;
      updatedContainers = Math.max(updatedContainers, result.live.updatedContainers);
      liveErrors.push(...result.live.errors);
    }
    setUiHistory([]); setUiFuture([]);
    setLive({ available: liveAvailable && !liveErrors.length, message: liveErrors.length ? liveErrors.join(' · ') : 'Jeu synchronisé' });
    setNotice(updatedContainers ? `Interfaces rechargées dans ${updatedContainers} serveur(s)` : 'Interfaces enregistrées ; aucun serveur actif');
  };

  const validateUi = async () => {
    const serialized = Object.fromEntries(uiSnapshots.map(file => [file.id, stringify(uiDocuments[file.id], { lineWidth: 0 })]));
    const errors: string[] = [];
    for (const file of uiSnapshots) {
      const result = await api<{errors: string[]}>('/api/ui/validate', { method: 'POST', body: JSON.stringify({ type: file.type, content: serialized[file.id] }) });
      errors.push(...result.errors.map(error => `${file.id}: ${error}`));
    }
    setNotice(errors.length ? errors.join(' · ') : 'Validation réussie');
    return errors.length === 0;
  };

  const createKey = () => {
    if (!docs) return;
    const key = window.prompt('Nouvelle clé complète (ex. lobby.menu-title)');
    if (!key || flatten(docs.fr).includes(key)) return;
    mutate(copy => locales.forEach(locale => setValue(copy[locale], key, locale === 'fr' ? 'Nouveau texte' : '')));
    setSelected(key);
  };

  const renameKey = () => {
    if (!docs) return;
    const key = window.prompt('Nouveau nom complet', selected);
    if (!key || key === selected) return;
    mutate(copy => locales.forEach(locale => rename(copy[locale], selected, key)));
    setSelected(key);
  };

  const deleteKey = () => {
    if (!docs || !window.confirm(`Supprimer ${selected} dans les quatre langues ?`)) return;
    mutate(copy => locales.forEach(locale => copy[locale].deleteIn(selected.split('.'))));
    setSelected('');
  };

  const updateEntry = (change: Record<string, unknown>) => {
    const identity = `${sets[setIndex].set.id}:${selected}`;
    const next = { ...catalog, entries: { ...(catalog.entries || {}), [identity]: { ...(catalog.entries?.[identity] || {}), ...change } } };
    setCatalog(next); setCatalogText(stringify(next));
  };

  const saveCatalog = async () => {
    const parsed = parse(catalogText);
    await api('/api/catalog', { method: 'POST', body: JSON.stringify({ content: catalogText }) });
    setCatalog(parsed); setNotice('Glossaire et contextes enregistrés');
  };

  if (!docs || !snapshots || !sets.length) return <main className="loading">{notice}</main>;
  return <main>
    <header><div><strong>TROPICUBE</strong><span>Éditeur de langues</span></div><div className="actions">
      <span className={provider ? 'status ok' : 'status'}>{provider ? 'LibreTranslate prêt' : 'Traduction hors ligne'}</span>
      <span title={live.message} className={live.available ? 'status ok' : 'status'}>{live.available ? 'Jeu connecté' : 'Jeu hors ligne'}</span>
      {mode !== 'placeholders' && <><button onClick={mode === 'texts' ? validate : validateUi}>Valider</button><button className="primary" onClick={mode === 'texts' ? apply : applyUi}>Appliquer</button></>}
    </div></header>
    <section className="toolbar">
      <div className="mode-tabs">{(['texts','scoreboards','tablists','menus','placeholders'] as EditorMode[]).map(item => <button className={mode === item ? 'active' : ''} key={item} onClick={() => { setMode(item); setSearch(''); }}>{item === 'texts' ? 'Textes' : item === 'scoreboards' ? 'Scoreboards' : item === 'tablists' ? 'Tablists' : item === 'menus' ? 'Menus' : `Placeholders (${placeholders.length})`}</button>)}</div>
      {mode === 'texts' && <select value={setIndex} onChange={event => setSetIndex(Number(event.target.value))}>{sets.map((entry, index) => <option key={entry.set.id} value={index}>{entry.set.id}</option>)}</select>}
      {(mode === 'scoreboards' || mode === 'tablists' || mode === 'menus') && <><button disabled={!uiHistory.length} onClick={undoUi}>Annuler</button><button disabled={!uiFuture.length} onClick={redoUi}>Rétablir</button></>}
      {mode === 'texts' && <>
      <select aria-label="Type de recherche" value={searchMode} onChange={event => setSearchMode(event.target.value as SearchMode)}>
        <option value="key">Clé</option><option value="text">Texte</option>
      </select>
      <input aria-label={searchMode === 'key' ? 'Rechercher par clé' : 'Rechercher par texte'}
        placeholder={searchMode === 'key' ? 'Rechercher une clé…' : 'Rechercher dans les traductions…'}
        value={search} onChange={event => setSearch(event.target.value)} />
      <button onClick={createKey}>+ Clé</button><button onClick={() => setRaw(!raw)}>{raw ? 'Édition structurée' : 'YAML français'}</button>
      </>}
      {mode === 'placeholders' && <input aria-label="Rechercher un placeholder" placeholder="Rechercher un placeholder, un module ou une clé…" value={search} onChange={event => setSearch(event.target.value)} />}
      <span className="notice">{notice}</span>
    </section>
    <div className="workspace">
      <aside>{mode === 'texts' ? keys.map(key => <button className={key === selected ? 'active' : ''} key={key} onClick={() => setSelected(key)}>{key}</button>)
        : mode === 'placeholders' ? filteredPlaceholders.map(entry => <button title={entry.description} className={entry.name === selectedPlaceholder ? 'active' : ''} key={entry.name} onClick={() => setSelectedPlaceholder(entry.name)}>{`{${entry.name}}`}<small>{entry.references.length} clé(s)</small></button>)
        : surfaces.map(surface => <button className={surface.identity === selectedUi ? 'active' : ''} key={surface.identity} onClick={() => { setSelectedUi(surface.identity); setSelectedVariant(Object.keys(surface.definition.variants || {})[0] || ''); }}>{surface.file.module}<small>{surface.id}</small></button>)}</aside>
      <section className="editor">
        {mode === 'placeholders' ? <PlaceholderEditor placeholder={placeholder} />
        : mode === 'scoreboards' && selectedSurface ? <ScoreboardEditor surface={selectedSurface} variant={selectedVariant} setVariant={setSelectedVariant} mutate={mutateUi} docs={docs} mutateLanguages={mutate} />
        : mode === 'tablists' && selectedSurface ? <TablistEditor surface={selectedSurface} variant={selectedVariant} setVariant={setSelectedVariant} mutate={mutateUi} docs={docs} mutateLanguages={mutate} />
        : mode === 'menus' && selectedSurface ? <MenuEditor surface={selectedSurface} selectedButton={selectedButton} setSelectedButton={setSelectedButton} mutate={mutateUi} />
        : raw ? <textarea className="raw" value={serialize(docs.fr)} onChange={event => mutate(copy => { copy.fr = documents({ ...snapshots, fr: { ...snapshots.fr, content: event.target.value } }).fr; })} /> : <>
          <div className="keyline"><h2>{selected}</h2><button onClick={renameKey}>Renommer</button><button className="danger" onClick={deleteKey}>Supprimer</button></div>
          <label>Français</label><textarea ref={frenchEditor} value={editableValue(current)} onChange={event => editFrench(event.target.value)} />
          <p className="edit-hint">Entrée insère un saut de ligne dans le texte.</p>
          <div className="translations">
            {(['en','de','es'] as Locale[]).map(locale => <div key={locale}><div className="locale"><b>{locale.toUpperCase()}</b>{locale === 'en' && <button disabled={!provider} onClick={() => translateLocale('en')}>Proposer</button>}</div>
              <textarea value={editableValue(value(docs[locale], selected))}
                onChange={event => mutate(copy => setValue(copy[locale], selected, valueFromEditor(current, event.target.value)))} /></div>)}
          </div>
          <button className="approve" disabled={!provider || englishApproved} onClick={approveEnglish}>Valider l’anglais et générer DE/ES</button>
          <details><summary>{usages.length} usage(s) détecté(s)</summary>{usages.map((usage, index) => <code key={index}>{usage.file}:{usage.line} — {usage.text}</code>)}</details>
        </>}
      </section>
      <section className="preview"><div className="preview-head"><b>Aperçu</b>{mode === 'texts' && <select value={context} onChange={event => { setContext(event.target.value); updateEntry({ context: event.target.value }); }}>{['chat','title','subtitle','actionbar','inventory','lore','scoreboard','tablist'].map(item => <option key={item}>{item}</option>)}</select>}</div>
        {mode === 'placeholders' ? <PlaceholderPreview placeholder={placeholder} />
        : mode === 'scoreboards' ? <div className="scoreboard-full"><strong><Rendered node={scoreboardTitlePreview} /></strong>{surfacePreview.map((line,index) => <div key={index}><Rendered node={line} /></div>)}</div>
        : mode === 'tablists' ? <div className="tablist-full"><div className="tablist-header"><Rendered node={tablistPreview?.header || null} /></div><div className="tablist-players">Nathan<span>42 ms</span><br />Alex<span>58 ms</span><br />Sam<span>71 ms</span></div><div className="tablist-footer"><Rendered node={tablistPreview?.footer || null} /></div></div>
        : mode === 'menus' && selectedSurface ? <MenuPreview surface={selectedSurface} selectedButton={selectedButton} docs={docs} />
        : <div className={`frame ${context}`}><Rendered node={preview} /></div>}
        {mode === 'texts' && <>
        {!!placeholderIds.length && <div className="samples"><b>Exemples des placeholders</b>{placeholderIds.map(id => <label key={id}>{`{${id}}`}<input value={catalog.entries?.[`${sets[setIndex].set.id}:${selected}`]?.placeholders?.[id] || `Valeur ${id}`} onChange={event => updateEntry({ placeholders: { ...(catalog.entries?.[`${sets[setIndex].set.id}:${selected}`]?.placeholders || {}), [id]: event.target.value } })} /></label>)}</div>}
        <details className="catalog"><summary>Glossaire et métadonnées</summary><textarea value={catalogText} onChange={event => setCatalogText(event.target.value)} /><button onClick={saveCatalog}>Enregistrer le catalogue</button></details>
        {!!diagnostics.length && <div className="diagnostics">{diagnostics.map((item, index) => <p key={index}><b>{item.language}:{item.key}</b> {item.message}</p>)}</div>}
        </>}
      </section>
    </div>
    {mode === 'texts' && !raw && <div className="placeholder-picker">
      {placeholderPickerOpen && <section className="placeholder-picker-panel"><div className="placeholder-picker-head"><b>Placeholders disponibles</b><button aria-label="Fermer les placeholders" onClick={() => setPlaceholderPickerOpen(false)}>×</button></div>
        <input aria-label="Rechercher dans les placeholders disponibles" placeholder="Rechercher…" value={placeholderPickerSearch} onChange={event => setPlaceholderPickerSearch(event.target.value)} />
        <div className="placeholder-picker-list">{pickerPlaceholders.map(entry => <button key={entry.name} title={`Insérer {${entry.name}}`} onClick={() => insertPlaceholder(entry.name)}><code>{`{${entry.name}}`}</code><span>{entry.description}</span></button>)}</div>
      </section>}
      <button className="placeholder-picker-toggle" aria-expanded={placeholderPickerOpen} onClick={() => setPlaceholderPickerOpen(open => !open)}>{'{ }'} Placeholders</button>
    </div>}
  </main>;
}

function TablistEditor({ surface, variant, setVariant, mutate, docs, mutateLanguages }: { surface: any; variant: string; setVariant: (value: string) => void; mutate: (fn: (copy: Record<string, any>) => void) => void; docs: Docs; mutateLanguages: (fn: (copy: Docs) => void) => void }) {
  const definition = surface.definition;
  const selected = definition.variants?.[variant];
  if (!selected) return <p className="empty-editor">Aucune variante de tablist.</p>;
  const updateKey = (field: 'header-key' | 'footer-key', key: string) => mutate(copy => { copy[surface.file.id].tablists[surface.id].variants[variant][field] = key; });
  return <div className="surface-editor tablist-editor"><div className="keyline"><h2>{surface.id}</h2><select value={variant} onChange={event => setVariant(event.target.value)}>{Object.keys(definition.variants || {}).map(item => <option key={item}>{item}</option>)}</select></div>
    {(['header-key', 'footer-key'] as const).map(field => <section key={field}><label>{field === 'header-key' ? 'Clé de l’en-tête' : 'Clé du pied'}</label><input value={selected[field]} onChange={event => updateKey(field, event.target.value)} />
      <div className="tablist-translations">{locales.map(locale => {
        const translationKey = selected[field];
        const currentText = value(docs[locale], translationKey);
        return <label key={locale}>{locale.toUpperCase()}<textarea value={editableValue(currentText)} onChange={event => mutateLanguages(copy => setValue(copy[locale], translationKey, valueFromEditor(currentText, event.target.value)))} /></label>;
      })}</div>
    </section>)}
  </div>;
}

function PlaceholderEditor({ placeholder }: { placeholder?: PlaceholderSummary }) {
  if (!placeholder) return <p className="empty-editor">Aucun placeholder ne correspond à la recherche.</p>;
  const modules = [...new Set(placeholder.references.map(reference => reference.set))];
  return <div className="placeholder-editor"><div className="keyline"><h2>{`{${placeholder.name}}`}</h2><span className="placeholder-count">{placeholder.references.length} clé(s)</span></div>
    <p className="placeholder-description">{placeholder.description}</p>
    <p>Présent dans {modules.length} module(s) : {modules.join(', ')}.</p>
    <h3>Clés de traduction</h3>
    <div className="placeholder-references">{placeholder.references.map(reference => <div key={`${reference.set}:${reference.key}`}><strong>{reference.key}</strong><small>{reference.set}</small></div>)}</div>
  </div>;
}

function PlaceholderPreview({ placeholder }: { placeholder?: PlaceholderSummary }) {
  if (!placeholder) return <div className="placeholder-card empty-editor">Aucun résultat</div>;
  return <div className="placeholder-card"><code>{`{${placeholder.name}}`}</code><p>{placeholder.description}</p><span>Exemple</span><strong>{placeholderSample(placeholder.name)}</strong><small>Valeur texte échappée avant insertion MiniMessage</small></div>;
}

function ScoreboardEditor({ surface, variant, setVariant, mutate, docs, mutateLanguages }: { surface: any; variant: string; setVariant: (value: string) => void; mutate: (fn: (copy: Record<string, any>) => void) => void; docs: Docs; mutateLanguages: (fn: (copy: Docs) => void) => void }) {
  const [editingLine, setEditingLine] = useState<number | null>(null);
  const definition = surface.definition;
  const lines: any[] = definition.variants?.[variant]?.lines || [];
  const updateLines = (next: any[]) => mutate(copy => { copy[surface.file.id].scoreboards[surface.id].variants[variant].lines = next; });
  const move = (index: number, delta: number) => { const next = [...lines]; const target = index + delta; if (target < 0 || target >= next.length) return; [next[index], next[target]] = [next[target], next[index]]; updateLines(next); setEditingLine(null); };
  return <div className="surface-editor"><div className="keyline"><h2>{surface.id}</h2><select value={variant} onChange={event => { setEditingLine(null); setVariant(event.target.value); }}>{Object.keys(definition.variants || {}).map(item => <option key={item}>{item}</option>)}</select></div>
    <label>Clé du titre</label><input value={definition['title-key']} onChange={event => mutate(copy => { copy[surface.file.id].scoreboards[surface.id]['title-key'] = event.target.value; })} />
    <div className="title-translations">{locales.map(locale => {
      const titleKey = definition['title-key'];
      const currentTitle = value(docs[locale], titleKey);
      return <label key={locale}>{locale.toUpperCase()}<textarea rows={2} value={editableValue(currentTitle)} onChange={event => mutateLanguages(copy => setValue(copy[locale], titleKey, valueFromEditor(currentTitle, event.target.value)))} /></label>;
    })}</div>
    <div className="line-list">{lines.map((line,index) => <div className="line-row" draggable key={index} onDragStart={event => event.dataTransfer.setData('text/plain',String(index))} onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); const from=Number(event.dataTransfer.getData('text/plain')); if (Number.isInteger(from) && from !== index) move(from,index-from); }}><span title="Glisser pour déplacer">↕ {index + 1}</span>{line.blank ? <i>Ligne vide</i> : <input value={line.key} onChange={event => { const next = [...lines]; next[index] = { key: event.target.value }; updateLines(next); }} />}{line.blank ? <span /> : <button title="Éditer le texte" onClick={() => setEditingLine(editingLine === index ? null : index)}>✎</button>}<button onClick={() => move(index,-1)}>↑</button><button onClick={() => move(index,1)}>↓</button><button className="danger" onClick={() => { updateLines(lines.filter((_,i) => i !== index)); setEditingLine(null); }}>×</button>
      {!line.blank && editingLine === index && <div className="scoreboard-line-translations">{locales.map(locale => {
        const currentText = value(docs[locale], line.key);
        return <label key={locale}>{locale.toUpperCase()}<textarea value={editableValue(currentText)} onChange={event => mutateLanguages(copy => setValue(copy[locale], line.key, valueFromEditor(currentText, event.target.value)))} /></label>;
      })}</div>}
    </div>)}</div>
    <div className="surface-actions"><button disabled={lines.length >= 15} onClick={() => { const key = window.prompt('Clé de traduction de la nouvelle ligne'); if (key) updateLines([...lines,{key}]); }}>+ Texte</button><button disabled={lines.length >= 15} onClick={() => updateLines([...lines,{blank:true}])}>+ Ligne vide</button><span>{lines.length}/15 lignes</span></div>
  </div>;
}

function MenuEditor({ surface, selectedButton, setSelectedButton, mutate }: { surface: any; selectedButton: string; setSelectedButton: (id: string) => void; mutate: (fn: (copy: Record<string, any>) => void) => void }) {
  const definition = surface.definition;
  const buttons: Record<string,any> = definition.buttons || {};
  const selected = buttons[selectedButton];
  const dynamicActions = Object.values<any>(definition['dynamic-regions'] || {}).map(region => region['template-action']);
  const allowedActions = Array.from(new Set([...Object.values<any>(buttons).map(button => button.action), ...dynamicActions, 'none']));
  const update = (id: string, change: Record<string,unknown>) => mutate(copy => Object.assign(copy[surface.file.id].menus[surface.id].buttons[id], change));
  const add = () => {
    const id = window.prompt('Identifiant stable du bouton (lower-kebab-case)'); if (!id || buttons[id]) return;
    const occupied = new Set(Object.values<any>(buttons).map(button => Number(button.slot)));
    const slot = Array.from({length: definition.rows * 9}, (_,index) => index).find(index => !occupied.has(index));
    if (slot === undefined) return;
    mutate(copy => { copy[surface.file.id].menus[surface.id].buttons[id] = { slot, material: 'STONE_BUTTON', 'name-key': '', 'lore-key': '', action: allowedActions[0] || 'none', amount: 1, glow: false }; });
    setSelectedButton(id);
  };
  const remove = () => {
    if (!selected || selected.required) return;
    mutate(copy => { delete copy[surface.file.id].menus[surface.id].buttons[selectedButton]; }); setSelectedButton('');
  };
  return <div className="surface-editor"><div className="keyline"><h2>{surface.id}</h2><button onClick={add}>+ Bouton</button></div>
    <div className="menu-properties"><label>Titre<input value={definition['title-key']} onChange={event => mutate(copy => { copy[surface.file.id].menus[surface.id]['title-key'] = event.target.value; })} /></label><label>Lignes<select value={definition.rows} onChange={event => mutate(copy => { copy[surface.file.id].menus[surface.id].rows = Number(event.target.value); })}>{[1,2,3,4,5,6].map(row => <option key={row}>{row}</option>)}</select></label><label>Cadrage<select value={definition.frame || 'network'} onChange={event => mutate(copy => { copy[surface.file.id].menus[surface.id].frame = event.target.value; })}>{['network','neutral','none'].map(frame => <option key={frame}>{frame}</option>)}</select></label></div>
    <InventoryGrid definition={definition} selectedButton={selectedButton} select={setSelectedButton} move={(id,slot) => update(id,{slot})} />
    {selected && <div className="button-editor"><div className="keyline"><h3>{selectedButton}</h3><button className="danger" disabled={!!selected.required} onClick={remove}>{selected.required ? 'Bouton requis' : 'Supprimer'}</button></div>
      <label>Slot<input type="number" min="0" max={definition.rows * 9 - 1} value={selected.slot} onChange={event => update(selectedButton,{slot:Number(event.target.value)})} /></label>
      <label>Matériau<input value={selected.material} onChange={event => update(selectedButton,{material:event.target.value.toUpperCase()})} /></label>
      <label>Clé du nom<input value={selected['name-key'] || ''} onChange={event => update(selectedButton,{'name-key':event.target.value})} /></label>
      <label>Clé du lore<input value={selected['lore-key'] || ''} onChange={event => update(selectedButton,{'lore-key':event.target.value})} /></label>
      <label>Action<select value={selected.action} onChange={event => update(selectedButton,{action:event.target.value})}>{allowedActions.map(action => <option key={action}>{action}</option>)}</select></label>
      <label>Quantité<input type="number" min="1" max="99" value={selected.amount || 1} onChange={event => update(selectedButton,{amount:Number(event.target.value)})} /></label>
      <label className="check"><input type="checkbox" checked={!!selected.glow} onChange={event => update(selectedButton,{glow:event.target.checked})} /> Effet enchanté</label>
    </div>}
  </div>;
}

function InventoryGrid({ definition, selectedButton, select, move }: { definition: any; selectedButton: string; select: (id: string) => void; move?: (id:string,slot:number) => void }) {
  const buttons: Record<string,any> = definition.buttons || {};
  const bySlot = Object.fromEntries(Object.entries<any>(buttons).map(([id,button]) => [button.slot,{id,...button}]));
  const dynamic = new Set(Object.values<any>(definition['dynamic-regions'] || {}).flatMap(region => region.slots || []));
  return <div className="inventory-grid" style={{gridTemplateRows:`repeat(${definition.rows}, 52px)`}}>{Array.from({length:definition.rows * 9},(_,slot) => { const button = bySlot[slot]; return <button draggable={!!button && !!move} onDragStart={event => button && event.dataTransfer.setData('application/x-tropicube-button',button.id)} onDragOver={event => { if (move && !button) event.preventDefault(); }} onDrop={event => { const id=event.dataTransfer.getData('application/x-tropicube-button'); if (move && id && !button) { event.preventDefault(); move(id,slot); } }} title={button ? `${button.id} · ${button.material}` : `Slot ${slot}`} className={`${button ? 'item-slot' : ''} ${button?.id === selectedButton ? 'selected' : ''} ${dynamic.has(slot) ? 'dynamic' : ''}`} key={slot} onClick={() => button && select(button.id)}>{button ? <><img src={`/api/assets/item/${button.material.toLowerCase()}`} onError={event => { event.currentTarget.style.display='none'; }} /><small>{materialAbbreviation(button.material)}</small>{button.glow && <i>✦</i>}</> : dynamic.has(slot) ? <small>ex.</small> : null}</button>; })}</div>;
}

function MenuPreview({ surface, selectedButton, docs }: { surface: any; selectedButton: string; docs: Docs }) {
  const definition = surface.definition;
  const button = definition.buttons?.[selectedButton];
  const translated = (key: string) => { if (!key) return ''; const found = value(docs.fr,key); return editableValue(found); };
  return <div className="menu-preview"><div className="menu-title">{translated(definition['title-key']) || definition['title-key']}</div><InventoryGrid definition={definition} selectedButton={selectedButton} select={() => {}} />{button && <div className="item-tooltip"><b>{translated(button['name-key']) || button.id}</b><p>{translated(button['lore-key'])}</p><code>{button.action}</code></div>}</div>;
}

function materialAbbreviation(material: string): string {
  return material.split('_').map((part:string) => part[0]).join('').slice(0,3);
}

function placeholderSample(name: string): string {
  const samples: Record<string,string> = { profile:'[VIP] Nathan', balance:'12 450', online_players:'128', visible_games:'7', queue:'Ranked 4v4', reserved_players:'6', capacity:'8', wait_seconds:'42', current_players:'12', max_players:'16', min_players:'8', map:'Archipel', countdown:'10', time:'08:42', red_players:'5', blue_players:'6', team:'Rouge', player_class:'Support', kills:'3', sheep_thrown:'14' };
  return samples[name] || `Valeur ${name}`;
}

function Rendered({ node }: { node: ComponentNode | null }) {
  if (!node) return null;
  const style: React.CSSProperties = { color: node.color ? color(node.color) : undefined, fontWeight: node.bold ? 700 : undefined, fontStyle: node.italic ? 'italic' : undefined,
    textDecoration: [node.underlined && 'underline', node.strikethrough && 'line-through'].filter(Boolean).join(' ') || undefined };
  return <span style={style}>{node.text}{node.extra?.map((child, index) => <Rendered node={child} key={index} />)}</span>;
}

function color(name: string) {
  const colors: Record<string,string> = { black:'#000',dark_blue:'#0000aa',dark_green:'#00aa00',dark_aqua:'#00aaaa',dark_red:'#aa0000',dark_purple:'#aa00aa',gold:'#ffaa00',gray:'#aaa',dark_gray:'#555',blue:'#5555ff',green:'#55ff55',aqua:'#55ffff',red:'#ff5555',light_purple:'#ff55ff',yellow:'#ffff55',white:'#fff' };
  return colors[name] || name;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
