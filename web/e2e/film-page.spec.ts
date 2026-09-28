import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const noHorizontalScroll = (page: Page) =>
  page.evaluate(
    () => document.documentElement.scrollWidth <= window.innerWidth,
  );

test("no accessibility violations with motion on", async ({ page }) => {
  await page.goto("/preview/film");
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual(
    [],
  );
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("no accessibility violations", async ({ page }) => {
    await page.goto("/preview/film");
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual(
      [],
    );
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

test("pausing inside the pinned pan unpins it and stacks the days", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "The pin is desktop only.");
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await page.evaluate(() => window.scrollBy(0, 1200));
  await page.getByRole("button", { name: "Pause motion" }).click();
  // Nothing is still pinned: the hero's React-rendered spacer stays in the DOM (ScrollTrigger reuses it,
  // so it cannot remove it), but with no pin padding and its section back in normal flow.
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          [...document.querySelectorAll(".pin-spacer")].filter(
            (s) =>
              parseFloat(getComputedStyle(s).paddingBottom) > 0 ||
              getComputedStyle(s.firstElementChild!).position === "fixed",
          ).length,
      ),
    )
    .toBe(0);
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "static");
  await page.locator("article[data-day='7']").scrollIntoViewIfNeeded();
  await expect(page.locator("article[data-day='7']")).toBeInViewport();
});

/** Transferred size of every script a page loads, in a fresh context so nothing comes from cache. */
async function scriptSizes(
  browser: import("@playwright/test").Browser,
  path: string,
) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const sizes = new Map<string, number>();
  const reads: Promise<void>[] = [];
  page.on("response", (response) => {
    if (response.request().resourceType() !== "script") return;
    reads.push(
      response
        .request()
        .sizes()
        .then((s) => {
          sizes.set(new URL(response.url()).pathname, s.responseBodySize);
        }),
    );
  });
  await page.goto(`http://localhost:3100${path}`, { waitUntil: "networkidle" });
  await Promise.all(reads); // every size read finishes before the context closes, or some would be lost
  await context.close();
  return sizes;
}

test("the film adds at most 70 KB of JavaScript over the current landing page", async ({
  browser,
}) => {
  const budget = Number(process.env.FILM_JS_BUDGET_KB ?? 70);
  const landing = await scriptSizes(browser, "/");
  const film = await scriptSizes(browser, "/preview/film");
  const added = [...film]
    .filter(([path]) => !landing.has(path))
    .reduce((sum, [, bytes]) => sum + bytes, 0);
  console.log(
    `JS the film adds over /: ${(added / 1024).toFixed(1)} KB (budget ${budget} KB)`,
  );
  expect(added).toBeGreaterThan(0);
  expect(added).toBeLessThanOrEqual(budget * 1024);
});

test("fonts and CSS stay light enough for a fast first paint on mobile", async ({
  browser,
}) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const bytes = { font: 0, stylesheet: 0 };
  const reads: Promise<void>[] = [];
  page.on("response", (response) => {
    const type = response.request().resourceType();
    if (type !== "font" && type !== "stylesheet") return;
    reads.push(
      response
        .request()
        .sizes()
        .then((s) => {
          bytes[type] += s.responseBodySize;
        }),
    );
  });
  await page.goto("http://localhost:3100/preview/film", {
    waitUntil: "networkidle",
  });
  await Promise.all(reads);
  await context.close();
  console.log(
    `fonts ${(bytes.font / 1024).toFixed(0)} KB, CSS ${(bytes.stylesheet / 1024).toFixed(0)} KB`,
  );
  // Measured on 29 Sep: web fonts for Chinese and Tamil added ~170 KB of fonts and a 68 KB render-blocking
  // stylesheet of @font-face rules, pushing mobile LCP to 5.1 s. Two Latin families fit well under this.
  expect(bytes.font).toBeLessThanOrEqual(130 * 1024);
  expect(bytes.stylesheet).toBeLessThanOrEqual(45 * 1024);
});
