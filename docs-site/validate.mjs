import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const pages = [
  ...readdirSync(siteDirectory).filter(file => file.endsWith(".html")),
  ...readdirSync(join(siteDirectory, "en")).filter(file => file.endsWith(".html")).map(file => `en/${file}`)
];
const errors = [];
const stylesheet = readFileSync(join(siteDirectory, "styles.css"));
const stylesheetVersion = createHash("sha256").update(stylesheet).digest("hex").slice(0, 12);
const expectedStylesheetHref = `styles.css?v=${stylesheetVersion}`;

for (const page of pages) {
  const html = readFileSync(join(siteDirectory, page), "utf8");
  if (!html.includes("<!doctype html>") || !html.includes("</html>")) {
    errors.push(`${page}: incomplete HTML document`);
  }
  const expectedHref = page.startsWith("en/") ? `../${expectedStylesheetHref}` : expectedStylesheetHref;
  if (!html.includes(`href="${expectedHref}"`)) {
    errors.push(`${page}: missing or stale stylesheet version`);
  }

  for (const match of html.matchAll(/href="([^"]+)"/g)) {
    const href = match[1];
    if (href.startsWith("#") || /^(https?:|mailto:)/.test(href)) continue;
    const target = href.split(/[?#]/, 1)[0];
    if (target.endsWith(".md")) errors.push(`${page}: unresolved Markdown link ${href}`);
    if (target && !existsSync(join(siteDirectory, page.startsWith("en/") ? "en" : "", target))) {
      errors.push(`${page} : missing local target ${href}`);
    }
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`HTML and link validation successful: ${pages.length} pages.`);
}
