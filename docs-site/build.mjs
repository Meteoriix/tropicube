import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const projectDirectory = resolve(siteDirectory, "..");
const stylesheet = await readFile(join(siteDirectory, "styles.css"));
const stylesheetVersion = createHash("sha256").update(stylesheet).digest("hex").slice(0, 12);

const pages = [
  { source: "README.md", sourceEn: "README.en.md", output: "index.html", label: { fr: "Accueil", en: "Home" }, icon: "⌂", description: { fr: "Vue d’ensemble du projet Tropicube", en: "Overview of the Tropicube project" } },
  { source: "docs/ARCHITECTURE.md", sourceEn: "docs/en/ARCHITECTURE.md", output: "architecture.html", label: { fr: "Architecture", en: "Architecture" }, icon: "◇", description: { fr: "Services, modules, données et flux réseau", en: "Services, modules, data, and network flows" } },
  { source: "docs/COMMANDS.md", sourceEn: "docs/en/COMMANDS.md", output: "commands.html", label: { fr: "Commandes", en: "Commands" }, icon: ">_", description: { fr: "Commandes Minecraft, alias et permissions", en: "Minecraft commands, aliases, and permissions" } },
  { source: "docs/CONFIGURATION.md", sourceEn: "docs/en/CONFIGURATION.md", output: "configuration.html", label: { fr: "Configuration", en: "Configuration" }, icon: "⚙", description: { fr: "Variables, templates et réglages des plugins", en: "Variables, templates, and plugin settings" } },
  { source: "docs/DEPLOYMENT.md", sourceEn: "docs/en/DEPLOYMENT.md", output: "deployment.html", label: { fr: "Déploiement", en: "Deployment" }, icon: "⇧", description: { fr: "Installation et exploitation sous Windows et Linux", en: "Installation and operations on Windows and Linux" } },
  { source: "docs/SHEEPWARS.md", sourceEn: "docs/en/SHEEPWARS.md", output: "sheepwars.html", label: { fr: "SheepWars", en: "SheepWars" }, icon: "♙", description: { fr: "Game design, règles, kits et moutons spéciaux", en: "Game design, rules, kits, and special sheep" } },
  { source: "docs/FALLEN_KINGDOMS_GAME_DESIGN.md", sourceEn: "docs/en/FALLEN_KINGDOMS_GAME_DESIGN.md", output: "fallen-kingdoms.html", label: { fr: "Fallen Kingdoms", en: "Fallen Kingdoms" }, icon: "♜", description: { fr: "Game design historique, royaumes, kits et sièges", en: "Historical game design, kingdoms, kits, and sieges" } },
  { source: "docs/FALLEN_KINGDOMS_TECHNICAL_SPEC.md", sourceEn: "docs/en/FALLEN_KINGDOMS_TECHNICAL_SPEC.md", output: "fallen-kingdoms-technical-spec.html", label: { fr: "FK technique", en: "FK technical" }, icon: "⌘", description: { fr: "Spécification V1, états, protections et contrats techniques", en: "V1 specification, states, protections, and technical contracts" } },
  { source: "docs/DEVELOPMENT.md", sourceEn: "docs/en/DEVELOPMENT.md", output: "development.html", label: { fr: "Développement", en: "Development" }, icon: "{ }", description: { fr: "Environnement, tests et contributions", en: "Environment, tests, and contributions" } },
  { source: "docs/GIT_CI.md", sourceEn: "docs/en/GIT_CI.md", output: "git-ci.html", label: { fr: "Git & CI", en: "Git & CI" }, icon: "⑂", description: { fr: "Branches, commits, CI, Dependabot et versions", en: "Branches, commits, CI, Dependabot, and releases" } },
  { source: "docs/CHANGELOG.md", sourceEn: "docs/en/CHANGELOG.md", output: "changelog.html", label: { fr: "Changements", en: "Changes" }, icon: "≡", description: { fr: "Historique fonctionnel et technique du projet", en: "Functional and technical project history" } }
];

