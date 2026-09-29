import { expect, test, type Page } from "@playwright/test";

const home = (page: Page, name: string) =>
  page.getByRole("button", { name: `${name}’s home` });
/** A home's roof: what a reader sees and clicks. The button's own box lies flat on the ground below it. */
const roof = (page: Page, name: string) =>
  home(page, name).locator("i").first();

test("the town's key counts every home by its reply, and says it is illustrative", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const key = page.locator("#act5 [data-town-key]");
  await expect(key).toContainText("Doing well · 8");
  await expect(key).toContainText("On the follow-up list · 3");
  await expect(key).toContainText("Emergency advice given · 1");
  await expect(page.locator("#act5")).toContainText("Illustrative");
  await expect(page.locator("#act5 [data-house]")).toHaveCount(12);
  await expect(page.locator("#act5 [data-town-thread]")).toHaveCount(12);
});

test("selecting a home shows its latest reply and what Khabar did", async ({
  page,
}) => {
  await page.goto("/preview/film");
  // The server's HTML is already interactive-looking; a click before hydration would be lost.
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const status = page.locator("#act5 [data-home-status]");
  await expect(status).toContainText("Select a home");
  // Click once the homes have risen, as a reader would: a roof still rising moves under the pointer.
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect
    .poll(async () => (await houseZ(page)).every((z) => z === 0))
    .toBe(true);
  await roof(page, "Mr Muthu").click();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "true");
  await expect(status).toContainText("நெஞ்சு வலி.");
  await expect(status).toContainText("999 advice at once");
  await expect(status.locator("[lang='ta']")).toHaveCount(1);
});

test("a home can be selected with the keyboard", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await home(page, "Pak Cik Rahim").focus();
  await page.keyboard.press("Enter");
  const status = page.locator("#act5 [data-home-status]");
  await expect(status).toContainText("Pening sikit hari ini.");
  await expect(status).toContainText("follow-up list");
  await expect(home(page, "Pak Cik Rahim")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});

const houseZ = (page: Page) =>
  page
    .locator("#act5 [data-house]")
    .evaluateAll((els) =>
      els.map((el) => new DOMMatrix(getComputedStyle(el).transform).m43),
    );
const threadOffsets = (page: Page) =>
  page
    .locator("#act5 [data-town-thread]")
    .evaluateAll((els) =>
      els.map((el) => parseFloat(getComputedStyle(el).strokeDashoffset)),
    );

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the town is built and every thread drawn: the end frame", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    const z = await houseZ(page);
    const offsets = await threadOffsets(page);
    expect(z).toHaveLength(12);
    expect(offsets).toHaveLength(12);
    expect(z.every((v) => v === 0)).toBe(true);
    expect(offsets.every((o) => o === 0)).toBe(true);
  });

  test("on a 320 px phone every home is its own target, centres 24 px apart or more", async ({
    page,
  }, info) => {
    test.skip(info.project.name !== "phone", "The phone layout.");
    await page.setViewportSize({ width: 320, height: 700 });
    await page.goto("/preview/film");
    const centres = await page
      .locator("#act5 [data-house]")
      .evaluateAll((els) =>
        els.map((el) => {
          const r = el.getBoundingClientRect();
          return [r.x + r.width / 2, r.y + r.height / 2];
        }),
      );
    expect(centres).toHaveLength(12);
    for (const [i, a] of centres.entries()) {
      for (const b of centres.slice(i + 1)) {
        expect(Math.hypot(a[0] - b[0], a[1] - b[1])).toBeGreaterThanOrEqual(24);
      }
    }
  });
});

test("scrolling to the town raises the homes and draws the threads", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  expect((await houseZ(page))[0]).toBeLessThan(-40);
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect
    .poll(async () => (await houseZ(page)).every((z) => z === 0), {
      timeout: 5000,
    })
    .toBe(true);
  await expect
    .poll(async () => (await threadOffsets(page)).every((o) => o === 0), {
      timeout: 5000,
    })
    .toBe(true);
});

