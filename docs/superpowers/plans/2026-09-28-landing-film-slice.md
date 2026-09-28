# Landing film: vertical slice (phase 1) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the storybook film's hero (Act 0) and "thirty days at home" (Act 3) to final quality at `/preview/film`, leaving `/` untouched, so the owner can approve the direction before the remaining acts are built.

**Architecture:**
- **Engine:** the acts are client components under `web/components/film/`, animated by GSAP ScrollTrigger inside `useGSAP`.
- **Shared state:** one `FilmProvider` holds motion (device setting plus the pause button) and the greeting language.
- **Static-first rendering:** every act renders its static end frame in HTML. Timelines are added only when motion is allowed, and are reverted when it is not.
- **Backgrounds:** a manifest decides whether each act shows AI art or a placeholder gradient.

**Tech Stack:** Next.js 16 (app router), React 19, TypeScript, CSS Modules, `gsap` 3.15 with `@gsap/react` 2.1, `next/font/google`. Dev only: `@playwright/test`, `@axe-core/playwright`, `sharp`.

**Spec:** `docs/superpowers/specs/2026-09-28-landing-storybook-film-design.md` (§10 phase 1). Phases 2 and 3 get their own plan after the owner's review gate.

## Global Constraints

- Native scrolling only: no scroll-jacking, no smooth-scroll library, no `ScrollSmoother`.
- Animate only `transform`, `opacity`, `filter` and `stroke-dashoffset`; never layout properties.
- Reduced motion, or "Pause motion" pressed: no pins, no timelines, every act shows its end frame, no content lost.
- The server render is the end frame, so the page works with no JavaScript and shows no layout jump in the hero.
- Patient-facing words come from `PatientMessages.java` (ms / en / zh / ta), copied exactly.
- Never promise a callback. Say "goes onto / to the top of the clinic's follow-up list".
- No statistics without a cited source. Every person is fictional.
- All copy is real HTML text.
  - Characters and art are `aria-hidden`.
  - Every interaction is a `<button>` usable by keyboard, with a visible focus ring.
- `/preview/film` has `robots: noindex, nofollow` and is not linked from anywhere.
- Budgets (spec §8):
  - LCP ≤ 2.5 s (mobile Lighthouse);
  - Lighthouse performance ≥ 90;
  - CLS ≤ 0.05;
  - AVIF ≤ 180 KB desktop and ≤ 110 KB mobile;
  - JS added over `/` ≤ 70 KB gzipped.
- Commits: `git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit`, with no co-author trailer.
- Work on branch `feat/landing-storybook-film`. Run every `npm` command from `web/`.

## Review Focus

1. **Arriving mid-page:** a visitor arrives by `#act3` or reloads mid-page. Pins must measure correctly, and Act 3 must show Day 1, not a blank pinned area. Tested in Task 4.
2. **Resizing mid-scroll:** the window is resized or the phone rotated after load. The pan must still end exactly on Day 30 (`invalidateOnRefresh`). Tested in Task 4.
3. **Pausing inside a pin:** "Pause motion" is pressed while scrolled inside the pinned pan. The pin must be removed, the cards must stack, and no blank gap may be left. Tested in Task 5.
4. **Tamil at 320 px:** the longest strings, in chips and bubbles, must cause no horizontal scroll. Tested in Task 5.
5. **Keyboard only:** tabbing must reach the language chips and the reply buttons, each focused control must be in the viewport, and Enter or Space must work. Tested in Tasks 3 and 4.

---

## File map

| File | Responsibility |
|---|---|
| `web/playwright.config.ts` | e2e runner: desktop 1440×900 and phone 390×844 projects against `next start` on port 3100 |
| `web/e2e/*.spec.ts` | Browser tests for the film |
| `web/app/preview/film/page.tsx` | The preview route: composes the provider, header, acts and footer; `noindex` |
| `web/components/film/film-provider.tsx` | GSAP registration; `useFilm()` → `{ motion, lang, setLang }`; the Pause motion button |
| `web/components/film/tokens.ts` | Easing, durations, media queries shared by every act |
| `web/components/film/fonts.ts` | Fraunces, Plus Jakarta Sans, Noto Sans SC / Tamil (loaded only for those glyphs) |
| `web/components/film/messages.ts` | Patient-facing strings (from `PatientMessages.java`), language list, replies, `answer()` |
| `web/components/film/backdrops.ts` | Generated manifest: which acts have AI art (`ActId`, `BACKDROPS`) |
| `web/components/film/backdrop.tsx` | `<picture>` for AI art, or the act's placeholder gradient |
| `web/components/film/thread.tsx` | The thread of light (states ok / watch / red) |
| `web/components/film/characters/aminah.tsx` | Code-drawn Aminah with blink / breathe parts |
| `web/components/film/characters/phone.tsx` | Phone frame and chat bubble (`Phone`, `Bubble`) |
| `web/components/film/acts/act0-hero.tsx` | Act 0 |
| `web/components/film/acts/act3-thirty-days.tsx` | Act 3: the day pan and "Reply for Aminah" |
| `web/components/film/film.module.css` | All film styles |
| `web/scripts/film-assets.mjs` (+ `.test.mjs`) | Turns the owner's AI art into budgeted AVIF / WebP and updates `backdrops.ts` |
| `docs/LANDING_FILM_SLICE_REVIEW.md` | The review pack for the owner's gate |

---

### Task 1: Test harness and the preview route

**Files:**
- Modify: `web/package.json` (scripts, devDependencies), `web/.gitignore`
- Create: `web/playwright.config.ts`, `web/e2e/film-route.spec.ts`, `web/app/preview/film/page.tsx`

**Interfaces:**
- Produces: route `/preview/film`; `npm run test:e2e`; Playwright projects `desktop` and `phone`.

- [ ] **Step 1: Install the test tools**

Run (in `web/`):
```bash
npm install --save-dev @playwright/test@1.63.0 @axe-core/playwright@4.13.0
npx playwright install chromium
```

Add to `web/package.json` `"scripts"`:
```json
    "test:e2e": "playwright test"
```

Append to `web/.gitignore`:
```
/test-results/
/playwright-report/
/blob-report/
```

- [ ] **Step 2: Add `web/playwright.config.ts`**

```ts
import { defineConfig, devices } from "@playwright/test";

// Runs against a production build, because scroll timing and bundle size differ in dev.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  reporter: [["list"]],
  use: { baseURL: "http://localhost:3100", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } },
    { name: "phone", use: { ...devices["Pixel 7"], viewport: { width: 390, height: 844 } } },
  ],
  webServer: {
    command: "npm run build && npx next start -p 3100",
    url: "http://localhost:3100/preview/film",
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
  },
});
```

- [ ] **Step 3: Write the failing test** `web/e2e/film-route.spec.ts`

```ts
import { expect, test } from "@playwright/test";

test("the film preview is served and kept out of search engines", async ({ page }) => {
  const response = await page.goto("/preview/film");
  expect(response?.status()).toBe(200);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/The visit ends\.\s*Care should not\./);
});

test("the current landing page is untouched", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/The visit ends\.\s*Care should not\./);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm run test:e2e -- e2e/film-route.spec.ts`
Expected: FAIL. The first test gets status 404 for `/preview/film`.

- [ ] **Step 5: Add `web/app/preview/film/page.tsx`**

```tsx
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

// Replaced act by act in the following tasks; the heading is the hero's.
export default function FilmPreviewPage() {
  return (
    <main id="main-content">
      <h1>
        The visit ends.
        <br />
        <em>Care should not.</em>
      </h1>
    </main>
  );
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `npm run test:e2e -- e2e/film-route.spec.ts`
Expected: 4 passed (2 tests × 2 projects).

- [ ] **Step 7: Commit**

```bash
git add web/package.json web/package-lock.json web/.gitignore web/playwright.config.ts web/e2e/film-route.spec.ts web/app/preview/film/page.tsx
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add the film preview route and a browser test harness"
```

---

### Task 2: Film provider, tokens, fonts, messages and the Pause motion button

**Files:**
- Create:
  - `web/components/film/film-provider.tsx`, `web/components/film/tokens.ts`, `web/components/film/fonts.ts`, `web/components/film/messages.ts`;
  - `web/components/film/film.module.css` (base section);
  - `web/e2e/film-motion.spec.ts`.
- Modify: `web/package.json` (gsap), `web/app/preview/film/page.tsx`

**Interfaces:**
- Produces:
  - `FilmProvider({ children, className })` and `useFilm(): { motion: boolean; lang: Lang; setLang(lang: Lang): void }`;
  - `ease`, `duration`, `media` from `tokens.ts`;
  - `fontVariables: string`;
  - from `messages.ts`: `Lang`, `LANGS`, `GREETING`, `CHECK_IN`, `Reply`, `REPLY_TEXT`, `MEDICINE_TAKEN`, `FEELING_BETTER`, `THANK_YOU`, `answer(reply, lang)`, `htmlLang(lang)`;
  - root element `div[data-motion="on"|"off"]` and the button named "Pause motion" with `aria-pressed`.

- [ ] **Step 1: Install GSAP**

Run: `npm install gsap@3.15.0 @gsap/react@2.1.2`

- [ ] **Step 2: Write the failing test** `web/e2e/film-motion.spec.ts`

```ts
import { expect, test } from "@playwright/test";

test("Pause motion stops the film and can resume it", async ({ page }) => {
  await page.goto("/preview/film");
  const root = page.locator("[data-motion]");
  const pause = page.getByRole("button", { name: "Pause motion" });
  await expect(root).toHaveAttribute("data-motion", "on");
  await expect(pause).toHaveAttribute("aria-pressed", "false");
  await pause.click();
  await expect(root).toHaveAttribute("data-motion", "off");
  await expect(pause).toHaveAttribute("aria-pressed", "true");
  await pause.click();
  await expect(root).toHaveAttribute("data-motion", "on");
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the device setting keeps motion off and explains why", async ({ page }) => {
    await page.goto("/preview/film");
    await expect(page.locator("[data-motion]")).toHaveAttribute("data-motion", "off");
    const pause = page.getByRole("button", { name: "Pause motion" });
    await expect(pause).toBeDisabled();
    await expect(pause).toContainText("Reduced motion is on");
  });
});