const outputByMarkdown = new Map([
  ["README.md", "index.html"],
  ["README.en.md", "index.html"],
  ["docs-site/index.html", "index.html"],
  ["docs-site/en/index.html", "index.html"],
  ["docs/ARCHITECTURE.md", "architecture.html"],
  ["ARCHITECTURE.md", "architecture.html"],
  ["docs/COMMANDS.md", "commands.html"],
  ["COMMANDS.md", "commands.html"],
  ["docs/CONFIGURATION.md", "configuration.html"],
  ["CONFIGURATION.md", "configuration.html"],
  ["docs/DEPLOYMENT.md", "deployment.html"],
  ["DEPLOYMENT.md", "deployment.html"],
  ["docs/SHEEPWARS.md", "sheepwars.html"],
  ["SHEEPWARS.md", "sheepwars.html"],
  ["docs/FALLEN_KINGDOMS_GAME_DESIGN.md", "fallen-kingdoms.html"],
  ["FALLEN_KINGDOMS_GAME_DESIGN.md", "fallen-kingdoms.html"],
  ["docs/FALLEN_KINGDOMS_TECHNICAL_SPEC.md", "fallen-kingdoms-technical-spec.html"],
  ["FALLEN_KINGDOMS_TECHNICAL_SPEC.md", "fallen-kingdoms-technical-spec.html"],
  ["docs/DEVELOPMENT.md", "development.html"],
  ["DEVELOPMENT.md", "development.html"],
  ["docs/GIT_CI.md", "git-ci.html"],
  ["GIT_CI.md", "git-ci.html"],
  ["docs/CHANGELOG.md", "changelog.html"],
  ["CHANGELOG.md", "changelog.html"],
  ...pages.map(page => [page.sourceEn.replaceAll("\\", "/"), page.output])
]);
const frenchSources = new Set(["README.md", "docs-site/index.html", ...pages.map(page => page.source)]);
const englishSources = new Set(["README.en.md", "docs-site/en/index.html", ...pages.map(page => page.sourceEn)]);

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function slug(value) {
  return value.normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function rewriteHref(href, locale) {
  const [path, fragment = ""] = href.split("#", 2);
  const normalizedPath = path.replaceAll("\\", "/");
  const mapped = outputByMarkdown.get(normalizedPath);
  if (!mapped) return href;
  const prefix = locale === "fr" && englishSources.has(normalizedPath)
    ? "en/"
    : locale === "en" && frenchSources.has(normalizedPath) ? "../" : "";
  return prefix + mapped + (fragment ? `#${fragment}` : "");
}

function inlineMarkdown(value, locale) {
  const codeSpans = [];
  let text = value.replace(/`([^`]+)`/g, (_, code) => {
    const token = `@@CODE${codeSpans.length}@@`;
    codeSpans.push(`<code>${escapeHtml(code)}</code>`);
    return token;
  });

  text = escapeHtml(text)
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, (_, label, href) => {
      const target = rewriteHref(href, locale);
      const external = /^https?:\/\//i.test(target);
      return `<a href="${escapeHtml(target)}"${external ? ' target="_blank" rel="noreferrer"' : ""}>${label}</a>`;
    })
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>");

  return text.replace(/@@CODE(\d+)@@/g, (_, index) => codeSpans[Number(index)]);
}

function isTableSeparator(line) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
}

function tableCells(line) {
  const source = line.trim().replace(/^\||\|$/g, "");
  const cells = [];
  let cell = "";
  let inCode = false;
  for (let index = 0; index < source.length; index++) {
    const character = source[index];
    if (character === "`") inCode = !inCode;
    if (character === "|" && !inCode && source[index - 1] !== "\\") {
      cells.push(cell.trim());
      cell = "";
    } else {
      cell += character;
    }
  }
  cells.push(cell.trim());
  return cells.map(value => value.replaceAll("\\|", "|"));
}

