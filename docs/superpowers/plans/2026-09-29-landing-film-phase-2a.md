# Landing film phase 2a: Acts 1, 2 and 4 implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Act 1 (Lost in the paper), Act 2 (The fifteen minutes) and Act 4 (Her daughter in KL) to `/preview/film`, in story order 0, 1, 2, 3, 4. Every patient- or clinician-facing word must be the product's own output.

**Architecture:**
- **Product wording:** a Python script runs the real agents code (`parse_line`, `build_summary`, `evaluate`) and writes `web/components/film/product-words.json`. A pytest test regenerates it and fails on any difference, so the page cannot drift from the product.
- **Acts:** each act is a client component in `web/components/film/acts/`. Following the slice's pattern, the server render is the end frame, and motion only adds scroll-triggered state changes that CSS transitions animate. Only Acts 0 and 3 stay pinned.
- **Act 2** reuses the existing `ProductPreview`, inside a new `LandingMotionScope` so the film's Pause and reduced motion reach it.

**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Modules, GSAP ScrollTrigger (`@gsap/react`), Motion (only via the reused `ProductPreview`), Playwright, Python 3.12 + pytest (agents).

**Spec:** `docs/superpowers/specs/2026-09-28-landing-storybook-film-design.md` (§3 Acts 1, 2 and 4; §4 truthfulness; §7 and §8). This plan follows the slice plan `docs/superpowers/plans/2026-09-28-landing-film-slice.md` and its ledger rulings, which are recorded in `docs/LANDING_FILM_SLICE_REVIEW.md`.

## Global Constraints

- Every word shown as coming from the product must be generated from the agents code:
  - Aminah's summary lines: `build_summary`.
  - The parsed draft: `parse_line`.
  - The safety finding: `evaluate`.
  - The check-in and answers already come from `PatientMessages.java` (slice).
- Never promise a callback, never give medical advice, no unsourced statistics, fictional people only.
- The server render is the end frame. Motion must never hide content from screen readers: animate `opacity` only, never `autoAlpha` or `visibility`.
- Interactions are `<button>`s (the consent control is `role="switch"` with `aria-checked`), keyboard-operable, with the kopi focus ring.
- New acts are not pinned. Pins stay in Act 0 and Act 3 only.
- Budgets from the slice still apply and their tests must stay green:
  - JS added over `/` ≤ 70 KB;
  - fonts ≤ 130 KB, CSS ≤ 45 KB;
  - zero axe violations;
  - no horizontal overflow at 320 px in Tamil.
- Commits: `git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit`, no co-author trailer. Branch `feat/landing-film-phase-2a`. Run `npm` from `web/`, and Python from `services/agents/` with `.venv/Scripts/python.exe`.

## Review Focus

1. **Language switch after a line is revealed:** the plain words must change language, and the shorthand must stay. Tested in Task 2.
2. **Toggling a line before scrolling to it:** a click must win over the scroll reveal and stay as clicked. Tested in Task 2.
3. **Pause while the Act 2 reveal is part-way:** everything is shown at once, with no stuck half-state. Tested in Task 3.
4. **Keyboard on the consent switch:** Space and Enter toggle it, the live text updates, and Nurul's phone never shows the summary while sharing is off. Tested in Task 4.
5. **Anchors for Pause:** the new acts carry `data-anchor`, so pausing inside them keeps the reader's place. Tested in Task 5.

---

## File map

| File | Responsibility |
|---|---|
| `services/agents/scripts/make_film_words.py` | Generates `web/components/film/product-words.json` from the real agents code |
| `services/agents/tests/test_film_words.py` | Fails if the JSON differs from what the agents code produces now |
| `web/components/film/product-words.json` | Generated: shorthand lines with summaries, Act 2 notes, draft rows and the safety finding |
| `web/components/film/product-words.ts` | Typed access to the JSON |
| `web/components/film/acts/act1-paper.tsx` | Act 1 |
| `web/components/film/acts/act2-visit.tsx` | Act 2 |
| `web/components/film/acts/act4-daughter.tsx` | Act 4 |
| `web/components/film/characters/nurul.tsx` | Code-drawn Nurul |
| `web/components/landing/landing-motion.tsx` | Add `LandingMotionScope` |
| `web/components/film/film.module.css` | Styles for the new acts (appended) |
| `web/app/preview/film/page.tsx` | Act order 0, 1, 2, 3, 4 |
| `web/e2e/film-act1.spec.ts`, `film-act2.spec.ts`, `film-act4.spec.ts` | Browser tests per act |

---

### Task 1: Product wording generated from the agents code

**Files:**
- Create: `services/agents/scripts/make_film_words.py`, `services/agents/tests/test_film_words.py`, `web/components/film/product-words.json` (generated), `web/components/film/product-words.ts`

**Interfaces:**
- Produces:
  - `PRODUCT_WORDS.rx`: `{ shorthand: string; medicine: string; how: Record<Lang, string> }[]`
  - `PRODUCT_WORDS.visit`: `{ notes: string; draft: { name: string; strengthMg: number | null; timesPerDay: number | null; timing: string | null }[]; finding: { severity: string; detail: string } }`

- [ ] **Step 1: Write the failing test** `services/agents/tests/test_film_words.py`

