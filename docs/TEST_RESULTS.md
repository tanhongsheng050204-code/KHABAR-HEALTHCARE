# Test results

What has been checked, when, and how. Update this when a number changes. Commands run from the folder named.

## Automated tests (25 Sep 2026)

### Opt-in overdue case routing change (25 Sep 2026)

- API: `./mvnw.cmd -q '-Dtest=FollowUpCaseTest,FlywayMigrationTest,PilotSchemaSmokeTest' test` from `services/api` — 21 tests passed in the full run below. These cover routing to today's rostered backup, idempotent repeated scheduler execution, retaining overdue/unacknowledged state, disabled-by-default scheduler registration, preserving the clinician-only urgent closure gate, no-backup behavior, V5/V6 migrations, and pilot-profile schema validation.
- Web: `npm run lint`, `npm run typecheck`, and `npm run build` from `web` — all passed after the retry/routing UI types and patient safety copy changes.
- Staff notification is intentionally not claimed: the event and UI state that no notification was sent. The scheduler is opt-in and disabled by default until clinician policy review.

### Follow-up retry idempotency change (25 Sep 2026)

- API: `./mvnw.cmd -q '-Dtest=FollowUpCaseTest,FlywayMigrationTest' test` from `services/api` — 17 tests passed, 0 failures/errors at that checkpoint. Covers matching request retries, conflicting reuse of a request ID, and V5 migration/nullability.
- Web: `npm run lint` and `npm run typecheck` from `web` — both passed.
- The retry test verifies the second identical request returns success without a second triage call, reply record, or outbound notice. The UI keeps only the opaque request ID in `sessionStorage` (no message text); a reused ID with changed text gets a fresh ID after the API conflict. Automated tests prove API idempotency. Browser checks later simulated a committed update response being dropped, then recovered it after reload; see [`RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md). Slow-network and deployed-provider checks remain open.

| Suite | Command | Result |
|---|---|---|
| Clinical API (`services/api`) | `./mvnw.cmd -q test` | **240 executed: 239 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally (fresh rerun 25 Sep). Embedded Neo4j tests passed locally; fresh H2 migration coverage extends through V6. |
| Agents (`services/agents`) | `.venv/Scripts/python -m pytest -q` | **209 passed**, 0 failed; one third-party deprecation warning |
| Web app (`web`) | `npm run lint`, `npm run typecheck`, `npm run build` | **All pass** on 25 Sep. |
| Latest hosted verification | GitHub Actions, pilot-foundations, commit `01be0f6`, 25 Sep ([run 36112985600](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112985600)) | **All jobs passed** for that commit: API tests, PostgreSQL 16 migration/schema validation, disposable backup/restore, agent tests, and web lint/typecheck/production build. This predates current uncommitted V5/V6 and finalisation changes; it does not verify or deploy the current worktree. |
| Hosted verification workflow | GitHub Actions, `pilot-foundations`, commit `da5658a`, 25 Sep ([run 36102047887](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36102047887)) | **All jobs passed**, including API, agents, web lint/typecheck/production build, and PostgreSQL 16 migration/schema smoke. This is CI evidence, not proof that the current branch has been deployed to the public demo. |
| Earlier hosted verification | GitHub Actions, `pilot-foundations` head `dd29a76`, 25 Sep ([run 36107956075](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36107956075)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and the point-in-time backup/restore check. It predates the pitch deck documentation commit; it did not deploy the feature branch. |
| Earlier hosted verification | GitHub Actions, `pilot-foundations` head `d66e873`, 25 Sep ([run 36109309203](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36109309203)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and point-in-time backup/restore. This CI run does not deploy the public services. |
| Flaky assertion found and corrected | GitHub Actions run 36109795707 on `7730df7` failed one API test because a short phone-number fragment matched timestamp digits. The assertion now compares full normalized IC and phone values. |
| Earlier hosted verification | GitHub Actions, `pilot-foundations` head `1bbdd10`, 25 Sep ([run 36110130766](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36110130766)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and point-in-time backup/restore. The corrected graph privacy assertion passed. |
| Earlier hosted verification | GitHub Actions, `pilot-foundations` head `40188c1`, 25 Sep ([run 36112593024](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112593024)) | **All jobs passed**, including API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and point-in-time backup/restore. This head contains rehearsal/evidence documentation and does not deploy the public services. |
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

### Earlier current-worktree rerun (25 Sep 2026)

- API: `./mvnw.cmd -q test` completed successfully. Surefire XML reports sum to **239 tests, 0 failures, 0 errors, 1 skipped**. Flyway V1–V6 and pilot-profile startup/schema validation passed with H2. The optional local PostgreSQL smoke test was skipped; this does not establish PostgreSQL 16 compatibility or backup/restore readiness for the current worktree. This run predates the latest issuer-validation regression test; see the newer 240-test result below.
- Agents: `.venv/Scripts/python.exe -m pytest -q` — **209 passed**, with one third-party Starlette/AnyIO deprecation warning.
- Web: `npm run lint`, `npm run typecheck`, and `npm run build` — all passed; Next.js generated the static and dynamic routes.
- Starter skeleton: not rerun in this pass; its prior local results remain in [`../starter-skeleton/VERIFICATION_2026-09-25.md`](../starter-skeleton/VERIFICATION_2026-09-25.md). No starter source changes were made.
- No application source changed during this verification. Hosted CI and public deployment evidence remain tied to earlier commits and do not verify this uncommitted tree.

### Skip-link focus fix (25 Sep 2026)

- Added `tabIndex={-1}` to the overview `<main>` targets used by the shared “Skip to workspace” link, including the patient, caregiver, doctor, record, visit, loading, and error workspace views. The targets can receive programmatic focus after anchor navigation without entering the regular Tab sequence.
- Added a login-page “Skip to sign in” link to jump over the introductory story content; its target accepts programmatic focus. The local browser accessibility tree exposed the named link and target.
- Added `lang={summary.language}` to localized summary text shown to patients, caregivers, and clinicians; the localized clinician follow-up note is tagged separately so its English label keeps the page language. `Summary.language` is now typed to the four locales the summary generator emits (`en`, `ms`, `zh`, `ta`), and the generator defaults unknown locale requests to English.
- Added an explicit visible “Consultation notes” label associated with the clinician visit-note textarea; the other textareas in the component scan are already inside labels.
- Web: `npm run lint`, `npm run typecheck`, and `npm run build` all passed after the change.
- The browser tree check confirms the login link and target are exposed. A keyboard activation/focus check, summary pronunciation check with assistive technology, and broader screen-reader review remain open; this does not close the accessibility review.

### Local API cold-start sample (25 Sep 2026)

- `services/scripts/measure-local-api-cold-start.ps1 -Port 8081 -TimeoutSeconds 90` launched the current API with the `local` profile and a fresh in-memory H2 database. The first successful `/api/health` response arrived **11.97 seconds** after launch; Spring reported startup in **6.557 seconds**.
- The script stopped its isolated API process, released port 8081, and removed its temporary log. This measures one local API startup only; deployed availability and web/agent cold starts remain unverified. Full record: [`COLD_START_CHECK_2026-09-25_LOCAL.md`](COLD_START_CHECK_2026-09-25_LOCAL.md).
- **Recovery:** hosted run [36109309203](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36109309203) passed the PostgreSQL 16 custom-format dump/restore rehearsal. The restored copy contains the marker and Flyway history from backup time and excludes a row written afterward. This verifies the recovery point on disposable CI data; production backup/restore, forward-migration recovery, and deployment rollback are not established by this smoke.

## Secret-pattern history scan (25 Sep 2026)

A full reachable-history scan with Gitleaks 8.30.1 reported seven `generic-api-key` pattern matches. The matched locations were test-only H2 configuration/test code, the local-profile configuration, and the encryption class's hard-coded placeholder sentinel. The sentinel is rejected by `FieldEncryptor` at startup; it is not used as an encryption key. The matching lines were inspected without displaying their values. No provider credential or production encryption key was identified in these findings. This pattern scan cannot prove that no credential was ever exposed or detect every possible secret; maintainers must still rotate any credential they know was exposed.
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
| 24 Sep | Call-list contact only resolves items in the clinician's server-timestamped queue snapshot; later replies and readings remain open. API errors include a safe code/message/status/request reference/retryability contract. Pilot schema migrations and validation added. | API + doctor workspace | At this historical checkpoint, 213 locally enabled API tests passed after clinic staff grants were added; the full suite at that time later reported 226 tests. The latest local suite is summarized above. H2 migration and pilot-profile checks passed locally. The hosted PostgreSQL 16 smoke job passed in the first `pilot-foundations` verification run. |
| 23 Sep | Bug bash of the core workflow | local | 11 defects found and fixed, one safety-related ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Rehearsal: pre-visit → draft → safety review → finalise → summary → reply → call list, demo sign-in | live URLs | passed ([report](BUG_BASH_2026-09-23.md)) |
| 23 Sep | Patient graph written and read by graph ID only | local Neo4j | passed |
| 24 Sep | Patient graph on AuraDB: 31 patients, Aminah's context read back through the live agents | live | passed; no names, IC or phone numbers in AuraDB |
| 24 Sep (night) | Follow-up cases and clinic settings in the browser: assign, "No answer", closing an urgent case as unreachable refused until escalated (message shown in the dialog, focus returned), escalate then close; the rota shows today's cover on the call list; activity log lists every step by case reference with no patient names; integration health lists five services; axe clean on the doctor home, staff settings, the case dialog and a 390 px phone | local, fictional data | passed |
| 24 Sep (night) | Clinic staff grants in the browser: the doctor home renders once, "Staff & access" invites a nurse, "Record contact" asks for confirmation and closes only the items in the displayed snapshot; duplicate check, safety gate and patient home unchanged; axe finds no violations on these screens | local, fictional data | passed |
| 24 Sep | Duplicate metformin caught across two clinics; safety gate blocks finalising; BM chest-pain reply gets 999 advice | local, fictional data | passed (screenshots in the README) |
| 25 Sep | Fresh public GETs: web home returned 200; `/api/health` returned 200 after one earlier timeout; `/agents/health` returned 404 | public demo | API health recovered on retry. Agent health still requires recheck after deployment. No authenticated or clinical workflow was exercised. |
| 25 Sep | Follow-up public GETs: web home 200, `/api/health` 200, `/agents/health` 404, `/agents/agents/health` 404 | public demo | Two likely agent health paths still fail; route must be checked after deployment. No authenticated or clinical workflow was exercised. |
| 25 Sep | Local fictional-data rehearsal: guided intake through visit, patient summary, urgent reply/call list; caregiver scope/revocation; agent-outage fallback | local H2 and demo tokens | Passed for the recorded cases; no real identity, provider delivery, graph context, clinical review, or deployed current-branch workflow was tested. Details in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md). |

## Not tested yet

Automated local checks on 25 Sep and hosted verification for branch head `01be0f6` (including PostgreSQL 16 migration validation and backup/restore rehearsal) passed in run [36112985600](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112985600). Run 36109795707 exposed a brittle test assertion; the corrected assertion passed in run 36110130766. Two later read-only public probe rounds returned web 200, timed out at API `/api/health`, and returned 404 for both tested agent health paths; `/agents` also timed out. Earlier probes had returned API health 200, so the current evidence points to intermittent or cold-start behavior rather than sustained API unavailability. The connected Vercel integration required authentication, so project settings and runtime logs remain unchecked. The last successful web deployment workflow was run 35976458448 on `main` at `79924b3` on 24 Sep; current `main` is `5a1056b` and includes later agent-service changes. The API and agents use a separate manual deploy path; current feature-branch code has not been deployed. A local fictional-data rehearsal is recorded separately; current-branch deployment, real role sign-in, provider tests, and human/clinical/privacy/security validation gates remain open (tracked in [UNDONE_WORK.md](../UNDONE_WORK.md)).

Real sign-in per role on the live site · WhatsApp with a real phone · a Favoriot device · Gemini on
packet photos · transcription on recordings · the 5-person understanding pilot. Tracked in
[UNDONE_WORK.md](../UNDONE_WORK.md).

### Current workspace rerun after Supabase issuer validation (25 Sep 2026)

- Khabar API: `cd services/api && .\\mvnw.cmd -q test` — **240 tests, 0 failures, 0 errors, 1 optional PostgreSQL smoke test skipped** (summed from Surefire XML). `SupabaseTokenDecoderTest` passed all 9 cases, including rejection of a signed token with the wrong issuer. The PostgreSQL smoke test remains skipped because no isolated PostgreSQL test connection was configured; hosted PostgreSQL 16 verification against this worktree remains open.
- Khabar agents: `cd services/agents && .\\.venv\\Scripts\\python.exe -m pytest -q` — **209 passed**, with one third-party Starlette/AnyIO deprecation warning.
- Khabar web: `npm run lint`, `npx tsc --noEmit`, and `npm run build` — all passed.
- Starter API: `cd starter-skeleton/services/api && .\\mvnw.cmd -q test` — **31 passed**, no failures or skips.
- Starter agents: `cd starter-skeleton/services/agents && .\\.venv\\Scripts\\python.exe -m pytest -q` — **17 passed**.
- Starter web: `npm run lint`, `npx tsc --noEmit`, and `npm run build` — all passed.
- These are fresh local checks on the current worktrees. They do not verify hosted PostgreSQL for the current Khabar V1–V6 tree, deployment, real providers, real identities, or human and clinical review. Those gates remain open.
- The repository health script was also rerun read-only against production. API `/api/health` timed out and `/agents/health` returned HTTP 404; live deployment health is not established and no deployment was started.

### Manual browser retry rehearsal (25 Sep 2026)

- A temporary local proxy let a fictional recovery update commit with HTTP 200, then dropped the browser response. After reloading and re-entering the same text, the patient page displayed the saved acknowledgement. The form text was not persisted; the retry ID supported replay. See [`RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md).
- Limit: local seeded data and local services only. This is not a deployed, slow-network, finalisation-retry, provider-outage, or clinical validation result.
\n
### Finalisation retry idempotency (25 Sep 2026)

