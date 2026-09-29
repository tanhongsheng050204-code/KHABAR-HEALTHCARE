# Landing film phase 2b: Acts 5, 6 and 7 and the progress rail — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the story on `/preview/film`: Act 5 (the whole town, a CSS 3D diorama), Act 6 (trust), Act 7 (the finale), and a slim progress rail that names the current act.

**Architecture:**
- **Town data:** twelve fictional homes live in `web/components/film/town.ts`, shared by Act 5 and Act 7.
  - Each home's reply is a text the film already shows.
  - The existing node test runs every reply through the product's triage word lists, so a home shown green really is triaged "ok".
- **Acts:** each act is a client component in `web/components/film/acts/`, following phases 1 and 2a.
  - The server render is the end frame; motion only adds scroll-triggered changes.
  - The new acts are not pinned.
  - Looping lights (the town's pings, the finale's stars) run only while their act is on screen and motion is on.
- **Act 5:** the town is server-rendered markup (houses are real `<button>`s on a CSS 3D board). A small hook adds drag-to-turn, plus device tilt on touch screens after a tap.
- **The rail:** a `<nav>` of links to the eight acts, fixed at the right edge on desktop only. It marks the act under the middle of the screen with `aria-current="step"`.

**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Modules, GSAP ScrollTrigger (`@gsap/react`), lucide-react, Playwright, node:test.

**Spec:** `docs/superpowers/specs/2026-09-28-landing-storybook-film-design.md`. The relevant sections are:
- §3, Acts 5, 6 and 7;
- §4, truthfulness;
- §7, mobile, accessibility and reduced motion;
- §8, performance;
- §13, the progress rail.

This plan follows the phase 2a plan (`docs/superpowers/plans/2026-09-29-landing-film-phase-2a.md`) and its review, recorded in `docs/LANDING_FILM_SLICE_REVIEW.md`.

## Global Constraints

- **Truthfulness:**
  - Never promise a callback, never give medical advice, no unsourced statistics, fictional people only.
  - An amber or red reply "goes onto / to the top of the clinic's follow-up list"; that is Act 3's wording.
  - Every number in Act 5 is labelled "Illustrative".
  - Act 6's principles and Act 7's words are the current `/` page's copy, word for word.
- **Static first:** the server render is the end frame. Motion must never hide content from screen readers, so animate `opacity` and `transform` only, never `autoAlpha` or `visibility`.
- **Interactions:** each is a real `<button>` or link, works with the keyboard, and has a visible focus ring. The ring is kopi on light acts and santan (`#f6efe4`) on the dark Acts 6 and 7.
- **Accessibility:**
  - Each act has one visually hidden sentence starting "Illustration: ".
  - Each act section carries `data-anchor`, so Pause keeps the reader's place.
  - Decorative SVG and the pings are `aria-hidden`.
- **Budgets:** the slice's budgets still apply, and their tests must stay green:
  - JS added over `/` ≤ 70 KB (about 61 KB now, so under 9 KB is left for this phase);
  - fonts ≤ 130 KB and CSS ≤ 45 KB;
  - zero axe violations;
  - no horizontal overflow at 320 px in Tamil.
- **Dependencies:** no new runtime dependencies.
- **Commits:** plain `git commit`, which uses the user's own git name and email, with no co-author trailer. Work on branch `feat/landing-film-phase-2b`, cut from `main`.
  - The owner's standing instruction (29 Sep) covers the end: when the work is verified, merge to `main` and push `origin main`. No PR.
- **Commands:** run `npm` and `npx` from `web/`.
  - Playwright reuses a server already on port 3100, and that server serves the old build.
  - Before a Playwright run after a code change, stop any server on 3100 (or run `npm run build` and restart it).

## Review Focus

1. **Drag that starts or ends on a home:** it turns the town and does not select that home. A press without movement selects. Tested in Task 3.
2. **Device tilt refused or unavailable:** drag keeps working, the refusal is explained, and mouse-only screens never see "Tilt to explore". Tested in Task 3.
3. **Looping lights (town pings, finale stars):** they stop when Pause is pressed and when their act is off screen. Tested in Tasks 2 and 5.
4. **The rail during the pinned pan:** it names "Thirty days at home" while the pan is pinned, and a rail link lands on its act even with pins above it. Tested in Task 6.
5. **A 320 px phone in Tamil:**
   - the town adds no sideways scroll (the existing page test);
   - every home stays its own target, with centres at least 24 px apart (WCAG 2.5.8 spacing).

   Tested in Task 2.

---

## File map

| File | Responsibility |
|---|---|
| `web/components/film/town.ts` | Twelve fictional homes and the clinic: name, language, reply, status, position. No imports. |
| `web/scripts/triage-consistency.test.mjs` | Extended: each home's reply is triaged as the status the town shows |
| `web/components/film/use-in-view.ts` | `useInView(ref)`: true while an element is on screen |
| `web/components/film/use-tilt.ts` | `useTilt(stage, world)`: drag to turn; device tilt after a tap |
| `web/components/film/acts/act5-town.tsx` | Act 5 |
| `web/components/film/acts/act6-trust.tsx` | Act 6 |
| `web/components/film/acts/act7-finale.tsx` | Act 7 |
| `web/components/film/story.ts` | The eight acts' ids and names, for the rail |
| `web/components/film/progress-rail.tsx` | The rail |
| `web/components/film/film.module.css` | Styles for Acts 5–7 and the rail (appended) |
| `web/app/preview/film/page.tsx` | Act order 0–7, and the rail |
| `web/e2e/film-act5.spec.ts`, `film-act6.spec.ts`, `film-act7.spec.ts`, `film-rail.spec.ts` | Browser tests |
| `web/e2e/film-page.spec.ts` | Story order and scene descriptions extended to the new acts |

---

### Task 1: The town's homes, triaged by the product's word lists

**Files:**
- Create: `web/components/film/town.ts`
- Modify: `web/scripts/triage-consistency.test.mjs`

**Interfaces:**
- Produces:
  - `type TownStatus = "ok" | "watch" | "red"`
  - `type TownLang = "ms" | "en" | "zh" | "ta"`
  - `type Home = { id: number; name: string; lang: TownLang; reply: string; status: TownStatus; x: number; y: number; h: number }`
  - `HOMES: Home[]` (12 entries, in the prototype's positions on a 480 × 480 board)
  - `CLINIC: { x: 200; y: 200; w: 76; d: 76; h: 70 }`

- [ ] **Step 1: Write the failing test.** Append to `web/scripts/triage-consistency.test.mjs`, and add `HOMES` to its imports at the top as a second import line:

```js
import { HOMES } from "../components/film/town.ts";
```

```js
for (const { name, reply, status } of HOMES) {
  test(`${name}'s reply in the town is triaged as the ${status} the town shows`, () => {
    assert.equal(classify(reply), status, reply);
  });
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test:unit`
Expected: FAIL, because `components/film/town.ts` cannot be found.

- [ ] **Step 3: Write `web/components/film/town.ts`**

```ts
/**
 * The town in Act 5 and the sky in Act 7: twelve fictional homes around one clinic, on a 480 × 480 board.
 * Each reply is one the film already shows elsewhere, and scripts/triage-consistency.test.mjs runs every
 * one through the product's triage word lists, so a home shown green really is triaged "ok". No imports:
 * that test loads this file directly with Node.
 */
