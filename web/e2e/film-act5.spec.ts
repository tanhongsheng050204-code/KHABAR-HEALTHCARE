import { expect, test, type Page } from "@playwright/test";

const home = (page: Page, name: string) =>
  page.getByRole("button", { name: `${name}’s home` });
/** A home's roof: what a reader sees and clicks. The button's own box lies flat on the ground below it. */
const roof = (page: Page, name: string) =>
  home(page, name).locator("i").first();

test("the town's key counts every home by its reply, and says it is illustrative", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const key = page.locator("#act5 [data-town-key]");
  await expect(key).toContainText("Doing well · 8");
  await expect(key).toContainText("On the follow-up list · 3");
  await expect(key).toContainText("Emergency advice given · 1");
  await expect(page.locator("#act5")).toContainText("Illustrative");
  await expect(page.locator("#act5 [data-house]")).toHaveCount(12);
  await expect(page.locator("#act5 [data-town-thread]")).toHaveCount(12);
});

test("selecting a home shows its latest reply and what Khabar did", async ({
  page,
}) => {
  await page.goto("/preview/film");
  // The server's HTML is already interactive-looking; a click before hydration would be lost.
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const status = page.locator("#act5 [data-home-status]");
  await expect(status).toContainText("Select a home");
  // Click once the homes have risen, as a reader would: a roof still rising moves under the pointer.
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect
    .poll(async () => (await houseZ(page)).every((z) => z === 0))
    .toBe(true);
  await roof(page, "Mr Muthu").click();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "true");
  await expect(status).toContainText("நெஞ்சு வலி.");
  await expect(status).toContainText("999 advice at once");
  await expect(status.locator("[lang='ta']")).toHaveCount(1);
});

test("a home can be selected with the keyboard", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await home(page, "Pak Cik Rahim").focus();
  await page.keyboard.press("Enter");
  const status = page.locator("#act5 [data-home-status]");
  await expect(status).toContainText("Pening sikit hari ini.");
  await expect(status).toContainText("follow-up list");
  await expect(home(page, "Pak Cik Rahim")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});

const houseZ = (page: Page) =>
  page
    .locator("#act5 [data-house]")
    .evaluateAll((els) =>
      els.map((el) => new DOMMatrix(getComputedStyle(el).transform).m43),
    );
const threadOffsets = (page: Page) =>
  page
    .locator("#act5 [data-town-thread]")
    .evaluateAll((els) =>
      els.map((el) => parseFloat(getComputedStyle(el).strokeDashoffset)),
    );

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the town is built and every thread drawn: the end frame", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    const z = await houseZ(page);
    const offsets = await threadOffsets(page);
    expect(z).toHaveLength(12);
    expect(offsets).toHaveLength(12);
    expect(z.every((v) => v === 0)).toBe(true);
    expect(offsets.every((o) => o === 0)).toBe(true);
  });

  test("on a 320 px phone every home is its own target, centres 24 px apart or more", async ({
    page,
  }, info) => {
    test.skip(info.project.name !== "phone", "The phone layout.");
    await page.setViewportSize({ width: 320, height: 700 });
    await page.goto("/preview/film");
    const centres = await page
      .locator("#act5 [data-house]")
      .evaluateAll((els) =>
        els.map((el) => {
          const r = el.getBoundingClientRect();
          return [r.x + r.width / 2, r.y + r.height / 2];
        }),
      );
    expect(centres).toHaveLength(12);
    for (const [i, a] of centres.entries()) {
      for (const b of centres.slice(i + 1)) {
        expect(Math.hypot(a[0] - b[0], a[1] - b[1])).toBeGreaterThanOrEqual(24);
      }
    }
  });
});

test("scrolling to the town raises the homes and draws the threads", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  expect((await houseZ(page))[0]).toBeLessThan(-40);
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect
    .poll(async () => (await houseZ(page)).every((z) => z === 0), {
      timeout: 5000,
    })
    .toBe(true);
  await expect
    .poll(async () => (await threadOffsets(page)).every((o) => o === 0), {
      timeout: 5000,
    })
    .toBe(true);
});

test("the town's lights bob only while it is on screen and motion is on", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const running = () =>
    page
      .locator("#act5")
      .evaluate(
        (el) =>
          el
            .getAnimations({ subtree: true })
            .filter((a) => a.playState === "running").length,
      );
  expect(await running()).toBe(0);
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect.poll(running).toBeGreaterThan(0);
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});
