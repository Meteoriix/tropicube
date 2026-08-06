import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const pages = readdirSync(siteDirectory).filter(file => file.endsWith(".html"));
const errors = [];
const stylesheet = readFileSync(join(siteDirectory, "styles.css"));
const stylesheetVersion = createHash("sha256").update(stylesheet).digest("hex").slice(0, 12);
const expectedStylesheetHref = `styles.css?v=${stylesheetVersion}`;

for (const page of pages) {
  const html = readFileSync(join(siteDirectory, page), "utf8");
  if (!html.includes("<!doctype html>") || !html.includes("</html>")) {
    errors.push(`${page} : document HTML incomplet`);
  }
  if (!html.includes(`href="${expectedStylesheetHref}"`)) {
    errors.push(`${page} : version de feuille de style absente ou obsolète`);
  }

  for (const match of html.matchAll(/href="([^"]+)"/g)) {
    const href = match[1];
    if (href.startsWith("#") || /^(https?:|mailto:)/.test(href)) continue;
    const target = href.split(/[?#]/, 1)[0];
    if (target.endsWith(".md")) errors.push(`${page} : lien Markdown résiduel ${href}`);
    if (target && !existsSync(join(siteDirectory, target))) {
      errors.push(`${page} : cible locale absente ${href}`);
    }
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Validation HTML et liens réussie : ${pages.length} pages.`);
}
