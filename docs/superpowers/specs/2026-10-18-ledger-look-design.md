# Ledger look: a quieter web app and a product-first landing page

**Date:** 18 Oct 2026
**Reference:** DuitDuit (https://duitduit-umber.vercel.app/), a finance copilot for Malaysian SMEs, chosen for its calm, credible product-page style.

## Decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Scope | Landing page and the signed-in app (sign-in, doctor, patient, caregiver, patient record, visit), on one design system |
| 2 | How far to follow the reference | Its visual language and page structure, not its chrome (no cookie bar, chat bubble or mega-menus) |
| 3 | The storybook film | Kept intact at `/story` (old `/preview/film` links redirect there); the landing page links to it |
| 4 | Colour | Khabar's own tokens, used with the reference's restraint: one ink (`--night`), one teal accent (`--tidal`), amber only for meaning, pale surfaces, thin lines |
| 5 | Type | Manrope for headings (heavy, tight tracking), Inter for text; reading text such as care plans stays in Inter |
| 6 | Shape | 8–12 px corners, hairline borders, almost no shadow, no hover lifts |
| 7 | Theme | Light first; no dark-mode redesign |
| 8 | Judge entry | A dark banner and the hero buttons go to `/login?demo=doctor`, which starts the demo at once |
| 9 | Hero | A replay of the clinic workspace with fictional data, not illustrations |
| 10 | Landing sections | Hero and replay; who it is for; channels with honest Live / Prototype / Not connected pills; what the AI reads; one day with Aminah; safety; FAQ; dark closing band; footer |
| 11 | Language | English page text; Malay where the product itself speaks Malay |
| 12 | Quality bar | Film tests keep passing at `/story`; new tests for the landing page; zero axe violations; Lighthouse mobile ≥ 90 on `/` |

## Honesty rules for the landing page

The page may only claim what the product does today. In particular, it says that reply routing does not page staff, that device readings are simulated, that WhatsApp and clinic EMRs are not connected, that only the patient's registered name, IC and phone numbers are removed before AI calls, and that Khabar is not a medical device.

## Implementation notes

- The signed-in app's CSS modules each end with a "Ledger look" layer, the same layering pattern those files already used.
- Fonts are not preloaded from the root layout, because a preload there reaches `/story` and slowed the film's LCP. `components/site/hero-font.ts` preloads Manrope on `/` only, which removed a layout shift in the hero.
