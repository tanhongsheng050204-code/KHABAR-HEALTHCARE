# Landing film: vertical slice review (29 Sep 2026)

The first two scenes of the new "storybook film" landing page are built at **`/preview/film`**. The current landing page at `/` is unchanged. The preview is marked `noindex, nofollow` and nothing links to it.

- Spec: `docs/superpowers/specs/2026-09-28-landing-storybook-film-design.md`
- Plan: `docs/superpowers/plans/2026-09-28-landing-film-slice.md`

## What was built

- **Act 0, the hero:** Aminah steps out of the clinic, and a thread of light runs from the door to her.
  - Scrolling pins the scene. She walks on, and the thread draws itself along the street behind her.
  - The BM / 中文 / தமிழ் / EN chips change the greeting language for the whole page.
- **Act 3, thirty days at home:**
  - On desktop the scene pins and pans sideways through days 1, 3, 7, 14 and 30. The light warms towards dusk, and day 7's "pening" turns the thread amber. On phones the days stack vertically.
  - **Reply for Aminah** follows. A visitor chooses "I'm okay", "A bit dizzy" or "Chest pain" and sees exactly what Khabar sends back. The words are copied from `PatientMessages.java` in all four languages.
  - The page never says a nurse will call. A reply "goes onto / to the top of the clinic's follow-up list".
- **Motion:** "Pause motion" and the device's reduced-motion setting both turn every scene into its still end frame. Nothing is lost, and the page also works without JavaScript.
- **Backgrounds:** placeholder gradients for now. `node scripts/film-assets.mjs <folder>` (in `web/`) turns the AI art into budgeted AVIF and WebP files. The prompts are in the plan's appendix.

## How to see it

- **After merge:** `https://khabar-landing-six.vercel.app/preview/film` on desktop and on a phone.
- **Locally:** in `web/`, run `npm run build && npx next start -p 3100`, then open `http://localhost:3100/preview/film`.

## Measured budgets

All measurements were taken on the production build, locally, on 29 Sep.

| Budget (spec §8) | Target | Measured | Verdict |
|---|---|---|---|
| Lighthouse performance, mobile | ≥ 90 | 91, 91, 92 (3 runs) | Met |
| LCP, mobile simulated 4G | ≤ 2.5 s | 2.9 s simulated; **0.36–0.40 s observed** | **Missed by 0.4 s** (see below) |
| CLS | ≤ 0.05 | 0 | Met |
| FCP | — | 0.9 s (current `/`: 1.4 s) | Faster than today |
| TBT | — | 230–240 ms (current `/`: 270 ms) | Same as today |
| JS added over `/` | ≤ 70 KB gzip | 55–66 KB (the range depends on when Next prefetches `/login`) | Met |
| Fonts / CSS | — | 67 KB / 30 KB | Held by a test |
| Scrolling, phone profile (CPU 4×) | no frames > 50 ms | 0 long tasks; median frame 16.7 ms, worst 33 ms | Met |
| Scrolling, desktop 60 fps | 60 fps | Inconclusive: the headless browser here has no GPU, and the current `/` drops as many frames on the same machine | Check on a real device |
| AVIF weight | ≤ 180 / 110 KB | No art yet | — |

**Why LCP was 5.1 s at first, and what fixed it:**
1. **Chinese and Tamil web fonts.** The language chips put both scripts on the first screen, so about 170 KB of fonts and a 68 KB blocking stylesheet loaded at once. Those two languages now use the fonts already on the device. This differs from spec §5.
2. **The pin moved the hero in the page.** Chrome counted the moved text as a new, late paint. The hero now supplies its own pin spacer, so nothing moves.
3. **The heading font.** Fraunces now loads as static weight 400 instead of the variable font: 108 → 67 KB.

**The remaining 0.4 s** in the simulation is mostly JavaScript: the Next.js framework plus GSAP. The current `/` measures 2.7 s by the same method. The next lever is loading GSAP only after first paint, which is a larger change. Say if it is worth doing before phase 2.

## Checks

