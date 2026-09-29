import { expect, test, type Page } from "@playwright/test";

const HEADING = /See the whole story,\s*not just the appointment\./;

test("the finale says what the current landing page says, and its button opens the prototype", async ({
  page,
}) => {
  for (const path of ["/", "/preview/film"]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { name: HEADING })).toHaveCount(1);
  }
  const enter = page
    .locator("#act7")
    .getByRole("link", { name: "Enter the live prototype" });
  await expect(enter).toHaveAttribute("href", "/login");
});

test("the way back to the thirty days lands on them", async ({ page }) => {
  await page.goto("/preview/film");
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

const offsets = (page: Page) =>
  page
    .locator("#act7 [data-sky-thread]")
    .evaluateAll((els) =>
      els.map((el) => parseFloat(getComputedStyle(el).strokeDashoffset)),
    );

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("every thread has risen and every light is lit: the end frame", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    const risen = await offsets(page);
    expect(risen).toHaveLength(12);
    expect(risen.every((o) => o === 0)).toBe(true);
    const lit = await page
      .locator("#act7 [data-star]")
      .evaluateAll((els) => els.map((el) => getComputedStyle(el).opacity));
    expect(lit).toHaveLength(12);
    expect(lit.every((o) => o === "1")).toBe(true);
  });
});

test("scrolling to the end raises every thread into the sky", async ({
  page,
}) => {
  await page.goto("/preview/film");
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
  await page.goto("/preview/film");
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
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});
