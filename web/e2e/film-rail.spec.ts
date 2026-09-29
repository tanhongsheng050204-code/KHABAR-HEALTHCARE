import { expect, test } from "@playwright/test";

const NAMES = [
  "The visit ends",
  "Lost in the paper",
  "The fifteen minutes",
  "Thirty days at home",
  "Her daughter in KL",
  "The whole town",
  "Trust",
  "The whole story",
];

test("the rail lists the eight acts and marks where the reader is", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "The rail is desktop only.");
  await page.goto("/preview/film");
  const rail = page.getByRole("navigation", { name: "Story" });
  await expect(rail.getByRole("link")).toHaveText(NAMES);
  await expect(
    rail.getByRole("link", { name: "The visit ends" }),
  ).toHaveAttribute("aria-current", "step");
});

test("while the thirty-day pan is pinned, the rail names it", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "The pan is desktop only.");
  await page.goto("/preview/film");
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "animated");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await page.evaluate(() => window.scrollBy(0, 1200));
  await expect(
    page
      .getByRole("navigation", { name: "Story" })
      .getByRole("link", { name: "Thirty days at home" }),
  ).toHaveAttribute("aria-current", "step");
});

test("a rail link takes the reader to its act, past the pins above it", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "The rail is desktop only.");
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const trust = page
    .getByRole("navigation", { name: "Story" })
    .getByRole("link", { name: "Trust", exact: true });
  await trust.click();
  await expect(page.locator("#act6-title")).toBeInViewport();
  await expect(trust).toHaveAttribute("aria-current", "step");
});

test("phones do not show the rail", async ({ page }, info) => {
  test.skip(info.project.name !== "phone", "Phone layout.");
  await page.goto("/preview/film");
  await expect(page.getByRole("navigation", { name: "Story" })).toBeHidden();
});
