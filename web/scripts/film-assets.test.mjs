import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import sharp from "sharp";
import { build, BUDGET } from "./film-assets.mjs";

/** A noisy test image, so compression has real work to do. */
async function noisyPng(path, width, height) {
  const noise = Buffer.alloc(width * height * 3);
  for (let i = 0; i < noise.length; i++) noise[i] = (i * 7919) % 256;
  await sharp(noise, { raw: { width, height, channels: 3 } }).blur(2).png().toFile(path);
}

test("converts an act's art to budgeted AVIF and WebP and lists it in the manifest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "film-"));
  const source = join(dir, "src");
  const publicDir = join(dir, "public");
  const manifest = join(dir, "backdrops.ts");
  await mkdir(source, { recursive: true });
  await noisyPng(join(source, "act0-desktop.png"), 2600, 1460);
  await noisyPng(join(source, "act0-mobile.png"), 1200, 2100);
  await writeFile(manifest, "");

  const results = await build(source, publicDir, manifest);

  assert.equal(results.length, 2);
  for (const r of results) assert.ok(r.avifBytes <= BUDGET[r.variant], `${r.variant} AVIF ${r.avifBytes} B over budget`);
  const meta = await sharp(join(publicDir, "film", "act0-desktop.webp")).metadata();
  assert.deepEqual([meta.width, meta.height], [2400, 1350]);
  assert.ok((await stat(join(publicDir, "film", "act0-mobile.avif"))).size > 0);
  const written = await readFile(manifest, "utf8");
  assert.match(written, /act0: \{ width: 2400, height: 1350 \}/);
  assert.match(written, /export type ActId =/);
});

test("an act with only one variant is refused", async () => {
  const dir = await mkdtemp(join(tmpdir(), "film-"));
  const source = join(dir, "src");
  await mkdir(source, { recursive: true });
  await noisyPng(join(source, "act4-desktop.png"), 2400, 1350);
  await assert.rejects(build(source, join(dir, "public"), join(dir, "backdrops.ts")), /act4 needs both desktop and mobile/);
});
