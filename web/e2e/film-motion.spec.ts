import { expect, test } from "@playwright/test";

test("Pause motion stops the film and can resume it", async ({ page }) => {
  await page.goto("/preview/film");
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

  test("the device setting keeps motion off and explains why", async ({ page }) => {
    await page.goto("/preview/film");
    await expect(page.locator("[data-motion]")).toHaveAttribute("data-motion", "off");
    const pause = page.getByRole("button", { name: "Pause motion" });
    await expect(pause).toBeDisabled();
    await expect(pause).toContainText("Reduced motion is on");
  });
});

test("without JavaScript the page still shows its words", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto("/preview/film");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.locator("[data-motion]")).toHaveAttribute("data-motion", "off");
  await context.close();
});

test("the film turns off CSS smooth scrolling, which fights ScrollTrigger's scroll jumps", async ({ page }) => {
  await page.goto("/preview/film");
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior)).toBe("auto");
});
