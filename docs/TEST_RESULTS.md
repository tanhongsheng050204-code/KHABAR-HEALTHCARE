# Test results

What has been checked, when, and how. Update this when a number changes. Commands run from the folder named.

## Automated tests (24 Sep 2026)

| Suite | Command | Result |
|---|---|---|
| Clinical API (`services/api`) | `./mvnw -q test` | **213 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally. Embedded Neo4j tests ran outside the restricted sandbox. |
| Agents (`services/agents`) | `.venv/Scripts/python -m pytest -q` | **204 passed**, 0 failed; one third-party deprecation warning |
| Web app (`web`) | `npm run lint`, `npm run typecheck`, `npm run build` | **All pass.** The production build was run outside the restricted sandbox because Next.js could not spawn its worker there (`EPERM`). |
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
- **Migrations:** Flyway V1 creates the initial clinical schema, V2 adds and backfills `reading.received_at`; tests verify fresh schema creation, one-time execution, legacy-row backfill, and Hibernate validation under the `pilot` profile using H2 PostgreSQL mode. A separate smoke test targets PostgreSQL 16 in CI and is not enabled in local runs.

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
| 24 Sep | Call-list contact only resolves items in the clinician's server-timestamped queue snapshot; later replies and readings remain open. API errors include a safe code/message/status/request reference/retryability contract. Pilot schema migrations and validation added. | API + doctor workspace | All locally enabled API tests passed (213 after the clinic staff grants were added), including embedded Neo4j tests, migration creation/backfill, pilot Hibernate validation, preservation of replies/readings submitted after the displayed snapshot, authorization errors, generated request references, hidden exception details, and missing-route behavior. The real-PostgreSQL smoke test is configured for CI but was not run locally. Web lint, typecheck, and production build passed. |
| 23 Sep | Bug bash of the core workflow | local | 11 defects found and fixed, one safety-related ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Rehearsal: pre-visit → draft → safety review → finalise → summary → reply → call list, demo sign-in | live URLs | passed ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Patient graph written and read by graph ID only | local Neo4j | passed |
| 24 Sep | Patient graph on AuraDB: 31 patients, Aminah's context read back through the live agents | live | passed; no names, IC or phone numbers in AuraDB |
| 24 Sep (night) | Clinic staff grants in the browser: the doctor home renders once, "Staff & access" invites a nurse, "Record contact" asks for confirmation and closes only the items in the displayed snapshot; duplicate check, safety gate and patient home unchanged; axe finds no violations on these screens | local, fictional data | passed |
| 24 Sep | Duplicate metformin caught across two clinics; safety gate blocks finalising; BM chest-pain reply gets 999 advice | local, fictional data | passed (screenshots in the README) |

## Not tested yet

Automated local service checks are complete. The first hosted run of the new verification workflow (including its PostgreSQL 16 migration smoke test), current-change deployment verification, role-based real sign-in, provider tests, and the human/clinical/privacy/security validation gates remain open (tracked in [UNDONE_WORK.md](../UNDONE_WORK.md)).

Real sign-in per role on the live site · WhatsApp with a real phone · a Favoriot device · Gemini on
packet photos · transcription on recordings · the 5-person understanding pilot. Tracked in
[UNDONE_WORK.md](../UNDONE_WORK.md).
