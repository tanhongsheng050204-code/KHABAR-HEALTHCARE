// Wraps khabar-landing.html (written as an Artifact body fragment) into a full
// HTML document for static hosting. Output: dist/index.html
import { mkdir, readFile, writeFile } from "node:fs/promises";

const src = await readFile(new URL("./khabar-landing.html", import.meta.url), "utf8");
const split = src.indexOf('<div class="backdrop"');
if (split === -1) throw new Error('Could not find <div class="backdrop"> in khabar-landing.html');

const head = src.slice(0, split).replace(/^\s*<meta charset="utf-8">\s*/i, "");
const body = src.slice(split);

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="description" content="Khabar follows Malaysian clinic patients home: plain-language instructions in their own language, 30 days of WhatsApp check-ins, and a daily call list for the clinic.">
<meta name="theme-color" content="#0D1A20">
${head.trim()}
</head>
<body>
${body.trim()}
</body>
</html>
`;

await mkdir(new URL("./dist/", import.meta.url), { recursive: true });
await writeFile(new URL("./dist/index.html", import.meta.url), html);
console.log(`Built dist/index.html (${html.length} bytes)`);