- **Browser tests:** 72 pass, and 14 are skipped by design because they are desktop-only or phone-only. They cover:
  - languages, by mouse and by real `Tab` keyboard use;
  - pinning and drawing, arriving via `#act3`, reloading mid-page, resizing, turning a phone sideways, and pausing inside the pin while keeping the reader's place;
  - the exact reply texts;
  - zero axe violations, with motion on and with reduced motion;
  - a 3:1 focus ring;
  - nothing running off a 320 px screen in Tamil, and the greeting never covering the buttons;
  - the hero thread drawn correctly at 1920×1080 and 2560×1440, checked on real pixels;
  - the days fitting a 1366×650 laptop screen;
  - the JS, font and CSS weight.
- **Unit tests:** 35 pass (`npm run test:unit`). They cover the asset pipeline, plus a check that runs every reply the page shows through the product's triage word lists, so a reply shown in green really is triaged "ok".
- **Lint and types:** clean.

## Independent review (29 Sep)

A fresh reviewer read the whole branch and found 0 critical and 7 important issues. All 7 are fixed, each with a test that failed first:
1. **Hero thread on other screen sizes:** it broke on any desktop size except 1440×900. It stopped short at 1920 wide and left a stray dash at 2560.
2. **Replies that didn't match triage:** twelve replies shown in green would really be triaged "review". The Tamil "I'm okay" in "Reply for Aminah" did not match what Khabar sends. The replies are reworded with words from the product's "ok" list.
3. **Short screens:** the pan cut off the days on a 1366×650 laptop, and a phone held sideways got a pan taller than its screen. The pan now needs at least 600 px of height, and it scales to fit.
4. **Pause losing the reader's place:** pausing or resuming threw the reader elsewhere on the page. Now the day being looked at stays in view.
5. **Screen readers on phones:** days not yet scrolled to were hidden from screen readers.
6. **Focus ring:** the amber ring was too faint (2.06:1). It is now kopi, about 13:1.
7. **Test gaps:** tests that looked complete but weren't were fixed. This uncovered the greeting bubble running off, and then covering the buttons, on a 320 px phone. Both are fixed.

A malformed link such as `#100%` used to crash the page. It is now ignored.

**Known issue, deferred:** dragging a desktop window narrower than 760 px mid-page sends the reader back to the top. Turning a phone sideways keeps the reader's place (tested).

Ten minor points are recorded for phase 2, for example: the Pause button comes last in keyboard order, and CSS hover transitions still run while paused.

## Screenshots

They are in `.playwright-mcp/` next to the repository, not committed:
- `film-desktop-hero.png`, `film-desktop-days.png`, `film-desktop-reply.png`
- `film-phone-hero.png`, `film-phone-days.png`, `film-phone-reply.png`
- `film-reduced-hero.png`, `film-reduced-days.png`, `film-reduced-reply.png`

## Decisions for the owner (the gate)

1. **Style:** is the storybook look right? This slice uses placeholder backgrounds; the painted AI backgrounds will do most of the work.
2. **Aminah:** is the code-drawn Aminah good enough next to painted art, or should she be redrawn before more scenes?
3. **Pin length:** does the hero or the thirty-day pan feel too long or too short?
4. **Reply for Aminah:** is it clear what happens for each reply?
5. **LCP:** accept 2.9 s simulated for now (observed 0.4 s), or make GSAP load after first paint first?
6. **Go or no-go for phase 2:** Acts 1, 2, 4, 5, 6 and 7, then the real backgrounds.

---

# Phase 2a: Acts 1, 2 and 4 (29 Sep 2026)

Three more scenes are on `/preview/film`, in story order: hero → **Act 1** → **Act 2** → thirty days → **Act 4**. The page at `/` is still unchanged.

- Plan: `docs/superpowers/plans/2026-09-29-landing-film-phase-2a.md`

## What was added

