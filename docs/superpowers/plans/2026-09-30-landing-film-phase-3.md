# Landing film phase 3: polish, performance and the switch-over — implementation plan

> **For agentic workers:** executed natively (superpowers:executing-plans) on the owner's standing instruction of 29 Sep: "just build everything until it is done without asking me anything". Every choice a skill would ask about is a ledgered ruling instead.

**Goal:** Make the storybook film the real landing page at `/`, after fixing every deferred minor from the slice, phase 2a and phase 2b, and meeting the Lighthouse budget.

**Architecture:**
- **One branch:** `feat/landing-film-phase-3`, in seven tasks. Each task is test-first, with the full suite run at its end.
- **The switch-over:** it moves the film from `app/preview/film/page.tsx` to `app/page.tsx`. `/preview/film` permanently redirects to `/`, and the old landing's hero, care-story, care-thread, followup-story and `LandingMotion` are removed. `landing.module.css` loses its unused rules, proven by a pixel-identical screenshot of the app preview before and after.

**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Modules, GSAP ScrollTrigger, Playwright, node:test, Python 3.12 + pytest.

**Spec:** `docs/superpowers/specs/2026-09-28-landing-storybook-film-design.md`. The relevant sections are §8 (budgets), §10.3 (the switch-over) and §7 (accessibility and reduced motion).

## Global Constraints

The constraints of phases 2a and 2b still apply:
- truthfulness;
- static-first rendering;
- real buttons with visible focus;
- budgets: JS added over the old `/` ≤ 70 KB, fonts ≤ 130 KB, CSS ≤ 45 KB, zero axe violations, and no horizontal overflow at 320 px in Tamil;
- plain `git commit` under the user's git config, with no co-author trailer;
- land on `main` and push when the work is verified.

**Out of the owner's reach, so not done here:** generating the painted AI backgrounds, which needs the owner's image-tool accounts. The pipeline (`web/scripts/film-assets.mjs`) is fixed in Task 5 so that dropping the art in later is one command.

## Review Focus

1. After the switch-over, `/` must still pass every film test, and old links must land somewhere sensible: `/preview/film` redirects to `/`.
2. **Pause:** stops every loop and transition, including hover lifts and colour fades, and comes early in keyboard order.
3. **The budgets and Lighthouse** are measured on the real `/`, and the JS budget is measured against the old `/`'s recorded size, not against itself.
4. **Screen readers** get accurate descriptions (the Act 7 sentence), live regions that actually announce, and a correct `aria-pressed` on the homes.
5. **No regression to the app preview** in Act 2 when `landing.module.css` is pruned: a pixel-identical screenshot.

---

### Task 1: Motion hygiene (slice S1–S4, 2a A6 and A8, 2b B3)
- **Pause button (S1):** it comes first in keyboard order, before the header links.
- **Transitions under Pause (S2):** they run only while `.film[data-motion="on"]`. This covers the thread colour fade and hover lifts.
- **Server render of the Pause button (S3):** it does not say "Reduced motion is on" until the browser has confirmed it.
- **The thread's breathing (S4):**
  - it animates opacity, not stroke-width on a blurred path;
  - it breathes only while its act is on screen;
  - `will-change` applies only while motion is on.
- **Early-scroll listener (A6):** it is not attached for reduced-motion users.
- **Blinks (A8):** Aminah's and Nurul's blinks run only on screen.
- **Tests (B3):** the looping-lights tests also check that the lights stop after the reader scrolls away.

### Task 2: Act fixes (slice S8; 2a A1, A2, A4, A5, A7; 2b B6, B7, B8)
- **Day 7 caption (S8):** it quotes the Day 7 reply in the chosen language.
- **Consent switch:**
  - its font declaration is valid (A1);
  - its off-state track is ≥ 3:1 against dusk (A2).
- **Act 2 stamp (A4):** it no longer carries a live-region role that never announces.
- **Act 2 timing column (A5):** it falls back to the raw timing value.
- **Tests (A7):**
  - a language switch after a line is revealed;
  - Pause part-way through the Act 2 reveal;
  - the early click waiting for hydration.
- **Homes (B6):** pressing the selected home again clears the selection, so `aria-pressed` is an honest toggle.
- **Act 6 on reload (B7):** a principle already scrolled past starts shown on hydration, with no fade out and in.
- **Amber and red threads in the town (B8):** they get a dark outline, for ≥ 3:1 against the board.

### Task 3: Town and finale fixes (2b B1, B2, B4, B5)
- **320 px (B1):** measure the spacing on the roofs, fit the whole board at 320 px (scale 0.5 at ≤ 360 px), and run the fit test at 320 and 390.
- **Refusal test (B2):** it drags after the refusal.
- **Tilt (B4):**
  - if no sensor reading arrives within 1 s, tilt switches off and says so;
  - the hint drops "or tilt your phone" after a refusal.
- **Finale (B5):**
  - the Act 7 description matches the drawing;
  - phones get a portrait sky that shows every home.

### Task 4: Product words through the real pipeline (2a A3)
`make_film_words.py` uses `draft_from_notes(NOTES)` and `source_text`, as the API does. If the product then adds findings, the film shows only the first CRITICAL one, and a test pins that.

### Task 5: Asset pipeline (slice S6, S9)
- **`film-assets.mjs`:**
  - it merges into the existing `backdrops.ts` instead of overwriting it;
  - it fails on duplicate source names;
  - Act 3's wide art is positioned from the left, not cropped from the centre.
- **Hero background:** a preload hint when the art exists.

### Task 6: The switch-over (spec §10.3; slice S5, S7)
- **The move:** the film page moves to `app/page.tsx`, indexable, with the landing's title and description. `/preview/film` permanently redirects to `/`.
- **Removals:** `care-story`, `care-thread`, `followup-story`, `CareOrbit`, `Reveal` and `LandingMotion` are removed. `LandingMotionScope` stays for the preview.
- **CSS:** `landing.module.css` is pruned of rules nothing uses, proven by a pixel-identical preview screenshot before and after.
- **Tests:**
  - they move from `/preview/film` to `/`;
  - the Act 6 and Act 7 copy tests compare with the recorded old-landing strings;
  - the route test checks `/` (indexable) and the redirect;
  - the JS budget compares with the old `/`'s size recorded before the switch-over (S5).
- **Playwright (S7):** it no longer reuses a stale server.

### Task 7: Lighthouse ≥ 90 on `/`
- **Step 1:** an A/B test of the "Sign in" link's prefetch, over 5 runs each.
- **Step 2:** if that is not enough, load GSAP after the first paint.
- Record medians. Target: the median ≥ 90 and LCP as low as the structure allows.

### Task 8: Docs, final review, land
- **Review doc:** a "Phase 3" section in `docs/LANDING_FILM_SLICE_REVIEW.md`.
- **Final review:** a fresh Opus reviewer, then one test-first fix pass.
- **Land:** merge to main, rerun the checks, push.
