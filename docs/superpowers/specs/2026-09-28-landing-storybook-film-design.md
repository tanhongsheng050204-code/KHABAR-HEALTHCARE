# Landing page: "a storybook film" — design

**Date:** 28 Sep 2026 · **Status:** approved in conversation, awaiting written-spec review
**Replaces:** the hero, "three moments" and 30-day sections of `web/app/page.tsx`. Supersedes the video-based plan in `docs/landing-page-brief.md` (kept as history).

## 1. Goal and audience

Make the landing page memorable and moving, and keep it very easy to use. It has one mixed audience:
- hackathon judges and demo viewers, who must "get it" in under two minutes;
- clinic doctors and nurses, who must trust it is professional and safe;
- patients and families, including older adults, who must find it warm and readable.

**Success:**
- A first-time visitor understands what Khabar does by scrolling alone, without clicking anything.
- The page meets the performance and accessibility budgets in §8 on a mid-range phone.
- The owner reviews the vertical slice (§10) and approves it before the rest is built.

## 2. Decisions (made with the owner, 28 Sep)

| Question | Decision |
|---|---|
| Visual direction | **D: a storybook film.** Illustrated storybook look (B), cinematic camera language (A), and the 3D town (C) as one chapter. Chosen from three live prototypes. |
| Illustration source | **C: hybrid.** AI-generated painted backgrounds for texture and "wow"; foreground characters, phone, chat bubbles and the thread are code-drawn SVG, so they can move and respond. |
| Animation engine | **GSAP + ScrollTrigger** (free for all use since 2025), with `@gsap/react`. Native scrolling is kept: no scroll-jacking or smooth-scroll libraries. |
| 3D town | CSS 3D transforms, not WebGL. |

## 3. Story: Aminah's day, morning to night

One continuous thread of light follows Aminah through the page. Its colour is the page's state language:
- **green (pandan):** all well;
- **amber:** needs attention;
- **red (cili):** true emergencies only.

The sky warms from morning to dusk to night as the reader scrolls down.

Each act is a **pinned scene** with a scroll-scrubbed timeline. Every act has an **end frame**: the complete, static composition that reduced-motion users see and that every animation settles into.

| Act | Background (AI art) | Foreground and motion (code) | Interaction | Copy (HTML) |
|---|---|---|---|---|
| **0 · Hero** | A small-town Malaysian clinic front, morning | Aminah walks out. The thread is born at the clinic door and follows her. The camera pushes in slowly. The headline resolves from a soft blur. | Language chips BM / 中文 / தமிழ் / EN set the greeting language for the whole page. A scroll cue. | "The visit ends. *Care should not.*" plus the existing lede and "Find your care space" |
| **1 · Lost in the paper** | Her kitchen table: medicine packets, a printed sheet | Scroll melts clinical shorthand into her language (`1 tab BD pc` → *1 biji, pagi & malam, lepas makan*), one line at a time | Tap any shorthand to see its plain version | "Instructions written for clinicians. Patients who think in BM, 中文 or தமிழ்." |
| **2 · The fifteen minutes** | The doctor's desk | Notes become a draft; the safety check blocks a planted dose error with a red stop stamp. | The existing clinic / patient `ProductPreview` switch lives here. | The existing "A clearer day. On both sides of care." copy |
| **3 · Thirty days at home** | The same window at home, morning to dusk (horizontal pan) | Day 1, 3, 7, 14 and 30. The phone lights up with "Apa khabar?" (in the chosen language) and she replies. On day 7, "pening" turns the thread amber and it travels back to the clinic. | **Reply for Aminah:** "Okay", "A bit dizzy" or "Chest pain". Each shows the real outcome (see §4). | "Care is a conversation. Keep it going." |
| **4 · Her daughter in KL** | A city apartment at night | The thread reaches Nurul's phone. | A consent switch: when Aminah shares, Nurul sees the summary; when she stops, Nurul's view locks at once. | "Family sees what she chooses to share." |
| **5 · The whole town** | None: the CSS 3D diorama on a soft gradient | Houses rise one by one. One thread per patient runs from the clinic, coloured by status. | Mouse or finger drag tilts the town (device tilt on phones, only after a tap, with drag as the fallback). Selecting a house shows its status. Every number is labelled "Illustrative". | "One clinic. Every home it cares for." |
| **6 · Trust** | A quiet night street | The three existing safety principles, revealed one at a time | None | The existing safety copy |
| **7 · Finale** | The town at night from above | Every thread rises and joins into a sky of lights. | "Enter the live prototype" | "See the whole story, not just the appointment." |

