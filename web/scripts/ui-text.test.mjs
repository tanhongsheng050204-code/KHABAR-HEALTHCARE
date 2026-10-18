// Every phrase the patient and caregiver screens pass to t("...") has a Malay, Chinese and Tamil version,
// and every translation keeps the {placeholders} of its English phrase.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const web = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = readFileSync(join(web, "lib", "ui-text.ts"), "utf8");
const rows = [...source.matchAll(/^\s*"((?:[^"\\]|\\.)+)":\s*\[\s*"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\s*\],?$/gm)];
const table = new Map(rows.map((m) => [m[1], [m[2], m[3], m[4]]]));

function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : path.endsWith(".tsx") ? [path] : [];
  });
}

test("the phrase table parses", () => {
  assert.ok(table.size > 150, `only ${table.size} phrases parsed`);
});

test("every phrase has all three translations", () => {
  for (const [phrase, translations] of table) {
    translations.forEach((text, i) => assert.ok(text.trim(), `${["ms", "zh", "ta"][i]} missing for "${phrase}"`));
  }
});

test("translations keep the phrase's placeholders", () => {
  for (const [phrase, translations] of table) {
    const wanted = [...phrase.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const text of translations) {
      const got = [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
      assert.deepEqual(got, wanted, `placeholders differ in "${text}"`);
    }
  }
});

test("every t(\"...\") in the screens has an entry", () => {
  const used = new Set();
  for (const file of [...files(join(web, "components", "home")), ...files(join(web, "components", "features"))]) {
    for (const m of readFileSync(file, "utf8").matchAll(/\bt\("((?:[^"\\]|\\.)+)"/g)) used.add(m[1]);
  }
  const missing = [...used].filter((p) => !table.has(p));
  assert.deepEqual(missing, []);
});