export type TownStatus = "ok" | "watch" | "red";
export type TownLang = "ms" | "en" | "zh" | "ta";
export type Home = {
  id: number;
  name: string;
  lang: TownLang;
  reply: string;
  status: TownStatus;
  /** Top-left corner of the 40 × 40 footprint on the board, in px. */
  x: number;
  y: number;
  /** Wall height in px. */
  h: number;
};

export const CLINIC = { x: 200, y: 200, w: 76, d: 76, h: 70 } as const;

export const HOMES: Home[] = [
  { id: 1, name: "Mak Cik Aminah", lang: "ms", reply: "Sihat, terima kasih Khabar.", status: "ok", x: 40, y: 40, h: 26 },
  { id: 2, name: "Mr David Tan", lang: "en", reply: "I'm okay.", status: "ok", x: 120, y: 30, h: 34 },
  { id: 3, name: "Pak Cik Rahim", lang: "ms", reply: "Pening sikit hari ini.", status: "watch", x: 60, y: 130, h: 42 },
  { id: 4, name: "Mdm Chong", lang: "zh", reply: "我很好。", status: "ok", x: 330, y: 40, h: 26 },
  { id: 5, name: "Mrs Letchumi", lang: "ta", reply: "நான் நலம்.", status: "ok", x: 400, y: 110, h: 34 },
  { id: 6, name: "Mr Muthu", lang: "ta", reply: "நெஞ்சு வலி.", status: "red", x: 320, y: 150, h: 42 },
  { id: 7, name: "Encik Azman", lang: "ms", reply: "Dah makan ubat.", status: "ok", x: 40, y: 320, h: 26 },
  { id: 8, name: "Mdm Wong", lang: "zh", reply: "今天有点头晕。", status: "watch", x: 130, y: 380, h: 34 },
  { id: 9, name: "Mrs Fernandez", lang: "en", reply: "All good, thank you Khabar.", status: "ok", x: 60, y: 410, h: 42 },
  { id: 10, name: "Puan Rosnah", lang: "ms", reply: "Okay, sihat.", status: "ok", x: 340, y: 330, h: 26 },
  { id: 11, name: "Mr Gopal", lang: "en", reply: "A bit dizzy today.", status: "watch", x: 410, y: 400, h: 34 },
  { id: 12, name: "Mr Lim", lang: "zh", reply: "吃了药，很好。", status: "ok", x: 300, y: 410, h: 42 },
];
```

- [ ] **Step 4: Run it and watch it pass**

Run: `npm run test:unit`
Expected: PASS, 47 tests (35 before plus 12 homes).

- [ ] **Step 5: Commit**

```bash
git add components/film/town.ts scripts/triage-consistency.test.mjs
git commit -m "Add the town's twelve fictional homes, each reply checked against the product's triage words"
```

---

### Task 2: Act 5, the whole town

**Files:**
- Create: `web/components/film/use-in-view.ts`, `web/components/film/acts/act5-town.tsx`, `web/e2e/film-act5.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`, `web/e2e/film-page.spec.ts`

**Interfaces:**
- Consumes: `HOMES`, `CLINIC`, `Home` and `TownStatus` (Task 1); `useFilm()`; `htmlLang(lang)` from `../messages` (accepts `TownLang`, because the two unions are identical); `SCRUB` and `ease` from `../tokens`.
- Produces:
  - `useInView(ref: RefObject<Element | null>): boolean`
  - `Act5Town`, with these DOM hooks:
    - `section#act5[data-anchor][data-inview]`
    - `[data-town]` (the stage) and `[data-world]` (the rotating group)
    - `button[data-house][aria-pressed]`, named "{name}’s home"
    - `[data-town-thread]`, `[data-ping]`, `[data-town-key]` and `[data-home-status]`

- [ ] **Step 1: Write the failing tests** in `web/e2e/film-act5.spec.ts`

```ts
import { expect, test, type Page } from "@playwright/test";

const home = (page: Page, name: string) =>
  page.getByRole("button", { name: `${name}’s home` });

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
  const status = page.locator("#act5 [data-home-status]");
  await expect(status).toContainText("Select a home");
  await home(page, "Mr Muthu").click();
  await expect(home(page, "Mr Muthu")).toHaveAttribute("aria-pressed", "true");
  await expect(status).toContainText("நெஞ்சு வலி.");
  await expect(status).toContainText("999 advice at once");
  await expect(status.locator("[lang='ta']")).toHaveCount(1);
});

test("a home can be selected with the keyboard", async ({ page }) => {
  await page.goto("/preview/film");
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
    expect((await houseZ(page)).every((z) => z === 0)).toBe(true);
    expect((await threadOffsets(page)).every((o) => o === 0)).toBe(true);
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
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});
```

In `web/e2e/film-page.spec.ts`, extend two tests:
- In "the acts run in story order", the expected list becomes `["act0", "act1", "act2", "act3", "act4", "act5"]`. Rename the test to "the acts run in story order".
- In "every act describes its illustration for screen readers", the id list becomes `["act0", "act1", "act2", "act3", "act4", "act5"]`.

- [ ] **Step 2: Run them and watch them fail**

Run: `npx playwright test e2e/film-act5.spec.ts e2e/film-page.spec.ts -g "town|home|story order|describes"`
Expected: FAIL, because `#act5` does not exist.

- [ ] **Step 3: Write `web/components/film/use-in-view.ts`**

```ts
"use client";

import { useEffect, useState, type RefObject } from "react";

/** True while the element is on screen: looping lights (bobbing, twinkling) run only then (spec §8). */
export function useInView(ref: RefObject<Element | null>) {
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const watch = new IntersectionObserver(([entry]) =>
      setInView(entry.isIntersecting),
    );
    watch.observe(el);
    return () => watch.disconnect();
  }, [ref]);
  return inView;
}
```

- [ ] **Step 4: Write `web/components/film/acts/act5-town.tsx`**