test("the town's lights bob only while it is on screen and motion is on", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const running = () =>
    page
      .locator("#act5")
      .evaluate(
        (el) =>
          el
            .getAnimations({ subtree: true })
            .filter((a) => a.playState === "running").length,
      );
  expect(await running()).toBe(0);
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect.poll(running).toBeGreaterThan(0);
  // Scrolled away, the lights rest; back on screen, they move again.
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(running).toBe(0);
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect.poll(running).toBeGreaterThan(0);
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});

const tiltX = (page: Page) =>
  page
    .locator("#act5 [data-world]")
    .evaluate((el) =>
      parseFloat((el as HTMLElement).style.getPropertyValue("--tilt-x") || "0"),
    );

/** Hydrated, with the town on screen and every home risen: what a reader sees before turning it. */
async function settledTown(page: Page) {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await page
    .locator("#act5 [data-town]")
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await expect
    .poll(async () => (await houseZ(page)).every((z) => z === 0))
    .toBe(true);
}

test("dragging the town turns it", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "A mouse drag.");
  await settledTown(page);
  const box = (await page.locator("#act5 [data-town]").boundingBox())!;
  await page.mouse.move(box.x + 30, box.y + 30);
  await page.mouse.down();
  await page.mouse.move(box.x + 230, box.y + 30, { steps: 8 });
  await page.mouse.up();
  expect(await tiltX(page)).toBeGreaterThan(0.3);
});

test("a drag that starts on a home turns the town without selecting the home", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "A mouse drag.");
  await settledTown(page);
  const b = (await roof(page, "Mr Muthu").boundingBox())!;
  const x = b.x + b.width / 2;
  const y = b.y + b.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x - 150, y, { steps: 8 });
  await page.mouse.move(x, y, { steps: 8 });
  await page.mouse.up();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator("#act5 [data-home-status]")).toContainText(
    "Select a home",
  );
});

test("mouse screens are not offered device tilt", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "A mouse screen.");
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  await expect(
    page.getByRole("button", { name: "Tilt to explore" }),
  ).toHaveCount(0);
});

test("on a touch screen, tilting the phone turns the town once the reader asks for it", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Touch screens only.");
  // The reader allows motion access when the browser asks.
  await page
    .context()
    .grantPermissions(["accelerometer", "gyroscope", "magnetometer"]);
  await page.goto("/preview/film");
  const tilt = page.getByRole("button", { name: "Tilt to explore" });
  await tilt.click();
  await expect(tilt).toHaveAttribute("aria-pressed", "true");
  const turn = (gamma: number) =>
    page.evaluate(
      (g) =>
        window.dispatchEvent(
          new DeviceOrientationEvent("deviceorientation", {
            beta: 50,
            gamma: g,
          }),
        ),
      gamma,
    );
  await turn(45);
  expect(await tiltX(page)).toBeCloseTo(1, 2);
  await tilt.click();
  await expect(tilt).toHaveAttribute("aria-pressed", "false");
  await turn(-45);
  expect(await tiltX(page)).toBeCloseTo(1, 2);
});

test("refusing device tilt says so, and the town can still be dragged", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Touch screens only.");
  await page.addInitScript(() => {
    (
      DeviceOrientationEvent as unknown as {
        requestPermission: () => Promise<string>;
      }
    ).requestPermission = async () => "denied";
  });
  await page.goto("/preview/film");
  const tilt = page.getByRole("button", { name: "Tilt to explore" });
  await tilt.click();
  await expect(tilt).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator("#act5")).toContainText(
    "Tilt is off. Drag the town to turn it instead.",
  );
});

test("on a phone, tapping a home shows its reply where the reader is looking", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "The phone layout.");
  await settledTown(page);
  await roof(page, "Mr Muthu").click();
  await expect(page.locator("#act5 [data-home-status]")).toContainText(
    "நெஞ்சு வலி.",
  );
  await expect(page.locator("#act5 [data-home-status]")).toBeInViewport({
    ratio: 1,
  });
});

test("on a phone the whole town fits across the screen", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "The phone layout.");
  await settledTown(page);
  const board = await page
    .locator("#act5 [data-world] > div")
    .first()
    .boundingBox();
  const width = page.viewportSize()!.width;
  expect(board!.x).toBeGreaterThanOrEqual(0);
  expect(board!.x + board!.width).toBeLessThanOrEqual(width);
});

