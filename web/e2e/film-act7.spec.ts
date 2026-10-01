import { expect, test, type Page } from "@playwright/test";

const HEADING = /See the whole story,\s*not just the appointment\./;

test("the finale keeps the old landing page's closing words, and its button opens the prototype", async ({
  page,
}) => {
  // The old landing page's closing heading, kept word for word.
  await page.goto("/");
  await expect(page.getByRole("heading", { name: HEADING })).toHaveCount(1);
  const enter = page
    .locator("#act7")
    .getByRole("link", { name: "Enter the live prototype" });
  await expect(enter).toHaveAttribute("href", "/login");
});

test("the way back to the thirty days lands on them", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await page
    .locator("#act7")
    .getByRole("link", { name: "Explore the 30-day story" })
    .click();
  await expect(page.locator("#act3-title")).toBeInViewport();
});

// The sky drawn for this screen: a wide one on desktops, a tall one on phones.
const offsets = (page: Page) =>
  page
    .locator("#act7 [data-sky-thread]")
    .evaluateAll((els) =>
      els
        .filter((el) => el.getBoundingClientRect().width > 0)
        .map((el) => parseFloat(getComputedStyle(el).strokeDashoffset)),
    );

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("every thread has risen and every light is lit: the end frame", async ({
    page,
  }) => {
    await page.goto("/");
    const risen = await offsets(page);
    expect(risen).toHaveLength(12);
    expect(risen.every((o) => o === 0)).toBe(true);
    const lit = await page
      .locator("#act7 [data-star]")
      .evaluateAll((els) =>
        els
          .filter((el) => el.getBoundingClientRect().width > 0)
          .map((el) => getComputedStyle(el).opacity),
      );
    expect(lit).toHaveLength(12);
    expect(lit.every((o) => o === "1")).toBe(true);
  });
});

test("scrolling to the end raises every thread into the sky", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  expect((await offsets(page))[0]).toBe(1);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect
    .poll(async () => (await offsets(page)).every((o) => o === 0), {
      timeout: 5000,
    })
    .toBe(true);
});

test("the stars twinkle only while the finale is on screen and motion is on", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const running = () =>
    page
      .locator("#act7")
      .evaluate(
        (el) =>
          el
            .getAnimations({ subtree: true })
            .filter((a) => a.playState === "running").length,
      );
  expect(await running()).toBe(0);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect.poll(running).toBeGreaterThan(0);
  // Scrolled away, the lights rest; back on screen, they move again.
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(running).toBe(0);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect.poll(running).toBeGreaterThan(0);
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});

test("no thread of light runs through the finale's words or buttons", async ({
  page,
}) => {
  await page.goto("/");
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(1500);
  const sharp = (await import("sharp")).default;
  for (const target of [
    page.locator("#act7-title"),
    page
      .locator("#act7")
      .getByRole("link", { name: "Enter the live prototype" }),
    page
      .locator("#act7")
      .getByRole("link", { name: "Explore the 30-day story" }),
  ]) {
    await target.scrollIntoViewIfNeeded();
    const png = await page.screenshot({ clip: (await target.boundingBox())! });
    const { data, info } = await sharp(png)
      .raw()
      .toBuffer({ resolveWithObject: true });
    let glowing = 0;
    for (let i = 0; i < data.length; i += info.channels) {
      const [r, g, b] = [data[i], data[i + 1], data[i + 2]];
      // The thread's bright green (#7fd1a8): green well above red, and bright. The heading's own green
      // italic (#9fd9bd) is paler: its red is above 140.
      if (g > 170 && g - r > 60 && r < 140 && b > 130) glowing++;
    }
    expect(glowing, (await target.textContent()) ?? "").toBe(0);
  }
});

test("the finale's description matches its drawing", async ({ page }) => {
  await page.goto("/");
  const text = await page
    .locator("#act7")
    .getByText(/^Illustration: /)
    .textContent();
  expect(text).not.toContain("from above");
  expect(text).toContain("row of homes");
});

test("on a phone the finale shows every home and its thread", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "The phone layout.");
  await page.goto("/");
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(800);
  const width = page.viewportSize()!.width;
  const homes = await page.locator("#act7 [data-sky-home]").evaluateAll(
    (els, w) =>
      els.filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.left >= 0 && r.right <= w;
      }).length,
    width,
  );
  expect(homes).toBe(12);
});

test("the finale's scroll animation drives only the sky on screen", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  // GSAP writes inline styles on what it animates; the hidden sky must be left alone.
  const touched = await page
    .locator("#act7 [data-sky-thread], #act7 [data-star]")
    .evaluateAll(
      (els) =>
        els
          .filter((el) => el.getBoundingClientRect().width === 0)
          .filter((el) => (el as SVGElement).style.length > 0).length,
    );
  expect(touched).toBe(0);
});

test("on a portrait tablet the finale still shows every home", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "A tablet-sized window.");
  await page.setViewportSize({ width: 768, height: 1024 });
  await page.goto("/");
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(800);
  const homes = await page.locator("#act7 [data-sky-home]").evaluateAll(
    (els, w) =>
      els.filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.left >= 0 && r.right <= w;
      }).length,
    768,
  );
  expect(homes).toBe(12);
});
