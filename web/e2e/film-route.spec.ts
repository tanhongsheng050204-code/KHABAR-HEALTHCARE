import { expect, test } from "@playwright/test";

test("the film lives at /story, open to search engines", async ({ page }) => {
  const response = await page.goto("/story");
  expect(response?.status()).toBe(200);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
  // Below the root, the layout's "%s · Khabar" title template applies.
  await expect(page).toHaveTitle("Care that carries on · Khabar");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    /The visit ends\.\s*Care should not\./,
  );
  await expect(page.locator("main section[id^='act']")).toHaveCount(8);
});

test("the old preview link lands on the film", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page).toHaveURL(/\/story$/);
  await expect(page.locator("#act0")).toBeVisible();
});
