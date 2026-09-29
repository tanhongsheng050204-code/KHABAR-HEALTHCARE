import { expect, test } from "@playwright/test";

const FINDING = "Metformin 10000 mg a day is above the 3000 mg maximum.";

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("the notes, the draft and the blocked dose are all shown at once", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    await expect(page.locator("#act2")).toHaveAttribute(
      "data-revealed",
      "true",
    );
    await expect(page.locator("#act2 [data-draft-row]")).toHaveCount(2);
    await expect(page.locator("#act2 [data-stamp]")).toContainText(FINDING);
  });
});

test("reaching the desk writes the draft and stamps the planted dose error", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "false");
  await page.locator("#act2 [data-visit]").scrollIntoViewIfNeeded();
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "true", {
    timeout: 5000,
  });
  await expect(page.locator("#act2 [data-stamp]")).toContainText(FINDING);
  await expect(page.locator("#act2 [data-draft-row]").first()).toContainText(
    "metformin",
  );
});

test("pausing part-way shows everything, with no half-revealed state", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "true");
});

test("the clinic and patient preview still switches views", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const preview = page.locator("#act2 [data-product-preview]");
  await preview.scrollIntoViewIfNeeded();
  await expect(preview.getByRole("button").first()).toBeVisible();
});

test("the app preview's code waits until the reader nears it, keeping the first load light", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const preview = page.locator("#act2 [data-product-preview]");
  await expect(preview).toHaveAttribute("data-loaded", "false");
  await preview.scrollIntoViewIfNeeded();
  await expect(preview).toHaveAttribute("data-loaded", "true", {
    timeout: 10_000,
  });
  await expect(preview.getByRole("button").first()).toBeVisible();
});

test("pausing the film also stops the app preview's own animations", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await page.getByRole("button", { name: "Pause motion" }).click();
  const preview = page.locator("#act2 [data-product-preview]");
  await preview.scrollIntoViewIfNeeded();
  await expect(preview).toHaveAttribute("data-loaded", "true");
  await expect(preview.getByRole("button").first()).toBeVisible();
  const running = await preview.evaluate(
    (el) =>
      el
        .getAnimations({ subtree: true })
        .filter((a) => a.playState === "running").length,
  );
  expect(running).toBe(0);
});