- The finalise endpoint now serializes requests with a pessimistic encounter lock. If the first request already committed, a retry returns the existing final visit without replacing follow-up check-ins or regenerating/resending the patient summary.
- `EncounterFlowTest` passed (14 tests). The new regression sends finalise twice and asserts five check-ins, one summary, one outbound message, and one summary-agent call.
- Limit: this is an API integration test on H2. Local browser checks later covered same-session retry and reload recovery after both finalise responses were lost (see the result below). PostgreSQL concurrency verification remains open.

### Current-worktree verification after finalise retry fix (25 Sep 2026)

- API: `./mvnw.cmd -q test` — **235 tests executed: 234 passed, 0 failures, 0 errors, 1 optional PostgreSQL test skipped**. Includes the finalise replay test in `EncounterFlowTest` (14 tests in that class) and the V4-to-V6 reply-idempotency upgrade test in `FlywayMigrationTest` (4 tests in that class).
- Web: `npm run lint`, `npm run typecheck`, `npm run build` — all passed.
- `git diff --check` passed. Hosted CI and deployment have not run against these uncommitted changes.
\n
- **Finalise lost-response browser checks (25 Sep):** Same-session retry passed. In a separate run, a proxy dropped both finalise responses after both upstream requests returned HTTP 200; reloading showed the visit as final with editing disabled. The visit page now reloads the summary only when it belongs to that encounter. The isolated API had no summary agent, so summary display after reload was not verified. `EncounterFlowTest` verifies retries do not duplicate follow-up, summary, outbound notice, or summary-agent work. Slow-network and PostgreSQL concurrency checks remain open. See [`FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md).
- **Held-out word-list baseline (25 Sep):** Re-ran `python -m scripts.compare_triage_models --set holdout` in the agents virtual environment with no provider key: 2/12 red replies caught, 16/22 cases under-triaged, and 0 false alarms on expected-ok replies. This is not a model comparison or clinical validation. Report: [`evals/triage-wordlist-current.md`](evals/triage-wordlist-current.md).
- **Latest full API rerun (25 Sep):** 236 tests executed, 235 passed, 0 failures/errors, and 1 optional PostgreSQL-only test skipped. The V5 migration regression starts from V4 with a legacy reply and verifies null-ID compatibility and per-patient client-ID uniqueness in H2 PostgreSQL mode. This does not establish compatibility against real PostgreSQL; CI must rerun on a commit containing V1–V6.
- **Caregiver revocation regression (25 Sep):** focused `PatientRecordAccessTest` rerun after adding a live-consent revocation sequence: **14 tests, 0 failures, 0 errors, 0 skipped**. The caregiver first gets HTTP 200, the consent is revoked, and the next request gets HTTP 403. This tests local request-time API authorization, not clearing data already rendered in an open browser tab or live Supabase role behavior.
- **API/agents health smoke check (25 Sep):** `node --check services/scripts/check-health.mjs` passed. With local API and agents running, `node scripts/check-health.mjs http://localhost:8080 http://localhost:8000/health` reported both healthy. Against the current public deployment, the same script reported API healthy and agents HTTP 404 and exited 1 as intended. The check detects the public failure; Vercel project configuration and deployment contents still need inspection.
- **Demo graph reset regression (25 Sep):** focused `DemoGraphTest` passed **4 tests, 0 failures, 0 errors, 0 skipped**. The new test clears the in-process Neo4j graph, calls `/dev/demo/reset` against fictional H2 data, then verifies Aminah's diabetes and hypertension are written back. This proves the local reset/sync path; the deployed AuraDB state remains unverified.
- **Caregiver consent scope (25 Sep):** focused `PatientRecordAccessTest` passed **16 tests, 0 failures, 0 errors, 0 skipped**; `OnboardingTest` passed **11/11**. A `SUMMARY` invite is reported by `/api/me`, can read the approved summary, and is denied the full patient record, medicines and readings. `SUMMARY_AND_ALERTS` retains detail access. Deployed scope enforcement remains unverified.
- **Earlier full API and web verification (25 Sep):** after updating a medication-list test fixture to grant its caregiver the full detail scope, `./mvnw.cmd test` passed **239 tests: 238 passed, 0 failures, 0 errors, 1 optional PostgreSQL-only test skipped**. A later run after issuer-validation coverage passed 240 tests; see the current workspace rerun above. Web lint, typecheck, and production build passed. Hosted PostgreSQL and deployment checks remain open.
- **Slow-response browser check (25 Sep):** a local-only browser `fetch` wrapper delayed one fictional `POST /api/followup/replies` by five seconds. The button remained disabled while pending; the request completed once with HTTP 200, the input cleared, and the acknowledgement appeared. Browser-side request count and network log both showed one POST. This does not simulate bandwidth throttling, packet loss, disconnect, provider outage, or deployment.
- **Recovery-update failure and retry (25 Sep):** a local-only browser wrapper made the first fictional follow-up POST fail before reaching Spring. The page retained the entered text and showed its connection error. On manual retry the same `clientMessageId` was reused; one POST reached the API and returned HTTP 200, the input cleared, and the acknowledgement appeared. The network log showed one server request. `POST /dev/demo/reset` then returned HTTP 200 and restored the seeded demo data. This is not a physical network disconnect or deployed/provider outage test.
- **Graph context unavailable warning (25 Sep):** the evaluator now persists a non-blocking `WARN` finding when no graph ID is supplied, Neo4j is unconfigured or unreachable, or the patient has no graph record. The safety card renders non-critical findings in its review style. New tests cover the response and all unavailable states. Full agents suite: **209 passed** with one existing third-party deprecation warning. Web lint, typecheck, and production build passed. A current-worktree browser run with Neo4j disabled confirmed the visit remained usable, the warning rendered, and the independent duplicate-medicine block remained active. Details in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- **Empty structured draft feedback (25 Sep):** a browser run with a deliberately unstructured fictional note exposed an inaccurate success notice. The UI now reports success only when at least one structured field or prescription exists; otherwise it explains that the notes were saved but no draft fields were returned. A post-fix browser check confirmed that message, “No draft yet,” and disabled safety-check/finalisation controls. Lint, typecheck, and production build pass.

### Repeat run in this continuation (25 Sep 2026)

- Khabar API: `cd services/api && .\\mvnw.cmd -q test` exited 0. Fresh Surefire XML reports **240 tests: 239 passed, 0 failed, 0 errored, 1 optional PostgreSQL smoke test skipped**. H2 validated migrations V1–V6, including pilot-profile startup/schema validation. This does not verify PostgreSQL 16 against the current migrations.
- Khabar agents: `.\\.venv\\Scripts\\python.exe -m pytest -q` — **209 passed**, with one third-party Starlette/AnyIO deprecation warning.
- Khabar web: `npm run lint`, `npm run typecheck`, and `npm run build` all exited 0.
- These checks ran against the current Khabar worktree. Hosted CI and deployment were not run.
