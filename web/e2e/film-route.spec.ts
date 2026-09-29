import { expect, test } from "@playwright/test";

test("the film is the landing page, open to search engines", async ({
  page,
}) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
  // The same title the old landing page had (the layout's "%s · Khabar" template skips its own segment).
  await expect(page).toHaveTitle("Care that carries on");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    /The visit ends\.\s*Care should not\./,
  );
  await expect(page.locator("main section[id^='act']")).toHaveCount(8);
});

test("the old preview link lands on the landing page", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator("#act0")).toBeVisible();
});
