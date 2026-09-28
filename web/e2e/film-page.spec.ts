import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const noHorizontalScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);

test("no accessibility violations with motion on", async ({ page }) => {
  await page.goto("/preview/film");
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("no accessibility violations", async ({ page }) => {
    await page.goto("/preview/film");
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
  });
});

test("no horizontal scroll, even in Tamil at 320 px", async ({ page }) => {
  await page.goto("/preview/film");
  expect(await noHorizontalScroll(page)).toBe(true);
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole("button", { name: "தமிழ்" }).click();
  await page.getByRole("button", { name: "Chest pain" }).click();
  expect(await noHorizontalScroll(page)).toBe(true);
});

test("pausing inside the pinned pan unpins it and stacks the days", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "The pin is desktop only.");
  await page.goto("/preview/film");
  await page.evaluate(() => document.getElementById("act3")!.scrollIntoView({ block: "start" }));
  await page.evaluate(() => window.scrollBy(0, 1200));
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "static");
  await page.locator("article[data-day='7']").scrollIntoViewIfNeeded();
  await expect(page.locator("article[data-day='7']")).toBeInViewport();
});

/** Transferred size of every script a page loads, in a fresh context so nothing comes from cache. */
async function scriptSizes(browser: import("@playwright/test").Browser, path: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const sizes = new Map<string, number>();
  const reads: Promise<void>[] = [];
  page.on("response", (response) => {
    if (response.request().resourceType() !== "script") return;
    reads.push(response.request().sizes().then((s) => { sizes.set(new URL(response.url()).pathname, s.responseBodySize); }));
  });
  await page.goto(`http://localhost:3100${path}`, { waitUntil: "networkidle" });
  await Promise.all(reads); // every size read finishes before the context closes, or some would be lost
  await context.close();
  return sizes;
}

test("the film adds at most 70 KB of JavaScript over the current landing page", async ({ browser }) => {
  const budget = Number(process.env.FILM_JS_BUDGET_KB ?? 70);
  const landing = await scriptSizes(browser, "/");
  const film = await scriptSizes(browser, "/preview/film");
  const added = [...film].filter(([path]) => !landing.has(path)).reduce((sum, [, bytes]) => sum + bytes, 0);
  console.log(`JS the film adds over /: ${(added / 1024).toFixed(1)} KB (budget ${budget} KB)`);
  expect(added).toBeGreaterThan(0);
  expect(added).toBeLessThanOrEqual(budget * 1024);
});
