import { expect, test, type Page } from "@playwright/test";

const principles = (page: Page, selector: string) =>
  page
    .locator(selector)
    .evaluateAll((els) =>
      els.map((el) =>
        [el.querySelector("h3")!, el.querySelector("p")!].map((n) =>
          n.textContent!.replace(/\s+/g, " ").trim(),
        ),
      ),
    );

test("the trust principles are word for word the ones on the current landing page", async ({
  page,
}) => {
  await page.goto("/");
  const landing = await principles(page, "#safety article");
  await page.goto("/preview/film");
  const film = await principles(page, "#act6 article");
  expect(film).toHaveLength(3);
  expect(film).toEqual(landing);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("all three principles are shown at once", async ({ page }) => {
    await page.goto("/preview/film");
    const shown = await page
      .locator("#act6 [data-principle]")
      .evaluateAll((els) => els.map((el) => el.getAttribute("data-shown")));
    expect(shown).toEqual(["true", "true", "true"]);
  });
});

test("the principles arrive as the reader reaches them, and pausing shows them all", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const items = page.locator("#act6 [data-principle]");
  await expect(items.nth(2)).toHaveAttribute("data-shown", "false");
  await items.nth(0).scrollIntoViewIfNeeded();
  await expect(items.nth(0)).toHaveAttribute("data-shown", "true");
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(items.nth(2)).toHaveAttribute("data-shown", "true");
});