```python
"""The landing film shows the product's own words. This fails when the agents code would now say something
different from web/components/film/product-words.json; regenerate it with scripts/make_film_words.py."""
import json
from pathlib import Path

from scripts.make_film_words import OUTPUT, film_words


def test_the_film_shows_exactly_what_the_agents_produce():
    assert OUTPUT.exists(), "run: .venv/Scripts/python.exe -m scripts.make_film_words"
    assert json.loads(OUTPUT.read_text(encoding="utf-8")) == film_words()


def test_the_planted_error_is_caught_as_critical():
    finding = film_words()["visit"]["finding"]
    assert finding["severity"] == "CRITICAL"
    assert "maximum" in finding["detail"]


def test_every_summary_line_reads_after_food_when_the_shorthand_says_pc():
    metformin = film_words()["rx"][0]
    assert metformin["shorthand"].lower().endswith("pc")
    assert metformin["how"]["en"].endswith("after food.")
```

- [ ] **Step 2: Run it to verify it fails**

Run (in `services/agents/`): `.venv/Scripts/python.exe -m pytest tests/test_film_words.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'scripts.make_film_words'` (tests already import `scripts.*` as a namespace package via `pytest.ini` `pythonpath = .`).

- [ ] **Step 3: Add `services/agents/scripts/make_film_words.py`**

```python
"""Writes the words the landing film shows, from the real agents code, so the page never says something the
product would not: web/components/film/product-words.json. Run from services/agents/:
    .venv/Scripts/python.exe -m scripts.make_film_words
tests/test_film_words.py fails when this output would change."""
import json
from pathlib import Path

from agents.evaluator import Draft, Rx, evaluate
from agents.prescription import parse_line
from agents.summary import build_summary

OUTPUT = Path(__file__).resolve().parents[3] / "web" / "components" / "film" / "product-words.json"
LANGS = ("ms", "en", "zh", "ta")

# Act 1: what the doctor writes, as doctors really type it (lower case included).
SHORTHAND = ["Tab metformin 500mg bd pc", "Tab amlodipine 5mg od", "Tab paracetamol 1g prn"]

# Act 2: the doctor's notes, with one slip of a zero (5000 mg instead of 500 mg) for the safety check to catch.
NOTES = "Dx: T2DM, HTN\nTab metformin 5000mg bd pc\nTab amlodipine 5mg od\nReview in 4 weeks"


def film_words() -> dict:
    rx_lines = []
    for line in SHORTHAND:
        rx = parse_line(line)
        rx_lines.append({
            "shorthand": line,
            "medicine": build_summary([rx], "en").medicines[0].medicine,
            "how": {lang: build_summary([rx], lang).medicines[0].how for lang in LANGS},
        })

    parsed = [rx for rx in (parse_line(line) for line in NOTES.splitlines()) if rx]
    draft = Draft(
        prescription=[Rx(name=rx.name, dose_mg=rx.dose_mg, times_per_day=rx.times_per_day) for rx in parsed],
        report={"diagnosis": "T2DM, HTN", "plan": "Continue treatment", "follow_up": "Review in 4 weeks"},
    )
    critical = next(f for f in evaluate(draft) if f.severity == "CRITICAL")
    return {
        "rx": rx_lines,
        "visit": {
            "notes": NOTES,
            "draft": [
                {"name": rx.name, "strengthMg": rx.strength_mg, "timesPerDay": rx.times_per_day, "timing": rx.timing}
                for rx in parsed
            ],
            "finding": {"severity": critical.severity, "detail": critical.detail},
        },
    }


if __name__ == "__main__":
    OUTPUT.write_text(json.dumps(film_words(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT}")
```

The report keys match `REQUIRED_REPORT_FIELDS = ("diagnosis", "plan", "follow_up")`, so the only finding besides CRITICAL ones would be none; the script takes the first CRITICAL.

- [ ] **Step 4: Generate the JSON and run the tests**

Run: `.venv/Scripts/python.exe -m scripts.make_film_words && .venv/Scripts/python.exe -m pytest tests/test_film_words.py -q`
Expected: `wrote …product-words.json`, then 3 passed. Open the JSON and check:
- `rx[0].how.en` is `"1 tablet, morning and night, after food."`;
- `visit.finding.detail` is `"Metformin 10000 mg a day is above the 3000 mg maximum."`.

- [ ] **Step 5: Add `web/components/film/product-words.ts`**

```ts
import words from "./product-words.json";
import type { Lang } from "./messages";

/**
 * Words generated from the real agents code by services/agents/scripts/make_film_words.py (checked by
 * tests/test_film_words.py): the summary lines, the parsed draft and the safety finding the film shows.
 */
export const PRODUCT_WORDS = words as {
  rx: { shorthand: string; medicine: string; how: Record<Lang, string> }[];
  visit: {
    notes: string;
    draft: { name: string; strengthMg: number | null; timesPerDay: number | null; timing: string | null }[];
    finding: { severity: string; detail: string };
  };
};
```

- [ ] **Step 6: Typecheck, run the whole agents suite, commit**