test("without JavaScript the page still shows its words", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto("/preview/film");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.locator("[data-motion]")).toHaveAttribute("data-motion", "off");
  await context.close();
});
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npm run test:e2e -- e2e/film-motion.spec.ts`
Expected: FAIL (`[data-motion]` not found).

- [ ] **Step 4: Add `web/components/film/tokens.ts`**

```ts
/** Motion values shared by every act, so the whole film moves with one rhythm. */
export const ease = {
  /** Scroll-scrubbed moves: linear progress feels like a camera on rails. */
  scrub: "none",
  /** Things arriving: quick start, long settle. */
  enter: "power3.out",
  /** Bubbles and taps: a small overshoot. */
  pop: "back.out(1.7)",
} as const;

export const duration = { tap: 0.25, enter: 0.7, pop: 0.5 } as const;

/** gsap.matchMedia conditions; the 760 px split matches film.module.css. */
export const media = {
  desktop: "(min-width: 761px)",
  mobile: "(max-width: 760px)",
} as const;

/** Seconds of catch-up smoothing on scrubbed timelines; small enough to feel attached to the finger. */
export const SCRUB = 0.6;
```

- [ ] **Step 5: Add `web/components/film/fonts.ts`**

```ts
import { Fraunces, Noto_Sans_SC, Noto_Sans_Tamil, Plus_Jakarta_Sans } from "next/font/google";

// Self-hosted by next/font. The Chinese and Tamil faces are not preloaded: the browser only downloads
// the glyph ranges a page actually shows, so visitors who never switch language never load them.
const display = Fraunces({ subsets: ["latin"], style: ["normal", "italic"], variable: "--film-display", display: "swap" });
const body = Plus_Jakarta_Sans({ subsets: ["latin"], variable: "--film-body", display: "swap" });
const chinese = Noto_Sans_SC({ weight: ["400", "600"], preload: false, variable: "--film-zh", display: "swap" });
const tamil = Noto_Sans_Tamil({ subsets: ["tamil"], weight: ["400", "600"], preload: false, variable: "--film-ta", display: "swap" });

export const fontVariables = [display.variable, body.variable, chinese.variable, tamil.variable].join(" ");
```

- [ ] **Step 6: Add `web/components/film/messages.ts`**

```ts
/**
 * Patient-facing words on the landing page. The check-in and answers are copied from
 * services/api/.../messaging/PatientMessages.java, so the page shows exactly what the product sends;
 * change both together. zh and ta await fluent-reader review, as in the app.
 */
export type Lang = "ms" | "en" | "zh" | "ta";

export const LANGS: { id: Lang; label: string }[] = [
  { id: "ms", label: "BM" },
  { id: "zh", label: "中文" },
  { id: "ta", label: "தமிழ்" },
  { id: "en", label: "EN" },
];

const HTML_LANG: Record<Lang, string> = { ms: "ms", en: "en", zh: "zh-Hans", ta: "ta" };
export const htmlLang = (lang: Lang) => HTML_LANG[lang];

/** The hero's short greeting (page copy, not a product message). */
export const GREETING: Record<Lang, string> = {
  ms: "Apa khabar, Mak Cik?",
  en: "How are you today?",
  zh: "阿姨，今天好吗？",
  ta: "நலமா, அம்மா?",
};

/** PatientMessages.CHECK_IN */
export const CHECK_IN: Record<Lang, string> = {
  ms: "Apa khabar hari ini? Dah makan ubat? Balas mesej ini untuk beritahu klinik.",
  en: "How are you today? Have you taken your medicine? Reply to this message to let the clinic know.",
  zh: "今天感觉怎么样？吃药了吗？回复这条信息告诉诊所。",
  ta: "இன்று எப்படி இருக்கிறீர்கள்? மருந்து சாப்பிட்டீர்களா? இந்த செய்திக்கு பதில் அனுப்பி கிளினிக்கிற்கு தெரியப்படுத்துங்கள்.",
};

export type Reply = "ok" | "dizzy" | "chest";

/** What Aminah types (fictional replies). */
export const REPLY_TEXT: Record<Reply, Record<Lang, string>> = {
  ok: { ms: "Okay, sihat.", en: "I'm okay.", zh: "我很好。", ta: "நான் நலமாக இருக்கிறேன்." },
  dizzy: { ms: "Pening sikit hari ini.", en: "A bit dizzy today.", zh: "今天有点头晕。", ta: "இன்று கொஞ்சம் தலைச்சுற்றல்." },
  chest: { ms: "Sakit dada.", en: "Chest pain.", zh: "胸口痛。", ta: "நெஞ்சு வலி." },
};

export const MEDICINE_TAKEN: Record<Lang, string> = {
  ms: "Dah makan ubat.", en: "I've taken my medicine.", zh: "药已经吃了。", ta: "மருந்து சாப்பிட்டேன்.",
};
export const FEELING_BETTER: Record<Lang, string> = {
  ms: "Dah okay, terima kasih.", en: "Better now, thank you.", zh: "好多了，谢谢。", ta: "இப்போது பரவாயில்லை, நன்றி.",
};
export const THANK_YOU: Record<Lang, string> = {
  ms: "Terima kasih, Khabar.", en: "Thank you, Khabar.", zh: "谢谢你，Khabar。", ta: "நன்றி, Khabar.",
};

/** PatientMessages.THANKS */
const THANKS: Record<Lang, string> = {
  ms: "Terima kasih kerana memberitahu. Jaga diri!",
  en: "Thanks for letting us know. Take care!",
  zh: "谢谢您告诉我们。请保重！",
  ta: "தெரிவித்ததற்கு நன்றி. உடல்நலத்தைக் கவனித்துக் கொள்ளுங்கள்!",
};
/** PatientMessages.WAITING_FOR_REVIEW */
const WAITING: Record<Lang, string> = {
  ms: "Terima kasih. Mesej anda ada dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya.",
  en: "Thank you. Your message is in the clinic's follow-up list, but the clinic may not have seen it yet.",
  zh: "谢谢。您的消息已加入诊所的随访列表，但诊所可能还没有看到。",
  ta: "நன்றி. உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம்.",
};
/** PatientMessages.EMERGENCY_ADVICE */
const EMERGENCY: Record<Lang, string> = {
  ms: "Kalau sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
  en: "If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
  zh: "如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
  ta: "நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.",
};
/** PatientMessages.URGENT */
const URGENT: Record<Lang, string> = {
  ms: "Mesej anda telah dimasukkan dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya. Jika sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
  en: "Your message has been added to the clinic's follow-up list, but the clinic may not have seen it yet. If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
  zh: "您的消息已加入诊所的随访列表，但诊所可能还没有看到。如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
  ta: "உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம். நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.",
};

/** Khabar's answer, as PatientMessages.acknowledgementText (OK, WATCH) and urgentText (RED) give it. */
export function answer(reply: Reply, lang: Lang): string {
  if (reply === "ok") return THANKS[lang];
  if (reply === "dizzy") return `${WAITING[lang]} ${EMERGENCY[lang]}`;
  return URGENT[lang];
}
```

- [ ] **Step 7: Add `web/components/film/film-provider.tsx`**

```tsx
"use client";

import { createContext, useContext, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { Pause, Play } from "lucide-react";
import type { Lang } from "./messages";
import styles from "./film.module.css";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger, useGSAP);
}

type Film = {
  /** True when scenes may animate: the device allows motion and the visitor has not paused it. */
  motion: boolean;
  lang: Lang;
  setLang: (lang: Lang) => void;
};

const FilmContext = createContext<Film>({ motion: false, lang: "ms", setLang: () => {} });
export const useFilm = () => useContext(FilmContext);

const REDUCED = "(prefers-reduced-motion: reduce)";
function subscribe(onChange: () => void) {
  const list = window.matchMedia(REDUCED);
  list.addEventListener("change", onChange);
  return () => list.removeEventListener("change", onChange);
}

export function FilmProvider({ children, className }: { children: ReactNode; className?: string }) {
  // The server cannot see the device setting, so it renders every act's static end frame (motion off);
  // the scenes switch to their animated layout once the browser confirms motion is allowed.
  const reduced = useSyncExternalStore(subscribe, () => window.matchMedia(REDUCED).matches, () => true);
  const [paused, setPaused] = useState(false);
  const [lang, setLang] = useState<Lang>("ms");
  const motion = !reduced && !paused;
  const value = useMemo(() => ({ motion, lang, setLang }), [motion, lang]);

  return (
    <FilmContext.Provider value={value}>
      <div className={`${styles.film} ${className ?? ""}`} data-motion={motion ? "on" : "off"}>
        {children}
        <button
          type="button"
          className={styles.motionToggle}
          onClick={() => setPaused((was) => !was)}
          disabled={reduced}
          aria-pressed={!motion}
        >
          {motion ? <Pause size={13} aria-hidden /> : <Play size={13} aria-hidden />}
          <span>Pause motion</span>
          {reduced ? <small>Reduced motion is on</small> : null}
        </button>
      </div>
    </FilmContext.Provider>
  );
}
```

- [ ] **Step 8: Start `web/components/film/film.module.css` with the base section**

```css
/* ---------- Base: palette, type, shared controls ---------- */
.film {
  --santan: #f6efe4;
  --kopi: #3b2a20;
  --teh: #7a5a44; /* darkened from #B08968 for 4.5:1 text contrast on santan */
  --pandan: #2f6b4f;
  --pandan-glow: #7fd1a8;
  --amber: #e8a33d;
  --cili: #c8452d;
  --dusk: #1e2a44;
  --ease-enter: cubic-bezier(0.22, 1, 0.36, 1);
  position: relative;
  background: var(--santan);
  color: var(--kopi);
  font-family: var(--film-body), var(--film-zh), var(--film-ta), system-ui, sans-serif;
  overflow-x: clip;
}
.film :where(h1, h2, h3) { font-family: var(--film-display), Georgia, serif; font-weight: 400; letter-spacing: -0.015em; }
.film :where(h1, h2) em { font-style: italic; color: var(--pandan); }
.film [lang|="zh"] { font-family: var(--film-zh), var(--film-body), sans-serif; }
.film [lang="ta"] { font-family: var(--film-ta), var(--film-body), sans-serif; }
.film :where(a, button):focus-visible { outline: 3px solid var(--amber); outline-offset: 3px; border-radius: 10px; }

