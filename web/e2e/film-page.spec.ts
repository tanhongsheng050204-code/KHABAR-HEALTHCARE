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

test("pausing and resuming keep the reader on the day they were looking at", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "The pinned pan is desktop only.");
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  const day14 = page.locator("article[data-day='14']");
  // Scroll the pan until Day 14 is on screen.
  await expect
    .poll(
      async () => {
        await page.evaluate(() => window.scrollBy(0, 200));
        return day14.evaluate((el) => {
          const r = el.getBoundingClientRect();
          return r.left >= 0 && r.right <= window.innerWidth;
        });
      },
      { timeout: 15_000, intervals: [150] },
    )
    .toBe(true);
  const pause = page.getByRole("button", { name: "Pause motion" });
  await pause.click();
  await expect(day14).toBeInViewport();
  await pause.click();
  await expect(day14).toBeInViewport({ timeout: 5000 });
});

test("a malformed link fragment does not break the page", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/preview/film#100%");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.waitForTimeout(500);
  expect(errors).toEqual([]);
});

test("after arriving by #act3 and going back up, pausing does not jump back to #act3", async ({
  page,
}) => {
  await page.goto("/preview/film#act3");
  await expect(page.locator("article[data-day='1']")).toBeInViewport({
    timeout: 5000,
  });
  await page.evaluate(() => window.scrollTo(0, 0));
  const pause = page.getByRole("button", { name: "Pause motion" });
  await pause.click();
  await pause.click();
  await expect(page.getByRole("heading", { level: 1 })).toBeInViewport();
});

/** Right edges past the screen: the page clips overflow, so the page-wide check alone cannot see these. */
const overflowing = (page: Page) =>
  page.evaluate(() =>
    [
      ...document.querySelectorAll(
        "[data-from], [aria-label='Khabar speaks her language'] button, [data-lab] button",
      ),
    ]
      .filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.right > window.innerWidth + 1;
      })
      .map((el) => (el.textContent ?? "").slice(0, 30)),
  );

test("in Tamil at 320 px no chip or bubble runs off the screen, even mid-scroll through the hero", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "தமிழ்" }).click();
  expect(await overflowing(page)).toEqual([]);
  await page.evaluate(() => window.scrollBy(0, window.innerHeight * 0.55));
  await page.waitForTimeout(700);
  expect(await overflowing(page)).toEqual([]);
  await page.getByRole("button", { name: "Chest pain" }).click();
  expect(await overflowing(page)).toEqual([]);
});

test("reloading mid-way through the pan shows the days, not an empty pinned area", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Desktop pan.");
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await page.evaluate(() => window.scrollBy(0, 700));
  await page.waitForTimeout(600);
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() =>
        [
          ...document.querySelectorAll(
            "article[data-day], #act0 h1, [data-lab] h3",
          ),
        ].some((el) => {
          const r = el.getBoundingClientRect();
          return (
            r.bottom > 0 &&
            r.top < window.innerHeight &&
            r.right > 0 &&
            r.left < window.innerWidth
          );
        }),
      ),
    )
    .toBe(true);
});

test("resizing to phone width mid-pan stacks the days, and back to desktop the pan still ends on Day 30", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Desktop pan.");
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await page.evaluate(() => window.scrollBy(0, 700));
  await page.setViewportSize({ width: 700, height: 900 });
  await page.waitForTimeout(500);
  const [one, three] = await Promise.all(
    ["1", "3"].map((d) =>
      page
        .locator(`article[data-day='${d}']`)
        .evaluate((el) => el.getBoundingClientRect().toJSON()),
    ),
  );
  expect(three.top).toBeGreaterThanOrEqual(one.bottom);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await expect
    .poll(
      async () => {
        await page.evaluate(() => window.scrollBy(0, 250));
        return page.locator("article[data-day='30']").evaluate((el) => {
          const r = el.getBoundingClientRect();
          return (
            r.left >= 0 &&
            r.right <= window.innerWidth &&
            r.top < window.innerHeight &&
            r.bottom > 0
          );
        });
      },
      { timeout: 15_000, intervals: [150] },
    )
    .toBe(true);
});

test("the keyboard alone reaches the language chips and the replies, each on screen when focused", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const reach = async (name: string, limit: number) => {
    for (let i = 0; i < limit; i++) {
      await page.keyboard.press("Tab");
      const focused = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) return null;
        const r = el.getBoundingClientRect();
        return {
          name: el.textContent?.trim() ?? "",
          onScreen:
            r.bottom > 0 &&
            r.top < window.innerHeight &&
            r.right > 0 &&
            r.left < window.innerWidth,
        };
      });
      if (focused?.name === name) return focused;
      if (focused)
        expect(
          focused.onScreen,
          `"${focused.name}" was focused off screen`,
        ).toBe(true);
    }
    return null;
  };
  expect(await reach("中文", 20)).toEqual({ name: "中文", onScreen: true });
  await page.keyboard.press("Enter");
  expect(await reach("Chest pain", 30)).toEqual({
    name: "Chest pain",
    onScreen: true,
  });
  await page.keyboard.press("Space");
  await expect(page.locator("[data-outcome]")).toHaveAttribute(
    "data-state",
    "red",
  );
});

test("turning a phone sideways and back keeps the reader where they were", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Phone rotation.");
  await page.goto("/preview/film");
  await page.locator("article[data-day='7']").scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  await page.setViewportSize({ width: 844, height: 390 });
  await page.waitForTimeout(600);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(600);
  const y = await page.evaluate(() => window.scrollY);
  expect(y).toBeGreaterThan(500); // not sent back to the top of the page
});
