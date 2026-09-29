#!/usr/bin/env node
// Turns the owner's chosen AI backgrounds into the film's web images, then rewrites
// components/film/backdrops.ts so those acts use them. Input files: <act>-desktop.<png|jpg|jpeg|webp>
// and <act>-mobile.<...> for act0, act1, act2, act3, act4, act6, act7 (act5 is the 3D town, no art).
// Usage (from web/): node scripts/film-assets.mjs <source-folder>
import { mkdir, readdir, stat, writeFile } from "node:fs/promises";
import { basename, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

export const ACTS = ["act0", "act1", "act2", "act3", "act4", "act6", "act7"];
export const SIZE = {
  desktop: { width: 2400, height: 1350 },
  mobile: { width: 1080, height: 1920 },
};
/** Act 3 pans sideways on desktop, so its desktop art is twice as wide. */
const WIDE = { act3: { width: 4800, height: 1350 } };
/** Bytes allowed per AVIF (spec §8). WebP is the fallback and is not budgeted. */
export const BUDGET = { desktop: 180_000, mobile: 110_000 };

const HEADER = `// Written by scripts/film-assets.mjs; run that script instead of editing by hand.
// An act listed here has its AI background in public/film/; an act missing from it shows its placeholder.
export type ActId = ${ACTS.map((a) => `"${a}"`).join(" | ")};
`;

async function encodeWithinBudget(input, size, budget) {
  for (let quality = 60; quality >= 30; quality -= 5) {
    const avif = await sharp(input).resize(size.width, size.height, { fit: "cover" }).avif({ quality, effort: 6 }).toBuffer();
    if (avif.length <= budget) return { avif, quality };
  }
  return null;
}

export async function build(sourceDir, publicDir, manifestPath) {
  const files = (await readdir(sourceDir)).filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  const found = new Map();
  for (const file of files) {
    const [act, variant] = basename(file, extname(file)).split("-");
    if (!ACTS.includes(act) || !(variant in SIZE)) continue;
    found.set(`${act}-${variant}`, join(sourceDir, file));
  }
  const acts = ACTS.filter((a) => found.has(`${a}-desktop`) || found.has(`${a}-mobile`));
  for (const act of acts) {
    if (!found.has(`${act}-desktop`) || !found.has(`${act}-mobile`)) throw new Error(`${act} needs both desktop and mobile images`);
  }

  const outDir = join(publicDir, "film");
  await mkdir(outDir, { recursive: true });
  const results = [];
  const manifest = {};
  for (const act of acts) {
    for (const variant of ["desktop", "mobile"]) {
      const size = variant === "desktop" && WIDE[act] ? WIDE[act] : SIZE[variant];
      const budget = BUDGET[variant] * (size.width / SIZE[variant].width);
      const input = found.get(`${act}-${variant}`);
      const encoded = await encodeWithinBudget(input, size, budget);
      if (!encoded) throw new Error(`${act}-${variant}: no AVIF quality from 60 down to 30 fits ${Math.round(budget / 1000)} KB; simplify the image`);
      const webp = await sharp(input).resize(size.width, size.height, { fit: "cover" }).webp({ quality: 72 }).toBuffer();
      await writeFile(join(outDir, `${act}-${variant}.avif`), encoded.avif);
      await writeFile(join(outDir, `${act}-${variant}.webp`), webp);
      results.push({ act, variant, avifBytes: encoded.avif.length, webpBytes: webp.length, quality: encoded.quality });
      if (variant === "desktop") manifest[act] = size;
    }
  }

  const entries = Object.entries(manifest).map(([act, s]) => `  ${act}: { width: ${s.width}, height: ${s.height} },`).join("\n");
  await writeFile(manifestPath, `${HEADER}\nexport const BACKDROPS: Partial<Record<ActId, { width: number; height: number }>> = {\n${entries}\n};\n`);
  return results;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const source = process.argv[2];
  if (!source || !(await stat(source).catch(() => null))?.isDirectory()) {
    console.error("Usage: node scripts/film-assets.mjs <folder with act0-desktop.png, act0-mobile.png, ...>");
    process.exit(1);
  }
  const web = resolve(fileURLToPath(new URL("..", import.meta.url)));
  const results = await build(source, join(web, "public"), join(web, "components", "film", "backdrops.ts"));
  for (const r of results) {
    console.log(`${r.act}-${r.variant}: AVIF ${(r.avifBytes / 1024).toFixed(0)} KB (q${r.quality}), WebP ${(r.webpBytes / 1024).toFixed(0)} KB`);
  }
}
