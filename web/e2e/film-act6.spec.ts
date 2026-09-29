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

/** The old landing page's safety principles, word for word, as they read on 29 Sep 2026 before the switch. */
const LANDING_PRINCIPLES = [
  [
    "Human decisions stay human",
    "Urgent replies and safety concerns go to a clinician. Khabar never presents itself as a diagnosis.",
  ],
  [
    "Privacy is part of the workflow",
    "Your registered name, IC and phone number are removed before AI-assisted intake and triage. Record access is logged and visible.",
  ],
  [
    "Safety has a hard stop",
    "Critical findings block finalisation until the clinician records a clear reason to proceed.",
  ],
];

test("the trust principles are word for word the ones the old landing page promised", async ({
  page,
}) => {
  await page.goto("/");
  expect(await principles(page, "#act6 article")).toEqual(LANDING_PRINCIPLES);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("all three principles are shown at once", async ({ page }) => {
    await page.goto("/");
    const shown = await page
      .locator("#act6 [data-principle]")
      .evaluateAll((els) => els.map((el) => el.getAttribute("data-shown")));
    expect(shown).toEqual(["true", "true", "true"]);
  });
});

test("the principles arrive as the reader reaches them, and pausing shows them all", async ({
  page,
}) => {
  await page.goto("/");
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
