import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("the landing page is the product page, open to search engines", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
  await expect(page).toHaveTitle("Khabar · Aftercare for Malaysian clinics");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Know who is slipping before they are back in the queue.",
  );
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true);
});

test("the judge banner and hero open the doctor demo in one click", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByRole("link", { name: /Judging Khabar\? Open the demo clinic/ }),
  ).toHaveAttribute("href", "/login?demo=doctor");
  await expect(
    page.getByRole("link", { name: "Open the demo clinic" }).first(),
  ).toHaveAttribute("href", "/login?demo=doctor");
  await expect(page.getByRole("link", { name: "Try the patient view" })).toHaveAttribute(
    "href",
    "/login?demo=patient",
  );
});

test("the story film is one link away", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Watch Aminah's story" }).click();
  await expect(page).toHaveURL(/\/story$/);
  await expect(page.locator("main section[id^='act']")).toHaveCount(8);
});

test("the replay steps through a check-in reply reaching the clinic", async ({ page }) => {
  await page.goto("/");
  const steps = page.getByRole("group", { name: "Replay steps" });
  await steps.getByRole("button", { name: /Call and record/ }).click();
  await expect(page.getByText("Reached by phone.", { exact: false })).toBeVisible();
  await expect(steps.getByRole("button", { name: /Call and record/ })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await page.getByRole("button", { name: "Replay" }).click();
  await expect(steps.getByRole("button", { name: /Morning list/ })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});

test("the replay plays by itself once it is on screen", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("group", { name: "Replay steps" }).scrollIntoViewIfNeeded();
  await expect(page.getByText("Reached by phone.", { exact: false })).toBeVisible({
    timeout: 15_000,
  });
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("the replay shows the finished story without playing", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("group", { name: "Replay steps" }).scrollIntoViewIfNeeded();
    await expect(page.getByText("Reached by phone.", { exact: false })).toBeVisible();
  });
  test("no accessibility violations", async ({ page }) => {
    await page.goto("/");
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
  });
});

test("one day with Aminah switches moments by click and by arrow keys", async ({ page }) => {
  await page.goto("/");
  const tabs = page.getByRole("tablist", { name: "One day with Aminah" });
  await tabs.getByRole("tab", { name: /14:00/ }).click();
  const panel = page.getByRole("tabpanel");
  await expect(panel.getByRole("heading")).toHaveText(
    "Answer a check-in, in any of four languages",
  );
  await page.keyboard.press("ArrowDown");
  await expect(tabs.getByRole("tab", { name: /16:00/ })).toHaveAttribute("aria-selected", "true");
  await expect(tabs.getByRole("tab", { name: /16:00/ })).toBeFocused();
  await expect(panel.getByRole("heading")).toHaveText("Call the right person first");
});

test("connector statuses say what is and is not connected", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText("Not connected", { exact: true })).toBeVisible();
  await expect(page.getByText("Prototype", { exact: true })).toBeVisible();
});

test("FAQ answers open in place", async ({ page }) => {
  await page.goto("/");
  await page.getByText("Will the clinic be alerted the moment a reply looks worrying?").click();
  await expect(page.getByText("staff are not paged", { exact: false })).toBeVisible();
});

test("no accessibility violations after the replay has played", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("group", { name: "Replay steps" }).getByRole("button", { name: /Call and record/ }).click();
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
});