The footer and the "Concept prototype using fictional patient data" line stay as they are.

## 4. Truthfulness rules for the copy

The page must not promise more than the product does:
- **Callbacks:** it never says a nurse *will* call. An amber reply "goes to the top of the clinic's call list" (matching commit fe880c6, "Stop promising unseen clinic callbacks").
- **Act 3 outcomes:**
  - "Okay" gets the acknowledgement text.
  - "A bit dizzy" gets the acknowledgement, turns the thread amber, and lifts her on the call list.
  - "Chest pain" gets the fixed 999 advice at once, in red, with the note that the clinic may not have seen the reply yet.
  - All wording comes from `PatientMessages` (ms / en / zh / ta).
- **Statistics:** none without a cited source. The "35%" figure in the old brief is not used unless a source is found and linked.
- **Data:** every person and number is fictional and says so where it could be mistaken for real data.

## 5. Visual system

- **Colour:** the brief's tokens.
  - `santan` #F6EFE4 for backgrounds and `kopi` #3B2A20 for text;
  - `pandan` #2F6B4F, `amber` #E8A33D and `cili` #C8452D for the thread states;
  - `dusk` #1E2A44 for night scenes.
  - Existing app tokens stay for the app pages.
- **Type:**
  - Fraunces for display and Plus Jakarta Sans for body, both through `next/font`.
  - Noto Sans SC and Noto Sans Tamil are loaded only when those greetings are shown.
- **Illustration:**
  - Backgrounds share one style prompt: painted, watercolour on warm paper, soft edges, Malaysian small-town details, no text, no logos, no readable screens, and the calm area kept free for copy.
  - Foreground SVG uses flat shapes with a single `kopi` outline weight, so sharp foreground over soft background reads as depth of field.
