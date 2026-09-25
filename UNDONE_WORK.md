# Khabar — Remaining Work

**Reviewed:** 25 September 2026
**Source of truth:** [`plan.md`](plan.md), checked against the current codebase and project documentation.  
**Scope:** This is an implementation and readiness backlog. It does not replace the SDC planning decisions in `plan.md`.

## Current position

The local core product is largely implemented and tested:

- API: **226 automated tests passing** in the 25 Sep full suite, including embedded Neo4j graph tests; the optional local PostgreSQL-only test is skipped, and hosted PostgreSQL 16 migration/schema checks passed again on 25 Sep. A focused follow-up-message regression suite also passed (9 tests). See [`docs/TEST_RESULTS.md`](docs/TEST_RESULTS.md).
- Agents: **205 automated tests passing** in the 25 Sep run; one third-party deprecation warning. This includes a regression check for the Vercel-prefixed health endpoint.
- Next.js web app: lint, TypeScript, and the production build pass. The build needed to run outside the restricted sandbox because its worker could not spawn there (`EPERM`). Hosted verification for branch head `1bbdd10` passed all jobs on 25 Sep (run [36110130766](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36110130766)), including the corrected full-identifier privacy assertion. These are CI results, not deployment evidence. Fresh read-only route checks on 25 Sep returned web 200 and API `/api/health` 200; both `/agents/health` and `/agents/agents/health` returned 404. Deployment and successful live agent health verification remain open.
- Local bug bash done (23 Sep): [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md), 11 defects found and fixed, including one safety issue.
- The main clinical workflow, access rules, encryption, de-identification, follow-up logic, and demo data are implemented locally.

The remaining work is primarily real-provider integration, missing stretch features, evidence-gathering, and demo/public-readiness work.

### Pilot-readiness engineering added — 24 September 2026

- [x] Call-list snapshots are server-timestamped. Recording successful contact closes only replies, readings, and unanswered check-ins that existed in that snapshot; later items remain open. The doctor UI confirms the effect before submission.
- [x] Controller errors return a safe, consistent code/message/status/request-reference/retryability shape. `X-Request-ID` is server-generated and exposed to the web app through CORS.
- [x] A separate Spring `pilot` profile refuses to run with `local` or `demo`, excludes local demo controllers/seeding, validates (rather than updates) the database schema, and leaves scheduled check-ins off by default.
- [x] Added `.github/workflows/verify.yml` to run API tests, agent tests, and frontend lint/typecheck/build on pushes and pull requests. The latest hosted run for `pilot-foundations` head `1bbdd10b5e75fa4d57551c11cbf5338327b422b5` passed on 25 Sep, including API tests, agent tests, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and disposable backup/restore ([run 36110130766](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36110130766)). Public deployment verification remains open.
- [x] Added V1 initial schema and V2 reading receive-time/backfill migrations. H2 PostgreSQL-mode tests verify clean creation, one-time migration, legacy timestamp backfill, and `pilot` profile startup with Hibernate schema validation.
- [x] Added an isolated PostgreSQL 16 migration/schema-validation smoke job to CI. It passed in hosted runs on 24 and 25 Sep, including the latest branch head run 36104133960.
- [x] Added a PostgreSQL 16 CI backup/restore rehearsal using a custom-format dump, a second disposable database, and checks for a marker row and Flyway history. Run 36104133960 passed; this is synthetic-data CI evidence only.
- [x] Follow-up cases (24 Sep): each listed patient gets one open case (New → Assigned → Acknowledged → In progress / Unable to contact / Escalated → Resolved) with owner, acknowledgement deadline, call attempts, escalation and a structured closure reason, plus an append-only history. Cases stay listed until closed; urgent cases need a note and, to close as unreachable, an escalation first. Assign/acknowledge/close are idempotent. Doctor and nurse queues show status, owner and overdue flags, with filters (mine, unassigned, overdue, urgent). V4 migration and 12 tests.
- [x] Clinic settings (24 Sep): hours, escalation contact, per-level acknowledgement times and a weekly rota with backup, set by doctors or clinic admins. The call list shows today's cover and says plainly when nobody is rostered. Admins also see a clinic activity log (cases by reference, never patient names) and integration health (agents, messages, scheduler, graph, sign-in).
- [ ] Still open for the alert lifecycle: notifying the on-duty person (push, SMS or WhatsApp to staff), automatic escalation when a case goes overdue, and a clinician review of the closure reasons and default times.
- [ ] Before pilot: compare and back up any existing database before review/baselining; rehearse forward migration recovery and deployment rollback. The disposable PostgreSQL 16 CI backup/restore checks the recovery point by confirming a post-backup write is absent ([run 36107201612](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36107201612)). This is not production recovery evidence and does not authorize using the public demo database for a pilot.