function renderMarkdown(markdown, locale) {
  const lines = markdown.replaceAll("\r\n", "\n").split("\n");
  const html = [];
  const headings = [];
  let index = 0;
  let firstHeading = null;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index++;
      continue;
    }

    const fence = line.match(/^```(.*)$/);
    if (fence) {
      const language = fence[1].trim().toLowerCase();
      const code = [];
      index++;
      while (index < lines.length && !lines[index].startsWith("```")) code.push(lines[index++]);
      index++;
      const className = language === "mermaid" ? "diagram-source" : "";
      html.push(`<div class="code-block"><span class="code-language">${escapeHtml(language || "text")}</span><pre class="${className}"><code>${escapeHtml(code.join("\n"))}</code></pre></div>`);
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length;
      const title = heading[2].replace(/\*\*|`/g, "");
      const id = slug(title);
      if (level === 1 && firstHeading === null) {
        firstHeading = title;
      } else {
        if (level <= 3) headings.push({ level, title, id });
        const anchorLabel = locale === "fr" ? "Lien vers cette section" : "Link to this section";
        html.push(`<h${level} id="${id}">${inlineMarkdown(heading[2], locale)}<a class="heading-anchor" href="#${id}" aria-label="${anchorLabel}">#</a></h${level}>`);
      }
      index++;
      continue;
    }

    if (line.includes("|") && index + 1 < lines.length && isTableSeparator(lines[index + 1])) {
      const headers = tableCells(line);
      index += 2;
      const rows = [];
      while (index < lines.length && lines[index].includes("|") && lines[index].trim()) {
        rows.push(tableCells(lines[index++]));
      }
      html.push(`<div class="table-scroll"><table><thead><tr>${headers.map(cell => `<th>${inlineMarkdown(cell, locale)}</th>`).join("")}</tr></thead><tbody>${rows.map(row => `<tr>${row.map(cell => `<td>${inlineMarkdown(cell, locale)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`);
      continue;
    }

    const unordered = line.match(/^\s*[-*]\s+(.+)$/);
    const ordered = line.match(/^\s*\d+\.\s+(.+)$/);
    if (unordered || ordered) {
      const tag = unordered ? "ul" : "ol";
      const items = [];
      const pattern = unordered ? /^\s*[-*]\s+(.+)$/ : /^\s*\d+\.\s+(.+)$/;
      while (index < lines.length) {
        const match = lines[index].match(pattern);
        if (!match) break;
        items.push(match[1]);
        index++;
      }
      html.push(`<${tag}>${items.map(item => `<li>${inlineMarkdown(item, locale)}</li>`).join("")}</${tag}>`);
      continue;
    }

    if (line.startsWith("> ")) {
      const quote = [];
      while (index < lines.length && lines[index].startsWith("> ")) quote.push(lines[index++].slice(2));
      html.push(`<blockquote>${inlineMarkdown(quote.join(" "), locale)}</blockquote>`);
      continue;
    }

    if (/^---+$/.test(line.trim())) {
      html.push("<hr>");
      index++;
      continue;
    }

    const paragraph = [line.trim()];
    index++;
    while (index < lines.length && lines[index].trim()
      && !/^(#{1,4})\s+/.test(lines[index])
      && !/^```/.test(lines[index])
      && !/^\s*[-*]\s+/.test(lines[index])
      && !/^\s*\d+\.\s+/.test(lines[index])
      && !(lines[index].includes("|") && index + 1 < lines.length && isTableSeparator(lines[index + 1]))) {
      paragraph.push(lines[index].trim());
      index++;
    }
    html.push(`<p>${inlineMarkdown(paragraph.join(" "), locale)}</p>`);
  }

  return { title: firstHeading ?? "Documentation", html: html.join("\n"), headings };
}

function navigation(activeOutput, locale) {
  return pages.map(page => `<a class="nav-link${page.output === activeOutput ? " active" : ""}" href="${page.output}"><span class="nav-icon">${page.icon}</span><span>${page.label[locale]}</span></a>`).join("\n");
}

function tableOfContents(headings, locale) {
  const entries = headings.filter(heading => heading.level === 2 || heading.level === 3);
  if (!entries.length) return "";
  const label = locale === "fr" ? "Sur cette page" : "On this page";
  return `<aside class="page-toc" aria-label="${label}"><span class="toc-title">${label}</span>${entries.map(heading => `<a class="toc-level-${heading.level}" href="#${heading.id}">${escapeHtml(heading.title)}</a>`).join("")}</aside>`;
}

function documentTemplate(page, rendered, locale) {
  const english = locale === "en";
  const assetPrefix = english ? "../" : "";
  const alternateHref = english ? `../${page.output}` : `en/${page.output}`;
  const alternateLabel = english ? "Français" : "English";
  return `<!doctype html>
<html lang="${locale}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="${escapeHtml(page.description[locale])}">
  <meta name="theme-color" content="#2d1b6b">
  <title>${escapeHtml(rendered.title)} · Tropicube</title>
  <link rel="stylesheet" href="${assetPrefix}styles.css?v=${stylesheetVersion}">
</head>
<body>
  <a class="skip-link" href="#content">${english ? "Skip to content" : "Aller au contenu"}</a>
  <header class="mobile-header"><a href="index.html" class="mobile-brand"><span class="brand-cube">T</span><span><span class="brand-name-accent">Tropi</span>cube</span></a><a class="language-switch" href="${alternateHref}" hreflang="${english ? "fr" : "en"}">${alternateLabel}</a></header>
  <aside class="sidebar">
    <a class="brand" href="index.html"><span class="brand-cube">T</span><span><strong><span class="brand-name-accent">Tropi</span>cube</strong><small>${english ? "Network documentation" : "Documentation réseau"}</small></span></a>
    <a class="language-switch" href="${alternateHref}" hreflang="${english ? "fr" : "en"}">${alternateLabel}</a>
    <nav aria-label="${english ? "Main documentation" : "Documentation principale"}">${navigation(page.output, locale)}</nav>
    <div class="sidebar-status"><span class="status-dot"></span><span><strong>Minecraft 26.2</strong><small>Paper · Velocity · Docker</small></span></div>
  </aside>
  <main id="content" class="main">
    <section class="hero"><div class="eyebrow">${english ? "Official documentation" : "Documentation officielle"}</div><h1>${escapeHtml(rendered.title)}</h1><p>${escapeHtml(page.description[locale])}</p></section>
    <div class="content-grid">
      <article class="documentation">${rendered.html}</article>
      ${tableOfContents(rendered.headings, locale)}
    </div>
    <footer><span><span class="brand-name-accent">Tropi</span>cube</span><span>${english ? "Static documentation generated from Markdown files" : "Documentation statique générée depuis les fichiers Markdown"}</span></footer>
  </main>
</body>
</html>`;
}

await mkdir(siteDirectory, { recursive: true });
await mkdir(join(siteDirectory, "en"), { recursive: true });
for (const page of pages) {
  const markdown = await readFile(join(projectDirectory, page.source), "utf8");
  const rendered = renderMarkdown(markdown, "fr");
  await writeFile(join(siteDirectory, page.output), documentTemplate(page, rendered, "fr"), "utf8");
  console.log(`Generated: ${page.output}`);

  const markdownEn = await readFile(join(projectDirectory, page.sourceEn), "utf8");
  const renderedEn = renderMarkdown(markdownEn, "en");
  await writeFile(join(siteDirectory, "en", page.output), documentTemplate(page, renderedEn, "en"), "utf8");
  console.log(`Generated: en/${page.output}`);
}