- **Act 1, the paper:** Aminah's prescription in the clinic's shorthand (`Tab metformin 500mg bd pc`). As the reader reaches each line, its plain meaning appears under it in her language ("1 biji, pagi dan malam, selepas makan."). Each line is also a button, so a reader can reveal it early or hide it again. The chips switch all four languages.
- **Act 2, the fifteen minutes:** the doctor's notes, then Khabar's draft table, then a red stamp on the planted dose error: "Metformin 10000 mg a day is above the 3000 mg maximum." Below it sits the existing clinic and patient preview from `/`. Its code now loads only when the reader comes within 600 px of it (see the budgets below).
- **Act 4, her daughter in KL:** the thread reaches Nurul's phone, which shows the approved care plan. The switch "Aminah shares her care plan with Nurul" turns sharing off, and her phone then shows only "This care plan is no longer shared with you." The switch works with a mouse, Space and Enter, and the change is announced to screen readers.
- **Reader's place:** Pause motion now keeps the reader on the act they are looking at, in every act, as it already did in the thirty-day pan. A reader who scrolls before the page has finished loading stays where they scrolled to.

## Where the words come from

Every medicine line, dose instruction, draft row and safety finding on these acts is the product's own output, not copy written for the page. The labels around them ("Mak's care plan", "Safety check · Blocked", and the lock message on Nurul's phone) are page copy describing what the product does.
- `services/agents/scripts/make_film_words.py` runs the real `parse_line`, `build_summary` and `evaluate` on the shorthand and the doctor's notes. It writes `web/components/film/product-words.json`, which the acts read.
- `services/agents/tests/test_film_words.py` fails if the JSON and the agents' output ever differ. It also checks that the stamp really is a CRITICAL finding. If the parser or the dose rules change, rerun the script.
- Building this found a real parser bug: lowercase shorthand like `bd pc` dropped the medicine. That fix shipped separately as PR #18.

## Measured budgets

Measured on the production build, locally, on 29 Sep. Mobile Lighthouse was run three times.

| Budget | Target | Measured | Verdict |
|---|---|---|---|
| Lighthouse performance, mobile | ≥ 90 | 90–91 | Met |
| LCP, mobile simulated | ≤ 2.5 s | 2.9 s simulated (unchanged from the slice); about 0.45 s observed | Missed by 0.4 s, as accepted at the slice gate |
| CLS | ≤ 0.05 | 0 | Met |
| JS added over `/` | ≤ 70 KB gzip | about 61 KB | Met |
| Fonts / CSS | ≤ 130 KB / ≤ 45 KB | 67 KB / 32 KB | Met (held by a test) |

With the preview in the first load, Lighthouse fell to 88 and LCP to 3.3 s. Loading it only when it is near brought both back.

## Checks

- **Browser tests:** 113 pass, and 15 are skipped by design (desktop-only or phone-only).
  - New for these acts:
    - revealing and hiding each line, including before it is reached;
    - the draft and the stamp, in both animated and reduced motion;
    - consent on and off, by keyboard;
    - the care plan following the language chips;
    - the preview loading late;
    - the thread to Nurul crossing no text;
    - pausing inside Act 2;
    - story order.
  - Added after the independent review (below):
    - the thirty-day pan pinning at the top after Tamil or the preview makes the page taller;
    - Pause keeping the preview and the consent switch at the same height on screen;
    - Pause stopping the preview's own animations;
    - a screen-reader description for every act.
  - Every earlier test still passes.
- **Unit tests:** 35 pass (`npm run test:unit`).
- **Agents:** 241 pass (`pytest`).
- **Lint and types:** clean.

## Independent review (29 Sep)

A fresh reviewer read the whole branch. It found 0 critical and 4 important issues; all 4 are fixed, each with a test that failed first:
1. **The thirty-day pan pinned too early.** Switching to Tamil, or the app preview loading in, made the page taller after the pins were measured. The pan then pinned while still 64–300 px below the top, and jumped. The pins are now measured again whenever the film's height changes, and the preview's reserved space matches its real height.
2. **Pause lost the reader's place inside tall acts.** It kept the act but scrolled to its top, so a phone reader looking at the preview ended up a full screen away. The part being looked at now stays at the same height on screen.
3. **Pause did not stop the preview's own animations.** A floating note kept bobbing. The preview now sits inside the landing page's pausable motion root.
4. **The new acts had no screen-reader description**, which the spec requires for every act. Each now has one sentence.

