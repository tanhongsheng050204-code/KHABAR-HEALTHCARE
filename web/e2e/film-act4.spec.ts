import { expect, test } from "@playwright/test";

const SHARE = "Aminah shares her care plan with Nurul";

test("Nurul sees the approved care plan while Aminah shares it", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const share = page.getByRole("switch", { name: SHARE });
  await expect(share).toHaveAttribute("aria-checked", "true");
  await expect(page.locator("[data-nurul-phone]")).toContainText(
    "metformin 500 mg: 1 biji, pagi dan malam, selepas makan.",
  );
});

test("when Aminah stops sharing, Nurul's phone locks and never shows the plan", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const share = page.getByRole("switch", { name: SHARE });
  await share.focus();
  await page.keyboard.press("Space");
  await expect(share).toHaveAttribute("aria-checked", "false");
  await expect(page.locator("[data-nurul-phone]")).not.toContainText(
    "metformin",
  );
  await expect(page.locator("[data-consent-note]")).toContainText(
    "can no longer open",
  );
  await page.keyboard.press("Enter");
  await expect(share).toHaveAttribute("aria-checked", "true");
});

test("the care plan on Nurul's phone follows the chosen language", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "EN", exact: true }).click();
  await expect(page.locator("[data-nurul-phone]")).toContainText(
    "1 tablet, morning and night, after food.",
  );
});

test("the thread to Nurul crosses no words: it runs in its own strip above her", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const hits = await page.evaluate(() => {
    const t = document
      .querySelector("[data-thread='to-nurul']")!
      .getBoundingClientRect();
    return ["#act4-title", "[data-nurul-phone]", "[data-consent-note]"]
      .map(
        (s) => [s, document.querySelector(s)!.getBoundingClientRect()] as const,
      )
      .filter(
        ([, r]) =>
          r.left < t.right &&
          r.right > t.left &&
          r.top < t.bottom &&
          r.bottom > t.top,
      )
      .map(([s]) => s);
  });
  expect(hits).toEqual([]);
});

/** WCAG contrast ratio between two colours given as "rgb(r, g, b)" or "#rrggbb". */
function contrast(a: string, b: string) {
  const rgb = (c: string) =>
    c.startsWith("#")
      ? [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16))
      : c.match(/\d+/g)!.slice(0, 3).map(Number);
  const lum = (c: string) => {
    const [r, g, b] = rgb(c).map((v) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

test("the consent switch reads well: bold label, and an off track that stands out from the night", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const share = page.getByRole("switch", { name: SHARE });
  await share.click();
  await expect(share).toHaveAttribute("aria-checked", "false");
  await page.waitForTimeout(400);
  const style = await share.evaluate((el) => {
    const track = el.querySelector("span")!;
    return {
      weight: getComputedStyle(el).fontWeight,
      track: getComputedStyle(track).backgroundColor,
    };
  });
  expect(style.weight).toBe("600");
  for (const night of ["#1e2a44", "#2b2447"])
    expect(contrast(style.track, night)).toBeGreaterThanOrEqual(3);
});
