# Test results

What has been checked, when, and how. Update this when a number changes. Commands run from the folder named.

## Automated tests (25 Sep 2026)

| Suite | Command | Result |
|---|---|---|
| Clinical API (`services/api`) | `./mvnw -q test` | **226 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally (hosted PostgreSQL 16 job passed on 24 Sep). Embedded Neo4j tests passed locally on 25 Sep. |
| Agents (`services/agents`) | `.venv/Scripts/python -m pytest -q` | **205 passed**, 0 failed; one third-party deprecation warning |
| Web app (`web`) | `npm run lint`, `npm run typecheck`, `npm run build` | **All pass** on 25 Sep. |
| Hosted verification workflow | GitHub Actions, `pilot-foundations`, commit `da5658a`, 25 Sep ([run 36102047887](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36102047887)) | **All jobs passed**, including API, agents, web lint/typecheck/production build, and PostgreSQL 16 migration/schema smoke. This is CI evidence, not proof that the current branch has been deployed to the public demo. |
| Latest hosted verification | GitHub Actions, code head `bd94862`, 25 Sep ([run 36104853590](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36104853590)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and disposable PostgreSQL backup/restore. This does not deploy the feature branch. |
| Review-message wording | API `ApprovedAnswersTest` + `CallListTest` | **21 passed**. Verified 999 guidance, clinic-queue wording, no promise of staff review/callback, and the triage-down fallback in all four configured languages. |

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
- **Recovery:** hosted PostgreSQL CI dumps the migrated disposable database, restores it to a second database, and verifies a marker row plus Flyway history. Production backup/restore, forward-migration recovery, and deployment rollback are not established by this smoke.

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
| 24 Sep | Call-list contact only resolves items in the clinician's server-timestamped queue snapshot; later replies and readings remain open. API errors include a safe code/message/status/request reference/retryability contract. Pilot schema migrations and validation added. | API + doctor workspace | At this checkpoint, 213 locally enabled API tests passed after clinic staff grants were added. The later full-suite summary above reports 226 API tests. H2 migration and pilot-profile checks passed locally. The hosted PostgreSQL 16 smoke job passed in the first `pilot-foundations` verification run. |
| 23 Sep | Bug bash of the core workflow | local | 11 defects found and fixed, one safety-related ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Rehearsal: pre-visit → draft → safety review → finalise → summary → reply → call list, demo sign-in | live URLs | passed ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Patient graph written and read by graph ID only | local Neo4j | passed |
| 24 Sep | Patient graph on AuraDB: 31 patients, Aminah's context read back through the live agents | live | passed; no names, IC or phone numbers in AuraDB |
| 24 Sep (night) | Follow-up cases and clinic settings in the browser: assign, "No answer", closing an urgent case as unreachable refused until escalated (message shown in the dialog, focus returned), escalate then close; the rota shows today's cover on the call list; activity log lists every step by case reference with no patient names; integration health lists five services; axe clean on the doctor home, staff settings, the case dialog and a 390 px phone | local, fictional data | passed |
| 24 Sep (night) | Clinic staff grants in the browser: the doctor home renders once, "Staff & access" invites a nurse, "Record contact" asks for confirmation and closes only the items in the displayed snapshot; duplicate check, safety gate and patient home unchanged; axe finds no violations on these screens | local, fictional data | passed |
| 24 Sep | Duplicate metformin caught across two clinics; safety gate blocks finalising; BM chest-pain reply gets 999 advice | local, fictional data | passed (screenshots in the README) |
| 25 Sep | Current public routes: web home and `/api/health` returned 200; `/agents/health` returned 404 | public demo | Agent health-route fix is in branch `fe880c6`, but the deployed response confirms it remains undeployed. No authenticated or clinical workflow was exercised. |

## Not tested yet

Automated local checks on 25 Sep and hosted verification for code head `bd94862` (including its PostgreSQL 16 migration smoke and backup/restore rehearsal) are documented as passing. The public site and API health endpoint returned 200, but the agent health route returned 404 before deployment of the fix. Current-branch public deployment verification, role-based real sign-in, provider tests, and human/clinical/privacy/security validation gates remain open (tracked in [UNDONE_WORK.md](../UNDONE_WORK.md)).

Real sign-in per role on the live site · WhatsApp with a real phone · a Favoriot device · Gemini on
packet photos · transcription on recordings · the 5-person understanding pilot. Tracked in
[UNDONE_WORK.md](../UNDONE_WORK.md).