The review also noted that the doc overstated which words come from the product. The "Where the words come from" section above now names the page copy.

Eight minor points are recorded for phase 2b. Examples:
- the consent switch's font weight is ignored because of an invalid `font` shorthand;
- the off-state track of the switch is 2.9:1 against the dusk background;
- the film-words generator builds the draft by hand instead of through `draft_from_notes`.

## Screenshots

In `.playwright-mcp/` next to the repository (not committed): `p2a-{desktop,phone,reduced}-{act1,act2,act4}.png`.

## Questions for the owner

1. **Act 1:** is it clear that the lines can be tapped, or should the first line reveal on its own as a hint?
2. **Act 2:** is the stamp strong enough without a sound or shake? (There are none on purpose.)
3. **Act 4:** Nurul is code-drawn like Aminah. Keep her until the AI art arrives?
4. **Go or no-go for phase 2b:** Act 5 (the clinic diorama), Act 6 (trust), Act 7 (the finale) and the progress rail.

---

# Phase 2b: Acts 5, 6 and 7 and the progress rail (29 Sep 2026)

The story on `/preview/film` is now complete: hero → paper → visit → thirty days → daughter → **town** → **trust** → **finale**. The page at `/` is still unchanged.

- Plan: `docs/superpowers/plans/2026-09-29-landing-film-phase-2b.md`

## What was added

- **Act 5, the whole town:** one clinic and twelve fictional homes on a small 3D board, drawn with CSS (no WebGL).
  - Each home has a thread from the clinic, coloured by its latest reply: green (doing well), amber (on the follow-up list) or red (emergency advice given).
  - The key counts them (8 / 3 / 1) and says "Illustrative".
  - Each home is a real button. Selecting one, by mouse, tap or keyboard, shows the patient's reply in their own language and what Khabar did, in Act 3's words. On phones the answer appears just below the town.
  - Scrolling in raises the homes and draws the threads.
  - The town can be turned by dragging. On phones, "Tilt to explore" asks for motion access after a tap; if that is refused, the page says so and dragging still works.
- **Act 6, trust:** the three safety principles from `/`, word for word, arriving one at a time on a quiet night.
- **Act 7, the finale:**
  - Every home's thread rises into a sky of lights, behind a calm dark patch that keeps the words clear.
  - It carries the same heading and "Enter the live prototype" button as `/`, plus "Explore the 30-day story", which returns to Act 3.
  - The finale threads are all soft green, on purpose: a red thread "rising into the sky" could read as a death.
- **Progress rail (desktop):** eight dots at the right edge.
  - The current act is marked, including while the thirty-day pan is pinned. Its name shows while you scroll and on hover or focus.
  - Every dot is a link that jumps to its act.
  - Phones do not get the rail: only the hero pins there, and a fixed rail would cover text beside the Pause button.
- **Looping lights:** the town's pings and the finale's stars move only while their act is on screen and motion is on (spec §8).

## Where the words come from

- **Town replies:** every reply in the town is one the film already shows. `web/scripts/triage-consistency.test.mjs` runs each through the product's triage word lists, so a home shown green really is triaged "ok".
- **Act 6 principles:** a browser test reads them from `/` and from the film and requires them to match.
- **Act 7 heading:** a browser test checks that `/` shows the same heading.
- **Page copy:** the status labels ("Doing well", "On the follow-up list", "Emergency advice given") and the names are page copy. The names are fictional.

## Measured budgets

These were measured on the production build, locally, on 29 Sep.
- **Lighthouse:** mobile, 8 runs on this branch and 5 on `main` (before phase 2b), both measured the same way.
- **Weights:** from the browser tests.

| Budget | Target | Measured | Verdict |
|---|---|---|---|
| Lighthouse performance, mobile | ≥ 90 | 85–92, **median 88.5** (4 of 8 runs ≥ 90). Main before this phase: 87–92, median 91 (3 of 5) | **Not met** (see below) |
| LCP, mobile simulated | ≤ 2.5 s | 2.8 s in fast runs, 3.4 s in slow runs | Missed, as accepted at the slice gate |
| CLS | ≤ 0.05 | 0 | Met |
| JS added over `/` | ≤ 70 KB gzip | 65.3 KB | Met |
| Fonts / CSS | ≤ 130 KB / ≤ 45 KB | 67 KB / 34 KB | Met (held by a test) |

