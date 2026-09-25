# Test results

What has been checked, when, and how. Update this when a number changes. Commands run from the folder named.

## Automated tests (25 Sep 2026)

| Suite | Command | Result |
|---|---|---|
| Clinical API (`services/api`) | `./mvnw -q test` | **226 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally (hosted PostgreSQL 16 job passed on 24 Sep). Embedded Neo4j tests passed locally on 25 Sep. |
| Agents (`services/agents`) | `.venv/Scripts/python -m pytest -q` | **205 passed**, 0 failed; one third-party deprecation warning |
| Web app (`web`) | `npm run lint`, `npm run typecheck`, `npm run build` | **All pass** on 25 Sep. |
| Hosted verification workflow | GitHub Actions, `pilot-foundations`, commit `da5658a`, 25 Sep ([run 36102047887](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36102047887)) | **All jobs passed**, including API, agents, web lint/typecheck/production build, and PostgreSQL 16 migration/schema smoke. This is CI evidence, not proof that the current branch has been deployed to the public demo. |
| Earlier hosted verification | GitHub Actions, `pilot-foundations` head `dd29a76`, 25 Sep ([run 36107956075](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36107956075)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and the point-in-time backup/restore check. It predates the pitch deck documentation commit; it did not deploy the feature branch. |
| Last fully passing hosted verification | GitHub Actions, `pilot-foundations` head `d66e873`, 25 Sep ([run 36109309203](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36109309203)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and point-in-time backup/restore. This CI run does not deploy the public services. |
| Hosted verification after documentation refresh | GitHub Actions, `pilot-foundations` head `7730df7`, 25 Sep ([run 36109795707](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36109795707)) | Agent and web jobs passed; the API suite had one false positive in `PatientGraphSyncTest`: checking short number fragments matched timestamp digits. The privacy assertion now checks the complete normalized IC and phone identifiers. Focused regression passes locally; full hosted rerun pending. |
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
- **Recovery:** hosted run [36109309203](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36109309203) passed the PostgreSQL 16 custom-format dump/restore rehearsal. The restored copy contains the marker and Flyway history from backup time and excludes a row written afterward. This verifies the recovery point on disposable CI data; production backup/restore, forward-migration recovery, and deployment rollback are not established by this smoke.

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
| 25 Sep | Fresh public GETs: web home returned 200; `/api/health` returned 200 after one earlier timeout; `/agents/health` returned 404 | public demo | API health recovered on retry. Agent health still requires recheck after deployment. No authenticated or clinical workflow was exercised. |
| 25 Sep | Follow-up public GETs: web home 200, `/api/health` 200, `/agents/health` 404, `/agents/agents/health` 404 | public demo | Two likely agent health paths still fail; route must be checked after deployment. No authenticated or clinical workflow was exercised. |

## Not tested yet

Automated local checks on 25 Sep and hosted verification for branch head `d66e873` (including PostgreSQL 16 migration validation and backup/restore rehearsal) passed. Run 36109795707 found and exposed a brittle test assertion, which has been fixed locally and awaits full hosted rerun. The latest read-only public checks returned web 200, API health 200, and 404 for both tested agent health paths. Current-branch deployment verification, role-based real sign-in, provider tests, and human/clinical/privacy/security validation gates remain open (tracked in [UNDONE_WORK.md](../UNDONE_WORK.md)).

Real sign-in per role on the live site · WhatsApp with a real phone · a Favoriot device · Gemini on
packet photos · transcription on recordings · the 5-person understanding pilot. Tracked in
[UNDONE_WORK.md](../UNDONE_WORK.md).