.srOnly {
  position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}

.motionToggle {
  position: fixed; right: 16px; bottom: 16px; z-index: 50;
  display: inline-flex; align-items: center; gap: 6px;
  border: 1px solid rgb(59 42 32 / 0.18); border-radius: 999px; padding: 8px 14px;
  background: rgb(246 239 228 / 0.9); color: var(--kopi); backdrop-filter: blur(10px);
  font: 600 12px/1 var(--film-body), sans-serif; cursor: pointer;
}
.motionToggle[aria-pressed="true"] { background: var(--kopi); color: var(--santan); }
.motionToggle:disabled { cursor: default; opacity: 0.85; }
.motionToggle small { font-weight: 500; margin-left: 4px; }
```

- [ ] **Step 9: Wrap the preview page in the provider** (replace `web/app/preview/film/page.tsx`)

```tsx
import type { Metadata } from "next";
import { FilmProvider } from "@/components/film/film-provider";
import { fontVariables } from "@/components/film/fonts";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

export default function FilmPreviewPage() {
  return (
    <FilmProvider className={fontVariables}>
      <main id="main-content">
        <h1>
          The visit ends.
          <br />
          <em>Care should not.</em>
        </h1>
      </main>
    </FilmProvider>
  );
}
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `npm run test:e2e -- e2e/film-motion.spec.ts e2e/film-route.spec.ts`
Expected: all pass (10 = 5 tests × 2 projects).

- [ ] **Step 11: Lint, typecheck, commit**

Run: `npm run lint && npm run typecheck`
Expected: no errors.
```bash
git add web/package.json web/package-lock.json web/components/film web/app/preview/film/page.tsx web/e2e/film-motion.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add the film provider: motion switch, greeting language, fonts and patient words"
```

---

### Task 3: Backdrop, thread, Aminah and Act 0 (the hero)

**Files:**
- Create:
  - `web/components/film/backdrops.ts`, `web/components/film/backdrop.tsx`, `web/components/film/thread.tsx`;
  - `web/components/film/characters/aminah.tsx`, `web/components/film/characters/phone.tsx`;
  - `web/components/film/acts/act0-hero.tsx`;
  - `web/e2e/film-hero.spec.ts`.
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`

**Interfaces:**
- Consumes: `useFilm()`, `ease`, `media`, `SCRUB`, `GREETING`, `LANGS`, `htmlLang`, `Lang` (Task 2).
- Produces:
  - `ActId`, `BACKDROPS`;
  - `Backdrop({ act, priority? })` rendering `[data-layer="backdrop"][data-act]`;
  - `Thread({ d, viewBox, state, name, pulse?, svgRef? })` rendering `svg[data-thread=name][data-state]` with `path[data-draw]`;
  - `type ThreadState = "ok" | "watch" | "red"`;
  - `Aminah()`;
  - `Phone({ label, children })` and `Bubble({ from, lang, tone?, isNew?, className?, children })`;
  - `Act0Hero()`, a `section#act0`.

- [ ] **Step 1: Write the failing test** `web/e2e/film-hero.spec.ts`

```ts
import { expect, test } from "@playwright/test";

const dashOffset = (page: import("@playwright/test").Page) =>
  page.locator('[data-thread="hero"] [data-draw]').first()
    .evaluate((el) => parseFloat(getComputedStyle(el).strokeDashoffset));

test("the hero offers the four greeting languages, BM first", async ({ page }) => {
  await page.goto("/preview/film");
  const chips = page.getByRole("group", { name: "Khabar speaks her language" }).getByRole("button");
  await expect(chips).toHaveText(["BM", "中文", "தமிழ்", "EN"]);
  await expect(chips.first()).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#act0 [data-from='khabar']")).toContainText("Apa khabar, Mak Cik?");
});

test("choosing a language changes the greeting, by mouse or keyboard", async ({ page }) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文" }).click();
  await expect(page.locator("#act0 [data-from='khabar']")).toContainText("阿姨，今天好吗？");
  const tamil = page.getByRole("button", { name: "தமிழ்" });
  await tamil.focus();
  await expect(tamil).toBeInViewport();
  await page.keyboard.press("Enter");
  await expect(tamil).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#act0 [data-from='khabar']")).toHaveAttribute("lang", "ta");
});

test("scrolling pins the hero and draws the thread after Aminah", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page.locator("#act0").locator("xpath=..")).toHaveClass(/pin-spacer/);
  await expect.poll(() => dashOffset(page)).toBeGreaterThan(0.5);
  await page.evaluate(() => window.scrollBy(0, window.innerHeight));
  await expect.poll(() => dashOffset(page), { timeout: 5000 }).toBeLessThan(0.35);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });

  test("the hero is its still frame: no pin, headline and Aminah in view", async ({ page }) => {
    await page.goto("/preview/film");
    await expect(page.locator(".pin-spacer")).toHaveCount(0);
    await expect(page.getByRole("heading", { level: 1 })).toBeInViewport();
    await expect(page.locator("#act0 [data-layer='aminah']")).toBeInViewport();
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test:e2e -- e2e/film-hero.spec.ts`
Expected: FAIL (no language group).

- [ ] **Step 3: Add `web/components/film/backdrops.ts`**

```ts
// Written by scripts/film-assets.mjs; run that script instead of editing by hand.
// An act listed here has its AI background in public/film/; an act missing from it shows its placeholder.
export type ActId = "act0" | "act1" | "act2" | "act3" | "act4" | "act6" | "act7";

export const BACKDROPS: Partial<Record<ActId, { width: number; height: number }>> = {};
```

- [ ] **Step 4: Add `web/components/film/backdrop.tsx`**

```tsx
import { BACKDROPS, type ActId } from "./backdrops";
import styles from "./film.module.css";

/** An act's painted background: art-directed AI art when present, otherwise its placeholder gradient. */
export function Backdrop({ act, priority = false }: { act: ActId; priority?: boolean }) {
  const art = BACKDROPS[act];
  return (
    <div className={styles.backdrop} data-layer="backdrop" data-act={act} aria-hidden="true">
      {art ? (
        <picture>
          <source media="(max-width: 760px)" type="image/avif" srcSet={`/film/${act}-mobile.avif`} />
          <source media="(max-width: 760px)" type="image/webp" srcSet={`/film/${act}-mobile.webp`} />
          <source type="image/avif" srcSet={`/film/${act}-desktop.avif`} />
          {/* eslint-disable-next-line @next/next/no-img-element -- <picture> art direction needs a plain <img>. */}
          <img
            src={`/film/${act}-desktop.webp`}
            alt=""
            width={art.width}
            height={art.height}
            loading={priority ? "eager" : "lazy"}
            fetchPriority={priority ? "high" : "auto"}
            decoding="async"
          />
        </picture>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 5: Add `web/components/film/thread.tsx`**

```tsx
import type { Ref } from "react";
import styles from "./film.module.css";

export type ThreadState = "ok" | "watch" | "red";

/**
 * The thread of light. Paths use pathLength 1, so stroke-dashoffset 1 hides the thread and 0 draws it
 * fully; acts tween the [data-draw] paths. The colour follows data-state: green, amber or red.
 */
export function Thread({
  d, viewBox, state, name, pulse = false, svgRef,
}: {
  d: string; viewBox: string; state: ThreadState; name: string; pulse?: boolean; svgRef?: Ref<SVGSVGElement>;
}) {
  return (
    <svg
      ref={svgRef}
      className={styles.thread}
      viewBox={viewBox}
      preserveAspectRatio="none"
      data-thread={name}
      data-state={state}
      data-pulse={pulse || undefined}
      aria-hidden="true"
      focusable="false"
    >
      <path className={styles.threadGlow} d={d} pathLength={1} data-draw="" />
      <path className={styles.threadLine} d={d} pathLength={1} data-draw="" />
      {pulse ? <path className={styles.threadPulse} d={d} pathLength={1} /> : null}
    </svg>
  );
}
```

- [ ] **Step 6: Add `web/components/film/characters/aminah.tsx`**

```tsx
import styles from "../film.module.css";

/** Mak Cik Aminah, a fictional patient: flat shapes, one outline weight, parts named for animation. */
export function Aminah() {
  return (
    <svg className={styles.aminah} viewBox="180 240 260 310" aria-hidden="true" focusable="false">
      <g data-part="body">
        <path d="M200 540 C 205 420, 395 420, 400 540 Z" fill="#7aa892" />
        <path d="M232 470 q68 30 136 0 l6 70 h-148z" fill="#5e8f78" />
        <path d="M240 330 C 240 250, 360 250, 360 330 L 372 440 C 330 470, 270 470, 228 440 Z" fill="#2f6b4f" />
        <ellipse cx="300" cy="330" rx="44" ry="50" fill="#a86f4c" />
        <path d="M256 312 C 262 262, 338 262, 344 312 C 330 290, 270 290, 256 312z" fill="#2f6b4f" />
        <ellipse data-part="eye" cx="284" cy="334" rx="4" ry="5" fill="#2b1d16" />
        <ellipse data-part="eye" cx="316" cy="334" rx="4" ry="5" fill="#2b1d16" />
        <path d="M288 356 q12 9 24 0" fill="none" stroke="#2b1d16" strokeWidth="3" strokeLinecap="round" />
        <circle cx="274" cy="350" r="6" fill="#d98a6a" opacity=".6" />
        <circle cx="326" cy="350" r="6" fill="#d98a6a" opacity=".6" />
        <path d="M236 430 C 214 440, 224 470, 250 462" fill="none" stroke="#5e8f78" strokeWidth="22" strokeLinecap="round" />
        <g data-part="phone">
          <rect x="238" y="400" width="34" height="56" rx="7" fill="#3b2a20" />
          <rect x="242" y="406" width="26" height="40" rx="3" fill="#bfe3cf" />
        </g>
        <path d="M362 420 C 380 440, 372 470, 352 466" fill="none" stroke="#5e8f78" strokeWidth="22" strokeLinecap="round" />
      </g>
    </svg>
  );
}
```

- [ ] **Step 7: Add `web/components/film/characters/phone.tsx`**

```tsx
import type { ReactNode } from "react";
import { htmlLang, type Lang } from "../messages";
import type { ThreadState } from "../thread";
import styles from "../film.module.css";

