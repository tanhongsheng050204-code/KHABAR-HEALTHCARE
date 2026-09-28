import { expect, test } from "@playwright/test";

test("the film preview is served and kept out of search engines", async ({ page }) => {
  const response = await page.goto("/preview/film");
  expect(response?.status()).toBe(200);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/The visit ends\.\s*Care should not\./);
});

test("the current landing page is untouched", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/The visit ends\.\s*Care should not\./);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
});