- **Motion principles:**
  1. One main action per scene.
  2. Parallax depth: background layers move slower than foreground layers.
  3. Motion carries meaning (the thread's colour, the time of day); it is never decoration only.
  4. One shared set of easing curves and durations (`film/tokens.ts`).
  5. Interaction is a bonus: the story is complete without any click.

## 6. Architecture

```
web/app/page.tsx                 composes the acts; server component
web/components/film/
  film-provider.tsx              registers GSAP + ScrollTrigger; gsap.matchMedia for reduced motion and breakpoints;
                                 bridges the existing "Pause motion" button; greeting-language context
  tokens.ts                      colours, easings, durations, breakpoints
  scene.tsx                      <Scene> wrapper: pinned section, background layers, end-frame fallback
  thread.tsx                     the thread: SVG path, draw-on, travelling pulse, green/amber/red states
  characters/aminah.tsx, nurul.tsx, phone.tsx, bubble.tsx   rigged SVG parts (blink, wave, buzz, pop)
  acts/act0-hero.tsx … act7-finale.tsx                     one file per act, each with its own timeline
  diorama.tsx                    CSS 3D town (from the prototype), tilt and selection
  film.module.css
web/public/film/<act>-desktop.avif|webp, <act>-mobile.avif|webp   AI backgrounds (placeholders until delivered)
```

- **Timelines:** each act builds its timeline in `useGSAP` (automatic cleanup) inside the provider's `matchMedia` context.
  - Desktop and mobile get different timelines.
  - Reduced motion gets no timeline, only the end frame.
- **Background placeholders:** until the AI art arrives, each act uses a CSS-gradient placeholder with the same dimensions, so the build is never blocked on assets.
- **Reuse and removal:**
  - `ProductPreview` is reused in Act 2.
  - `care-story`, `care-thread` and `followup-story` are removed once the acts that replace them ship.
- **Dependencies added:** `gsap`, `@gsap/react`. No other new runtime dependencies.

## 7. Mobile, accessibility and reduced motion

- **Mobile:** the same story in portrait compositions, with separate portrait backgrounds.
  - Camera moves are halved and horizontal pans become vertical.
  - Every interaction is a tap.
  - Device tilt for Act 5 is asked for only after a tap on "Tilt to explore"; refusing leaves drag working.
- **Accessibility:**
  - All copy is real HTML text over the art, in reading order.
  - SVG characters are `aria-hidden`, and each act has a one-sentence visually-hidden description.
  - Every interaction is a real button or switch that works with the keyboard and has a visible focus ring.
  - The skip link and the existing automated accessibility checks stay, with zero violations.
- **Reduced motion and pause:** with reduced motion (or "Pause motion" pressed):
  - ScrollTrigger pins are off;
  - each act shows its end frame;
  - interactions change state without animating;
  - no content is lost.

## 8. Performance budgets

Measured on the production build:

| Metric | Budget |
|---|---|
| LCP (mobile, simulated 4G, Lighthouse) | ≤ 2.5 s |
| Lighthouse performance (mobile) | ≥ 90 |
| CLS | ≤ 0.05 |
| Scroll smoothness | 60 fps on desktop, no long frames > 50 ms while scrolling on a mid-range Android profile (Chrome DevTools CPU 4× slowdown) |
| Per-background weight | ≤ 180 KB AVIF desktop, ≤ 110 KB mobile |
| Added JS for the landing route | ≤ 70 KB gzipped (GSAP, ScrollTrigger and the acts) |

- **Loading:**
  - only the hero background loads eagerly, with a `preload` hint;
  - every other background loads when its act is within one viewport.
- **Motion:**
  - only `transform` and `opacity` are animated;
  - `will-change` is set only while an act is active;
  - offscreen loops (blink, pulse) pause.

## 9. AI background assets

Needed: **seven scenes** × desktop (2400×1350, calm left 40%) and mobile (1080×1920, calm top 35%). The scenes are Acts 0, 1, 2, 3 (a wide strip for the pan, 4800×1350 on desktop), 4, 6 and 7.

- The implementation plan will carry one ready-to-paste prompt per scene, sharing the style prompt in §5.
- The owner generates them with any image tool (Midjourney, Higgsfield or ChatGPT images) and picks the best take.
- Claude then crops, colour-grades them to the tokens, and exports AVIF and WebP.
- No real people, brands or readable text may appear in any image.

## 10. Delivery in phases, with a review gate

1. **Vertical slice:** the provider, tokens, thread and characters, **Act 0 (hero)** and **Act 3 (reply for Aminah)**.
   - Built to final quality, with placeholder backgrounds or the first AI backgrounds if they are ready.
   - Behind the existing landing page at `/preview/film`, so production is untouched.
   - **Gate:** the owner reviews on desktop and phone, with budgets measured, and approves or asks for changes.
2. **Remaining acts:** 1, 2, 4, 5, 6 and 7, then real backgrounds for all of them.
3. **Switch-over:** the film becomes `/`. The old hero, care-story, care-thread and followup-story are removed, and `/preview/film` is deleted.

## 11. Testing

- `npm run lint`, `npm run typecheck` and `npm run build` for every change.
- Playwright checks at 1440×900 and 390×844, each with motion on and with reduced motion:
  - every act's end frame renders its copy;
  - every interaction works with the mouse and with the keyboard;
  - there is no horizontal page scroll;
  - automated accessibility checks show zero violations.
- A Lighthouse mobile run against the budgets in §8, and a DevTools performance trace of a full scroll with CPU slowdown.
- A screenshot set for the owner at each gate: desktop, phone and reduced motion.

## 12. Out of scope

- Changes to the login, doctor, patient or caregiver app screens.
- WebGL / Three.js, video backgrounds, sound, and scroll-jacking or smooth-scroll libraries.
- Translating the landing page's body copy. Only the greetings change language; the body stays English, as today.

## 13. Risks

| Risk | Mitigation |
|---|---|
| AI backgrounds vary in style between scenes | One shared style prompt, the owner picks from several takes, and Claude colour-grades all of them to the same palette. Placeholders keep the build moving. |
| Code-drawn characters look basic next to painted backgrounds | A deliberately simple, confident flat style, reviewed at the vertical-slice gate before more acts are built. |
| Pinned scroll scenes feel long or confusing | Each act's length is tuned at the gate; a slim progress rail shows the act name; the skip link and nav jump straight to acts. |
| Performance on older phones | Budgets are enforced by measurement; mobile timelines are lighter; reduced motion is always complete. |
