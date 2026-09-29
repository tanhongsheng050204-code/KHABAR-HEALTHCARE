import { expect, test } from "@playwright/test";

const DAYS = ["Day 1", "Day 3", "Day 7", "Day 14", "Day 30"];

/** Scrolls down in small steps until Day 30 is fully on screen, so the test never overshoots the pin. */
async function panToDay30(page: import("@playwright/test").Page) {
  await expect
    .poll(
      async () => {
        await page.evaluate(() => window.scrollBy(0, 250));
        return page.locator("article[data-day='30']").evaluate((el) => {
          const r = el.getBoundingClientRect();
          return (
            r.left >= 0 &&
            r.right <= window.innerWidth &&
            r.top < window.innerHeight &&
            r.bottom > 0
          );
        });
      },
      { timeout: 15_000, intervals: [150] },
    )
    .toBe(true);
}

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("all five days are laid out in order, no pin", async ({ page }) => {
    await page.goto("/preview/film");
    const act = page.locator("#act3");
    await expect(act).toHaveAttribute("data-mode", "static");
    await expect(act.locator("article[data-day] h3")).toHaveText(DAYS);
    await expect(page.locator(".pin-spacer")).toHaveCount(0);
  });
});

test("desktop pans through the thirty days while pinned, and ends on Day 30", async ({
  page,
}, info) => {
  test.skip(
    info.project.name !== "desktop",
    "The horizontal pan is desktop only.",
  );
  await page.goto("/preview/film");
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "animated");
  // To the top of the act: scrollIntoViewIfNeeded would centre this tall section, half-way into the pan.
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await expect(page.locator("article[data-day='1']")).toBeInViewport();
  await panToDay30(page);
});

test("arriving by the #act3 link shows Day 1, not an empty pin", async ({
  page,
}) => {
  await page.goto("/preview/film#act3");
  await expect(page.locator("article[data-day='1']")).toBeInViewport({
    timeout: 5000,
  });
});

test("after a resize the pan still ends exactly on Day 30", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Desktop pan only.");
  await page.goto("/preview/film");
  await page.setViewportSize({ width: 1100, height: 800 });
  await page.locator("#act3").scrollIntoViewIfNeeded();
  await panToDay30(page);
});

test("Day 7's dizziness turns the thread amber", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(
    page.locator("article[data-day='7'] [data-thread]"),
  ).toHaveAttribute("data-state", "watch");
  await expect(
    page.locator("article[data-day='3'] [data-thread]"),
  ).toHaveAttribute("data-state", "ok");
});

test("Reply for Aminah shows exactly what Khabar sends back", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const lab = page.getByRole("group", { name: "Reply for Aminah" });
  const outcome = page.locator("[data-outcome]");

  await lab.getByRole("button", { name: "I'm okay" }).click();
  await expect(
    page.locator("[data-lab] [data-from='khabar']").last(),
  ).toContainText("Terima kasih kerana memberitahu. Jaga diri!");
  await expect(outcome).toHaveAttribute("data-state", "ok");

  await lab.getByRole("button", { name: "A bit dizzy" }).click();
  await expect(
    page.locator("[data-lab] [data-from='khabar']").last(),
  ).toContainText("klinik mungkin belum membacanya");
  await expect(
    page.locator("[data-lab] [data-from='khabar']").last(),
  ).toContainText("999");
  await expect(outcome).toHaveAttribute("data-state", "watch");

  await lab.getByRole("button", { name: "Chest pain" }).click();
  await expect(
    page.locator("[data-lab] [data-from='khabar']").last(),
  ).toContainText("Mesej anda telah dimasukkan");
  await expect(outcome).toHaveAttribute("data-state", "red");
  await expect(page.locator("[data-lab] [data-thread]")).toHaveAttribute(
    "data-state",
    "red",
  );
  await expect(outcome).not.toContainText(/will call|calls you/i);
});

test("the language chosen in the hero carries into the check-ins and answers", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文" }).click();
  await expect(
    page.locator("article[data-day='1'] [data-from='khabar']"),
  ).toContainText("今天感觉怎么样？吃药了吗？回复这条信息告诉诊所。");
  const chest = page
    .getByRole("group", { name: "Reply for Aminah" })
    .getByRole("button", { name: "Chest pain" });
  await chest.focus();
  await expect(chest).toBeInViewport();
  await page.keyboard.press("Space");
  await expect(chest).toHaveAttribute("aria-pressed", "true");
  await expect(
    page.locator("[data-lab] [data-from='khabar']").last(),
  ).toContainText("拨打999");
});

test("the day threads are solid lines, not dashes", async ({ page }) => {
  await page.goto("/preview/film");
  const dash = await page
    .locator("[data-thread='day-7'] path")
    .evaluateAll((paths) =>
      paths.map((p) => getComputedStyle(p).strokeDasharray),
    );
  expect(dash.every((d) => d === "none")).toBe(true);
});

test("on a short laptop screen the pinned days fit: every caption and thread is on screen", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Desktop pan.");
  await page.setViewportSize({ width: 1366, height: 650 });
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  await page.waitForTimeout(700);
  const cut = await page.evaluate(() => {
    const stage = document
      .querySelector("#act3 > div")!
      .getBoundingClientRect();
    const limit = Math.min(stage.bottom, window.innerHeight);
    return [...document.querySelectorAll("article[data-day]")]
      .filter((a) => a.getBoundingClientRect().left < window.innerWidth)
      .filter((a) => a.getBoundingClientRect().bottom > limit + 1)
      .map((a) => a.getAttribute("data-day"));
  });
  expect(cut).toEqual([]);
});

test("a phone turned sideways stacks the days instead of pinning a pan taller than the screen", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Landscape phone.");
  await page.setViewportSize({ width: 844, height: 390 });
  await page.goto("/preview/film");
  await page.evaluate(() =>
    document.getElementById("act3")!.scrollIntoView({ block: "start" }),
  );
  const [one, three] = await Promise.all(
    ["1", "3"].map((d) =>
      page
        .locator(`article[data-day='${d}']`)
        .evaluate((el) => el.getBoundingClientRect().toJSON()),
    ),
  );
  expect(three.top).toBeGreaterThanOrEqual(one.bottom);
});

test("screen readers can reach every day before it is scrolled to", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "animated");
  // Hidden elements (visibility:hidden) drop out of the accessibility tree; fading must not hide them.
  await expect(page.getByRole("heading", { name: "Day 30" })).toHaveCount(1);
});