Run (in `web/`): `npm run typecheck`. Expected: no errors (`resolveJsonModule` is on in Next's tsconfig; if not, add `"resolveJsonModule": true`).
Run (in `services/agents/`): `.venv/Scripts/python.exe -m pytest -q`. Expected: all pass.
```bash
git add services/agents/scripts/make_film_words.py services/agents/tests/test_film_words.py web/components/film/product-words.json web/components/film/product-words.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Generate the film's product words from the agents code, and test they stay in step"
```

---

### Task 2: Act 1, lost in the paper

**Files:**
- Create: `web/components/film/acts/act1-paper.tsx`, `web/e2e/film-act1.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx` (insert after `<Act0Hero />`)

**Interfaces:**
- Consumes: `PRODUCT_WORDS.rx`, `useFilm()`, `htmlLang`, `Backdrop`.
- Produces: `Act1Paper()`, a `section#act1[data-anchor]` containing `[data-rx-line]` buttons with `aria-pressed` (true means the plain words are shown).

- [ ] **Step 1: Write the failing test** `web/e2e/film-act1.spec.ts`

```ts
import { expect, test } from "@playwright/test";

const lines = (page: import("@playwright/test").Page) => page.locator("#act1 [data-rx-line]");

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("every line shows both the shorthand and her words, from the product's summary", async ({ page }) => {
    await page.goto("/preview/film");
    await expect(lines(page)).toHaveCount(3);
    await expect(lines(page).first()).toContainText("Tab metformin 500mg bd pc");
    await expect(lines(page).first()).toContainText("1 biji, pagi dan malam, selepas makan.");
    await expect(lines(page).first()).toHaveAttribute("aria-pressed", "true");
  });
});

test("scrolling to the paper melts the shorthand into her words", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(lines(page).first()).toHaveAttribute("aria-pressed", "false");
  await page.locator("#act1").scrollIntoViewIfNeeded();
  await expect(lines(page).first()).toHaveAttribute("aria-pressed", "true", { timeout: 5000 });
});

test("a line clicked before it is reached keeps the reader's choice", async ({ page }) => {
  await page.goto("/preview/film");
  const third = lines(page).nth(2);
  await third.click(); // reveal it early
  await expect(third).toHaveAttribute("aria-pressed", "true");
  await third.click(); // back to shorthand, by choice
  await page.locator("#act1").scrollIntoViewIfNeeded();
  await page.waitForTimeout(800);
  await expect(third).toHaveAttribute("aria-pressed", "false");
});

test("the plain words follow the language chosen in the hero; the shorthand stays", async ({ page }) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文" }).click();
  await page.locator("#act1").scrollIntoViewIfNeeded();
  const first = lines(page).first();
  await expect(first).toContainText("每次1粒，早上和晚上，饭后服用。");
  await expect(first).toContainText("Tab metformin 500mg bd pc");
  await expect(first.locator("[lang='zh-Hans']")).toHaveCount(1);
});

test("the keyboard toggles a line with Enter", async ({ page }) => {
  await page.goto("/preview/film");
  await page.locator("#act1").scrollIntoViewIfNeeded();
  const second = lines(page).nth(1);
  await expect(second).toHaveAttribute("aria-pressed", "true", { timeout: 5000 });
  await second.focus();
  await page.keyboard.press("Enter");
  await expect(second).toHaveAttribute("aria-pressed", "false");
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npx playwright test e2e/film-act1.spec.ts`
Expected: FAIL (`#act1` not found).

- [ ] **Step 3: Add `web/components/film/acts/act1-paper.tsx`**

```tsx
"use client";

import { useRef, useState } from "react";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { htmlLang } from "../messages";
import { PRODUCT_WORDS } from "../product-words";
import styles from "../film.module.css";

const LINES = PRODUCT_WORDS.rx;

/**
 * Act 1: the prescription as the doctor writes it, melting into the words Khabar sends her. Each line
 * reveals itself when it scrolls into view; a line the reader has clicked keeps the reader's choice. The
 * server render (and reduced motion) is the end frame: every line shown in her words, shorthand above.
 */
export function Act1Paper() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  // null: not yet decided by the reader, so the scroll reveal may set it.
  const [chosen, setChosen] = useState<(boolean | null)[]>(() => LINES.map(() => null));
  const [reached, setReached] = useState<boolean[]>(() => LINES.map(() => false));

  useGSAP(
    () => {
      if (!motion) return;
      root.current!.querySelectorAll<HTMLElement>("[data-rx-line]").forEach((el, i) => {
        ScrollTrigger.create({
          trigger: el,
          start: "top 80%",
          once: true,
          onEnter: () => setReached((was) => was.map((r, j) => (j === i ? true : r))),
        });
      });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  const shown = (i: number) => chosen[i] ?? (!motion || reached[i]);

  return (
    <section ref={root} id="act1" data-anchor="" className={styles.act1} aria-labelledby="act1-title">
      <Backdrop act="act1" />
      <header className={styles.act1Head}>
        <p className={styles.eyebrow}>At home, after the visit</p>
        <h2 id="act1-title">
          Instructions written for clinicians.
          <br />
          <em>Patients who think in BM, 中文 or தமிழ்.</em>
        </h2>
        <p className={styles.lede}>
          Khabar turns each prescription line into her own language from fixed, reviewed phrases, never a
          chatbot&rsquo;s guess, so the dose she reads is the dose her doctor wrote.
        </p>
      </header>
      <div className={styles.paper} role="group" aria-label="Aminah’s prescription">
        {LINES.map((line, i) => (
          <button
            key={line.shorthand}
            type="button"
            className={styles.rxLine}
            data-rx-line={i}
            aria-pressed={shown(i)}
            onClick={() => setChosen((was) => was.map((c, j) => (j === i ? !shown(i) : c)))}
          >
            <code className={styles.rxShort}>{line.shorthand}</code>
            <span className={styles.rxPlain} lang={htmlLang(lang)}>
              {line.medicine}: {line.how[lang]}
            </span>
          </button>
        ))}
      </div>
      <p className={styles.rxHint}>Tap a line to switch between the doctor&rsquo;s shorthand and her words.</p>
    </section>
  );
}
```

Note: `useGSAP` reverts every ScrollTrigger created inside its context, so no cleanup needs returning.

- [ ] **Step 4: Append the Act 1 styles to `film.module.css`**

```css
/* ---------- Act 1: lost in the paper ---------- */
.act1 { position: relative; overflow: hidden; padding: clamp(72px, 12vh, 140px) clamp(16px, 6vw, 96px); }
.backdrop[data-act="act1"]:not(:has(picture)) { background: linear-gradient(180deg, #f6efe4 0%, #efe2cc 100%); }
.act1Head { position: relative; z-index: 2; max-width: 760px; }
.act1Head h2 { font-size: clamp(34px, 4.6vw, 60px); line-height: 1.02; margin: 0 0 18px; }
.paper {
  position: relative; z-index: 2; display: grid; gap: 14px; margin-top: 32px; max-width: 720px;
  padding: clamp(18px, 3vw, 32px); background: #fffdf7; border: 2px solid var(--kopi); border-radius: 18px;
  box-shadow: 0 30px 60px rgb(59 42 32 / 0.12); transform: rotate(-0.6deg);
}
.rxLine {
  display: grid; gap: 4px; text-align: left; width: 100%; padding: 12px 14px; border-radius: 12px;
  border: 1.5px dashed rgb(59 42 32 / 0.25); background: transparent; color: var(--kopi); cursor: pointer;
  font: inherit; overflow-wrap: anywhere;
}
.rxShort { font: 600 clamp(15px, 1.6vw, 18px) / 1.4 ui-monospace, "Cascadia Mono", Menlo, monospace; }
.rxPlain { font-size: clamp(16px, 1.7vw, 19px); line-height: 1.5; color: var(--pandan); font-weight: 600; }
.rxHint { position: relative; z-index: 2; color: var(--teh); font-size: 14px; margin: 14px 0 0; }
/* aria-pressed="false": the shorthand only. "true": her words, with the shorthand small above them. */
.rxLine[aria-pressed="false"] .rxPlain { opacity: 0; max-height: 0; }
.rxLine[aria-pressed="true"] .rxShort { opacity: 0.55; font-size: 13px; text-decoration: line-through; text-decoration-color: rgb(59 42 32 / 0.4); }
.film[data-motion="on"] .rxPlain, .film[data-motion="on"] .rxShort { transition: opacity 0.6s var(--ease-enter), font-size 0.4s var(--ease-enter), max-height 0.6s var(--ease-enter); }
.rxLine[aria-pressed="true"] .rxPlain { max-height: 6em; }
```

- [ ] **Step 5: Put Act 1 into the page** (`web/app/preview/film/page.tsx`)

Add the import `import { Act1Paper } from "@/components/film/acts/act1-paper";`, then render `<Act1Paper />` directly after `<Act0Hero />`.

- [ ] **Step 6: Run the tests to verify they pass, then the whole suite**

Run: `npx playwright test e2e/film-act1.spec.ts`. Expected: all pass.
Run: `npx playwright test`. Expected: everything green, including the axe, 320 px overflow and budget tests.

- [ ] **Step 7: Lint, typecheck, commit**

```bash
npm run lint && npm run typecheck
git add web/components/film web/app/preview/film/page.tsx web/e2e/film-act1.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add Act 1: the prescription melts from shorthand into her own language"
```

---

### Task 3: Act 2, the fifteen minutes

**Files:**
- Create: `web/components/film/acts/act2-visit.tsx`, `web/e2e/film-act2.spec.ts`
- Modify: `web/components/landing/landing-motion.tsx` (export `LandingMotionScope`), `film.module.css` (append), `page.tsx` (insert after `<Act1Paper />`)

**Interfaces:**
- Consumes: `PRODUCT_WORDS.visit`, `useFilm()`, `ProductPreview`.
- Produces:
  - `Act2Visit()`: a `section#act2[data-anchor][data-revealed="true"|"false"]` with `[data-draft-row]` rows and `[data-stamp]` (`role="status"`);
  - `LandingMotionScope({ enabled, children })`.

- [ ] **Step 1: Write the failing test** `web/e2e/film-act2.spec.ts`

```ts
import { expect, test } from "@playwright/test";

const FINDING = "Metformin 10000 mg a day is above the 3000 mg maximum.";

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("the notes, the draft and the blocked dose are all shown at once", async ({ page }) => {
    await page.goto("/preview/film");
    await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "true");
    await expect(page.locator("#act2 [data-draft-row]")).toHaveCount(2);
    await expect(page.locator("#act2 [data-stamp]")).toContainText(FINDING);
  });
});

test("reaching the desk writes the draft and stamps the planted dose error", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "false");
  await page.locator("#act2 [data-visit]").scrollIntoViewIfNeeded();
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "true", { timeout: 5000 });
  await expect(page.locator("#act2 [data-stamp]")).toContainText(FINDING);
  await expect(page.locator("#act2 [data-draft-row]").first()).toContainText("metformin");
});

test("pausing part-way shows everything, with no half-revealed state", async ({ page }) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator("#act2")).toHaveAttribute("data-revealed", "true");
});

test("the clinic and patient preview still switches views", async ({ page }) => {
  await page.goto("/preview/film");
  const preview = page.locator("#act2 [data-product-preview]");
  await preview.scrollIntoViewIfNeeded();
  await expect(preview.getByRole("button").first()).toBeVisible();
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npx playwright test e2e/film-act2.spec.ts`
Expected: FAIL (`#act2` not found).

- [ ] **Step 3: Export `LandingMotionScope` from `web/components/landing/landing-motion.tsx`**

Add, after the `LandingMotion` function:

```tsx
/** Lets a host page (the film) switch the landing components' motion on or off with its own setting. */
export function LandingMotionScope({ enabled, children }: { enabled: boolean; children: ReactNode }) {
  return (
    <MotionContext.Provider value={enabled}>
      <MotionConfig reducedMotion={enabled ? "user" : "always"}>{children}</MotionConfig>
    </MotionContext.Provider>
  );
}
```

- [ ] **Step 4: Add `web/components/film/acts/act2-visit.tsx`**

```tsx
"use client";

import { useRef, useState } from "react";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { ShieldAlert } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { PRODUCT_WORDS } from "../product-words";
import { LandingMotionScope } from "@/components/landing/landing-motion";
import { ProductPreview } from "@/components/landing/product-preview";
import styles from "../film.module.css";

const { notes, draft, finding } = PRODUCT_WORDS.visit;
const TIMING: Record<string, string> = { after_food: "after food", before_food: "before food" };

/**
 * Act 2: the doctor's notes become a structured draft, and the safety check blocks a slip of one zero. The
 * words are the agents' own output. Reaching the desk plays the reveal once; with motion off everything is
 * shown (the end frame). The existing clinic/patient preview follows.
 */
export function Act2Visit() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [reached, setReached] = useState(false);

  useGSAP(
    () => {
      if (!motion) return;
      ScrollTrigger.create({
        trigger: root.current!.querySelector("[data-visit]"),
        start: "top 70%",
        once: true,
        onEnter: () => setReached(true),
      });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  const revealed = !motion || reached;

  return (
    <section ref={root} id="act2" data-anchor="" data-revealed={revealed} className={styles.act2} aria-labelledby="act2-title">
      <Backdrop act="act2" />
      <header className={styles.act2Head}>
        <p className={styles.eyebrow}>The fifteen minutes</p>
        <h2 id="act2-title">
          Notes in, a safe draft out.
          <br />
          <em>The doctor stays in charge.</em>
        </h2>
        <p className={styles.lede}>
          The doctor writes the way they always do. Khabar structures the notes and checks every dose,
          allergy and interaction before the visit can be finalised.
        </p>
      </header>
      <div className={styles.visit} data-visit="">
        <figure className={styles.notesCard}>
          <figcaption>The doctor&rsquo;s notes</figcaption>
          <pre>{notes}</pre>
        </figure>
        <figure className={styles.draftCard}>
          <figcaption>Khabar&rsquo;s draft</figcaption>
          <table>
            <thead>
              <tr><th>Medicine</th><th>Strength</th><th>Times a day</th><th>Timing</th></tr>
            </thead>
            <tbody>
              {draft.map((row, i) => (
                <tr key={row.name} data-draft-row="" style={{ transitionDelay: `${0.25 + i * 0.2}s` }}>
                  <td>{row.name}</td>
                  <td>{row.strengthMg === null ? "—" : `${row.strengthMg} mg`}</td>
                  <td>{row.timesPerDay ?? "—"}</td>
                  <td>{row.timing ? TIMING[row.timing] : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </figure>
        <div className={styles.stamp} data-stamp="" role="status">
          <ShieldAlert size={22} aria-hidden />
          <div>
            <strong>Safety check · {finding.severity === "CRITICAL" ? "Blocked" : "Warning"}</strong>
            <p>{finding.detail}</p>
            <small>A slip of one zero. Finalising stays blocked until the doctor corrects it or records a reason.</small>
          </div>
        </div>
      </div>
      <div className={styles.act2Preview} data-product-preview="">
        <h3>Explore the app</h3>
        <p>Switch between the clinic and the patient view.</p>
        <LandingMotionScope enabled={motion}>
          <ProductPreview />
        </LandingMotionScope>
      </div>
    </section>
  );
}
```

- [ ] **Step 5: Append the Act 2 styles to `film.module.css`**

```css
/* ---------- Act 2: the fifteen minutes ---------- */
.act2 { position: relative; overflow: hidden; padding: clamp(72px, 12vh, 140px) clamp(16px, 6vw, 96px); }
.backdrop[data-act="act2"]:not(:has(picture)) { background: linear-gradient(180deg, #f3ebdf 0%, #e8f0ea 100%); }
.act2Head { position: relative; z-index: 2; max-width: 760px; }
.act2Head h2 { font-size: clamp(34px, 4.6vw, 60px); line-height: 1.02; margin: 0 0 18px; }
.visit { position: relative; z-index: 2; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: clamp(16px, 3vw, 32px); align-items: start; margin-top: 32px; }
.notesCard, .draftCard { margin: 0; background: #fffdf8; border: 2px solid var(--kopi); border-radius: 18px; padding: 16px 18px; min-width: 0; }
.notesCard figcaption, .draftCard figcaption { font-size: 12px; letter-spacing: 0.12em; text-transform: uppercase; color: var(--teh); font-weight: 700; margin-bottom: 10px; }
.notesCard pre { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font: 500 15px/1.7 ui-monospace, "Cascadia Mono", Menlo, monospace; }
.draftCard table { width: 100%; border-collapse: collapse; font-size: 15px; }
.draftCard th { text-align: left; font-size: 12px; color: var(--teh); font-weight: 700; padding: 6px 8px; border-bottom: 1px solid rgb(59 42 32 / 0.15); }
.draftCard td { padding: 8px; border-bottom: 1px solid rgb(59 42 32 / 0.08); }
.stamp {
  grid-column: 1 / -1; display: flex; gap: 12px; align-items: flex-start; padding: 16px 18px; border-radius: 16px;
  border: 2.5px solid #9e3320; background: #fff4f0; color: #5a1a0e; transform: rotate(-1.2deg);
}
.stamp strong { display: block; text-transform: uppercase; letter-spacing: 0.1em; font-size: 13px; }
.stamp p { margin: 4px 0; font-weight: 700; font-size: 17px; }
.stamp small { color: #6d2b1c; }
/* data-revealed="false" (motion on, not yet reached): rows and stamp wait, visible to screen readers. */
.act2[data-revealed="false"] [data-draft-row] { opacity: 0; }
.act2[data-revealed="false"] .stamp { opacity: 0; transform: rotate(-1.2deg) scale(1.3); }
.film[data-motion="on"] [data-draft-row] { transition: opacity 0.5s var(--ease-enter); }
.film[data-motion="on"] .stamp { transition: opacity 0.35s ease-out 0.9s, transform 0.45s cubic-bezier(0.3, 1.6, 0.5, 1) 0.9s; }
.act2Preview { position: relative; z-index: 2; margin-top: clamp(48px, 8vh, 96px); }
.act2Preview h3 { font-size: clamp(28px, 3.2vw, 40px); margin: 0 0 6px; }
.act2Preview > p { color: var(--teh); margin: 0 0 18px; }
@media (max-width: 760px) {
  .visit { grid-template-columns: minmax(0, 1fr); }
}
```

`data-draft-row` elements stay in the accessibility tree (opacity only).

- [ ] **Step 6: Put Act 2 into the page**

Import `Act2Visit`, and render it right after `<Act1Paper />`.

- [ ] **Step 7: Run the tests to verify they pass, then the whole suite**

Run: `npx playwright test e2e/film-act2.spec.ts`, then `npx playwright test`.
Expected: all pass. The CSS budget test now includes `landing.module.css` (about 7 KB gzipped). If it exceeds 45 KB, record the measured size and raise the budget with a ruling only if the fonts and JS still pass; otherwise extract the preview's styles.

- [ ] **Step 8: Lint, typecheck, commit**

```bash
npm run lint && npm run typecheck
git add web/components/film web/components/landing/landing-motion.tsx web/app/preview/film/page.tsx web/e2e/film-act2.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add Act 2: notes become a draft, and the safety check blocks a slip of one zero"
```

---

### Task 4: Act 4, her daughter in KL

**Files:**
- Create: `web/components/film/characters/nurul.tsx`, `web/components/film/acts/act4-daughter.tsx`, `web/e2e/film-act4.spec.ts`
- Modify: `film.module.css` (append), `page.tsx` (render after `<Act3ThirtyDays />`)

**Interfaces:**
- Consumes: `PRODUCT_WORDS.rx`, `useFilm()`, `Phone`, `Thread`, `htmlLang`, `Backdrop`.
- Produces: `Act4Daughter()`, a `section#act4[data-anchor]` with a `role="switch"` named "Aminah shares her care plan with Nurul", the `[data-nurul-phone]` content, and an `aria-live` `[data-consent-note]`.

- [ ] **Step 1: Write the failing test** `web/e2e/film-act4.spec.ts`

```ts
import { expect, test } from "@playwright/test";

test("Nurul sees the approved care plan while Aminah shares it", async ({ page }) => {
  await page.goto("/preview/film");
  const share = page.getByRole("switch", { name: "Aminah shares her care plan with Nurul" });
  await expect(share).toHaveAttribute("aria-checked", "true");
  await expect(page.locator("[data-nurul-phone]")).toContainText("metformin 500 mg: 1 biji, pagi dan malam, selepas makan.");
});

test("when Aminah stops sharing, Nurul's phone locks and never shows the plan", async ({ page }) => {
  await page.goto("/preview/film");
  const share = page.getByRole("switch", { name: "Aminah shares her care plan with Nurul" });
  await share.focus();
  await page.keyboard.press("Space");
  await expect(share).toHaveAttribute("aria-checked", "false");
  await expect(page.locator("[data-nurul-phone]")).not.toContainText("metformin");
  await expect(page.locator("[data-consent-note]")).toContainText("can no longer open");
  await page.keyboard.press("Enter");
  await expect(share).toHaveAttribute("aria-checked", "true");
});

test("the care plan on Nurul's phone follows the chosen language", async ({ page }) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "EN" }).click();
  await expect(page.locator("[data-nurul-phone]")).toContainText("1 tablet, morning and night, after food.");
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npx playwright test e2e/film-act4.spec.ts`
Expected: FAIL (no switch).

- [ ] **Step 3: Add `web/components/film/characters/nurul.tsx`**

```tsx
import styles from "../film.module.css";

/** Nurul, Aminah's fictional daughter in Kuala Lumpur, drawn in the same flat style as Aminah. */
export function Nurul() {
  return (
    <svg className={styles.nurul} viewBox="180 250 240 300" aria-hidden="true" focusable="false">
      <g data-part="body">
        <path d="M205 548 C 210 440, 390 440, 395 548 Z" fill="#e8a33d" />
        <path d="M244 336 C 244 262, 356 262, 356 336 L 366 438 C 330 462, 270 462, 234 438 Z" fill="#6b4f7a" />
        <ellipse cx="300" cy="336" rx="42" ry="48" fill="#8a5a3c" />
        <path d="M260 318 C 266 272, 334 272, 340 318 C 326 298, 274 298, 260 318z" fill="#6b4f7a" />
        <ellipse data-part="eye" cx="285" cy="340" rx="4" ry="5" fill="#2b1d16" />
        <ellipse data-part="eye" cx="315" cy="340" rx="4" ry="5" fill="#2b1d16" />
        <path d="M290 360 q10 7 20 0" fill="none" stroke="#2b1d16" strokeWidth="3" strokeLinecap="round" />
        <path d="M250 470 C 232 480, 240 506, 262 500" fill="none" stroke="#c98a2e" strokeWidth="20" strokeLinecap="round" />
        <rect x="256" y="440" width="34" height="56" rx="7" fill="#3b2a20" />
        <rect x="260" y="446" width="26" height="40" rx="3" fill="#bfe3cf" />
      </g>
    </svg>
  );
}
```

- [ ] **Step 4: Add `web/components/film/acts/act4-daughter.tsx`**

```tsx
"use client";

import { useRef, useState } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { LockKeyhole } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread } from "../thread";
import { Phone } from "../characters/phone";
import { Nurul } from "../characters/nurul";
import { htmlLang } from "../messages";
import { PRODUCT_WORDS } from "../product-words";
import { ease } from "../tokens";
import styles from "../film.module.css";

/**
 * Act 4: the thread reaches Nurul in KL. What she sees depends on Aminah's consent: the approved care plan
 * (a caregiver with summary access sees the approved summary), or nothing once Aminah stops sharing.
 */
export function Act4Daughter() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [shared, setShared] = useState(true);

  useGSAP(
    () => {
      if (!motion) return;
      gsap.fromTo(
        "[data-thread='to-nurul'] [data-draw]",
        { strokeDashoffset: 1 },
        {
          strokeDashoffset: 0,
          autoRound: false,
          ease: ease.scrub,
          scrollTrigger: { trigger: root.current, start: "top 75%", end: "top 20%", scrub: 0.6 },
        },
      );
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section ref={root} id="act4" data-anchor="" className={styles.act4} aria-labelledby="act4-title">
      <Backdrop act="act4" />
      <Thread name="to-nurul" d="M 0 60 C 300 10, 700 110, 1000 60" viewBox="0 0 1000 120" state="ok" fit="cover" />
      <header className={styles.act4Head}>
        <p className={styles.eyebrow}>Her daughter in Kuala Lumpur</p>
        <h2 id="act4-title">
          Family sees what she
          <br />
          <em>chooses to share.</em>
        </h2>
      </header>
      <div className={styles.kl}>
        <div className={styles.nurulFigure}>
          <Nurul />
        </div>
        <div data-nurul-phone="">
          <Phone label="Nurul’s phone">
            {shared ? (
              <div className={styles.plan} lang={htmlLang(lang)}>
                <strong lang="en">Mak&rsquo;s care plan</strong>
                {PRODUCT_WORDS.rx.map((line) => (
                  <p key={line.shorthand}>{line.medicine}: {line.how[lang]}</p>
                ))}
              </div>
            ) : (
              <div className={styles.locked}>
                <LockKeyhole size={22} aria-hidden />
                <p>This care plan is no longer shared with you.</p>
              </div>
            )}
          </Phone>
        </div>
        <div className={styles.consent}>
          <button
            type="button"
            role="switch"
            aria-checked={shared}
            className={styles.switch}
            onClick={() => setShared((was) => !was)}
          >
            <span className={styles.switchTrack} aria-hidden="true"><span /></span>
            Aminah shares her care plan with Nurul
          </button>
          <p data-consent-note="" aria-live="polite">
            {shared
              ? "Nurul sees the care plan Aminah’s doctor approved. Aminah can stop sharing at any time."
              : "Aminah stopped sharing. Nurul can no longer open her care plan."}
          </p>
        </div>
      </div>
    </section>
  );
}
```

`role="switch"` on a `<button>` responds to Space and Enter by default, because both keys click a button.

- [ ] **Step 5: Append the Act 4 styles to `film.module.css`**

```css
/* ---------- Act 4: her daughter in KL ---------- */
.act4 { position: relative; overflow: hidden; padding: clamp(72px, 12vh, 140px) clamp(16px, 6vw, 96px); background: var(--dusk); color: #f6efe4; }
.backdrop[data-act="act4"]:not(:has(picture)) { background: radial-gradient(60% 50% at 80% 20%, #3a4a78 0%, transparent 70%), linear-gradient(180deg, #1e2a44 0%, #2b2447 100%); }
.act4 .eyebrow { color: #9fd9bd; }
.act4Head { position: relative; z-index: 2; max-width: 760px; }
.act4Head h2 { font-size: clamp(34px, 4.6vw, 60px); line-height: 1.02; margin: 0 0 18px; }
.act4Head h2 em { color: #9fd9bd; }
.thread[data-thread="to-nurul"] { top: 30%; height: 18%; }
.thread[data-thread="to-nurul"] [data-draw] { stroke-dasharray: 1; }
.kl { position: relative; z-index: 2; display: grid; grid-template-columns: 180px minmax(0, 360px) minmax(0, 1fr); gap: clamp(16px, 3vw, 40px); align-items: end; margin-top: 32px; }
.nurul { display: block; width: 100%; height: auto; }
.film[data-motion="on"] .nurul [data-part="eye"] { transform-box: fill-box; transform-origin: center; animation: blink 5.2s infinite; }
.act4 .phone { color: var(--kopi); }
.plan { display: grid; gap: 8px; font-size: 15px; line-height: 1.45; }
.plan p { margin: 0; overflow-wrap: anywhere; }
.locked { display: grid; justify-items: center; gap: 8px; padding: 28px 8px; text-align: center; color: #6d2b1c; }
.consent { display: grid; gap: 12px; align-self: center; }
.consent p { margin: 0; color: #e7dccb; line-height: 1.55; max-width: 44ch; }
.switch { display: inline-flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 14px; border: 1.5px solid #f6efe4; background: transparent; color: #f6efe4; font: 600 16px/1.3 inherit; cursor: pointer; text-align: left; }
.switchTrack { flex: none; width: 46px; height: 26px; border-radius: 99px; background: #6b6f86; position: relative; }
.switchTrack span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: #fff; }
.switch[aria-checked="true"] .switchTrack { background: var(--pandan); }
.switch[aria-checked="true"] .switchTrack span { transform: translateX(20px); }
.film[data-motion="on"] .switchTrack, .film[data-motion="on"] .switchTrack span { transition: transform 0.25s var(--ease-enter), background 0.25s; }
.film .act4 :where(a, button):focus-visible { outline-color: #f6efe4; }
@media (max-width: 760px) {
  .kl { grid-template-columns: minmax(0, 1fr); }
  .nurulFigure { width: 120px; }
}
```

The focus ring on the dark night background is santan (#F6EFE4 on #1E2A44 is about 12:1), not kopi.

- [ ] **Step 6: Put Act 4 into the page**

Import `Act4Daughter`, and render it after `<Act3ThirtyDays />`.

- [ ] **Step 7: Run the tests, then the whole suite**

Run: `npx playwright test e2e/film-act4.spec.ts`, then `npx playwright test`.
Expected: all pass. The existing axe tests now cover Act 4's dark scene, and contrast must pass.

- [ ] **Step 8: Lint, typecheck, commit**

```bash
npm run lint && npm run typecheck
git add web/components/film web/app/preview/film/page.tsx web/e2e/film-act4.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add Act 4: the thread reaches her daughter, who sees only what Aminah shares"
```

---

### Task 5: Page flow: skip links and pausing inside the new acts

**Files:**
- Modify: `web/e2e/film-page.spec.ts` (append)

**Interfaces:**
- Consumes: the `data-anchor` sections from Tasks 2–4 and the provider's `bringBack`.

- [ ] **Step 1: Write the test**

```ts
test("pausing inside Act 2 keeps the reader on Act 2", async ({ page }) => {
  await page.goto("/preview/film");
  await page.locator("#act2 [data-visit]").scrollIntoViewIfNeeded();
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator("#act2 [data-visit]")).toBeInViewport();
});

test("the acts run in story order: hero, paper, visit, thirty days, daughter", async ({ page }) => {
  await page.goto("/preview/film");
  const order = await page.evaluate(() => [...document.querySelectorAll("main > section, main > div > section")].map((s) => s.id));
  expect(order.filter((id) => id.startsWith("act"))).toEqual(["act0", "act1", "act2", "act3", "act4"]);
});
```

- [ ] **Step 2: Run it**

Run: `npx playwright test e2e/film-page.spec.ts -g "Act 2|story order"`
Expected: pass. Tasks 2–4 already added `data-anchor` and the order. If the Act 2 test fails, check that `#act2` has `data-anchor` and is the largest anchor on screen, then fix the markup, not the test.

- [ ] **Step 3: Commit**

```bash
git add web/e2e/film-page.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Test the film's story order and pausing inside the new acts"
```

---

### Task 6: Measure, review pack and PR

**Files:**
- Modify: `docs/LANDING_FILM_SLICE_REVIEW.md` (add a "Phase 2a" section)

- [ ] **Step 1: Full checks**

Run (in `web/`): `npx playwright test && npm run test:unit && npm run lint && npm run typecheck`
Run (in `services/agents/`): `.venv/Scripts/python.exe -m pytest -q`
Expected: all green.

- [ ] **Step 2: Lighthouse (mobile, three runs) on the production build**, as in the slice's Task 7 Step 2.
Expected: the score stays at or above 90, CLS stays at or below 0.05, and simulated LCP is unchanged (about 2.9 s), because the new acts are below the fold. Record the numbers.

- [ ] **Step 3: Screenshots of each new act** at 1440×900, 390×844 and with reduced motion. Look at every one: no overlap, nothing cut off, and the dark Act 4 readable.

- [ ] **Step 4: Add a "Phase 2a" section to `docs/LANDING_FILM_SLICE_REVIEW.md`**
  - what was added;
  - where the product words come from, and the test that keeps them in step;
  - the measured numbers;
  - the screenshots;
  - the questions for the owner.

- [ ] **Step 5: Final whole-branch review** by a fresh reviewer, fix Critical and Important findings with failing-test-first, then commit, push `feat/landing-film-phase-2a`, and open a PR for the owner to merge.
