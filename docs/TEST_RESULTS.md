# Test results

What has been checked, when, and how. Update this when a number changes. Commands run from the folder named.

## Automated tests (24 Sep 2026)

| Suite | Command | Result |
|---|---|---|
| Clinical API (`services/api`) | `./mvnw test` | **197 passed**, 0 failed |
| Agents (`services/agents`) | `.venv/Scripts/python -m pytest -q` | **204 passed**, 0 failed |
| Web app (`web`) | `npm run lint`, `npm run typecheck`, `npm run build` | all pass |
| Web deploy workflow | GitHub Actions on push to `main` | lint and type check pass on GitHub, then deploys |

Highlights of what the tests prove:
- **Safety checks:** the ten planted mistakes (allergy, double dose, duplicate from another clinic, herb
  clash, invented symptom, invented drug, interaction with a medicine from elsewhere, pregnancy, missing
  plan, unknown drug) are all caught, and a clean draft raises nothing.
- **Access:** a doctor sees only their clinic's patients, a patient only their own record, a caregiver
  only what the patient consented to, and withdrawing consent blocks the next request.
- **Sign-in:** real Supabase (ES256) and demo (HS256) tokens are each accepted only with their own key;
  expired, wrong-audience, unsigned and forged tokens are refused.
- **Privacy:** no name, IC or phone number reaches the patient graph (tested against a real in-process
  Neo4j), and the agents receive text with identity removed.
- **Follow-up:** red flags get the 999 advice and top the call list; any reply a person has yet to read
  also gets the 999 advice; only a reassuring reply gets a plain thank-you.

## Evaluations

See [evals/README.md](evals/README.md). Triage word lists alone: 22/22 red replies on the set they were
tuned on, **2/12 on held-out replies**. Packet-photo and transcription tests are ready and wait for keys.

## Accessibility (24 Sep 2026)

axe-core 4.10, WCAG 2.1 A/AA and best practice: **no violations** on landing, login, doctor home,
patient record, visit with safety findings, and the patient home with each section, at desktop and phone
widths. Keyboard walk, reduced motion, clipboard refusal, 320 px and 200% zoom all checked. Not yet done:
a real screen reader, and the caregiver home. Details: [UI_REDESIGN_2026-09-24.md](UI_REDESIGN_2026-09-24.md).

## Manual and end-to-end checks

| Date | What | Where | Result |
|---|---|---|---|
| 23 Sep | Bug bash of the core workflow | local | 11 defects found and fixed, one safety-related ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Rehearsal: pre-visit → draft → safety review → finalise → summary → reply → call list, demo sign-in | live URLs | passed ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Patient graph written and read by graph ID only | local Neo4j | passed |
| 24 Sep | Patient graph on AuraDB: 31 patients, Aminah's context read back through the live agents | live | passed; no names, IC or phone numbers in AuraDB |
| 24 Sep | Duplicate metformin caught across two clinics; safety gate blocks finalising; BM chest-pain reply gets 999 advice | local, fictional data | passed (screenshots in the README) |

## Not tested yet

Real sign-in per role on the live site · WhatsApp with a real phone · a Favoriot device · Gemini on
packet photos · transcription on recordings · the 5-person understanding pilot. Tracked in
[UNDONE_WORK.md](../UNDONE_WORK.md).
