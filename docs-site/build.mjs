import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const projectDirectory = resolve(siteDirectory, "..");

const pages = [
  { source: "README.md", output: "index.html", label: "Accueil", icon: "⌂", description: "Vue d’ensemble du projet Tropicube" },
  { source: "docs/ARCHITECTURE.md", output: "architecture.html", label: "Architecture", icon: "◇", description: "Services, modules, données et flux réseau" },
  { source: "docs/COMMANDS.md", output: "commands.html", label: "Commandes", icon: ">_", description: "Commandes Minecraft, alias et permissions" },
  { source: "docs/CONFIGURATION.md", output: "configuration.html", label: "Configuration", icon: "⚙", description: "Variables, templates et réglages des plugins" },
  { source: "docs/DEPLOYMENT.md", output: "deployment.html", label: "Déploiement", icon: "⇧", description: "Installation et exploitation sous Windows et Linux" },
  { source: "docs/SHEEPWARS.md", output: "sheepwars.html", label: "SheepWars", icon: "♙", description: "Game design, règles, kits et moutons spéciaux" },
  { source: "docs/FALLEN_KINGDOMS_GAME_DESIGN.md", output: "fallen-kingdoms.html", label: "Fallen Kingdoms", icon: "♜", description: "Game design historique, royaumes, kits et sièges" },
  { source: "docs/FALLEN_KINGDOMS_TECHNICAL_SPEC.md", output: "fallen-kingdoms-technical-spec.html", label: "FK technique", icon: "⌘", description: "Spécification V1, états, protections et contrats techniques" },
  { source: "docs/DEVELOPMENT.md", output: "development.html", label: "Développement", icon: "{ }", description: "Environnement, tests et contributions" },
  { source: "docs/GIT_CI.md", output: "git-ci.html", label: "Git & CI", icon: "⑂", description: "Branches, commits, CI, Dependabot et versions" },
  { source: "docs/CHANGELOG.md", output: "changelog.html", label: "Changements", icon: "≡", description: "Historique fonctionnel et technique du projet" }
];

const outputByMarkdown = new Map([
  ["README.md", "index.html"],
  ["docs-site/index.html", "index.html"],
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
  ["CHANGELOG.md", "changelog.html"]
]);

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

function rewriteHref(href) {
  const [path, fragment = ""] = href.split("#", 2);
  const mapped = outputByMarkdown.get(path.replaceAll("\\", "/"));
  if (!mapped) return href;
  return mapped + (fragment ? `#${fragment}` : "");
}

function inlineMarkdown(value) {
  const codeSpans = [];
  let text = value.replace(/`([^`]+)`/g, (_, code) => {
    const token = `@@CODE${codeSpans.length}@@`;
    codeSpans.push(`<code>${escapeHtml(code)}</code>`);
    return token;
  });

  text = escapeHtml(text)
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, (_, label, href) => {
      const target = rewriteHref(href);
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

function renderMarkdown(markdown) {
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
        html.push(`<h${level} id="${id}">${inlineMarkdown(heading[2])}<a class="heading-anchor" href="#${id}" aria-label="Lien vers cette section">#</a></h${level}>`);
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
      html.push(`<div class="table-scroll"><table><thead><tr>${headers.map(cell => `<th>${inlineMarkdown(cell)}</th>`).join("")}</tr></thead><tbody>${rows.map(row => `<tr>${row.map(cell => `<td>${inlineMarkdown(cell)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`);
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
      html.push(`<${tag}>${items.map(item => `<li>${inlineMarkdown(item)}</li>`).join("")}</${tag}>`);
      continue;
    }

    if (line.startsWith("> ")) {
      const quote = [];
      while (index < lines.length && lines[index].startsWith("> ")) quote.push(lines[index++].slice(2));
      html.push(`<blockquote>${inlineMarkdown(quote.join(" "))}</blockquote>`);
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
    html.push(`<p>${inlineMarkdown(paragraph.join(" "))}</p>`);
  }

  return { title: firstHeading ?? "Documentation", html: html.join("\n"), headings };
}

function navigation(activeOutput) {
  return pages.map(page => `<a class="nav-link${page.output === activeOutput ? " active" : ""}" href="${page.output}"><span class="nav-icon">${page.icon}</span><span>${page.label}</span></a>`).join("\n");
}

function tableOfContents(headings) {
  const entries = headings.filter(heading => heading.level === 2 || heading.level === 3);
  if (!entries.length) return "";
  return `<aside class="page-toc" aria-label="Sommaire de la page"><span class="toc-title">Sur cette page</span>${entries.map(heading => `<a class="toc-level-${heading.level}" href="#${heading.id}">${escapeHtml(heading.title)}</a>`).join("")}</aside>`;
}

function documentTemplate(page, rendered) {
  return `<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="${escapeHtml(page.description)}">
  <meta name="theme-color" content="#2d1b6b">
  <title>${escapeHtml(rendered.title)} · Tropicube</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <a class="skip-link" href="#contenu">Aller au contenu</a>
  <header class="mobile-header"><a href="index.html" class="mobile-brand"><span class="brand-cube">T</span><span><span class="brand-name-accent">Tropi</span>cube</span></a><span>Documentation</span></header>
  <aside class="sidebar">
    <a class="brand" href="index.html"><span class="brand-cube">T</span><span><strong><span class="brand-name-accent">Tropi</span>cube</strong><small>Documentation réseau</small></span></a>
    <nav aria-label="Documentation principale">${navigation(page.output)}</nav>
    <div class="sidebar-status"><span class="status-dot"></span><span><strong>Minecraft 26.2</strong><small>Paper · Velocity · Docker</small></span></div>
  </aside>
  <main id="contenu" class="main">
    <section class="hero"><div class="eyebrow">Documentation officielle</div><h1>${escapeHtml(rendered.title)}</h1><p>${escapeHtml(page.description)}</p></section>
    <div class="content-grid">
      <article class="documentation">${rendered.html}</article>
      ${tableOfContents(rendered.headings)}
    </div>
    <footer><span><span class="brand-name-accent">Tropi</span>cube</span><span>Documentation statique générée depuis les fichiers Markdown</span></footer>
  </main>
</body>
</html>`;
}

await mkdir(siteDirectory, { recursive: true });
for (const page of pages) {
  const markdown = await readFile(join(projectDirectory, page.source), "utf8");
  const rendered = renderMarkdown(markdown);
  await writeFile(join(siteDirectory, page.output), documentTemplate(page, rendered), "utf8");
  console.log(`Généré : ${page.output}`);
}
