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

validateDatedChangelog(join(siteDirectory, "..", "docs", "CHANGELOG.md"), "CHANGELOG.md");
validateDatedChangelog(join(siteDirectory, "..", "docs", "en", "CHANGELOG.md"), "en/CHANGELOG.md");

function validateDatedChangelog(path, label) {
  const lines = readFileSync(path, "utf8").replace(/\r\n/g, "\n").split("\n");
  const unreleased = lines.findIndex(line => /^## (Non publié|Unreleased)$/.test(line));
  if (unreleased < 0) {
    errors.push(`${label}: missing Unreleased section`);
    return;
  }
  let activeDate;
  let previousDate;
  for (const line of lines.slice(unreleased + 1)) {
    if (line.startsWith("## ")) break;
    const heading = line.match(/^### (.+)$/);
    if (heading) {
      const parsedDate = new Date(`${heading[1]}T00:00:00Z`);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(heading[1])
          || Number.isNaN(parsedDate.valueOf())
          || parsedDate.toISOString().slice(0, 10) !== heading[1]) {
        errors.push(`${label}: invalid date heading ${heading[1]}`);
        activeDate = undefined;
        continue;
      }
      activeDate = heading[1];
      if (previousDate && activeDate >= previousDate) {
        errors.push(`${label}: dates are not strictly descending (${previousDate}, ${activeDate})`);
      }
      previousDate = activeDate;
    } else if (line.startsWith("- ") && !activeDate) {
      errors.push(`${label}: undated entry ${line}`);
    }
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`HTML and link validation successful: ${pages.length} pages.`);
}