**Why the score splits in two:**
- **The two groups:** every run falls into one of two groups, on `main` too. Fast runs have FCP 0.9 s and score 91–92; slow runs have FCP 1.4–1.5 s and score 85–88.
- **What differs:** both groups make the same 26 requests (400 KB). The difference is timing.
  - The likely cause, not yet proven by an A/B run: Next.js prefetches the `/login` page (its CSS included) because the header's "Sign in" link is on screen.
  - When that prefetch lands early, Lighthouse's simulation seems to count it against the first paint.
- **This phase's share:** its slow runs are about 2 points lower than `main`'s, from the extra JS and CSS of the three acts, and its median fell from 91 to 88.5.
- **Blocker:** Lighthouse ≥ 90 must be met before the film replaces `/` (phase 3).
- **Levers, for your call:**
  - turn off prefetch on the header's "Sign in" link, so the login page loads a moment slower when clicked;
  - load GSAP after the first paint, which the slice review already named as the next lever.

## Checks

- **Browser tests:** 162 pass, and 30 are skipped by design (desktop-only or phone-only). New for these acts:
  - the town's key counts and "Illustrative";
  - selecting a home by mouse, tap and keyboard, and its reply appearing in view on a phone;
  - the end frame and the scroll-in rise;
  - pings and stars stopping off screen and when paused;
  - dragging (a drag never selects a home);
  - device tilt, allowed and refused, and no tilt button on mouse screens;
  - the whole town fitting a 390 px phone;
  - every home staying its own target at 320 px. The buttons' ground centres are at least 24 px apart. The roofs of Puan Rosnah's and Mr Lim's homes are 18 px apart there, but each still takes its own tap. At 320 px the board's corners are clipped by about 26 px;
  - Act 6 matching `/` word for word;
  - Act 7's heading and button, the way back to Act 3, and no thread running through its words;
  - the rail, including during the pinned pan;
  - story order and a scene description for every act.
- **Unit tests:** 47 pass, including the town's 12 replies.
- **Lint and types:** clean.

## Independent review (29 Sep)

A fresh reviewer read the whole branch and tried it on a build. It found 0 critical and 3 important issues. I raised a fourth to important, because the spec requires the wording. All four are fixed, each with a test that failed first:
1. **Dragging could get stuck.**
   - Releasing the mouse just outside the town left the town following the mouse.
   - After a finger drag, the next keyboard press on a home was swallowed.
2. **The town could only be turned by dragging.** "Turn left" and "Turn right" buttons now do it by keyboard or a single tap.
3. **Focus looked the same as selection.** A focused home now gets a ring, and a selected home a roof filled in its status colour. Both stay visible in Windows high-contrast (forced colours) mode.
4. **The emergency line was missing a caveat.** It dropped the note that the clinic may not have seen the reply yet, which spec §4 requires. The note is back, and the green line now says "nothing of concern" rather than "all is well".

The docs also stated the phone geometry and the Lighthouse results too strongly; both are corrected above. Eight minor points are recorded for phase 3. Examples:
- "Tilt to explore" can look on but do nothing on a phone without motion sensors;
- the finale's screen-reader description says "from above" for a side-on skyline;
- phones see only a third of the finale's homes.

## Screenshots

In `.playwright-mcp/` next to the repository (not committed): `p2b-{desktop,phone,reduced}-{act5,act6,act7}.png`.

## Questions for the owner

1. **The town:** do the simple box homes read well next to the painted acts, or should they get roofs and windows before the switch-over?
2. **Tilt:** please try "Tilt to explore" on a real phone. Emulators cannot show how it feels.
3. **The rail:** is the right edge the right place, and is desktop-only right?
4. **Lighthouse:** turn off the "Sign in" prefetch now, or handle performance in phase 3?
5. **Phase 3:** generate the AI backgrounds (the prompts are in the slice plan's appendix), then make the film the real `/`.
