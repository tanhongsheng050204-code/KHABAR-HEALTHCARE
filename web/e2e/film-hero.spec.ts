import { expect, test } from "@playwright/test";
import sharp from "sharp";

const dashOffset = (page: import("@playwright/test").Page) =>
  page
    .locator('[data-thread="hero"] [data-draw]')
    .first()
    .evaluate((el) => parseFloat(getComputedStyle(el).strokeDashoffset));

test("the hero offers the four greeting languages, BM first", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const chips = page
    .getByRole("group", { name: "Khabar speaks her language" })
    .getByRole("button");
  await expect(chips).toHaveText(["BM", "中文", "தமிழ்", "EN"]);
  await expect(chips.first()).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#act0 [data-from='khabar']")).toContainText(
    "Apa khabar, Mak Cik?",
  );
});

test("choosing a language changes the greeting, by mouse or keyboard", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文" }).click();
  await expect(page.locator("#act0 [data-from='khabar']")).toContainText(
    "阿姨，今天好吗？",
  );
  const tamil = page.getByRole("button", { name: "தமிழ்" });
  await tamil.focus();
  await expect(tamil).toBeInViewport();
  await page.keyboard.press("Enter");
  await expect(tamil).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#act0 [data-from='khabar']")).toHaveAttribute(
    "lang",
    "ta",
  );
});

test("scrolling pins the hero and draws the thread after Aminah", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("#act0").locator("xpath=..")).toHaveClass(
    /pin-spacer/,
  );
  // At rest the thread runs part-way, from the door to Aminah: neither hidden (1) nor complete (0).
  await expect.poll(() => dashOffset(page)).toBeCloseTo(0.62, 1);
  await page.evaluate(() => window.scrollBy(0, window.innerHeight * 0.5));
  // Half-way through the pin it is part-drawn further, not snapped to either end.
  await expect
    .poll(() => dashOffset(page), { timeout: 5000 })
    .toBeLessThan(0.5);
  expect(await dashOffset(page)).toBeGreaterThan(0.05);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the hero is its still frame: no pin, headline and Aminah in view", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    await expect(page.locator(".pin-spacer")).toHaveCount(0);
    await expect(page.getByRole("heading", { level: 1 })).toBeInViewport();
    await expect(page.locator("#act0 [data-layer='aminah']")).toBeInViewport();
  });
});

test("pinning never moves the hero in the DOM, so its text is not re-counted as a late LCP", async ({
  page,
}) => {
  await page.goto("/preview/film");
  // React renders the spacer itself; ScrollTrigger reuses it (pinSpacer) instead of wrapping the section.
  const spacer = page.locator("#act0").locator("xpath=..");
  await expect(spacer).toHaveClass(/pin-spacer/);
  await expect(spacer).toHaveAttribute("data-pin-for", "act0");
});

test("each screen shape gets its own hero thread, so a phone never shows a stretched one", async ({
  page,
}, info) => {
  await page.goto("/preview/film");
  const visible = page.locator('[data-thread="hero"]:visible');
  await expect(visible).toHaveCount(1);
  await expect(visible).toHaveAttribute(
    "data-shape",
    info.project.name === "phone" ? "tall" : "wide",
  );
});

/** Is the visible hero thread drawn (green) at this fraction of its length? Reads real screen pixels. */
async function threadPixelAt(
  page: import("@playwright/test").Page,
  fraction: number,
) {
  const point = await page.evaluate((f) => {
    const svg = [
      ...document.querySelectorAll<SVGSVGElement>('[data-thread="hero"]'),
    ].find((s) => s.checkVisibility());
    const line = svg!.querySelectorAll<SVGPathElement>("[data-draw]")[1];
    const p = line
      .getPointAtLength(line.getTotalLength() * f)
      .matrixTransform(line.getScreenCTM()!);
    return {
      x: Math.round(Math.min(p.x, innerWidth - 6)),
      y: Math.round(Math.min(p.y, innerHeight - 6)),
    };
  }, fraction);
  const png = await page.screenshot({
    clip: { x: point.x - 5, y: point.y - 5, width: 10, height: 10 },
  });
  const { data } = await sharp(png).raw().toBuffer({ resolveWithObject: true });
  for (let i = 0; i < data.length; i += 3)
    if (data[i + 1] - data[i] > 30) return true; // thread green: g well above r
  return false;
}

for (const size of [
  { width: 1920, height: 1080 },
  { width: 2560, height: 1440 },
]) {
  test(`the hero thread draws completely, and only as far as it should, at ${size.width}×${size.height}`, async ({
    page,
  }, info) => {
    test.skip(info.project.name !== "desktop", "Large desktop screens.");
    await page.setViewportSize(size);
    await page.goto("/preview/film");
    await page.addStyleTag({
      content:
        "[data-layer='aminah'],[data-layer='copy'],header{visibility:hidden!important}",
    });
    await expect(page.locator("#act0").locator("xpath=..")).toHaveClass(
      /pin-spacer/,
    );
    // At rest only the stretch from the door is drawn: nothing near the far end.
    expect(await threadPixelAt(page, 0.85)).toBe(false);
    // Fully drawn, the thread reaches the far end of the street.
    await page.evaluate(() =>
      document
        .querySelectorAll<SVGPathElement>('[data-thread="hero"] [data-draw]')
        .forEach((p) => (p.style.strokeDashoffset = "0")),
    );
    expect(await threadPixelAt(page, 0.95)).toBe(true);
  });
}

test("the keyboard focus ring is dark enough to see on the cream background (3:1)", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const chip = page.getByRole("button", { name: "中文" });
  await chip.focus();
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Tab"); // keyboard focus, so :focus-visible applies
  await expect(chip).toBeFocused();
  // kopi #3B2A20 on santan #F6EFE4 is about 13:1; the old amber ring was 2.06:1.
  expect(await chip.evaluate((el) => getComputedStyle(el).outlineColor)).toBe(
    "rgb(59, 42, 32)",
  );
});
