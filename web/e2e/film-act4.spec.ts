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