```tsx
"use client";

import { useRef, useState, type CSSProperties } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { useInView } from "../use-in-view";
import { htmlLang } from "../messages";
import { CLINIC, HOMES, type Home, type TownStatus } from "../town";
import { SCRUB, ease } from "../tokens";
import styles from "../film.module.css";

/** What each reply led to, in Act 3's words. Never a promised call. */
const STATUS: Record<TownStatus, { label: string; detail: string }> = {
  ok: {
    label: "Doing well",
    detail: "Replied that all is well, and Khabar thanked them.",
  },
  watch: {
    label: "On the follow-up list",
    detail:
      "Khabar said the clinic may not have seen it yet and gave the 999 advice. The reply went onto the clinic’s follow-up list.",
  },
  red: {
    label: "Emergency advice given",
    detail:
      "Khabar gave the 999 advice at once. The reply went to the top of the clinic’s follow-up list.",
  },
};
const ORDER: TownStatus[] = ["ok", "watch", "red"];
const ROOFS = ["#f4d9b8", "#e9c9a6", "#f7e6cf", "#e3cfb5"];
const CENTRE = CLINIC.x + CLINIC.w / 2;

/** One curved thread from the clinic to a home's doorstep (the prototype's curve). */
function threadPath(home: Home, i: number) {
  const x = home.x + 20;
  const y = home.y + 20;
  const bend = i % 2 ? 40 : -40;
  return `M ${CENTRE} ${CENTRE} Q ${(CENTRE + x) / 2 + bend} ${(CENTRE + y) / 2} ${x} ${y}`;
}

const box = (
  <>
    <i className={styles.faceTop} />
    <i className={styles.faceFront} />
    <i className={styles.faceSide} />
  </>
);

/**
 * Act 5: one clinic and every home it cares for, as a small CSS 3D town. Each thread is one patient's
 * follow-up, coloured by their latest reply; selecting a home shows that reply and what Khabar did. The
 * server render is the end frame (every home built, every thread drawn); scrolling in raises the homes.
 */
export function Act5Town() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const inView = useInView(root);
  const [selected, setSelected] = useState<number | null>(null);
  const chosen = HOMES.find((h) => h.id === selected);

  useGSAP(
    () => {
      if (!motion) return;
      gsap
        .timeline({
          defaults: { duration: 1, ease: ease.scrub },
          scrollTrigger: {
            trigger: root.current!.querySelector("[data-town]"),
            start: "top 85%",
            end: "center 55%",
            scrub: SCRUB,
          },
        })
        .fromTo("[data-clinic], [data-house]", { z: -90 }, { z: 0, stagger: 0.08 }, 0)
        .fromTo(
          "[data-town-thread]",
          { strokeDashoffset: 1 },
          { strokeDashoffset: 0, autoRound: false, stagger: 0.05 },
          0.5,
        )
        .fromTo("[data-ping]", { opacity: 0 }, { opacity: 1 }, ">");
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act5"
      data-anchor=""
      data-inview={inView}
      className={styles.act5}
      aria-labelledby="act5-title"
    >
      <p className={styles.srOnly}>
        Illustration: a small town seen from above, with a thread from the
        clinic to each of twelve homes, coloured by how each patient is doing.
      </p>
      <div className={styles.act5Head}>
        <p className={styles.eyebrow}>The whole town</p>
        <h2 id="act5-title">
          One clinic.
          <br />
          <em>Every home it cares for.</em>
        </h2>
        <p className={styles.lede}>
          Each thread is one patient&rsquo;s follow-up, coloured by their latest
          reply.
        </p>
        <ul className={styles.townKey} data-town-key="">
          {ORDER.map((status) => (
            <li key={status} data-state={status}>
              {STATUS[status].label} ·{" "}
              {HOMES.filter((h) => h.status === status).length}
            </li>
          ))}
        </ul>
        <p className={styles.illustrative}>
          Illustrative: fictional homes, names and replies.
        </p>
        <div
          className={styles.homeStatus}
          data-home-status=""
          aria-live="polite"
        >
          {chosen ? (
            <>
              <strong>
                {chosen.name} · {STATUS[chosen.status].label}
              </strong>
              <p>
                Replied <q lang={htmlLang(chosen.lang)}>{chosen.reply}</q>
              </p>
              <p>{STATUS[chosen.status].detail}</p>
            </>
          ) : (
            <p>Select a home to see its latest reply.</p>
          )}
        </div>
      </div>
      <div className={styles.townStage} data-town="">
        <div
          className={styles.world}
          data-world=""
          role="group"
          aria-label="The town: twelve fictional homes"
        >
          <div className={styles.board} aria-hidden="true" />
          <div
            className={styles.road}
            style={{ left: 0, top: 227, width: 480, height: 22 }}
            aria-hidden="true"
          />
          <div
            className={styles.road}
            style={{ left: 227, top: 0, width: 22, height: 480 }}
            aria-hidden="true"
          />
          <svg
            className={styles.townLinks}
            viewBox="0 0 480 480"
            aria-hidden="true"
          >
            {HOMES.map((h, i) => (
              <path
                key={h.id}
                data-town-thread=""
                data-state={h.status}
                className={styles.townThread}
                pathLength={1}
                d={threadPath(h, i)}
              />
            ))}
          </svg>
          <div
            className={styles.clinic}
            data-clinic=""
            style={
              { left: CLINIC.x, top: CLINIC.y, "--h": `${CLINIC.h}px` } as CSSProperties
            }
            aria-hidden="true"
          >
            {box}
          </div>
          {HOMES.map((h, i) => (
            <button
              key={h.id}
              type="button"
              className={styles.house}
              data-house={h.id}
              style={
                {
                  left: h.x,
                  top: h.y,
                  "--h": `${h.h}px`,
                  "--top": ROOFS[i % ROOFS.length],
                } as CSSProperties
              }
              aria-pressed={selected === h.id}
              aria-label={`${h.name}’s home`}
              onClick={() => setSelected(h.id)}
            >
              {box}
            </button>
          ))}
          {HOMES.filter((h) => h.status !== "ok").map((h) => (
            <span
              key={h.id}
              className={styles.ping}
              data-ping=""
              data-state={h.status}
              style={
                { left: h.x + 20, top: h.y + 20, "--z": `${h.h + 34}px` } as CSSProperties
              }
              aria-hidden="true"
            >
              {h.status === "red" ? "!" : ""}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 5: Append the Act 5 styles** to `web/components/film/film.module.css`

```css
/* ---------- Act 5: the whole town ---------- */
.act5 { position: relative; overflow: hidden; display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); gap: clamp(24px, 4vw, 64px); align-items: center; padding: clamp(72px, 12vh, 140px) clamp(16px, 6vw, 96px); background: linear-gradient(180deg, #eaf3ee 0%, #dcebe3 60%, #cfe2d8 100%); }
.act5Head { position: relative; z-index: 2; }
.act5Head h2 { font-size: clamp(34px, 4.6vw, 60px); line-height: 1.02; margin: 0 0 18px; }
.act5 [data-state="ok"] { --status: var(--pandan); }
.act5 [data-state="watch"] { --status: var(--amber); }
.act5 [data-state="red"] { --status: var(--cili); }
.townKey { list-style: none; display: grid; gap: 8px; margin: 0 0 8px; padding: 0; font-size: 15px; }
.townKey li::before { content: ""; display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 10px; background: var(--status); box-shadow: 0 0 8px var(--status); }
.illustrative { font-size: 13px; color: var(--teh); margin: 0 0 18px; }
.homeStatus { min-height: 7.5em; padding: 14px 16px; border-radius: 16px; background: rgba(255, 255, 255, 0.75); border: 1px solid rgba(59, 42, 32, 0.15); }
.homeStatus p { margin: 6px 0 0; }
.homeStatus q { font-weight: 600; }
.townStage { position: relative; height: 560px; perspective: 1400px; touch-action: pan-y; user-select: none; }
.world { --town-scale: 0.95; position: absolute; left: 50%; top: 50%; width: 480px; height: 480px; margin: -240px 0 0 -240px; transform-style: preserve-3d; transform: rotateX(calc(58deg - var(--tilt-y, 0) * 14deg)) rotateZ(calc(-38deg + var(--tilt-x, 0) * 26deg)) scale(var(--town-scale)); }
.film[data-motion="on"] .world:not([data-dragging]) { transition: transform 0.5s cubic-bezier(0.2, 0.7, 0.2, 1); }
.board { position: absolute; inset: 0; border-radius: 34px; background: linear-gradient(135deg, #f7fbf8, #e3efe8); box-shadow: 0 60px 90px rgba(18, 60, 45, 0.28), inset 0 0 0 1px rgba(255, 255, 255, 0.8); }
.road { position: absolute; background: #cfdcd4; border-radius: 12px; }
.house, .clinic { position: absolute; width: 40px; height: 40px; padding: 0; border: 0; background: none; overflow: visible; transform-style: preserve-3d; }
.house { --side: #d8b894; --side2: #c9a57f; cursor: pointer; }
.clinic { --top: #ffffff; --side: #cfe3d9; --side2: #b6d2c4; width: 76px; height: 76px; }
.faceTop, .faceFront, .faceSide { position: absolute; display: block; }
.faceTop { inset: 0; transform: translateZ(var(--h)); background: var(--top); border-radius: 6px; }
.faceFront { left: 0; right: 0; bottom: 0; height: var(--h); transform-origin: bottom; transform: rotateX(-90deg); background: var(--side); }
.faceSide { top: 0; bottom: 0; right: 0; width: var(--h); transform-origin: right; transform: rotateY(90deg); background: var(--side2); }
/* The ring sits on the roof, where the eye is; the button's own outline would lie flat on the ground. */
.film .house:focus-visible { outline: none; }
.house:focus-visible .faceTop, .house[aria-pressed="true"] .faceTop { box-shadow: 0 0 0 3px var(--kopi); }
.townLinks { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; transform: translateZ(1px); pointer-events: none; }
.townThread { fill: none; stroke: var(--status); stroke-width: 2; stroke-linecap: round; stroke-dasharray: 1; }
.townThread:not([data-state="ok"]) { stroke-width: 3.5; filter: drop-shadow(0 0 4px var(--status)); }
.ping { position: absolute; width: 26px; height: 26px; margin: -13px 0 0 -13px; border-radius: 50%; background: var(--status); box-shadow: 0 0 20px var(--status); color: #fff; font-weight: 700; font-size: 14px; line-height: 26px; text-align: center; transform: translateZ(var(--z)) rotateZ(38deg) rotateX(-58deg); pointer-events: none; }
.film[data-motion="on"] .act5[data-inview="true"] .ping { animation: townBob 2.4s ease-in-out infinite; }
@keyframes townBob { 50% { transform: translateZ(calc(var(--z) + 12px)) rotateZ(38deg) rotateX(-58deg); } }
@media (max-width: 760px) {
  .act5 { grid-template-columns: minmax(0, 1fr); }
  .townStage { height: 400px; }
  .world { --town-scale: 0.66; }
}
```

- [ ] **Step 6: Add Act 5 to the page.** In `web/app/preview/film/page.tsx`, import it and render it after `<Act4Daughter />`:

```tsx
import { Act5Town } from "@/components/film/acts/act5-town";
```

```tsx
        <Act4Daughter />
        <Act5Town />
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `npx playwright test e2e/film-act5.spec.ts e2e/film-page.spec.ts`
Expected: PASS, with the 320 px spacing test skipped on desktop.
- If Chromium flattens the 3D faces inside `<button>`, the homes render as flat squares. Rule on keeping `<button>` with the faces moved into a sibling `div[aria-hidden]` positioned at the same footprint, and ledger it.
- If `click()` cannot reach a home through the 3D transform, rule on the smallest fix that keeps a real button, and ledger it.

- [ ] **Step 8: Full suite, lint and types**

Run: `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green. The JS-budget test prints the new "JS the film adds over /" figure; record it in the ledger.

- [ ] **Step 9: Commit**

```bash
git add components/film/use-in-view.ts components/film/acts/act5-town.tsx components/film/film.module.css app/preview/film/page.tsx e2e/film-act5.spec.ts e2e/film-page.spec.ts
git commit -m "Add Act 5: one clinic and every home it cares for, as a small 3D town"
```

---

### Task 3: Turning the town: drag, and device tilt after a tap

**Files:**
- Create: `web/components/film/use-tilt.ts`
- Modify: `web/components/film/acts/act5-town.tsx`, `web/components/film/film.module.css` (append), `web/e2e/film-act5.spec.ts`

**Interfaces:**
- Consumes: the Act 5 DOM from Task 2 (`[data-town]` stage, `[data-world]`).
- Produces: `useTilt(stage: RefObject<HTMLElement | null>, world: RefObject<HTMLElement | null>): { canTilt: boolean; tilting: boolean; refused: boolean; toggleDeviceTilt: () => Promise<void> }`.
  - It writes `--tilt-x` and `--tilt-y` (each from -1 to 1) on the world element.
  - It sets `data-dragging` on the world while a drag is under way.

- [ ] **Step 1: Write the failing tests.** Append to `web/e2e/film-act5.spec.ts`:

```ts
const tiltX = (page: Page) =>
  page
    .locator("#act5 [data-world]")
    .evaluate((el) =>
      parseFloat((el as HTMLElement).style.getPropertyValue("--tilt-x") || "0"),
    );

test("dragging the town turns it", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "A mouse drag.");
  await page.goto("/preview/film");
  const stage = page.locator("#act5 [data-town]");
  await stage.scrollIntoViewIfNeeded();
  const box = (await stage.boundingBox())!;
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
  await page.goto("/preview/film");
  const target = home(page, "Mr Muthu");
  await target.scrollIntoViewIfNeeded();
  const b = (await target.boundingBox())!;
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2);
  await page.mouse.down();
  await page.mouse.move(b.x + b.width / 2 - 150, b.y + b.height / 2, {
    steps: 8,
  });
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 8 });
  await page.mouse.up();
  await expect(target).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator("#act5 [data-home-status]")).toContainText(
    "Select a home",
  );
});

test("mouse screens are not offered device tilt", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "A mouse screen.");
  await page.goto("/preview/film");
  await expect(
    page.getByRole("button", { name: "Tilt to explore" }),
  ).toHaveCount(0);
});