/** A phone showing a Khabar conversation; the words are real text, so screen readers read them. */
export function Phone({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.phone} role="group" aria-label={label}>
      <div className={styles.phoneTop} aria-hidden="true">
        <span className={styles.phoneAvatar}>K</span> Khabar
      </div>
      <div className={styles.phoneScreen}>{children}</div>
    </div>
  );
}

export function Bubble({
  from, lang, tone, isNew = false, className, children,
}: {
  from: "khabar" | "aminah"; lang: Lang; tone?: ThreadState; isNew?: boolean; className?: string; children: ReactNode;
}) {
  return (
    <p
      className={`${styles.bubble} ${className ?? ""}`}
      data-from={from}
      data-tone={tone}
      data-new={isNew || undefined}
      lang={htmlLang(lang)}
    >
      <span className={styles.srOnly} lang="en">{from === "khabar" ? "Khabar says: " : "Aminah replies: "}</span>
      {children}
    </p>
  );
}
```

- [ ] **Step 8: Add `web/components/film/acts/act0-hero.tsx`**

```tsx
"use client";

import { useRef } from "react";
import Link from "next/link";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { ArrowRight } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread } from "../thread";
import { Aminah } from "../characters/aminah";
import { Bubble } from "../characters/phone";
import { GREETING, LANGS, htmlLang } from "../messages";
import { ease, media, SCRUB } from "../tokens";
import styles from "../film.module.css";

/**
 * The thread leaves the clinic door and runs along the street, in the hero's 1440×900 frame.
 * Tune these points to the act0 background once the AI art is in (the door is at about 44%, 62%).
 */
const HERO_THREAD = "M 640 560 C 720 640, 820 700, 960 700 S 1260 690, 1480 700";

