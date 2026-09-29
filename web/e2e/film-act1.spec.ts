import { expect, test } from "@playwright/test";

const lines = (page: import("@playwright/test").Page) =>
  page.locator("#act1 [data-rx-line]");

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("every line shows both the shorthand and her words, from the product's summary", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    await expect(lines(page)).toHaveCount(3);
    await expect(lines(page).first()).toContainText("Tab metformin 500mg bd pc");
    await expect(lines(page).first()).toContainText(
      "1 biji, pagi dan malam, selepas makan.",
    );
    await expect(lines(page).first()).toHaveAttribute("aria-pressed", "true");
  });
});

test("scrolling to the paper melts the shorthand into her words", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(lines(page).first()).toHaveAttribute("aria-pressed", "false");
  await page.locator("#act1").scrollIntoViewIfNeeded();
  await expect(lines(page).first()).toHaveAttribute("aria-pressed", "true", {
    timeout: 5000,
  });
});

test("a line clicked before it is reached keeps the reader's choice", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const third = lines(page).nth(2);
  // dispatchEvent clicks without scrolling, so the line is clicked before its scroll reveal can run.
  await third.dispatchEvent("click"); // reveal it early
  await expect(third).toHaveAttribute("aria-pressed", "true");
  await third.dispatchEvent("click"); // back to shorthand, by choice
  await page.locator("#act1").scrollIntoViewIfNeeded();
  await page.waitForTimeout(800);
  await expect(third).toHaveAttribute("aria-pressed", "false");
});

test("the plain words follow the language chosen in the hero; the shorthand stays", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文", exact: true }).click();
  await page.locator("#act1").scrollIntoViewIfNeeded();
  const first = lines(page).first();
  await expect(first).toContainText("每次1粒，早上和晚上，饭后服用。");
  await expect(first).toContainText("Tab metformin 500mg bd pc");
  await expect(first.locator("[lang='zh-Hans']")).toHaveCount(1);
});

test("the keyboard toggles a line with Enter", async ({ page }) => {
  await page.goto("/preview/film");
  await page.locator("#act1").scrollIntoViewIfNeeded();
  const second = lines(page).nth(1);
  await expect(second).toHaveAttribute("aria-pressed", "true", { timeout: 5000 });
  await second.focus();
  await page.keyboard.press("Enter");
  await expect(second).toHaveAttribute("aria-pressed", "false");
});