test("on a touch screen, tilting the phone turns the town once the reader asks for it", async ({
  page,
}, info) => {
  test.skip(info.project.name !== "phone", "Touch screens only.");
  await page.goto("/preview/film");
  const tilt = page.getByRole("button", { name: "Tilt to explore" });
  await tilt.click();
  await expect(tilt).toHaveAttribute("aria-pressed", "true");
  const turn = (gamma: number) =>
    page.evaluate(
      (g) =>
        window.dispatchEvent(
          new DeviceOrientationEvent("deviceorientation", { beta: 50, gamma: g }),
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
```

- [ ] **Step 2: Run them and watch them fail**

Run: `npx playwright test e2e/film-act5.spec.ts -g "drag|tilt"`
Expected: FAIL. `--tilt-x` stays 0, and the button "Tilt to explore" is not found.

- [ ] **Step 3: Write `web/components/film/use-tilt.ts`**

```ts
"use client";

import {
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type RefObject,
} from "react";

type Tilt = { x: number; y: number };
type Orientation = typeof DeviceOrientationEvent & {
  requestPermission?: () => Promise<"granted" | "denied">;
};

const clamp = (v: number) => Math.max(-1, Math.min(1, v));
const noSubscription = () => () => {};
const touchScreen = () =>
  "DeviceOrientationEvent" in window &&
  window.matchMedia("(pointer: coarse)").matches;

function apply(world: HTMLElement | null, store: Tilt, x: number, y: number) {
  store.x = clamp(x);
  store.y = clamp(y);
  world?.style.setProperty("--tilt-x", store.x.toFixed(3));
  world?.style.setProperty("--tilt-y", store.y.toFixed(3));
}

/**
 * Turns the town. Dragging the stage (mouse or finger) sets --tilt-x / --tilt-y on the world, from -1 to 1;
 * a press that moves less than 6 px stays a click, so selecting a home still works, and a drag never
 * selects. Device tilt is offered only on touch screens and only after a tap, because iOS asks permission
 * then; refusing leaves drag working.
 */
export function useTilt(
  stage: RefObject<HTMLElement | null>,
  world: RefObject<HTMLElement | null>,
) {
  const canTilt = useSyncExternalStore(noSubscription, touchScreen, () => false);
  const [tilting, setTilting] = useState(false);
  const [refused, setRefused] = useState(false);
  const tilt = useRef<Tilt>({ x: 0, y: 0 });

  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    let start: { x: number; y: number; tx: number; ty: number; id: number } | null =
      null;
    let dragged = false;
    const down = (e: PointerEvent) => {
      start = {
        x: e.clientX,
        y: e.clientY,
        tx: tilt.current.x,
        ty: tilt.current.y,
        id: e.pointerId,
      };
      dragged = false;
    };
    const move = (e: PointerEvent) => {
      if (!start || e.pointerId !== start.id) return;
      const dx = e.clientX - start.x;
      const dy = e.clientY - start.y;
      if (!dragged && Math.hypot(dx, dy) < 6) return;
      if (!dragged) {
        dragged = true;
        el.setPointerCapture(e.pointerId);
        world.current?.setAttribute("data-dragging", "");
      }
      const r = el.getBoundingClientRect();
      apply(
        world.current,
        tilt.current,
        start.tx + (dx / r.width) * 2,
        start.ty + (dy / r.height) * 2,
      );
    };
    const up = (e: PointerEvent) => {
      if (!start || e.pointerId !== start.id) return;
      start = null;
      world.current?.removeAttribute("data-dragging");
    };
    // Capture phase, before React's handlers: the click that ends a drag must not select a home.
    const click = (e: MouseEvent) => {
      if (!dragged) return;
      dragged = false;
      e.stopPropagation();
      e.preventDefault();
    };
    el.addEventListener("pointerdown", down);
    el.addEventListener("pointermove", move);
    el.addEventListener("pointerup", up);
    el.addEventListener("pointercancel", up);
    el.addEventListener("click", click, true);
    return () => {
      el.removeEventListener("pointerdown", down);
      el.removeEventListener("pointermove", move);
      el.removeEventListener("pointerup", up);
      el.removeEventListener("pointercancel", up);
      el.removeEventListener("click", click, true);
    };
  }, [stage, world]);

  useEffect(() => {
    if (!tilting) return;
    const onTilt = (e: DeviceOrientationEvent) => {
      if (e.gamma == null || e.beta == null) return;
      apply(world.current, tilt.current, e.gamma / 45, (e.beta - 50) / 30);
    };
    window.addEventListener("deviceorientation", onTilt);
    return () => window.removeEventListener("deviceorientation", onTilt);
  }, [tilting, world]);

  const toggleDeviceTilt = async () => {
    if (tilting) {
      setTilting(false);
      return;
    }
    const orientation = window.DeviceOrientationEvent as Orientation;
    if (orientation.requestPermission) {
      const answer = await orientation
        .requestPermission()
        .catch(() => "denied" as const);
      if (answer !== "granted") {
        setRefused(true);
        return;
      }
    }
    setRefused(false);
    setTilting(true);
  };

  return { canTilt, tilting, refused, toggleDeviceTilt };
}
```

- [ ] **Step 4: Wire it into Act 5.** In `act5-town.tsx`:
  - import `useTilt` from `"../use-tilt"`;
  - add refs to the stage and world;
  - add the hint, the tilt button and its note after the status panel.

```tsx
  const stage = useRef<HTMLDivElement>(null);
  const world = useRef<HTMLDivElement>(null);
  const { canTilt, tilting, refused, toggleDeviceTilt } = useTilt(stage, world);
```

```tsx
        <p className={styles.townHint}>
          {canTilt
            ? "Drag sideways to turn the town, or tilt your phone."
            : "Drag to turn the town."}
        </p>
        {canTilt ? (
          <>
            <button
              type="button"
              className={styles.tiltButton}
              aria-pressed={tilting}
              onClick={toggleDeviceTilt}
            >
              Tilt to explore
            </button>
            <p className={styles.townHint} aria-live="polite">
              {refused ? "Tilt is off. Drag the town to turn it instead." : ""}
            </p>
          </>
        ) : null}
```

Add `ref={stage}` to the `[data-town]` div and `ref={world}` to the `[data-world]` div.

Append to `film.module.css`:

```css
.townHint { font-size: 14px; color: var(--teh); margin: 12px 0 0; }
.townStage { cursor: grab; }
.world[data-dragging] { cursor: grabbing; }
.tiltButton { margin-top: 12px; font: inherit; font-weight: 600; padding: 10px 16px; border-radius: 999px; border: 1.5px solid var(--kopi); background: transparent; color: var(--kopi); cursor: pointer; }
.tiltButton[aria-pressed="true"] { background: var(--kopi); color: var(--santan); }
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `npx playwright test e2e/film-act5.spec.ts`
Expected: PASS.
- If `(pointer: coarse)` does not match under the phone project's touch emulation, the tilt button is missing there. Rule on `(any-pointer: coarse)` and ledger it.

- [ ] **Step 6: Full suite, lint and types**

Run: `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green; ledger the JS-budget figure.

- [ ] **Step 7: Commit**

```bash
git add components/film/use-tilt.ts components/film/acts/act5-town.tsx components/film/film.module.css e2e/film-act5.spec.ts
git commit -m "Let readers turn the town by dragging, or by tilting a phone after a tap"
```

---

### Task 4: Act 6, trust

**Files:**
- Create: `web/components/film/acts/act6-trust.tsx`, `web/e2e/film-act6.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`, `web/e2e/film-page.spec.ts`

**Interfaces:**
- Consumes: `useFilm()`, `Backdrop` (`act="act6"`, already an `ActId`).
- Produces: `Act6Trust`, with DOM hooks `section#act6[data-anchor]` and `article[data-principle][data-shown]` ×3.

- [ ] **Step 1: Write the failing tests** in `web/e2e/film-act6.spec.ts`

```ts
import { expect, test, type Page } from "@playwright/test";

const principles = (page: Page, selector: string) =>
  page.locator(selector).evaluateAll((els) =>
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
    for (const shown of await page
      .locator("#act6 [data-principle]")
      .evaluateAll((els) => els.map((el) => el.getAttribute("data-shown"))))
      expect(shown).toBe("true");
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
```

In `web/e2e/film-page.spec.ts`, extend the story-order list and the scene-description list to end with `"act6"`.

- [ ] **Step 2: Run them and watch them fail**

Run: `npx playwright test e2e/film-act6.spec.ts e2e/film-page.spec.ts -g "principles|story order|describes"`
Expected: FAIL, because `#act6` does not exist.

- [ ] **Step 3: Write `web/components/film/acts/act6-trust.tsx`**

```tsx
"use client";

import { useRef, useState } from "react";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { Check, LockKeyhole, ShieldCheck } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import styles from "../film.module.css";

/** The current landing page's three safety principles, word for word (a test compares them with "/"). */
const PRINCIPLES = [
  {
    icon: ShieldCheck,
    title: "Human decisions stay human",
    text: "Urgent replies and safety concerns go to a clinician. Khabar never presents itself as a diagnosis.",
  },
  {
    icon: LockKeyhole,
    title: "Privacy is part of the workflow",
    text: "Your registered name, IC and phone number are removed before AI-assisted intake and triage. Record access is logged and visible.",
  },
  {
    icon: Check,
    title: "Safety has a hard stop",
    text: "Critical findings block finalisation until the clinician records a clear reason to proceed.",
  },
];

/**
 * Act 6: a quiet night, and the three promises the product keeps. Each principle arrives as the reader
 * reaches it; the server render (and reduced motion, and Pause) shows all three.
 */
export function Act6Trust() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [reached, setReached] = useState<boolean[]>(() =>
    PRINCIPLES.map(() => false),
  );

  useGSAP(
    () => {
      if (!motion) return;
      root
        .current!.querySelectorAll<HTMLElement>("[data-principle]")
        .forEach((el, i) => {
          ScrollTrigger.create({
            trigger: el,
            start: "top 85%",
            once: true,
            onEnter: () =>
              setReached((was) => was.map((r, j) => (j === i ? true : r))),
          });
        });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act6"
      data-anchor=""
      className={styles.act6}
      aria-labelledby="act6-title"
    >
      <p className={styles.srOnly}>
        Illustration: a quiet kampung road at night, the town asleep.
      </p>
      <Backdrop act="act6" />
      <header className={styles.act6Head}>
        <p className={styles.eyebrow}>Designed for trust</p>
        <h2 id="act6-title">
          AI supports the care team.
          <br />
          <em>It does not replace one.</em>
        </h2>
      </header>
      <div className={styles.principles}>
        {PRINCIPLES.map(({ icon: Icon, title, text }, i) => (
          <article
            key={title}
            className={styles.principle}
            data-principle=""
            data-shown={!motion || reached[i]}
          >
            <span>{String(i + 1).padStart(2, "0")}</span>
            <Icon size={21} aria-hidden />
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Append the Act 6 styles** to `film.module.css`

```css
/* ---------- Act 6: trust ---------- */
.act6 { position: relative; overflow: hidden; padding: clamp(72px, 12vh, 140px) clamp(16px, 6vw, 96px); background: var(--dusk); color: #f6efe4; }
.backdrop[data-act="act6"]:not(:has(picture)) { background: radial-gradient(40% 30% at 18% 88%, rgba(232, 163, 61, 0.22) 0%, transparent 70%), linear-gradient(180deg, #1b2440 0%, #151c33 100%); }
.act6 .eyebrow, .act6 h2 em { color: #9fd9bd; }
.act6Head { position: relative; z-index: 2; max-width: 760px; }
.act6Head h2 { font-size: clamp(34px, 4.6vw, 60px); line-height: 1.02; margin: 0 0 32px; }
.principles { position: relative; z-index: 2; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: clamp(16px, 2vw, 28px); }
.principle { padding: 22px; border-radius: 20px; background: rgba(246, 239, 228, 0.06); border: 1px solid rgba(246, 239, 228, 0.16); }
.principle > span { display: block; font-size: 15px; color: #9fd9bd; margin-bottom: 10px; }
.principle svg { color: #9fd9bd; }
.principle h3 { font-size: 21px; margin: 10px 0 8px; }
.principle p { margin: 0; color: #d9cfc3; line-height: 1.6; }
.act6 .principle[data-shown="false"] { opacity: 0; transform: translateY(16px); }
.film[data-motion="on"] .principle { transition: opacity 0.6s ease-out, transform 0.6s ease-out; }
.film[data-motion="on"] .principle:nth-child(2) { transition-delay: 0.15s; }
.film[data-motion="on"] .principle:nth-child(3) { transition-delay: 0.3s; }
.film .act6 :where(a, button):focus-visible, .film .act7 :where(a, button):focus-visible { outline-color: #f6efe4; }
@media (max-width: 760px) { .principles { grid-template-columns: minmax(0, 1fr); } }
```

- [ ] **Step 5: Add Act 6 to the page** after `<Act5Town />`, importing it from `@/components/film/acts/act6-trust`.

- [ ] **Step 6: Run the tests and watch them pass**

Run: `npx playwright test e2e/film-act6.spec.ts e2e/film-page.spec.ts`
Expected: PASS.

- [ ] **Step 7: Full suite, lint and types**

Run: `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green; ledger the JS-budget figure.

- [ ] **Step 8: Commit**

```bash
git add components/film/acts/act6-trust.tsx components/film/film.module.css app/preview/film/page.tsx e2e/film-act6.spec.ts e2e/film-page.spec.ts
git commit -m "Add Act 6: the three safety principles, one at a time on a quiet night"
```

---

### Task 5: Act 7, the finale

**Files:**
- Create: `web/components/film/acts/act7-finale.tsx`, `web/e2e/film-act7.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`, `web/e2e/film-page.spec.ts`

**Interfaces:**
- Consumes: `HOMES` (Task 1), `useInView` (Task 2), `useFilm()`, `Backdrop` (`act="act7"`), `SCRUB` and `ease`.
- Produces: `Act7Finale`, with DOM hooks:
  - `section#act7[data-anchor][data-inview]`;
  - `[data-sky-thread]` ×12 and `[data-star]` ×12;
  - the link "Enter the live prototype" to `/login`;
  - the link "Explore the 30-day story" to `#act3`.

**Ruling carried from planning:** every finale thread is the same soft green light, whatever the home's status. A red thread "rising into the sky" could read as a death, so status colours stay in Act 5.

- [ ] **Step 1: Write the failing tests** in `web/e2e/film-act7.spec.ts`

```ts
import { expect, test, type Page } from "@playwright/test";

const HEADING = /See the whole story,\s*not just the appointment\./;

test("the finale says what the current landing page says, and its button opens the prototype", async ({
  page,
}) => {
  for (const path of ["/", "/preview/film"]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { name: HEADING })).toHaveCount(1);
  }
  const enter = page
    .locator("#act7")
    .getByRole("link", { name: "Enter the live prototype" });
  await expect(enter).toHaveAttribute("href", "/login");
});

test("the way back to the thirty days lands on them", async ({ page }) => {
  await page.goto("/preview/film");
  await page
    .locator("#act7")
    .getByRole("link", { name: "Explore the 30-day story" })
    .click();
  await expect(page.locator("#act3-title")).toBeInViewport();
});

const offsets = (page: Page) =>
  page
    .locator("#act7 [data-sky-thread]")
    .evaluateAll((els) =>
      els.map((el) => parseFloat(getComputedStyle(el).strokeDashoffset)),
    );

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("every thread has risen and every light is lit: the end frame", async ({
    page,
  }) => {
    await page.goto("/preview/film");
    expect((await offsets(page)).every((o) => o === 0)).toBe(true);
    const lit = await page
      .locator("#act7 [data-star]")
      .evaluateAll((els) => els.map((el) => getComputedStyle(el).opacity));
    expect(lit.every((o) => o === "1")).toBe(true);
  });
});

test("scrolling to the end raises every thread into the sky", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  expect((await offsets(page))[0]).toBe(1);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect
    .poll(async () => (await offsets(page)).every((o) => o === 0), {
      timeout: 5000,
    })
    .toBe(true);
});

test("the stars twinkle only while the finale is on screen and motion is on", async ({
  page,
}) => {
  await page.goto("/preview/film");
  await expect(page.locator("[data-motion]")).toHaveAttribute(
    "data-motion",
    "on",
  );
  const running = () =>
    page
      .locator("#act7")
      .evaluate(
        (el) =>
          el
            .getAnimations({ subtree: true })
            .filter((a) => a.playState === "running").length,
      );
  expect(await running()).toBe(0);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect.poll(running).toBeGreaterThan(0);
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect.poll(running).toBe(0);
});
```

In `web/e2e/film-page.spec.ts`, extend the story-order list and the scene-description list to end with `"act7"`.

- [ ] **Step 2: Run them and watch them fail**

Run: `npx playwright test e2e/film-act7.spec.ts e2e/film-page.spec.ts -g "finale|thirty days lands|thread|stars|story order|describes"`
Expected: FAIL, because `#act7` does not exist.

- [ ] **Step 3: Write `web/components/film/acts/act7-finale.tsx`**

```tsx
"use client";

import { useRef } from "react";
import Link from "next/link";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { ArrowRight } from "lucide-react";
import { useFilm } from "../film-provider";
import { useInView } from "../use-in-view";
import { Backdrop } from "../backdrop";
import { HOMES } from "../town";
import { SCRUB, ease } from "../tokens";
import styles from "../film.module.css";

const W = 1440;
const H = 900;
const GROUND = H - 60;
/**
 * Each home in the town sends its thread up from its roof to its own light. The lights are spread with a
 * fixed shuffle (7 and 5 share no factor with 12 and 6), so none overlap.
 */
const SKY = HOMES.map((home, i) => {
  const x = 90 + i * ((W - 180) / (HOMES.length - 1));
  const roof = GROUND - 44 - (home.h - 26);
  const sx = 120 + ((i * 7) % 12) * ((W - 240) / 11);
  const sy = 80 + ((i * 5) % 6) * 34;
  return {
    id: home.id,
    x,
    roof,
    sx,
    sy,
    d: `M ${x} ${roof} C ${x} ${roof - 240}, ${sx} ${sy + 260}, ${sx} ${sy}`,
  };
});

/**
 * Act 7: night over the town. Every home's thread rises and joins a sky of lights, and the reader is
 * invited into the live prototype. The server render is the end frame: every thread risen, every light lit.
 */
export function Act7Finale() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const inView = useInView(root);

  useGSAP(
    () => {
      if (!motion) return;
      gsap
        .timeline({
          defaults: { duration: 1, ease: ease.scrub },
          scrollTrigger: {
            trigger: root.current,
            start: "top 70%",
            end: "bottom bottom",
            scrub: SCRUB,
          },
        })
        .fromTo(
          "[data-sky-thread]",
          { strokeDashoffset: 1 },
          { strokeDashoffset: 0, autoRound: false, stagger: 0.04 },
          0,
        )
        .fromTo("[data-star]", { opacity: 0 }, { opacity: 1, stagger: 0.04 }, 0.6);
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act7"
      data-anchor=""
      data-inview={inView}
      className={styles.act7}
      aria-labelledby="act7-title"
    >
      <p className={styles.srOnly}>
        Illustration: the town at night from above, every home&rsquo;s thread
        rising into a sky of lights.
      </p>
      <Backdrop act="act7" />
      <svg
        className={styles.sky}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="xMidYMax slice"
        aria-hidden="true"
      >
        {SKY.map(({ id, d }) => (
          <path
            key={id}
            className={styles.skyThread}
            data-sky-thread=""
            pathLength={1}
            d={d}
          />
        ))}
        {SKY.map(({ id, sx, sy }) => (
          <circle key={id} className={styles.star} data-star="" cx={sx} cy={sy} r={4} />
        ))}
        <g className={styles.skyHome}>
          {SKY.map(({ id, x, roof }) => (
            <path
              key={id}
              d={`M ${x - 26} ${GROUND} V ${roof + 20} L ${x} ${roof} L ${x + 26} ${roof + 20} V ${GROUND} Z`}
            />
          ))}
          <rect x={0} y={GROUND} width={W} height={H - GROUND} />
        </g>
        {SKY.map(({ id, x, roof }) => (
          <rect
            key={id}
            className={styles.skyWindow}
            x={x - 5}
            y={roof + 28}
            width={10}
            height={10}
            rx={2}
          />
        ))}
      </svg>
      <div className={styles.act7Copy}>
        <p className={styles.eyebrow}>Care that carries on</p>
        <h2 id="act7-title">
          See the whole story,
          <br />
          <em>not just the appointment.</em>
        </h2>
        <div className={styles.act7Actions}>
          <Link className="button-primary" href="/login">
            Enter the live prototype <ArrowRight size={17} />
          </Link>
          <a className={styles.backToStory} href="#act3">
            Explore the 30-day story
          </a>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Append the Act 7 styles** to `film.module.css`

```css
/* ---------- Act 7: finale ---------- */
.act7 { position: relative; overflow: hidden; min-height: 100svh; display: grid; align-content: start; justify-items: center; text-align: center; padding: clamp(96px, 16vh, 180px) clamp(16px, 6vw, 96px) clamp(220px, 34vh, 340px); background: linear-gradient(180deg, #151c33 0%, #0e1426 100%); color: #f6efe4; }
.backdrop[data-act="act7"]:not(:has(picture)) { background: radial-gradient(70% 45% at 50% 100%, rgba(127, 209, 168, 0.18) 0%, transparent 70%); }
.sky { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.skyThread { fill: none; stroke: #7fd1a8; stroke-width: 2; stroke-linecap: round; stroke-dasharray: 1; opacity: 0.8; filter: drop-shadow(0 0 5px #7fd1a8); }
.skyHome { fill: #0a0f1d; }
.skyWindow { fill: #e8a33d; opacity: 0.85; }
.star { fill: #fff6e0; filter: drop-shadow(0 0 6px #ffe7b0); transform-box: fill-box; transform-origin: center; }
.film[data-motion="on"] .act7[data-inview="true"] .star { animation: twinkle 3.2s ease-in-out infinite; }
.film[data-motion="on"] .act7[data-inview="true"] .star:nth-of-type(3n) { animation-delay: -1.1s; }
.film[data-motion="on"] .act7[data-inview="true"] .star:nth-of-type(3n + 1) { animation-delay: -2.2s; }
@keyframes twinkle { 50% { transform: scale(0.6); } }
.act7Copy { position: relative; z-index: 2; max-width: 760px; }
.act7 .eyebrow, .act7Copy h2 em { color: #9fd9bd; }
.act7Copy h2 { font-size: clamp(36px, 5vw, 68px); line-height: 1.02; margin: 0 0 28px; }
.act7Actions { display: flex; flex-wrap: wrap; gap: 14px 22px; justify-content: center; align-items: center; }
.backToStory { color: #f6efe4; font-weight: 600; }
```

- [ ] **Step 5: Add Act 7 to the page** after `<Act6Trust />`, importing it from `@/components/film/acts/act7-finale`.

- [ ] **Step 6: Run the tests and watch them pass**

Run: `npx playwright test e2e/film-act7.spec.ts e2e/film-page.spec.ts`
Expected: PASS.

- [ ] **Step 7: Full suite, lint and types**

Run: `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green; ledger the JS-budget figure.

- [ ] **Step 8: Commit**

```bash
git add components/film/acts/act7-finale.tsx components/film/film.module.css app/preview/film/page.tsx e2e/film-act7.spec.ts e2e/film-page.spec.ts
git commit -m "Add Act 7: every thread rises into a sky of lights, and the way into the prototype"
```

---

### Task 6: The progress rail

**Files:**
- Create: `web/components/film/story.ts`, `web/components/film/progress-rail.tsx`, `web/e2e/film-rail.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`

**Interfaces:**
- Consumes: the eight act sections `#act0`–`#act7`.
- Produces:
  - `STORY: readonly { id: string; name: string }[]`
  - `ProgressRail`: `nav[aria-label="Story"]` with 8 links, where the current one has `aria-current="step"`.

**Ruling carried from planning:** the rail shows on desktop only, meaning the `media.desktop` screens where Act 3 pins.
- On phones nothing is pinned except the hero, and a fixed rail would cover text beside the Pause button.
- There, the rail is `display: none`, so it is also out of the accessibility tree.
- The current act's name shows while the reader scrolls and for 1.2 s after, and on hover or focus. At rest it does not cover a day card's text.

- [ ] **Step 1: Write the failing tests** in `web/e2e/film-rail.spec.ts`

```ts
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
```

- [ ] **Step 2: Run them and watch them fail**

Run: `npx playwright test e2e/film-rail.spec.ts`
Expected: FAIL. On desktop the "Story" navigation is not found; the phone test passes trivially. That is fine, because it guards against the rail showing later.

- [ ] **Step 3: Write `web/components/film/story.ts`**

```ts
/** The film's eight acts in order, as the progress rail names them (spec §3, §13). */
export const STORY = [
  { id: "act0", name: "The visit ends" },
  { id: "act1", name: "Lost in the paper" },
  { id: "act2", name: "The fifteen minutes" },
  { id: "act3", name: "Thirty days at home" },
  { id: "act4", name: "Her daughter in KL" },
  { id: "act5", name: "The whole town" },
  { id: "act6", name: "Trust" },
  { id: "act7", name: "The whole story" },
] as const;
```

- [ ] **Step 4: Write `web/components/film/progress-rail.tsx`**

```tsx
"use client";

import { useEffect, useState } from "react";
import { STORY } from "./story";
import styles from "./film.module.css";

/**
 * A slim rail of the eight acts at the right edge (desktop only). The act under the middle of the screen is
 * marked current, which also holds while Act 3 is pinned, because its section contains the pin. Its name
 * shows while the reader scrolls and for a moment after, then gets out of the way.
 */
export function ProgressRail() {
  const [current, setCurrent] = useState<string>(STORY[0].id);
  const [moving, setMoving] = useState(false);

  useEffect(() => {
    let frame = 0;
    let rest: number | undefined;
    const read = () => {
      frame = 0;
      const middle = window.innerHeight / 2;
      for (const { id } of STORY) {
        const r = document.getElementById(id)?.getBoundingClientRect();
        if (r && r.top <= middle && r.bottom >= middle) {
          setCurrent(id);
          return;
        }
      }
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(read);
      setMoving(true);
      window.clearTimeout(rest);
      rest = window.setTimeout(() => setMoving(false), 1200);
    };
    frame = requestAnimationFrame(read);
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      cancelAnimationFrame(frame);
      window.clearTimeout(rest);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  return (
    <nav className={styles.rail} aria-label="Story" data-moving={moving}>
      <ol>
        {STORY.map(({ id, name }) => (
          <li key={id}>
            <a href={`#${id}`} aria-current={current === id ? "step" : undefined}>
              <span className={styles.railDot} aria-hidden="true" />
              <span className={styles.railName}>{name}</span>
            </a>
          </li>
        ))}
      </ol>
    </nav>
  );
}
```

- [ ] **Step 5: Append the rail styles** to `film.module.css`

```css
/* ---------- Progress rail (desktop only, where Act 3 pins) ---------- */
.rail { display: none; }
@media (min-width: 761px) and (min-height: 600px) {
  .rail { display: block; position: fixed; right: 12px; top: 50%; transform: translateY(-50%); z-index: 45; }
}
.rail ol { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
.rail a { position: relative; display: grid; place-items: center; width: 24px; height: 24px; }
.railDot { width: 9px; height: 9px; border-radius: 50%; background: rgba(59, 42, 32, 0.45); box-shadow: 0 0 0 2px rgba(246, 239, 228, 0.9); }
.rail a[aria-current="step"] .railDot { background: var(--kopi); transform: scale(1.4); }
.railName { position: absolute; right: 30px; top: 50%; transform: translateY(-50%); white-space: nowrap; padding: 3px 10px; border-radius: 999px; background: rgba(246, 239, 228, 0.96); color: var(--kopi); font-size: 13px; font-weight: 600; opacity: 0; pointer-events: none; }
.film[data-motion="on"] .railName { transition: opacity 0.3s; }
.rail[data-moving="true"] a[aria-current="step"] .railName, .rail a:hover .railName, .rail a:focus-visible .railName { opacity: 1; }
```

- [ ] **Step 6: Add the rail to the page.** In `page.tsx`, import `ProgressRail` from `@/components/film/progress-rail` and render it right after `</main>`, so it comes after the story in keyboard order:

```tsx
      </main>
      <ProgressRail />
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `npx playwright test e2e/film-rail.spec.ts`
Expected: PASS.

- [ ] **Step 8: Full suite, lint and types**

Run: `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green; ledger the JS-budget figure.

- [ ] **Step 9: Commit**

```bash
git add components/film/story.ts components/film/progress-rail.tsx components/film/film.module.css app/preview/film/page.tsx e2e/film-rail.spec.ts
git commit -m "Add a slim progress rail that names the current act and jumps to any act"
```

---

### Task 7: Measure, review and land on main

**Files:**
- Modify: `docs/LANDING_FILM_SLICE_REVIEW.md` (add a "Phase 2b" section)

- [ ] **Step 1: Full checks**

Run (in `web/`): `npx playwright test && npm run test:unit && npm run lint && npx tsc --noEmit`
Expected: all green.

- [ ] **Step 2: Lighthouse, mobile, three runs on the production build**, as in the slice's Task 7 Step 2.
Expected:
- the score stays at 90 or above;
- CLS stays at or below 0.05;
- simulated LCP is unchanged (about 2.9 s), because the new acts are below the fold.

Record the numbers.

- [ ] **Step 3: Screenshots** of Acts 5, 6 and 7 at 1440×900, at 390×844, and with reduced motion. Name them `p2b-{desktop,phone,reduced}-{act5,act6,act7}.png` in `.playwright-mcp/`. Look at every one:
- no overlap and nothing cut off;
- the rail clear of text;
- the dark acts readable;
- the town centred, with its pings above the homes.

- [ ] **Step 4: Add a "Phase 2b" section to `docs/LANDING_FILM_SLICE_REVIEW.md`**, covering:
  - what was added;
  - where Act 5's replies, Act 6's principles and Act 7's words come from, and the tests that keep them in step;
  - the measured numbers;
  - the screenshots;
  - the questions for the owner (the town's look, tilt on a real phone, rail placement, then phase 3: the real AI backgrounds and the switch-over).

- [ ] **Step 5: Final whole-branch review** by a fresh reviewer (the most capable model). Fix the Critical and Important findings failing-test-first, and commit.

- [ ] **Step 6: Land on main** (the owner's standing instruction of 29 Sep):
  1. Run `git status` first, and leave any file the owner or Codex is working on alone.
  2. Check out main, pull, and merge `feat/landing-film-phase-2b`.
  3. Rerun Step 1 on the merged result.
  4. Push `origin main` and delete the merged branch.
