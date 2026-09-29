import { expect, test } from "@playwright/test";

test("Pause motion stops the film and can resume it", async ({ page }) => {
  await page.goto("/");
  const root = page.locator("[data-motion]");
  const pause = page.getByRole("button", { name: "Pause motion" });
  await expect(root).toHaveAttribute("data-motion", "on");
  await expect(pause).toHaveAttribute("aria-pressed", "false");
  await pause.click();
  await expect(root).toHaveAttribute("data-motion", "off");
  await expect(pause).toHaveAttribute("aria-pressed", "true");
  await pause.click();
  await expect(root).toHaveAttribute("data-motion", "on");
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the device setting keeps motion off and explains why", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.locator("[data-motion]")).toHaveAttribute(
      "data-motion",
      "off",
    );
    const pause = page.getByRole("button", { name: "Pause motion" });
    await expect(pause).toBeDisabled();
    await expect(pause).toContainText("Reduced motion is on");
  });
});

test("without JavaScript the page still shows its words", async ({
  browser,
}) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "off",
  );
  await context.close();
});

test("the film turns off CSS smooth scrolling, which fights ScrollTrigger's scroll jumps", async ({
  page,
}) => {
  await page.goto("/");
  expect(
    await page.evaluate(
      () => getComputedStyle(document.documentElement).scrollBehavior,
    ),
  ).toBe("auto");
});

test("on a phone the Pause motion button is a small round button that keeps its name", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Phone layout only.");
  await page.goto("/");
  const pause = page.getByRole("button", { name: "Pause motion" });
  const box = await pause.boundingBox();
  expect(box!.width).toBeLessThanOrEqual(48);
});

test("Pause motion comes first in keyboard order, before the header's links", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const order = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>("a[href], button")]
      .filter((el) => el.tabIndex >= 0)
      .map((el) => el.textContent!.trim()),
  );
  expect(order.indexOf("Pause motion")).toBeLessThan(order.indexOf("Sign in"));
});

test("while paused nothing animates, not even hover lifts or the thread's colour fade", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "off",
  );
  await page.getByRole("button", { name: "EN", exact: true }).hover();
  await page.getByRole("button", { name: "A bit dizzy" }).click();
  const running = await page.evaluate(
    () =>
      document.getAnimations().filter((a) => a.playState === "running").length,
  );
  expect(running).toBe(0);
});

test("the page as served does not claim reduced motion is on", async ({
  request,
}) => {
  const html = await (await request.get("/")).text();
  expect(html).not.toContain("Reduced motion is on");
});

test("the hero, the thirty days and Nurul stop their loops once scrolled away", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const running = (id: string) =>
    page
      .locator(`#${id}`)
      .evaluate(
        (el) =>
          el
            .getAnimations({ subtree: true })
            .filter((a) => a.playState === "running").length,
      );
  await expect.poll(() => running("act0")).toBeGreaterThan(0);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  for (const id of ["act0", "act3", "act4"])
    await expect.poll(() => running(id), { message: id }).toBe(0);
});

test("a thread that needs attention breathes with its glow's opacity, not a blurred stroke width", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "A bit dizzy" }).click();
  const lab = page.locator("[data-lab]");
  await lab.scrollIntoViewIfNeeded();
  const props = await lab.evaluate((el) =>
    el
      .getAnimations({ subtree: true })
      .flatMap((a) =>
        (a.effect as KeyframeEffect)
          .getKeyframes()
          .flatMap((k) => Object.keys(k)),
      ),
  );
  expect(props).toContain("opacity");
  expect(props).not.toContain("strokeWidth");
});

test("the lab thread rests when Aminah's reply is fine", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "I'm okay" }).click();
  await expect(page.locator("[data-thread='lab']")).not.toHaveAttribute(
    "data-pulse",
  );
});

test.describe("with reduced motion, the page does no layout work while scrolling", () => {
  test.use({ reducedMotion: "reduce" });
  test("no day card is measured on scroll", async ({ page }) => {
    await page.addInitScript(() => {
      const w = window as unknown as { dayReads: number };
      w.dayReads = 0;
      const read = Element.prototype.getBoundingClientRect;
      Element.prototype.getBoundingClientRect = function () {
        if (this.hasAttribute("data-day")) w.dayReads++;
        return read.call(this);
      };
    });
    await page.goto("/");
    await page.waitForTimeout(500);
    await page.evaluate(() => {
      (window as unknown as { dayReads: number }).dayReads = 0;
    });
    for (let i = 0; i < 6; i++) {
      await page.mouse.wheel(0, 600);
      await page.waitForTimeout(80);
    }
    expect(
      await page.evaluate(
        () => (window as unknown as { dayReads: number }).dayReads,
      ),
    ).toBe(0);
  });
});