export function Act0Hero() {
  const { motion, lang, setLang } = useFilm();
  const root = useRef<HTMLElement>(null);

  useGSAP(
    () => {
      if (!motion) return;
      const mm = gsap.matchMedia();
      mm.add({ desktop: media.desktop, mobile: media.mobile }, (context) => {
        const { mobile } = context.conditions as { mobile: boolean };
        const tl = gsap.timeline({
          defaults: { ease: ease.scrub },
          scrollTrigger: { trigger: root.current, start: "top top", end: mobile ? "+=60%" : "+=110%", scrub: SCRUB, pin: true },
        });
        tl.to("[data-layer='backdrop']", { scale: 1, yPercent: -3 }, 0)
          .to("[data-layer='aminah']", { xPercent: mobile ? 45 : 120 }, 0)
          .to("[data-part='body']", { y: -5, repeat: 7, yoyo: true, ease: "sine.inOut", duration: 1 / 8 }, 0)
          .to("[data-thread='hero'] [data-draw]", { strokeDashoffset: 0 }, 0)
          .to("[data-layer='copy']", { yPercent: -10, opacity: 0.4 }, 0.55);
      });
      return () => mm.revert();
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section ref={root} id="act0" className={styles.act0} aria-labelledby="act0-title">
      <p className={styles.srOnly}>Illustration: Aminah walks out of her clinic one morning, and a thread of light follows her home.</p>
      <Backdrop act="act0" priority />
      <Thread name="hero" d={HERO_THREAD} viewBox="0 0 1440 900" state="ok" />
      <div className={styles.act0Aminah} data-layer="aminah">
        <Bubble from="khabar" lang={lang} className={styles.act0Greeting}>{GREETING[lang]}</Bubble>
        <Aminah />
      </div>
      <div className={styles.act0Copy} data-layer="copy">
        <p className={styles.eyebrow}>Clinic-led continuity</p>
        <h1 id="act0-title">
          The visit ends.
          <br />
          <em>Care should not.</em>
        </h1>
        <p className={styles.lede}>
          Khabar carries the important details from first question to recovery—so patients feel clear,
          families stay informed, and clinicians see who needs them next.
        </p>
        <div className={styles.langs} role="group" aria-label="Khabar speaks her language">
          {LANGS.map((l) => (
            <button key={l.id} type="button" lang={htmlLang(l.id)} aria-pressed={lang === l.id} onClick={() => setLang(l.id)}>
              {l.label}
            </button>
          ))}
        </div>
        <div className={styles.actions}>
          <Link className="button-primary" href="/login">
            Find your care space <ArrowRight size={17} aria-hidden />
          </Link>
          <a className="button-quiet" href="#act3">Follow Aminah home</a>
        </div>
      </div>
      <p className={styles.scrollCue} aria-hidden="true"><i /> Scroll to follow Aminah</p>
    </section>
  );
}
```

- [ ] **Step 9: Append the Act 0, backdrop, thread, character and bubble styles to `film.module.css`**

```css
/* ---------- Backdrops: AI art, or a painted-looking placeholder ---------- */
.backdrop { position: absolute; inset: 0; overflow: hidden; transform: scale(1.06); transform-origin: 50% 70%; }
.backdrop picture, .backdrop img { display: block; width: 100%; height: 100%; object-fit: cover; }
.backdrop::after { /* paper grain over art and placeholders alike */
  content: ""; position: absolute; inset: 0; pointer-events: none; opacity: 0.35; mix-blend-mode: multiply;
  background-image: radial-gradient(rgb(59 42 32 / 0.08) 1px, transparent 1px); background-size: 4px 4px;
}
.backdrop[data-act="act0"]:not(:has(picture)) {
  background:
    radial-gradient(60% 50% at 78% 18%, #fde6bf 0%, transparent 60%),
    linear-gradient(180deg, #f9f1e3 0%, #f3e3cb 58%, #e6cfae 58.2%, #dcc3a0 100%);
}
.backdrop[data-act="act3"]:not(:has(picture)) { background: linear-gradient(180deg, #eef5f1 0%, #f6efe4 100%); }

/* ---------- Thread ---------- */
.thread { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; --thread: var(--pandan-glow); }
.thread[data-state="watch"] { --thread: var(--amber); }
.thread[data-state="red"] { --thread: var(--cili); }
.thread path { fill: none; stroke: var(--thread); stroke-linecap: round; vector-effect: non-scaling-stroke; transition: stroke 0.6s var(--ease-enter); }
.threadLine { stroke-width: 3px; stroke-dasharray: 1; }
.threadGlow { stroke-width: 12px; stroke-dasharray: 1; opacity: 0.45; filter: blur(5px); }
.threadPulse { stroke-width: 6px; stroke-dasharray: 0.03 0.97; stroke-dashoffset: 1; opacity: 0; }
.film[data-motion="on"] .threadPulse { opacity: 0.9; animation: threadPulse 2.8s linear infinite; }
@keyframes threadPulse { to { stroke-dashoffset: 0; } }
/* The hero's still frame: the thread runs from the door to Aminah. Scrolling draws the rest. */
[data-thread="hero"] [data-draw] { stroke-dashoffset: 0.62; }

/* ---------- Characters and bubbles ---------- */
.aminah { display: block; width: 100%; height: auto; overflow: visible; }
.film[data-motion="on"] .aminah [data-part="body"] { transform-box: fill-box; transform-origin: 50% 100%; animation: breathe 3.6s ease-in-out infinite; }
.film[data-motion="on"] .aminah [data-part="eye"] { transform-box: fill-box; transform-origin: center; animation: blink 4.8s infinite; }
@keyframes breathe { 50% { transform: scaleY(1.012); } }
@keyframes blink { 0%, 94%, 100% { transform: scaleY(1); } 97% { transform: scaleY(0.1); } }

.bubble {
  margin: 0; max-width: 32ch; padding: 10px 14px; border-radius: 18px; line-height: 1.45; font-size: 15px;
  background: #fff; border: 2px solid var(--kopi); color: var(--kopi); overflow-wrap: anywhere;
}
.bubble[data-from="aminah"] { margin-left: auto; background: var(--pandan); border-color: var(--pandan); color: #fff; border-bottom-right-radius: 6px; }
.bubble[data-from="khabar"] { border-bottom-left-radius: 6px; }
.bubble[data-tone="watch"] { border-color: var(--amber); box-shadow: 0 0 0 4px rgb(232 163 61 / 0.18); }
.bubble[data-tone="red"] { border-color: var(--cili); box-shadow: 0 0 0 4px rgb(200 69 45 / 0.16); }

/* ---------- Header and footer ---------- */
.header { position: absolute; top: 16px; left: 16px; right: 16px; z-index: 20; display: flex; justify-content: space-between; align-items: center; }
.skipLink { position: absolute; left: 16px; top: -60px; z-index: 60; background: var(--kopi); color: var(--santan); padding: 10px 14px; border-radius: 10px; }
.skipLink:focus { top: 16px; }
.footer { display: flex; gap: 16px; align-items: center; justify-content: space-between; flex-wrap: wrap; padding: 32px clamp(16px, 5vw, 64px); color: var(--teh); font-size: 14px; }

/* ---------- Act 0: hero ---------- */
.act0 { position: relative; height: 100svh; min-height: 640px; overflow: hidden; }
.act0Copy { position: relative; z-index: 5; max-width: min(560px, 42vw); padding: clamp(120px, 18vh, 180px) 0 0 clamp(16px, 6vw, 96px); }
.act0Copy h1 { font-size: clamp(44px, 6.4vw, 88px); line-height: 0.98; margin: 0 0 18px; }
.eyebrow { font-size: 13px; letter-spacing: 0.14em; text-transform: uppercase; color: var(--pandan); font-weight: 700; margin: 0 0 14px; }
.lede { font-size: 17px; line-height: 1.6; color: var(--teh); margin: 0 0 22px; max-width: 46ch; }
.langs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 22px; }
.langs button {
  border: 1.5px solid var(--kopi); background: transparent; color: var(--kopi); border-radius: 999px;
  padding: 9px 16px; font: 600 15px/1 inherit; cursor: pointer; transition: transform 0.25s var(--ease-enter), background 0.2s;
}
.langs button:hover { transform: translateY(-2px); }
.langs button[aria-pressed="true"] { background: var(--kopi); color: var(--santan); }
.actions { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
.act0Aminah { position: absolute; z-index: 4; left: 50%; bottom: 7%; width: clamp(150px, 16vw, 230px); }
.act0Greeting { position: absolute; bottom: 100%; left: 40%; width: max-content; max-width: 26ch; }
.scrollCue { position: absolute; left: clamp(16px, 6vw, 96px); bottom: 28px; z-index: 5; margin: 0; font-size: 12px; letter-spacing: 0.14em; text-transform: uppercase; color: var(--teh); }
.scrollCue i { display: inline-block; width: 1px; height: 24px; background: currentColor; vertical-align: middle; margin-right: 10px; }
.film[data-motion="on"] .scrollCue i { animation: cue 1.6s ease-in-out infinite; transform-origin: top; }
@keyframes cue { 0% { transform: scaleY(0); } 50% { transform: scaleY(1); } 100% { transform: scaleY(0); transform-origin: bottom; } }

@media (max-width: 760px) {
  .act0 { min-height: 700px; }
  .act0Copy { max-width: none; padding: 96px 16px 0; }
  .act0Copy h1 { font-size: clamp(40px, 11vw, 56px); }
  .lede { font-size: 16px; }
  .act0Aminah { left: 12%; bottom: 3%; width: 128px; }
  .act0Greeting { left: 70%; max-width: 20ch; font-size: 14px; }
  .scrollCue { display: none; }
}
```

- [ ] **Step 10: Use the hero in the page** (replace `web/app/preview/film/page.tsx`)

```tsx
import type { Metadata } from "next";
import Link from "next/link";
import { Brand } from "@/components/brand";
import { FilmProvider } from "@/components/film/film-provider";
import { fontVariables } from "@/components/film/fonts";
import { Act0Hero } from "@/components/film/acts/act0-hero";
import styles from "@/components/film/film.module.css";

export const metadata: Metadata = {
  title: "Film preview",
  robots: { index: false, follow: false },
};

export default function FilmPreviewPage() {
  return (
    <FilmProvider className={fontVariables}>
      <a className={styles.skipLink} href="#act3">Skip to the thirty days at home</a>
      <header className={styles.header}>
        <Brand />
        <Link className="button-secondary" href="/login">Sign in</Link>
      </header>
      <main id="main-content">
        <Act0Hero />
      </main>
    </FilmProvider>
  );
}
```

- [ ] **Step 11: Run the tests to verify they pass**

Run: `npm run test:e2e -- e2e/film-hero.spec.ts e2e/film-motion.spec.ts e2e/film-route.spec.ts`
Expected: all pass.

If the pin test fails, it is because ScrollTrigger wraps the pinned section in `.pin-spacer`. Check that `pin: true` is on the `#act0` trigger, not on a child.

- [ ] **Step 12: Look at it**

Run `npx next start -p 3100` after the build, and open `http://localhost:3100/preview/film` at 1440×900 and at 390×844.
- The headline must sit left, Aminah on the ground right of the copy, the bubble above her head, and no overlap.
- Adjust `.act0Aminah`, `.act0Greeting` or `HERO_THREAD` if they collide.

- [ ] **Step 13: Lint, typecheck, commit**

Run: `npm run lint && npm run typecheck`
```bash
git add web/components/film web/app/preview/film/page.tsx web/e2e/film-hero.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add the film hero: Aminah leaves the clinic and the thread follows her"
```

---

### Task 4: Act 3, thirty days at home, and "Reply for Aminah"

**Files:**
- Create: `web/components/film/acts/act3-thirty-days.tsx`, `web/e2e/film-thirty-days.spec.ts`
- Modify: `web/components/film/film.module.css` (append), `web/app/preview/film/page.tsx`

**Interfaces:**
- Consumes:
  - `useFilm`, `Backdrop`, `Thread`, `ThreadState`, `Phone`, `Bubble`;
  - `CHECK_IN`, `REPLY_TEXT`, `MEDICINE_TAKEN`, `FEELING_BETTER`, `THANK_YOU`, `answer`, `Reply`;
  - `ease`, `duration`, `media`, `SCRUB`.
- Produces:
  - `Act3ThirtyDays()`: `section#act3[data-mode="animated"|"static"]` with `article[data-day]` cards;
  - a `group` named "Reply for Aminah" with three buttons;
  - `[data-outcome][data-state]` with `aria-live="polite"`.

- [ ] **Step 1: Write the failing test** `web/e2e/film-thirty-days.spec.ts`

```ts
import { expect, test } from "@playwright/test";

const DAYS = ["Day 1", "Day 3", "Day 7", "Day 14", "Day 30"];

/** Scrolls down in small steps until Day 30 is fully on screen, so the test never overshoots the pin. */
async function panToDay30(page: import("@playwright/test").Page) {
  await expect.poll(async () => {
    await page.evaluate(() => window.scrollBy(0, 250));
    return page.locator("article[data-day='30']").evaluate((el) => {
      const r = el.getBoundingClientRect();
      return r.left >= 0 && r.right <= window.innerWidth && r.top < window.innerHeight && r.bottom > 0;
    });
  }, { timeout: 15_000, intervals: [150] }).toBe(true);
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

test("desktop pans through the thirty days while pinned, and ends on Day 30", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "The horizontal pan is desktop only.");
  await page.goto("/preview/film");
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "animated");
  await page.locator("#act3").scrollIntoViewIfNeeded();
  await expect(page.locator("article[data-day='1']")).toBeInViewport();
  await panToDay30(page);
});

test("arriving by the #act3 link shows Day 1, not an empty pin", async ({ page }) => {
  await page.goto("/preview/film#act3");
  await expect(page.locator("article[data-day='1']")).toBeInViewport({ timeout: 5000 });
});

test("after a resize the pan still ends exactly on Day 30", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "Desktop pan only.");
  await page.goto("/preview/film");
  await page.setViewportSize({ width: 1100, height: 800 });
  await page.locator("#act3").scrollIntoViewIfNeeded();
  await panToDay30(page);
});

test("Day 7's dizziness turns the thread amber", async ({ page }) => {
  await page.goto("/preview/film");
  await expect(page.locator("article[data-day='7'] [data-thread]")).toHaveAttribute("data-state", "watch");
  await expect(page.locator("article[data-day='3'] [data-thread]")).toHaveAttribute("data-state", "ok");
});

test("Reply for Aminah shows exactly what Khabar sends back", async ({ page }) => {
  await page.goto("/preview/film");
  const lab = page.getByRole("group", { name: "Reply for Aminah" });
  const outcome = page.locator("[data-outcome]");

  await lab.getByRole("button", { name: "I'm okay" }).click();
  await expect(page.locator("[data-lab] [data-from='khabar']").last()).toContainText("Terima kasih kerana memberitahu. Jaga diri!");
  await expect(outcome).toHaveAttribute("data-state", "ok");

  await lab.getByRole("button", { name: "A bit dizzy" }).click();
  await expect(page.locator("[data-lab] [data-from='khabar']").last()).toContainText("klinik mungkin belum membacanya");
  await expect(page.locator("[data-lab] [data-from='khabar']").last()).toContainText("999");
  await expect(outcome).toHaveAttribute("data-state", "watch");

  await lab.getByRole("button", { name: "Chest pain" }).click();
  await expect(page.locator("[data-lab] [data-from='khabar']").last()).toContainText("Mesej anda telah dimasukkan");
  await expect(outcome).toHaveAttribute("data-state", "red");
  await expect(page.locator("[data-lab] [data-thread]")).toHaveAttribute("data-state", "red");
  await expect(outcome).not.toContainText(/will call|calls you/i);
});

test("the language chosen in the hero carries into the check-ins and answers", async ({ page }) => {
  await page.goto("/preview/film");
  await page.getByRole("button", { name: "中文" }).click();
  await expect(page.locator("article[data-day='1'] [data-from='khabar']")).toContainText("今天感觉怎么样？吃药了吗？回复这条信息告诉诊所。");
  const chest = page.getByRole("group", { name: "Reply for Aminah" }).getByRole("button", { name: "Chest pain" });
  await chest.focus();
  await expect(chest).toBeInViewport();
  await page.keyboard.press("Space");
  await expect(chest).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("[data-lab] [data-from='khabar']").last()).toContainText("拨打999");
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test:e2e -- e2e/film-thirty-days.spec.ts`
Expected: FAIL (`#act3` not found).

- [ ] **Step 3: Add `web/components/film/acts/act3-thirty-days.tsx`**

```tsx
"use client";

import { useRef, useState } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread, type ThreadState } from "../thread";
import { Bubble, Phone } from "../characters/phone";
import { CHECK_IN, FEELING_BETTER, MEDICINE_TAKEN, REPLY_TEXT, THANK_YOU, answer, type Lang, type Reply } from "../messages";
import { duration, ease, media, SCRUB } from "../tokens";
import styles from "../film.module.css";

type Day = { day: number; reply: Record<Lang, string>; state: ThreadState; caption: string };

// Fictional. Captions never promise a call: a reply goes onto the clinic's follow-up list.
const DAYS: Day[] = [
  { day: 1, reply: MEDICINE_TAKEN, state: "ok", caption: "The first check-in arrives in the language she thinks in." },
  { day: 3, reply: REPLY_TEXT.ok, state: "ok", caption: "A two-word reply is enough. The thread stays green." },
  { day: 7, reply: REPLY_TEXT.dizzy, state: "watch", caption: "“Pening” turns the thread amber, and her reply goes onto the clinic’s follow-up list." },
  { day: 14, reply: FEELING_BETTER, state: "ok", caption: "A week later she is feeling better. Every reply stays on her record." },
  { day: 30, reply: THANK_YOU, state: "ok", caption: "Thirty days, one thread. Her follow-up closes on her record." },
];

const CHOICES: { id: Reply; label: string; state: ThreadState; outcome: string }[] = [
  { id: "ok", label: "I'm okay", state: "ok", outcome: "Khabar thanks her. Nothing else is needed, so the thread stays green." },
  {
    id: "dizzy", label: "A bit dizzy", state: "watch",
    outcome: "Khabar tells her the clinic may not have seen it yet and gives the 999 advice. Her reply goes onto the clinic’s follow-up list, and the thread turns amber.",
  },
  {
    id: "chest", label: "Chest pain", state: "red",
    outcome: "Chest pain gets the 999 advice straight away. Her reply goes to the top of the clinic’s follow-up list, and the thread turns red.",
  },
];

export function Act3ThirtyDays() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const lab = useRef<HTMLDivElement>(null);
  const [choice, setChoice] = useState<Reply | null>(null);
  const picked = CHOICES.find((c) => c.id === choice) ?? null;

  useGSAP(
    () => {
      if (!motion) return;
      const mm = gsap.matchMedia();
      mm.add(media.desktop, () => {
        const distance = () => track.current!.scrollWidth - stage.current!.clientWidth;
        gsap.timeline({
          defaults: { ease: ease.scrub },
          scrollTrigger: {
            trigger: stage.current, start: "top top", end: () => `+=${distance() * 1.15}`,
            scrub: SCRUB, pin: true, invalidateOnRefresh: true, anticipatePin: 1,
          },
        })
          .to(track.current, { x: () => -distance() }, 0)
          .to("[data-layer='dusk']", { opacity: 1 }, 0)
          .to("[data-layer='backdrop']", { xPercent: -4 }, 0);
      });
      mm.add(media.mobile, () => {
        gsap.utils.toArray<HTMLElement>("article[data-day]").forEach((card) => {
          gsap.from(card, { y: 40, autoAlpha: 0, duration: duration.enter, ease: ease.enter, scrollTrigger: { trigger: card, start: "top 85%", once: true } });
        });
      });
      return () => mm.revert();
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  useGSAP(
    () => {
      if (!motion || !choice) return;
      gsap.from("[data-new]", { scale: 0.7, autoAlpha: 0, duration: duration.pop, ease: ease.pop, stagger: 0.18, transformOrigin: "bottom left" });
    },
    { scope: lab, dependencies: [choice, lang, motion], revertOnUpdate: true },
  );

  return (
    <section ref={root} id="act3" className={styles.act3} data-mode={motion ? "animated" : "static"} aria-labelledby="act3-title">
      <div ref={stage} className={styles.act3Stage}>
        <p className={styles.srOnly}>Illustration: the same window at home from morning to dusk, while Aminah&rsquo;s phone receives five check-ins.</p>
        <Backdrop act="act3" />
        <div className={styles.act3Dusk} data-layer="dusk" aria-hidden="true" />
        <header className={styles.act3Head}>
          <p className={styles.eyebrow}>The thirty days after the visit</p>
          <h2 id="act3-title">Care is a conversation.<br /><em>Keep it going.</em></h2>
        </header>
        <div ref={track} className={styles.act3Track}>
          {DAYS.map((d) => (
            <article key={d.day} className={styles.day} data-day={d.day} aria-labelledby={`day-${d.day}`}>
              <h3 id={`day-${d.day}`}>Day {d.day}</h3>
              <Phone label={`Aminah’s phone on day ${d.day}`}>
                <Bubble from="khabar" lang={lang}>{CHECK_IN[lang]}</Bubble>
                <Bubble from="aminah" lang={lang}>{d.reply[lang]}</Bubble>
              </Phone>
              <p className={styles.dayCaption}>{d.caption}</p>
              <div className={styles.dayThread}>
                <Thread name={`day-${d.day}`} d="M 0 20 L 100 20" viewBox="0 0 100 40" state={d.state} pulse={d.state !== "ok"} />
              </div>
            </article>
          ))}
        </div>
      </div>

      <div ref={lab} className={styles.lab} data-lab="">
        <div className={styles.labCopy}>
          <h3 id="lab-title">Reply for Aminah</h3>
          <p>Choose what she says. You will see exactly what Khabar sends back, and where her reply goes.</p>
          <div className={styles.choices} role="group" aria-labelledby="lab-title">
            {CHOICES.map((c) => (
              <button key={c.id} type="button" data-state={c.state} aria-pressed={choice === c.id} onClick={() => setChoice(c.id)}>
                {c.label}
              </button>
            ))}
          </div>
          <p className={styles.outcome} data-outcome="" data-state={picked?.state ?? "ok"} aria-live="polite">
            {picked ? picked.outcome : "Choose a reply to see what happens."}
          </p>
        </div>
        <div className={styles.labPhone}>
          <Phone label="Aminah’s phone">
            <Bubble from="khabar" lang={lang}>{CHECK_IN[lang]}</Bubble>
            {picked ? (
              <>
                <Bubble key={`r-${picked.id}-${lang}`} from="aminah" lang={lang} isNew>{REPLY_TEXT[picked.id][lang]}</Bubble>
                <Bubble key={`a-${picked.id}-${lang}`} from="khabar" lang={lang} tone={picked.state} isNew>{answer(picked.id, lang)}</Bubble>
              </>
            ) : null}
          </Phone>
          <div className={styles.labThread}>
            <Thread name="lab" d="M 0 20 C 30 0, 70 40, 100 20" viewBox="0 0 100 40" state={picked?.state ?? "ok"} pulse />
          </div>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Append the Act 3 styles to `film.module.css`**

```css
/* ---------- Act 3: thirty days at home ---------- */
.act3 { position: relative; }
.act3Stage { position: relative; overflow: hidden; padding: 72px 0 56px; }
.act3Dusk { position: absolute; inset: 0; opacity: 0; pointer-events: none; background: linear-gradient(180deg, #f7c98d 0%, #e9a27a 45%, #7d5a86 100%); mix-blend-mode: multiply; }
.act3Head { position: relative; z-index: 2; padding: 0 clamp(16px, 6vw, 96px); max-width: 720px; }
.act3Head h2 { font-size: clamp(36px, 5vw, 64px); line-height: 1; margin: 0 0 28px; }
.act3Track { position: relative; z-index: 2; display: grid; gap: 28px; padding: 0 clamp(16px, 6vw, 96px); }
.day { position: relative; display: grid; gap: 14px; align-content: start; max-width: 460px; }
.day h3 { margin: 0; font-size: 28px; }
.dayCaption { margin: 0; color: var(--teh); line-height: 1.55; max-width: 40ch; }
.dayThread { position: relative; height: 40px; }

.phone { background: #fffdf8; border: 2px solid var(--kopi); border-radius: 28px; padding: 12px 12px 16px; box-shadow: 0 24px 50px rgb(59 42 32 / 0.14); max-width: 360px; }
.phoneTop { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 14px; padding: 4px 6px 10px; border-bottom: 1px solid rgb(59 42 32 / 0.12); margin-bottom: 10px; }
.phoneAvatar { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--pandan); color: #fff; font-size: 12px; }
.phoneScreen { display: grid; gap: 10px; }

@media (min-width: 761px) {
  .act3[data-mode="animated"] .act3Stage { height: 100vh; min-height: 640px; }
  .act3[data-mode="animated"] .act3Track { display: flex; width: max-content; gap: 56px; padding-right: 20vw; will-change: transform; }
  .act3[data-mode="animated"] .day { width: min(420px, 34vw); flex: none; }
  .act3[data-mode="static"] .act3Track { grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
}

/* ---------- Reply for Aminah ---------- */
.lab { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: clamp(24px, 5vw, 72px); align-items: center; padding: 72px clamp(16px, 6vw, 96px) 96px; }
.labCopy h3 { font-size: clamp(30px, 3.6vw, 44px); margin: 0 0 12px; }
.labCopy > p { color: var(--teh); line-height: 1.6; max-width: 46ch; }
.choices { display: flex; flex-wrap: wrap; gap: 10px; margin: 18px 0; }
.choices button {
  border: 1.5px solid var(--kopi); background: #fff; color: var(--kopi); border-radius: 14px; padding: 12px 18px;
  font: 600 16px/1.2 inherit; cursor: pointer; transition: transform 0.2s var(--ease-enter), background 0.2s;
}
.choices button:hover { transform: translateY(-2px); }
.choices button[aria-pressed="true"][data-state="ok"] { background: var(--pandan); border-color: var(--pandan); color: #fff; }
.choices button[aria-pressed="true"][data-state="watch"] { background: #8a5a12; border-color: #8a5a12; color: #fff; }
.choices button[aria-pressed="true"][data-state="red"] { background: #9e3320; border-color: #9e3320; color: #fff; }
.outcome { min-height: 5.2em; margin: 0; padding: 14px 16px; border-radius: 14px; background: #fff; border-left: 5px solid var(--pandan-glow); line-height: 1.55; }
.outcome[data-state="watch"] { border-left-color: var(--amber); }
.outcome[data-state="red"] { border-left-color: var(--cili); }
.labPhone { position: relative; }
.labThread { position: relative; height: 40px; margin-top: 12px; max-width: 360px; }

@media (max-width: 760px) {
  .act3Stage { padding-top: 56px; }
  .lab { grid-template-columns: minmax(0, 1fr); padding-top: 48px; }
}
```

- [ ] **Step 5: Add Act 3 to the page and a footer** (in `web/app/preview/film/page.tsx`)

Add the import `import { Act3ThirtyDays } from "@/components/film/acts/act3-thirty-days";`, then replace the `<main>` element and add the footer after it:

```tsx
      <main id="main-content">
        <Act0Hero />
        <Act3ThirtyDays />
      </main>
      <footer className={styles.footer}>
        <Brand compact />
        <p>Concept prototype using fictional patient data.</p>
      </footer>
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `npm run test:e2e -- e2e/film-thirty-days.spec.ts`
Expected: all pass. The desktop-only tests are skipped on `phone`.

- [ ] **Step 7: Run the whole e2e suite, lint, typecheck, commit**

Run: `npm run test:e2e && npm run lint && npm run typecheck`
```bash
git add web/components/film web/app/preview/film/page.tsx web/e2e/film-thirty-days.spec.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add the thirty days at home, and let visitors reply for Aminah"
```

---

### Task 5: Whole-page quality: accessibility, overflow, pausing inside a pin, JS budget

**Files:**
- Create: `web/e2e/film-page.spec.ts`
- Modify: whatever the tests expose (CSS contrast or overflow fixes in `film.module.css`)

**Interfaces:**
- Consumes: the finished preview page from Tasks 1–4.
- Produces: no new interfaces. Only the checks, and any fixes they require.

- [ ] **Step 1: Write the tests** `web/e2e/film-page.spec.ts`

```ts
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const noHorizontalScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);

test("no accessibility violations with motion on", async ({ page }) => {
  await page.goto("/preview/film");
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
});

test.describe("with reduced motion", () => {
  test.use({ reducedMotion: "reduce" });
  test("no accessibility violations", async ({ page }) => {
    await page.goto("/preview/film");
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.length}`)).toEqual([]);
  });
});

test("no horizontal scroll, even in Tamil at 320 px", async ({ page }) => {
  await page.goto("/preview/film");
  expect(await noHorizontalScroll(page)).toBe(true);
  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole("button", { name: "தமிழ்" }).click();
  await page.getByRole("button", { name: "Chest pain" }).click();
  expect(await noHorizontalScroll(page)).toBe(true);
});

test("pausing inside the pinned pan unpins it and stacks the days", async ({ page }, info) => {
  test.skip(info.project.name !== "desktop", "The pin is desktop only.");
  await page.goto("/preview/film");
  await page.locator("#act3").scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, 1200));
  await page.getByRole("button", { name: "Pause motion" }).click();
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await expect(page.locator("#act3")).toHaveAttribute("data-mode", "static");
  await page.locator("article[data-day='7']").scrollIntoViewIfNeeded();
  await expect(page.locator("article[data-day='7']")).toBeInViewport();
});

test("the film adds at most 70 KB of JavaScript over the current landing page", async ({ page }) => {
  const jsBytes = async (path: string) => {
    let total = 0;
    const count = async (response: import("@playwright/test").Response) => {
      if (response.request().resourceType() === "script") total += (await response.request().sizes()).responseBodySize;
    };
    page.on("response", count);
    await page.goto(path, { waitUntil: "networkidle" });
    page.off("response", count);
    return total;
  };
  const landing = await jsBytes("/");
  const film = await jsBytes("/preview/film");
  console.log(`JS transferred: / ${(landing / 1024).toFixed(1)} KB, /preview/film ${(film / 1024).toFixed(1)} KB`);
  expect(film - landing).toBeLessThanOrEqual(70 * 1024);
});
```

- [ ] **Step 2: Run them**

Run: `npm run test:e2e -- e2e/film-page.spec.ts`
Expected: pass. If axe reports `color-contrast`, darken only the failing colour in `film.module.css`, keeping its hue (as commit 79924b3 did), and re-run. If there is horizontal scroll, find the widest element with:

```bash
npx playwright test e2e/film-page.spec.ts --debug
```

In the browser console, run:

```js
[...document.querySelectorAll('*')].filter(e => e.getBoundingClientRect().right > innerWidth).slice(0,5)
```

Then constrain that element. The usual fix is `min-width: 0` on a grid child, or `overflow-wrap: anywhere`.

- [ ] **Step 3: Run the full suite, lint, typecheck, commit**

Run: `npm run test:e2e && npm run lint && npm run typecheck`
```bash
git add web/e2e/film-page.spec.ts web/components/film
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Check the film for accessibility, overflow, pausing inside a pin and JS weight"
```

---

### Task 6: Background asset pipeline

**Files:**
- Create: `web/scripts/film-assets.mjs`, `web/scripts/film-assets.test.mjs`
- Modify: `web/package.json` (sharp devDependency, `test:assets` script)

**Interfaces:**
- Consumes: `BACKDROPS` / `ActId` file format from Task 3 (`web/components/film/backdrops.ts`).
- Produces:
  - `node scripts/film-assets.mjs <source-folder>`, which writes `public/film/<act>-<desktop|mobile>.<avif|webp>` and rewrites `backdrops.ts`;
  - the exported `build(sourceDir, publicDir, manifestPath)` returns `{ act, variant, avifBytes, webpBytes, quality }[]` and throws if an AVIF cannot meet its budget.

- [ ] **Step 1: Install sharp and add the script entry**

Run: `npm install --save-dev sharp@0.35.4`
Add to `"scripts"`: `"test:assets": "node --test scripts/film-assets.test.mjs"`

- [ ] **Step 2: Write the failing test** `web/scripts/film-assets.test.mjs`

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import sharp from "sharp";
import { build, BUDGET } from "./film-assets.mjs";

/** A noisy test image, so compression has real work to do. */
async function noisyPng(path, width, height) {
  const noise = Buffer.alloc(width * height * 3);
  for (let i = 0; i < noise.length; i++) noise[i] = (i * 7919) % 256;
  await sharp(noise, { raw: { width, height, channels: 3 } }).blur(2).png().toFile(path);
}

test("converts an act's art to budgeted AVIF and WebP and lists it in the manifest", async () => {
  const dir = await mkdtemp(join(tmpdir(), "film-"));
  const source = join(dir, "src");
  const publicDir = join(dir, "public");
  const manifest = join(dir, "backdrops.ts");
  await import("node:fs/promises").then((fs) => fs.mkdir(source, { recursive: true }));
  await noisyPng(join(source, "act0-desktop.png"), 2600, 1460);
  await noisyPng(join(source, "act0-mobile.png"), 1200, 2100);
  await writeFile(manifest, "");

  const results = await build(source, publicDir, manifest);

  assert.equal(results.length, 2);
  for (const r of results) assert.ok(r.avifBytes <= BUDGET[r.variant], `${r.variant} AVIF ${r.avifBytes} B over budget`);
  const meta = await sharp(join(publicDir, "film", "act0-desktop.webp")).metadata();
  assert.deepEqual([meta.width, meta.height], [2400, 1350]);
  assert.ok((await stat(join(publicDir, "film", "act0-mobile.avif"))).size > 0);
  const written = await readFile(manifest, "utf8");
  assert.match(written, /act0: \{ width: 2400, height: 1350 \}/);
  assert.match(written, /export type ActId =/);
});

test("an act with only one variant is refused", async () => {
  const dir = await mkdtemp(join(tmpdir(), "film-"));
  const source = join(dir, "src");
  await import("node:fs/promises").then((fs) => fs.mkdir(source, { recursive: true }));
  await noisyPng(join(source, "act4-desktop.png"), 2400, 1350);
  await assert.rejects(build(source, join(dir, "public"), join(dir, "backdrops.ts")), /act4 needs both desktop and mobile/);
});
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npm run test:assets`
Expected: FAIL (`Cannot find module './film-assets.mjs'`).

- [ ] **Step 4: Add `web/scripts/film-assets.mjs`**

```js
#!/usr/bin/env node
// Turns the owner's chosen AI backgrounds into the film's web images, then rewrites
// components/film/backdrops.ts so those acts use them. Input files: <act>-desktop.<png|jpg|jpeg|webp>
// and <act>-mobile.<...> for act0, act1, act2, act3, act4, act6, act7 (act5 is the 3D town, no art).
// Usage (from web/): node scripts/film-assets.mjs <source-folder>
import { mkdir, readdir, stat, writeFile } from "node:fs/promises";
import { basename, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

export const ACTS = ["act0", "act1", "act2", "act3", "act4", "act6", "act7"];
export const SIZE = {
  desktop: { width: 2400, height: 1350 },
  mobile: { width: 1080, height: 1920 },
};
/** Act 3 pans sideways on desktop, so its desktop art is twice as wide. */
const WIDE = { act3: { width: 4800, height: 1350 } };
/** Bytes allowed per AVIF (spec §8). WebP is the fallback and is not budgeted. */
export const BUDGET = { desktop: 180_000, mobile: 110_000 };

const HEADER = `// Written by scripts/film-assets.mjs; run that script instead of editing by hand.
// An act listed here has its AI background in public/film/; an act missing from it shows its placeholder.
export type ActId = ${ACTS.map((a) => `"${a}"`).join(" | ")};
`;

async function encodeWithinBudget(input, size, budget) {
  for (let quality = 60; quality >= 30; quality -= 5) {
    const avif = await sharp(input).resize(size.width, size.height, { fit: "cover" }).avif({ quality, effort: 6 }).toBuffer();
    if (avif.length <= budget) return { avif, quality };
  }
  return null;
}

export async function build(sourceDir, publicDir, manifestPath) {
  const files = (await readdir(sourceDir)).filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  const found = new Map();
  for (const file of files) {
    const [act, variant] = basename(file, extname(file)).split("-");
    if (!ACTS.includes(act) || !(variant in SIZE)) continue;
    found.set(`${act}-${variant}`, join(sourceDir, file));
  }
  const acts = ACTS.filter((a) => found.has(`${a}-desktop`) || found.has(`${a}-mobile`));
  for (const act of acts) {
    if (!found.has(`${act}-desktop`) || !found.has(`${act}-mobile`)) throw new Error(`${act} needs both desktop and mobile images`);
  }

  const outDir = join(publicDir, "film");
  await mkdir(outDir, { recursive: true });
  const results = [];
  const manifest = {};
  for (const act of acts) {
    for (const variant of ["desktop", "mobile"]) {
      const size = variant === "desktop" && WIDE[act] ? WIDE[act] : SIZE[variant];
      const budget = BUDGET[variant] * (size.width / SIZE[variant].width);
      const input = found.get(`${act}-${variant}`);
      const encoded = await encodeWithinBudget(input, size, budget);
      if (!encoded) throw new Error(`${act}-${variant}: no AVIF quality from 60 down to 30 fits ${Math.round(budget / 1000)} KB; simplify the image`);
      const webp = await sharp(input).resize(size.width, size.height, { fit: "cover" }).webp({ quality: 72 }).toBuffer();
      await writeFile(join(outDir, `${act}-${variant}.avif`), encoded.avif);
      await writeFile(join(outDir, `${act}-${variant}.webp`), webp);
      results.push({ act, variant, avifBytes: encoded.avif.length, webpBytes: webp.length, quality: encoded.quality });
      if (variant === "desktop") manifest[act] = size;
    }
  }

  const entries = Object.entries(manifest).map(([act, s]) => `  ${act}: { width: ${s.width}, height: ${s.height} },`).join("\n");
  await writeFile(manifestPath, `${HEADER}\nexport const BACKDROPS: Partial<Record<ActId, { width: number; height: number }>> = {\n${entries}\n};\n`);
  return results;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const source = process.argv[2];
  if (!source || !(await stat(source).catch(() => null))?.isDirectory()) {
    console.error("Usage: node scripts/film-assets.mjs <folder with act0-desktop.png, act0-mobile.png, ...>");
    process.exit(1);
  }
  const web = resolve(fileURLToPath(new URL("..", import.meta.url)));
  const results = await build(source, join(web, "public"), join(web, "components", "film", "backdrops.ts"));
  for (const r of results) {
    console.log(`${r.act}-${r.variant}: AVIF ${(r.avifBytes / 1024).toFixed(0)} KB (q${r.quality}), WebP ${(r.webpBytes / 1024).toFixed(0)} KB`);
  }
}
```

The wide Act 3 desktop art gets a budget proportional to its width (360 KB for 4800 px). It is still one image, loaded lazily.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm run test:assets`
Expected: 2 passing.

- [ ] **Step 6: Commit**

```bash
git add web/package.json web/package-lock.json web/scripts/film-assets.mjs web/scripts/film-assets.test.mjs
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add the pipeline that turns AI backgrounds into budgeted film images"
```

---

### Task 7: Measure the budgets and prepare the owner's review

**Files:**
- Create: `docs/LANDING_FILM_SLICE_REVIEW.md`
- (Only if the owner has delivered art: `web/public/film/*`, `web/components/film/backdrops.ts` via the script)

**Interfaces:**
- Consumes: the finished slice.
- Produces: the review pack the owner uses at the gate.

- [ ] **Step 1: If AI backgrounds for act0 or act3 have arrived, process them**

Run (in `web/`): `node scripts/film-assets.mjs <folder>`
Expected: one line per image, all within budget.

Then re-tune `HERO_THREAD` in `act0-hero.tsx` so the thread starts at the painted clinic door, and re-run `npm run test:e2e`.

- [ ] **Step 2: Lighthouse, mobile, against the production build**

Run in one terminal: `npm run build && npx next start -p 3100`

Then in another:
```bash
CHROME_PATH="$(node -e "console.log(require('playwright-core').chromium.executablePath())")" \
  npx lighthouse@13.5.0 http://localhost:3100/preview/film --only-categories=performance \
  --output=json --output-path=../docs/lighthouse-film-slice.json --chrome-flags="--headless=new" --quiet
node -e "const r=require('../docs/lighthouse-film-slice.json');const a=r.audits;console.log('score',Math.round(r.categories.performance.score*100),'LCP',a['largest-contentful-paint'].displayValue,'CLS',a['cumulative-layout-shift'].displayValue,'TBT',a['total-blocking-time'].displayValue)"
```
Expected: score ≥ 90, LCP ≤ 2.5 s, CLS ≤ 0.05. Delete the JSON after recording the numbers; it is not committed.

If the budget is missed:
- **LCP:** check that only `act0` loads eagerly and that the Fraunces preload is present in the page head.
- **TBT:** check that the timelines are created inside `useGSAP`, not during render.

- [ ] **Step 3: Scroll smoothness trace**

In Chrome DevTools on `http://localhost:3100/preview/film`:
1. Performance panel, CPU 4× slowdown, Record.
2. Scroll from the top to the footer at a normal pace, then Stop.

Expected: no long tasks over 50 ms while scrolling, and frames steady. Record the worst frame time.

- [ ] **Step 4: Screenshots for the owner**

Save this one-off script as `web/shots.tmp.mjs` (it must sit in `web/` to find `playwright-core`), run `node shots.tmp.mjs` with the production server still running, then **delete it**; it is not committed:
```js
import { chromium } from "playwright-core";
const b = await chromium.launch();
for (const [name, viewport, reducedMotion] of [
  ["desktop", { width: 1440, height: 900 }, "no-preference"],
  ["phone", { width: 390, height: 844 }, "no-preference"],
  ["reduced", { width: 1440, height: 900 }, "reduce"],
]) {
  const p = await b.newPage({ viewport, reducedMotion });
  await p.goto("http://localhost:3100/preview/film");
  await p.screenshot({ path: `../.playwright-mcp/film-${name}-hero.png` });
  await p.locator("#act3").scrollIntoViewIfNeeded();
  await p.evaluate(() => window.scrollBy(0, 1400));
  await p.waitForTimeout(900);
  await p.screenshot({ path: `../.playwright-mcp/film-${name}-days.png` });
  await p.getByRole("button", { name: "A bit dizzy" }).click();
  await p.locator("[data-lab]").screenshot({ path: `../.playwright-mcp/film-${name}-reply.png` });
  await p.close();
}
await b.close();
```
Expected: nine screenshots. Look at every one before sending. Nothing may overlap or be cut off, and the reduced-motion shots must show the full content.

- [ ] **Step 5: Write `docs/LANDING_FILM_SLICE_REVIEW.md`**

Record, with the real numbers and names from Steps 2–4:
- what was built: Act 0 and Act 3, with placeholders or with art;
- how to see it: `/preview/film` on the deployed site after merge, or the local command;
- the measured Lighthouse score, LCP, CLS, TBT and worst scroll frame;
- the JS difference from the Task 5 test log;
- the AVIF sizes, if any;
- the screenshot list;
- what the owner is asked to decide:
  1. Is the storybook style right?
  2. Is Aminah's drawing good enough, or should she be redrawn before more acts?
  3. Is the pin length right: too long, or too short?
  4. Is "Reply for Aminah" clear?
  5. Go or no-go for phase 2.

- [ ] **Step 6: Final checks and commit**

Run (in `web/`): `npm run lint && npm run typecheck && npm run test:e2e && npm run test:assets`
Expected: all pass.
```bash
git add docs/LANDING_FILM_SLICE_REVIEW.md web/public/film web/components/film/backdrops.ts
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Record the film slice's measured budgets for the owner's review"
```

- [ ] **Step 7: Push and open the PR** (the owner merges; `/` is unchanged, `/preview/film` goes live with `noindex`)

```bash
git push -u origin feat/landing-storybook-film
gh pr create --title "Landing film: vertical slice at /preview/film" --body-file ../docs/LANDING_FILM_SLICE_REVIEW.md
```

---

## Appendix: AI background prompts (for the owner)

Generate each scene in **both** sizes, pick the best take, and save it as `<act>-desktop.png` / `<act>-mobile.png` in one folder. Then give Claude the folder path (Task 7, Step 1).

**Shared style: paste at the start of every prompt:**
> Hand-painted storybook illustration for an animated film background. Soft watercolour and gouache on warm textured paper, visible paper grain, gentle hand-drawn edges, atmospheric depth. Warm, dignified, quietly hopeful. Palette limited to cream (#F6EFE4), coffee brown (#3B2A20), milk-tea brown (#B08968), pandan green (#2F6B4F), warm amber light (#E8A33D) and dusk blue (#1E2A44). Malaysian setting. No people, no animals in the foreground, no text, no letters, no numbers, no signage lettering, no logos, no readable screens, no watermark.

**Sizes and calm areas (add to every prompt):**
- **Desktop:** 16:9 (2400×1350), and for Act 3 32:9 (4800×1350). *"Keep the left 40% of the frame calm and uncluttered (open sky or plain wall) for text."*
- **Mobile:** 9:16 (1080×1920). *"Keep the top 35% of the frame calm and uncluttered for text; the main subject sits in the lower half."*

| File prefix | Scene prompt (after the shared style) |
|---|---|
| `act0` (hero, phase 1) | Morning in a small Malaysian town. A row of pre-war two-storey shophouses with a covered five-foot way, green wooden shutters and pastel walls. The middle shop is a small community clinic: a glass door with a plain white cross shape above it (no words), potted bougainvillea and a bench outside. A frangipani tree, a parked kapchai motorcycle, soft morning sun from the right casting long gentle shadows along the pavement. The pavement runs left to right across the lower fifth of the frame. The clinic door sits a little left of centre. |
| `act3` (thirty days, phase 1) | One continuous wide panorama seen through the large open window of a Malaysian terrace-house living room, with no seams. Time of day flows from left to right: soft dawn at the far left, bright late morning, noon, warm late afternoon, then golden dusk at the far right. The foreground is a low window ledge with potted plants, a rattan chair edge and light curtains, repeating gently across the width. Outside: rooftops, a coconut palm and a distant hill line that continue across the whole strip. (Mobile: the same room at late afternoon, window in the lower half.) |
| `act1` (phase 2) | A close, slightly top-down view of a wooden kitchen table in a kampung house: plastic medicine packets, a blister strip, a folded printed sheet whose marks are soft blurs (unreadable), a glass of teh tarik and a small plate of kuih. Window light from the right, a rattan chair edge, a calendar on the wall with no readable numbers. |
| `act2` (phase 2) | A calm, small clinic consultation room: a wooden desk with a stethoscope, a closed laptop, a jar of tongue depressors and a potted plant; a window with afternoon light through blinds, soft striped shadows on the wall, and an examination bed with a folded sheet in the background. |
| `act4` (phase 2) | A high-floor apartment in Kuala Lumpur at night, seen from inside: a large window with soft city-light bokeh and generic tall towers in the distance (no recognisable landmark), a warm table lamp, the corner of a sofa with a cushion, and a small plant. Warm interior against a deep blue night. |
| `act6` (phase 2) | A quiet kampung road at night just after rain: wet reflections, one warm street lamp, wooden houses on stilts with warmly lit windows, frangipani and banana trees, a few fireflies, and a calm deep-blue sky with faint stars. |
| `act7` (phase 2) | A high aerial view of the same small Malaysian town at night: clusters of houses with warm windows, gently winding roads, palm and rubber trees, a slow river and light mist. The upper half is a calm starry sky with a soft Milky Way. |

Tips: generate 4–8 takes per scene and choose the one whose calm area is truly empty. Keep the same tool and style prompt for every scene so they look like one book.
