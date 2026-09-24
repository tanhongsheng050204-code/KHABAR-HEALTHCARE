# Khabar UI redesign — implementation and verification

Date: 24 September 2026  
Status: implemented, committed and published to the live web app on 24 September 2026 (GitHub deploy workflow). Remaining browser verification is listed below.

## Approved direction

The grill-me design interview settled these choices before implementation:

- Deep teal identity, warm editorial typography, and restrained lavender accents.
- Redesign the landing page, login, doctor home, and patient home.
- Expressive motion across these screens, with pause controls and device reduced-motion support.
- An original animated care story: patient check-in → context reaches the clinic → human review. Product previews remain clearly illustrative.

The supplied Healink landing-page and healthcare mobile-app references informed the hierarchy, colour, soft cards, and focal composition. Their features, clinicians, testimonials, and claims were not copied into Khabar.

## Implemented

### Landing

- Three-chapter animated care-story hero with direct chapter selection and play/pause.
- Automatic chapter progression stops when out of view or the page is hidden; direct selection stops autoplay.
- Dedicated switchable clinic/patient product-preview section.
- Responsive composition, lavender accents, and existing global motion control retained.

### Login

- Revised desktop story composition and a compact mobile header that brings the form closer to the top.
- Password visibility toggle, OTP input focus, resend feedback and cooldown.
- Invitation details separated from the development-only token input.
- Page-level motion control, entrance animations, and reduced-motion handling.
- Existing sign-in endpoints and role/access logic retained. Real email delivery was not tested in this redesign.

### Shared workspace

- Working role-specific section navigation, active indicators, mobile navigation, and accessible sign-out controls.
- Hash-based sections support direct links and browser history. Modified link clicks retain normal browser behaviour.
- Teal/lavender cards, readable metadata, entrance and interaction animations, and pause controls.
- Explicit initial loading/error/retry states; failed refreshes retain and label previously loaded data.
- Short desktop sidebars can scroll; decorative content is hidden at shorter heights.

### Doctor home

- Mobile call-list urgency is visible; patient names open records.
- Clear refresh timestamp, accurate zero counts, search-empty feedback, and expandable call/patient lists.
- Schedule and patient section links restore their destination after loading.
- Native add-patient dialog with Escape handling and opener focus restoration.
- Creation errors and clipboard success/failure feedback appear inside the active dialog.

### Patient home

- Clearer care summary, mobile action cards, and usable section controls.
- Mounted panels preserve unsent drafts when switching sections.
- Full available-slot list and explicit no-availability feedback.
- Available appointment days are grouped behind a date selector so patients can inspect every day without tabbing through hundreds of time buttons at once.
- Completed intake offers an explicit new-intake action; failed chat sends retain the draft without appending duplicate user messages on retry.
- Follow-up output is labelled as a response rather than implying clinician approval.

## Verification completed

After the final code changes:

- `npm run lint` — passed.
- `npm run typecheck` — passed.
- `npm run build` — passed; static and dynamic routes generated successfully.
- `git diff --check` — passed (Git reports only its usual LF/CRLF conversion notices).
- `OnboardingTest` — 10 tests passed after adding an API check for malformed IC numbers.

Earlier browser checks during this redesign, before the browser connection became unavailable:

- Desktop landing composition and manual story-chapter interaction.
- Mobile landing document width checked at 390 px with no horizontal page overflow.
- Mobile login at 390 × 844: compact header, form, and demo-entry layout inspected.
- Local doctor demo sign-in, mobile priority visibility, patient navigation and no-match search feedback.
- Add-patient native modal and Escape focus restoration checked without submitting a new patient.
- Desktop doctor workspace inspected at 1440 × 900.

Further local browser checks on 24 September:

- Patient mobile sections (plan, check-in, readings and privacy) loaded. An unsent recovery update and unsent reading values survived switching sections.
- The intake service was temporarily stopped: a failed submission kept its draft and showed an error; restarting it and retrying appended the answer once. A completed fictional intake offered “Start a new intake,” which reset the local conversation.
- The patient sidebar and sign-out were reachable at 1280 × 720, with no horizontal page overflow.
- From a doctor patient record, Patients and Schedule links scrolled to their targets after the clinic data loaded.
- The add-patient dialog showed a local API error inside the modal, gave a copy-code success message inside the modal, and restored focus to Add patient on close.
- An invalid IC format was accepted by the earlier local API. The form and API now reject it; the browser displayed the format guidance, and the new API test passed. The accidental fictional record existed only in the in-memory local profile and disappeared on service restart.
- Manual motion pause/resume was verified on landing, login, and doctor home.
- The appointment day selector was checked on mobile: all available dates remained selectable, only one day’s times were shown, times had spoken date labels, and changing the day cleared the previous selection.

These checks do not constitute full accessibility conformance, performance certification, or a complete end-to-end regression run. Clinical decision logic was unchanged. The API now validates supplied IC numbers, and its targeted onboarding suite was rerun; the full backend suite was not rerun.

## Still to verify before publication

- [x] Patient homepage on desktop and mobile, including all navigation sections and preservation of unsent drafts.
- [x] Completed intake → new intake, including a failed-send/retry case using fictional data.
- [x] Doctor add-patient error and successful clipboard feedback inside the dialog after the final fix.
- [x] Clipboard rejection feedback if browser clipboard permission is denied: with the clipboard refused, the add-patient dialog says "The code could not be copied. Select the code and copy it manually." and announces it as an alert.
- [x] Cross-route links from patient records to doctor Patients/Schedule after asynchronous loading.
- [x] Patient sidebar and sign-out at a short desktop viewport, e.g. 1280 × 720.
- [x] Manual pause/resume across landing, login, and doctor home.
- [x] Device reduced-motion setting across those screens: with `prefers-reduced-motion: reduce` the care story stays on its chapter, no long or looping animation runs on landing, login or doctor home, and the control reads "Reduced motion". Without it, the story advances and four animations run.
- [x] Browser check of the appointment day selector, time selection and mobile layout after the production build.
- [x] Keyboard, contrast, landmarks, zoom and narrow screens (24 Sep, automated with axe-core 4.10 plus a keyboard walk):
  - axe (WCAG 2.1 A/AA and best practice) finds no violations on landing, login, doctor home, patient record, visit (with safety findings showing) or the patient home and its four sections on a phone. Before the fixes it found 60+ text colours under 4.5:1, a sidebar landmark without a name, a skip link with no target on the record and visit pages, and side panels marked as nested landmarks. Text over gradients cannot be measured automatically and was checked by eye only.
  - Keyboard: the first Tab reaches "Skip to workspace", which jumps past the sidebar; focus order follows the page and every stop shows a focus ring. The patient search and the sign-in email field had no visible ring; both now do. Each patient row's link is named "Open <name>'s record" instead of "Open".
  - No sideways page scrolling on any screen at 320 px, or at 640 px (a 1280 px window at 200% zoom).
  - Toasts are announced (errors as alerts, others as status).
- [ ] A pass with a real screen reader (NVDA or VoiceOver) and a check of the caregiver home, which has no demo button.
- [ ] Real Supabase password/OTP/invitation flows with authorized test accounts and email delivery configured.
- [ ] Production performance and public-deployment smoke checks after an explicitly requested release.

Provider integration, deployment credentials, and broader readiness work remain tracked in `UNDONE_WORK.md`; this redesign does not mark them complete.
