import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { parse, stringify } from 'yaml';
import { Diagnostic, documents, editableValue, FileSnapshot, filterKeys, flatten, inferContext, Locale, locales, rename, SearchMode, serialize, setValue, StateSet, value, valueFromEditor, withTranslations } from './model';
import './styles.css';

type Docs = ReturnType<typeof documents>;
type ComponentNode = { text?: string; color?: string; bold?: boolean; italic?: boolean; underlined?: boolean; strikethrough?: boolean; extra?: ComponentNode[] };
type LiveStatus = { available: boolean; message: string };
type LiveResult = { available: boolean; updatedContainers: number; containers: string[]; errors: string[] };

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
  return body;
}

function App() {
  const [sets, setSets] = useState<StateSet[]>([]);
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

  useEffect(() => {
    Promise.all([api<{sets: StateSet[]; catalog: string; live: LiveStatus}>('/api/state'), api<{available: boolean}>('/api/translation/status')])
      .then(([state, status]) => { setSets(state.sets); setCatalog(parse(state.catalog)); setCatalogText(state.catalog); setProvider(status.available); setLive(state.live); setNotice('Prêt'); })
      .catch(error => setNotice(error.message));
  }, []);

  useEffect(() => {
    if (!sets[setIndex]) return;
    setSnapshots(sets[setIndex].files);
    const parsed = documents(sets[setIndex].files);
    setDocs(parsed);
    setSelected(flatten(parsed.fr)[0] || '');
  }, [sets, setIndex]);

  const keys = useMemo(() => docs ? filterKeys(docs, search, searchMode) : [], [docs, search, searchMode]);
  const current = docs && selected ? value(docs.fr, selected) : '';
  const placeholderIds = useMemo(() => Array.from(new Set((Array.isArray(current) ? current.join('\n') : current).match(/\{\d+}/g) || [])).map(token => token.slice(1, -1)), [current]);

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
    for (const match of message.matchAll(/\{(\d+)}/g)) placeholders[match[1]] = catalog.entries?.[`${sets[setIndex]?.set.id}:${selected}`]?.placeholders?.[match[1]] || `Valeur ${match[1]}`;
    api<ComponentNode>('/api/preview', { method: 'POST', body: JSON.stringify({ message, placeholders }) })
      .then(setPreview).catch(error => setNotice(error.message));
  }, [docs, selected, catalog, setIndex, sets]);

  const mutate = (fn: (copy: Docs) => void) => {
    if (!docs) return;
    const copy = Object.fromEntries(locales.map(locale => [locale, docs[locale].clone()])) as Docs;
    fn(copy); setDocs(copy); setEnglishApproved(false);
  };

  const editFrench = (text: string) => mutate(copy => setValue(copy.fr, selected, valueFromEditor(current, text)));

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
    setLive({ available: result.live.available && !result.live.errors.length, message: result.live.errors.length ? result.live.errors.join(' · ') : 'Jeu synchronisé' });
    if (result.live.updatedContainers > 0 && !result.live.errors.length) {
      setNotice(`Enregistré et rechargé dans ${result.live.updatedContainers} serveur(s)`);
    } else if (result.live.errors.length) {
      setNotice(`Enregistré, mise à jour en jeu incomplète : ${result.live.errors.join(' · ')}`);
    } else setNotice('Enregistré ; aucun serveur actif à recharger');
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
      <button onClick={validate}>Valider</button><button className="primary" onClick={apply}>Appliquer</button>
    </div></header>
    <section className="toolbar">
      <select value={setIndex} onChange={event => setSetIndex(Number(event.target.value))}>{sets.map((entry, index) => <option key={entry.set.id} value={index}>{entry.set.id}</option>)}</select>
      <select aria-label="Type de recherche" value={searchMode} onChange={event => setSearchMode(event.target.value as SearchMode)}>
        <option value="key">Clé</option><option value="text">Texte</option>
      </select>
      <input aria-label={searchMode === 'key' ? 'Rechercher par clé' : 'Rechercher par texte'}
        placeholder={searchMode === 'key' ? 'Rechercher une clé…' : 'Rechercher dans les traductions…'}
        value={search} onChange={event => setSearch(event.target.value)} />
      <button onClick={createKey}>+ Clé</button><button onClick={() => setRaw(!raw)}>{raw ? 'Édition structurée' : 'YAML français'}</button>
      <span className="notice">{notice}</span>
    </section>
    <div className="workspace">
      <aside>{keys.map(key => <button className={key === selected ? 'active' : ''} key={key} onClick={() => setSelected(key)}>{key}</button>)}</aside>
      <section className="editor">
        {raw ? <textarea className="raw" value={serialize(docs.fr)} onChange={event => mutate(copy => { copy.fr = documents({ ...snapshots, fr: { ...snapshots.fr, content: event.target.value } }).fr; })} /> : <>
          <div className="keyline"><h2>{selected}</h2><button onClick={renameKey}>Renommer</button><button className="danger" onClick={deleteKey}>Supprimer</button></div>
          <label>Français</label><textarea value={editableValue(current)} onChange={event => editFrench(event.target.value)} />
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
      <section className="preview"><div className="preview-head"><b>Aperçu</b><select value={context} onChange={event => { setContext(event.target.value); updateEntry({ context: event.target.value }); }}>{['chat','title','subtitle','actionbar','inventory','lore','scoreboard','tablist'].map(item => <option key={item}>{item}</option>)}</select></div>
        <div className={`frame ${context}`}><Rendered node={preview} /></div>
        {!!placeholderIds.length && <div className="samples"><b>Exemples des placeholders</b>{placeholderIds.map(id => <label key={id}>{`{${id}}`}<input value={catalog.entries?.[`${sets[setIndex].set.id}:${selected}`]?.placeholders?.[id] || `Valeur ${id}`} onChange={event => updateEntry({ placeholders: { ...(catalog.entries?.[`${sets[setIndex].set.id}:${selected}`]?.placeholders || {}), [id]: event.target.value } })} /></label>)}</div>}
        <details className="catalog"><summary>Glossaire et métadonnées</summary><textarea value={catalogText} onChange={event => setCatalogText(event.target.value)} /><button onClick={saveCatalog}>Enregistrer le catalogue</button></details>
        {!!diagnostics.length && <div className="diagnostics">{diagnostics.map((item, index) => <p key={index}><b>{item.language}:{item.key}</b> {item.message}</p>)}</div>}
      </section>
    </div>
  </main>;
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