### UI redesign update — 24 September 2026

The approved teal/lavender care-story redesign is implemented across landing, login, doctor home, and patient home. It includes motion controls, navigation repairs, loading/error states, and mobile usability improvements. The previously deployed version was confirmed on the live site on 24 Sep. The current branch head passed hosted lint, TypeScript, production build, API, agent, and PostgreSQL migration checks on 25 Sep; deployment of this branch to the public demo remains open. The root layout no longer downloads fonts during builds: it uses the existing system sans/monospace stacks and Georgia serif fallback. If exact Geist/Newsreader branding is required, self-host reviewed font assets and recheck their licensing before adding them.

See [`docs/UI_REDESIGN_2026-09-24.md`](docs/UI_REDESIGN_2026-09-24.md) for the implementation summary and explicit remaining checks. Local patient and doctor browser flows have since been verified; device reduced-motion, full accessibility checks, real sign-in, and deployment verification remain open.

---

## 1. Highest priority — make the claimed architecture real

### 1.1 Connect the Neo4j patient graph

**Status:** Done, locally (23 Sep 2026) and on the deployed demo with AuraDB (24 Sep 2026).

**Required work**

- [x] Add a graph client with credentials supplied only by environment variables (`NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`; off when empty).
- [x] Write de-identified patient facts from Spring Boot using only `graph_id`; never write names, IC numbers, or phone numbers (`services/api/.../graph/`).
- [x] Implement the planned nodes and relationships needed for the demo: patient, conditions, medicines, brands, allergies, herbs, encounters, symptoms, and readings. (`INTERACTS_WITH` and `DUPLICATE_OF` are not stored: the evaluator derives them from the drug data and from two `TAKES` to one medicine.)
- [x] Give the agents read-only graph-query tools for intake, evaluator, and follow-up context (`services/agents/core/graph.py`, plus `GET /agents/graph/{graph_id}/context`). The report agent does not use the graph: it only structures the doctor's notes.
- [x] Add tests proving that personally identifiable information cannot enter Neo4j (`PatientGraphSyncTest`, `DemoGraphTest`, against a real in-process Neo4j).
- [x] Manual happy path (23 Sep): local Neo4j + API + agents; Aminah's context read back by graph ID through the agents; a medicine added in the API appeared in the graph; the safety check given only her graph ID caught the duplicate metformin and the bitter-gourd clash.
- [x] Run the deployed flow against AuraDB (24 Sep): free AuraDB instance `ebc9e325` (replaced the first instance, `18125f54`, whose password had been exposed; Aura Free allows no password change); the three `NEO4J_*` variables set on `khabar-api` production; redeployed; `POST /dev/graph/sync` wrote 31 patients. Aminah's context read back through the live agents by graph ID only (duplicate metformin from two clinics, bitter gourd, last visit, "pening" symptom). AuraDB checked directly: patient nodes hold only `graph_id` and `pregnant`; no names, IC numbers or phone numbers found.
- [ ] No patient in the live database has a recorded condition yet, so the graph has no `Condition` nodes. Conditions arrive through the guided intake; run it once on the live site before the demo.

**Done when:** the demo patient’s medication, allergy, herb, and condition context is written to and read from Neo4j using only the random graph ID.

### 1.2 Complete the real Supabase sign-in journey

**Status:** Built and wired on the live site (23 Sep): the web project has the Supabase URL and publishable key, Supabase has email sign-in on, and the live API refuses forged or missing tokens. Not yet done: a real sign-in for each role, which needs someone to receive the email code. Doctors need a login created in the Supabase dashboard (the screen has no staff sign-up), then link it with a doctor-invite code. Supabase's built-in email only reaches the project's team members, so patient codes to other addresses need a custom SMTP sender.