test("a drag released outside the town does not leave it following the mouse", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "A mouse drag.");
  await settledTown(page);
  const box = (await page.locator("#act5 [data-town]").boundingBox())!;
  const y = box.y + box.height / 2;
  await page.mouse.move(box.x + box.width - 3, y);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width + 40, y, { steps: 4 });
  await page.mouse.up();
  const before = await tiltX(page);
  await page.mouse.move(box.x + box.width / 2, y, { steps: 8 });
  expect(await tiltX(page)).toBe(before);
  await expect(page.locator("#act5 [data-world]")).not.toHaveAttribute(
    "data-dragging",
  );
});

test("after a drag that ends without a click, the keyboard still selects a home", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Synthetic pointer drag.");
  await settledTown(page);
  // A finger drag ends without a click event; imitate that with pointer events alone.
  await page.locator("#act5 [data-town]").evaluate((el) => {
    const r = el.getBoundingClientRect();
    const at = (type: string, x: number) =>
      el.dispatchEvent(
        new PointerEvent(type, {
          bubbles: true,
          pointerId: 1,
          isPrimary: true,
          button: type === "pointermove" ? -1 : 0,
          buttons: type === "pointerup" ? 0 : 1,
          clientX: r.x + x,
          clientY: r.y + 20,
        }),
      );
    at("pointerdown", 20);
    at("pointermove", 80);
    at("pointerup", 80);
  });
  await home(page, "Pak Cik Rahim").focus();
  await page.keyboard.press("Enter");
  await expect(home(page, "Pak Cik Rahim")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});

test("the town can be turned with buttons, by mouse or keyboard", async ({
  page,
}) => {
  await settledTown(page);
  await page.getByRole("button", { name: "Turn right" }).click();
  expect(await tiltX(page)).toBeCloseTo(0.5, 2);
  await page.getByRole("button", { name: "Turn left" }).focus();
  await page.keyboard.press("Enter");
  await page.keyboard.press("Enter");
  expect(await tiltX(page)).toBeCloseTo(-0.5, 2);
});

const roofStyle = (page: Page, name: string) =>
  roof(page, name).evaluate((el) => {
    const s = getComputedStyle(el);
    return { background: s.backgroundColor, outline: s.outlineStyle };
  });

test("the focused home and the selected home look different, even in forced colours", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "desktop", "Keyboard focus.");
  for (const forcedColors of ["none", "active"] as const) {
    await page.emulateMedia({ forcedColors });
    await settledTown(page);
    await roof(page, "Mr Muthu").click();
    await page.keyboard.press("Tab");
    await expect(home(page, "Encik Azman")).toBeFocused();
    const selected = await roofStyle(page, "Mr Muthu");
    const focused = await roofStyle(page, "Encik Azman");
    expect(focused.outline, forcedColors).toBe("solid");
    expect(selected.outline, forcedColors).toBe("none");
    expect(selected.background, forcedColors).not.toBe(focused.background);
  }
});

test("the emergency reply keeps the note that the clinic may not have seen it yet", async ({
  page,
}) => {
  await settledTown(page);
  await roof(page, "Mr Muthu").click();
  await expect(page.locator("#act5 [data-home-status]")).toContainText(
    "may not have seen it yet",
  );
});

test("pressing the selected home again clears the selection", async ({
  page,
}) => {
  await settledTown(page);
  await roof(page, "Mr Muthu").click();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "true");
  await roof(page, "Mr Muthu").click();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator("#act5 [data-home-status]")).toContainText(
    "Select a home",
  );
});

test("the amber and red threads stand out from the board (3:1)", async ({
  page,
}) => {
  await page.goto("/preview/film");
  const strokes = await page
    .locator("#act5 [data-town-thread]:not([data-state='ok'])")
    .evaluateAll((els) => els.map((el) => getComputedStyle(el).stroke));
  expect(strokes).toHaveLength(4);
  const lum = (c: string) => {
    const [r, g, b] = c
      .match(/\d+/g)!
      .slice(0, 3)
      .map((v) => {
        const s = Number(v) / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
      });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  // The board runs from #f7fbf8 to #e3efe8; the lighter end is the harder one.
  const board = lum("rgb(247, 251, 248)");
  for (const s of strokes)
    expect((board + 0.05) / (lum(s) + 0.05), s).toBeGreaterThanOrEqual(3);
});