**Required work**

- [ ] Configure Supabase Auth for doctor email/password and patient/caregiver email OTP.
- [x] Configure the web app with the public Supabase URL and publishable key (already set on the `khabar-landing` project).
- [ ] Validate issuer, audience, signing keys, expiry, and role mapping in the deployed API. Local part done (24 Sep): the API now accepts real Supabase sign-ins (ES256, checked against the project's published keys) and the demo buttons' HS256 tokens side by side, each only against its own key (`SecurityConfig.jwtDecoder`). `SupabaseTokenDecoderTest` (8 tests) covers both token kinds, another project's key, the wrong secret, expired tokens, the wrong audience, unsigned and malformed tokens, and refusing to start with neither key configured. The live key set was checked: one ES256 P-256 key. The issuer is not checked: the project's key set already pins tokens to this project. Deployed to `khabar-api` production on 24 Sep (production has both `SUPABASE_JWKS_URL` and `SUPABASE_JWT_SECRET`; the code deployed before this used only the key set when both were present, which refused demo-button tokens). Still open: confirm one real and one demo sign-in on the live site.
- [ ] Test doctor, patient, and caregiver sign-in on the deployed web app.
- [ ] Test revoked caregiver consent immediately blocks access in the deployed environment.

**Done when:** all three roles can sign in without a developer token and can access only the records allowed by their role.

### 1.3 Verify the deployed end-to-end product

**Status:** Deployed rehearsal with demo sign-in passed on 23 Sep (see the end of [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md)). Still open: the same run with real Supabase sign-in and AuraDB connected. The web deploy workflow was fixed on 24 Sep (new token; the org and project ID secrets had a hidden character from a PowerShell pipe): every push to `main` that changes `web/` now lints, type-checks and deploys the web app.

**Required work**

- [ ] Confirm deployed web, API, agent service, Supabase, and Neo4j environment variables are configured correctly.
- [x] Perform an end-to-end rehearsal using fake data on the public URLs, with demo sign-in: pre-visit → draft → safety review → finalise → summary → follow-up reply → call list (booking and intake were run locally the same day).
- [ ] Repeat the rehearsal for doctor, patient, and caregiver permissions.
- [ ] Record defects and fix only issues that affect the core demo.
- [x] Keep a concise demo-run checklist and result: [`docs/DEMO_RUN_CHECKLIST.md`](docs/DEMO_RUN_CHECKLIST.md) and [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md).

**Done when:** the entire core story works from public URLs without local-only services or developer tokens.

---

## 2. Provider integration and external setup

### 2.1 WhatsApp Cloud API and approved templates

**Status:** API client and webhook handling are implemented. Without credentials and approved templates, outbound messages go to the local outbox instead of WhatsApp.

**Required work**

- [ ] Configure Meta developer app, test number, phone-number ID, long-lived access token, webhook verify token, and app secret.
- [ ] Submit and obtain approval for the check-in template.
- [ ] Submit and obtain approval for the summary template.
- [ ] Configure `WHATSAPP_CHECKIN_TEMPLATE` and `WHATSAPP_SUMMARY_TEMPLATE` in the deployed environment.
- [ ] Subscribe the webhook and verify Meta signature validation with a real test reply.
- [ ] Send a fake-patient summary and check-in to an approved test phone.
- [ ] Confirm an incoming reply is linked to the correct fake patient, triaged, and reflected in the call list.

**Done when:** one complete outbound-and-inbound WhatsApp test succeeds with fake data.

### 2.2 Favoriot validation

**Status:** Linked-device and webhook logic is implemented and tested using simulated data. A live Favoriot test has not been completed.

**Required work**

- [ ] Confirm Favoriot’s current free-tier availability and limits.
- [ ] Configure a test device or forwarding rule using the per-device secret.
- [ ] Send one blood pressure and one glucose reading through Favoriot.
- [ ] Verify a worrying reading affects the doctor call list.

**Decision:** If live setup is not quick and free, keep the simulated reading flow in the demo and present real Favoriot connection as a next step.

### 2.3 LLM and transcription evaluation

**Status:** Gemini and Groq integration points exist, but the formal selection experiments in `plan.md` are not documented as complete.

**Required work**

- [ ] Run the ten-case planted-error set on the candidate LLMs.
- [ ] Record which critical errors each model catches and select the safer model for this prototype.
- [ ] Run the one-hour transcription comparison on five Manglish recordings.
- [ ] Count clinically important drug-name errors, not only general transcription quality.
- [ ] Record the selected LLM, transcription model, results, date, and rationale in `plan.md` or `docs/EXPLAIN.md`.

### 2.4 Triage limitations and clinician-authored evaluation

**Status:** Open. The documented word-list fallback scored 22/22 on examples it was tuned against and 2/12 on held-out replies. A configured model may raise urgency but cannot reduce a word-list result; neither path is validated for detecting emergencies. Patient replies needing a person receive precautionary 999 wording, and the clinic queue explicitly says it does not replace clinic escalation procedures.

- [x] Align the README, product plan, integration docs, and demo description with measured limitations; do not claim reliable emergency detection.
- [x] Change both urgent and unclassified/review patient replies so they say the clinic may not have seen the message; regression tests cover all four configured languages and reject the old alert/callback promise.
- [ ] Have a qualified clinician and fluent readers review the updated emergency wording in all supported languages before external use.
- [ ] Keep the held-out evaluation independent of tuning and replace or extend it with clinician-authored replies.
- [ ] Run the model comparison on the clinician-authored set and document critical misses, false alarms, and limitations before making any performance claim.
- [ ] Have a qualified clinician review patient-facing emergency wording, queue labels, and the operational response process.

---

## 3. Missing or intentionally deferred product features

### 3.1 Medicine-packet photo reading (B4)

**Status:** Implemented locally and tested end to end with a stand-in model (23 Sep 2026); a real Gemini test is open. Patient/clinic-authorized users can upload an explicitly consented packet image through the API to the configured Gemini service for ephemeral label extraction. Results are mapped to known generics where possible and require user review before the existing medication-list add flow. The API and agent do not persist the image. A real-provider test remains open.

**Required work**

- [x] Add a consent-gated, size/MIME-checked image upload flow; uploads are not stored by Khabar and the UI warns to use fake/demo packets unless real-patient use is approved.
- [x] Add packet-label extraction behind the authenticated agent service (`services/agents/agents/packet_reader.py`).
- [x] Map extracted ingredient/brand text against known generics; present confidence/evidence and require explicit review before adding.
- [x] Connect reviewed candidate selection to the existing medication list.
- [x] Test duplicate and herb-clash findings end-to-end from a photo-derived item (`services/agents/tests/test_packet_to_findings.py`), and once by hand through the real API and agents with a local stand-in for Gemini (`GEMINI_API_BASE`): the doctor's pre-visit check named the packet photo in the CRITICAL duplicate.
- [ ] Run a real Gemini test on fictional packet images and confirm OCR quality/limitations.

**Scope note:** This is a P1 feature and may be reduced to the existing typed medicine list if time is limited.

### 3.2 Voice-note summaries (A3)

**Status:** Not implemented. There is no text-to-speech or WhatsApp audio-message implementation.

**Required work**

- [ ] Select a text-to-speech provider after testing BM, Chinese, and Tamil quality.
- [ ] Generate a voice version of the doctor-approved summary only.
- [ ] Send audio through the selected messaging channel.
- [ ] Test readability, correct medicine pronunciation, and language quality with fake cases.

**Scope note:** This is P2 / stretch work and is first to defer after the core workflow.

### 3.3 DDInter data replacement

**Status:** A reproducible DDInter 2.0 subset is now used by the checker (23 Sep 2026). Herb rules now include literature references and evidence caveats. Gliclazide has no pair records in the downloaded DDInter files; absence is not treated as safety.

**Required work**

- [x] Build a small, reproducible subset covering the demo generics (`services/agents/scripts/import_ddinter.py`; 70 pairs from the eight official CSV files).
- [x] Preserve DDInter attribution and the CC BY-NC-SA 4.0 notice in the README.
- [x] Add literature references and evidence caveats to the four herb rules. A locally curated Malaysian herb list still needs pharmacist review.
- [x] Re-run the evaluator and planted-error checks: 35 passed. The one graph-endpoint test was deselected because the machine-wide Python lacks the Neo4j driver.
- [x] Full local agent suite passed (183 tests); full API suite passed (181 tests, including Neo4j-backed tests), then the packet raw-byte transport test passed in the focused API service suite (4 tests). Combined API test count is 182. One Starlette/AnyIO deprecation warning remains.

### 3.4 Doctor writing-style learning (V5)

**Status:** Not implemented.

**Decision:** Do not start until the P0 workflow and deployment verification are complete. It is first in the plan’s drop order.

---

## 4. Evidence, usability, and public-demo readiness

### 4.1 Patient-understanding pilot

**Status:** Not started / no results recorded.

**Required work**

- [ ] Recruit five people using their preferred languages.
- [ ] Compare English-only and Khabar summaries using the balanced two-case method in `plan.md`.
- [ ] Ask the three planned questions: medicine, timing/dose, and warning symptom.
- [ ] Report all results honestly as a five-person pilot; do not overstate the conclusion.

### 4.2 Demo, pitch, and reliability work

**Status:** Future scheduled work; not evidenced as complete.

- [x] Bug bash the core workflow locally ([`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md)). Repeat on the deployed site once it is redeployed.
- [ ] Prepare stable fake demo data, including Mak Cik Aminah’s full story.
- [ ] Move services to reliable / always-on hosting before a live demo.
- [x] Prepare first drafts of 3-, 5-, and 7-minute pitch versions (`docs/PITCH_SCRIPTS.md`). Personalization, factual check against the live demo environment, and timed rehearsal remain open.
- [ ] Record and review a demo video.
- [ ] Rehearse the demo with poor-network and provider-failure fallbacks.
- [x] Create a repeatable rehearsal checklist with core workflow, role/access checks, failure fallbacks, and a result template (`docs/DEMO_RUN_CHECKLIST.md`). This is preparation only; no rehearsal result is implied.

### 4.3 Documentation habit

**Status:** `docs/EXPLAIN.md` exists, but daily coverage has not been verified.

- [x] Add a dated entry explaining the DDInter evaluator and sign-in changes in plain language (`docs/EXPLAIN.md`, 23 Sep 2026). Ongoing daily coverage remains the builder's responsibility.
- [ ] Record architecture decisions, integration credentials setup steps without secrets, and test results.
- [ ] Keep a short list of code areas you can personally explain for the SDC review.

---

## 5. SDC decisions and administration

These are not implementation tasks, but they are still open in `plan.md`.

- [ ] Get organisers’ answers on SDGs, declaration changes, pitch length, and judging weights.
- [ ] Decide available working hours for the SDC week alongside classes.
- [x] Create a separate local Git repository at `../starter-skeleton/` containing the generic skeleton. It currently has no remote configured; publish/configure the intended repository and confirm its contents before registration.
- [x] The tracked starter-skeleton files contain the generic example flow rather than Khabar clinical features, screens, patient data, or prompts. Review the full tracked history again before publication.
- [ ] Submit the declaration before registration. A draft is in `../starter-skeleton/DECLARATION_DRAFT.md`; insert the public repository link, verify the event wording, and submit through the organiser's process.

---

## Recommended next sequence

1. ~~Implement and test **Neo4j graph writes and reads**~~ (done locally and on the deployment with AuraDB, 24 Sep).
2. Complete deployed **Supabase authentication** for every role.
3. Finish a deployed end-to-end rehearsal using fake data.
4. Configure **WhatsApp templates and webhook**; perform one real test-number loop.
5. Run the LLM and transcription selection experiments and document the decisions.
6. Decide whether B4 photo reading, A3 voice notes, and live Favoriot are worth the remaining time. Defer them before compromising the P0 demo.
7. Run the usability pilot, bug bash, and pitch/demo preparation.

## Completion rule

Do not mark a task complete merely because code exists. Mark it complete only when it has the relevant evidence:

- **Implementation work:** automated test plus a manual happy-path test.
- **Provider integration:** a successful test against the real provider using fake data.
- **Security/access work:** an explicit denied-access test as well as an allowed-access test.
- **Demo work:** one uninterrupted rehearsal from deployed URLs.
