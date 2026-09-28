# Khabar — Remaining Work

**Reviewed:** 25 September 2026; status updated 28 September 2026
**Source of truth:** [`plan.md`](plan.md), checked against the current codebase and project documentation.  
**Scope:** This is an implementation and readiness backlog. It does not replace the SDC planning decisions in `plan.md`.

## Status on 28 September 2026

**Verified on the live site with real accounts (27–28 Sep):** all three roles sign in with real Supabase accounts; caregiver scope and immediate revocation; the full core story (intake → pre-visit → draft → safety gate → finalise → summary → urgent reply → call list); AuraDB resumed and re-synced. Records: §1.2, §1.3 and [`docs/DEMO_RUN_2026-09-28_DEPLOYED.md`](docs/DEMO_RUN_2026-09-28_DEPLOYED.md).

**Merged to `main`:** #1 pilot foundations (web deployed), #2 time-zone test fix, #3 clearer invitation-code errors (web deployed), #4 intake first-turn fix for Gemini (deployed 28 Sep 01:31; **the live check-in now runs on Gemini**, confirmed at 10:03).

**Also verified on the live site on 28 Sep:** the first real Gemini packet-photo read (§3.1), and cross-patient and forged-token denial with read-only requests (#10).

**Merged on 28 Sep morning; the web deploy for `71a7777` succeeded and CI is green:**

| PR | What |
| --- | --- |
| #5 | `services/scripts/warm-up.mjs`: wakes the services and refills the patient graph, failing clearly if AuraDB is paused |
| #6 | "Listen to this plan": read-aloud of the approved summary, right-language device voice only (idea L, partial) |
| #7 | Notes placeholder that the parser fully understands (the old example lost the follow-up interval) |
| #8 | README and backlog status for 28 Sep |
| #9 | Accurate identity-removal wording: only the patient's registered name, IC and phone are removed, and patients are asked not to type names |

**Open:** #10 (checklist record of the deployed denial checks, docs only).

**Owner actions (need a person, an account or a decision):**

1. On the live site, continue a check-in to the medicines question and confirm the model mentions the medicines on record.
2. Merge #10. Run `node scripts/warm-up.mjs` from `services/` before any live demo. On a phone, check that "Listen to this plan" appears under the care plan (it needs a voice for the summary's language).
3. Reproduce the caregiver-home toast (finding 5 in the run record) and read the Vercel logs within the hour.
4. Clinician decision: the patient summary omits the doctor's free-text plan and specific warning signs (finding 2).
5. Listen to "Listen to this plan" on real phones in BM, Chinese and Tamil with fluent readers, including medicine names (§3.2).
6. Providers: Groq key for speech-to-text (§2.3), WhatsApp app and template approval (§2.1), Favoriot device (§2.2).
7. SDC administration (§5): publish `starter-skeleton` (committed locally, no remote), confirm AI tools used, submit declarations.

**Not started, by decision:** doctor writing-style learning (§3.4, first in the drop order) and notifying the on-duty staff member (needs a provider and clinician approval).

## Current position (25 September 2026)

The local core product is largely implemented and tested:

- API: the latest full local rerun on 25 Sep executed **240 tests: 239 passed, 0 failed, 0 errored, 1 optional PostgreSQL smoke skipped**, including the Supabase wrong-issuer regression. A fresh rerun in this continuation reproduced that count; H2 applied and validated V1–V6. Hosted PostgreSQL 16 migration/schema/backup checks passed on an earlier branch head before current V5/V6 changes. See [`docs/TEST_RESULTS.md`](docs/TEST_RESULTS.md). Hosted CI has not run against the current uncommitted changes.
- Agents: **209 automated tests passing** in the latest 25 Sep run; one third-party deprecation warning. This includes graph-unavailable evaluator warnings, status coverage for missing IDs/unconfigured or unreachable Neo4j/missing graph patients, and a regression check for the Vercel-prefixed health endpoint.
- Next.js web app: lint, TypeScript, and production build pass on the current worktree (25 Sep). Hosted verification for head `40188c1` passed all jobs on 25 Sep (run [36112593024](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112593024)); this head adds documentation after application-code commit `1bbdd10`, whose corrected privacy assertion passed. These are CI results, not deployment evidence. **27 Sep 2026 update:** the user redeployed `khabar-api` (`npx vercel deploy --prod` from `services/`, commit `f12cc9b`, hosted CI green on run [36320590174](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36320590174)); Vercel aliased the build to `https://khabar-api.vercel.app`. `node scripts/check-health.mjs` timed out on `/api/health` on the first run (cold start) and returned `API: healthy` and `Agents: healthy` on the immediate retry, so the agent-health 404 is resolved on this deployment. Vercel project settings (framework/root directory) have not been separately reconfirmed since the 25 Sep inspection.
- Local bug bash done (23 Sep): [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md), 11 defects found and fixed, including one safety issue.
- The main clinical workflow, access rules, encryption, de-identification, follow-up logic, and demo data are implemented locally.

The remaining work is primarily real-provider integration, missing stretch features, evidence-gathering, and demo/public-readiness work. The local evaluator now adds a visible, non-blocking safety warning whenever graph-derived context could not be checked; a current-worktree browser rehearsal with Neo4j disabled confirmed that the warning appears in the visit safety card and the separate critical duplicate-medicine gate remains active. The local graph-disabled check is recorded in [`docs/DEMO_RUN_2026-09-25_LOCAL.md`](docs/DEMO_RUN_2026-09-25_LOCAL.md).

### Pilot-readiness engineering added — 24 September 2026

- [x] Prepared a fictional-demo-only intended-use and claims-boundary draft ([`docs/INTENDED_USE_DRAFT.md`](docs/INTENDED_USE_DRAFT.md)). It explicitly excludes real-patient and clinical use, states the measured triage limitation, and identifies owner, clinician, privacy, and regulatory reviews still required. It is not approved and does not close the pilot acceptance gate.

- [x] Call-list snapshots are server-timestamped. Recording successful contact closes only replies, readings, and unanswered check-ins that existed in that snapshot; later items remain open. The doctor UI confirms the effect before submission.
- [x] Controller errors return a safe, consistent code/message/status/request-reference/retryability shape. `X-Request-ID` is server-generated and exposed to the web app through CORS.
- [x] A separate Spring `pilot` profile refuses to run with `local` or `demo`, excludes local demo controllers/seeding, validates (rather than updates) the database schema, and leaves scheduled check-ins off by default.
- [x] Added `.github/workflows/verify.yml` to run API tests, agent tests, and frontend lint/typecheck/build on pushes and pull requests. The latest hosted run for `pilot-foundations` head `01be0f6e36b9ba0971f90de8cd40b63c3f604d8f` passed on 25 Sep, including API tests, agent tests, web lint/typecheck/build, PostgreSQL 16 migration/schema validation, and disposable backup/restore ([run 36112985600](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112985600)). Public deployment verification remains open.
- [x] Added V1 initial schema and V2 reading receive-time/backfill migrations. H2 PostgreSQL-mode tests verify clean creation, one-time migration, legacy timestamp backfill, and `pilot` profile startup with Hibernate schema validation.
- [x] Added an isolated PostgreSQL 16 migration/schema-validation smoke job to CI. It passed in hosted runs on 24 and 25 Sep, including the latest branch head run 36104133960.
- [x] Added a PostgreSQL 16 CI backup/restore rehearsal using a custom-format dump, a second disposable database, and checks for a marker row and Flyway history. Run 36104133960 passed; this is synthetic-data CI evidence only.
- [x] Follow-up cases (24 Sep): each listed patient gets one open case (New → Assigned → Acknowledged → In progress / Unable to contact / Escalated → Resolved) with owner, acknowledgement deadline, call attempts, escalation and a structured closure reason, plus an append-only history. Cases stay listed until closed; urgent cases need a note and, to close as unreachable, an escalation first. Assign/acknowledge/close are idempotent. Doctor and nurse queues show status, owner and overdue flags, with filters (mine, unassigned, overdue, urgent). V4 migration and 12 tests.
- [x] Clinic settings (24 Sep): hours, escalation contact, per-level acknowledgement times and a weekly rota with backup, set by doctors or clinic admins. The call list shows today's cover and says plainly when nobody is rostered. Admins also see a clinic activity log (cases by reference, never patient names) and integration health (agents, messages, scheduler, graph, sign-in).
- [x] Added opt-in automatic overdue routing to that weekday's active rostered backup. V6 stores routing separately from clinician escalation; it records an append-only event, leaves the case unacknowledged and visibly overdue, and explicitly says no staff notification was sent. It cannot satisfy the clinician-escalation gate for closing an urgent case as unreachable. `KHABAR_AUTO_ESCALATION_ENABLED` is false by default; automated tests cover routing, idempotency, the no-backup case and the closure safeguard.
- [ ] Still open for the alert lifecycle: send an actual notification to the on-duty person (push, SMS or WhatsApp to staff), and have a clinician approve the closure reasons, acknowledgement deadlines and auto-routing behavior before anyone enables the scheduler or uses this for care.
- [ ] Before pilot: compare and back up any existing database before review/baselining; rehearse forward migration recovery and deployment rollback. CI run [36107201612](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36107201612) rehearsed restore from a disposable PostgreSQL 16 backup, but predates the current uncommitted V5/V6 migrations. Rerun the hosted PostgreSQL migration and backup/restore jobs on a commit containing V1–V6 before treating the current migration head as PostgreSQL-verified. No disposable PostgreSQL server or container runtime is available in this local shell. None of this is production recovery evidence or authorization to use the public demo database for a pilot.

### UI redesign update — 24 September 2026

The approved teal/lavender care-story redesign is implemented across landing, login, doctor home, and patient home. It includes motion controls, navigation repairs, loading/error states, and mobile usability improvements. The previously deployed version was confirmed on the live site on 24 Sep. Hosted checks passed on an earlier 25 Sep branch head; those checks predate the current uncommitted V5/V6 migrations and finalisation changes. Deployment and PostgreSQL verification of the current worktree remain open. The root layout no longer downloads fonts during builds: it uses the existing system sans/monospace stacks and Georgia serif fallback. If exact Geist/Newsreader branding is required, self-host reviewed font assets and recheck their licensing before adding them.

See [`docs/UI_REDESIGN_2026-09-24.md`](docs/UI_REDESIGN_2026-09-24.md) for the implementation summary and explicit remaining checks. Local patient and doctor browser flows have since been verified. A current-worktree caregiver UI check also confirmed the `SUMMARY`-only state hides medication and reading details; because that patient had no finalised summary, populated-summary rendering remains unverified. On 25 Sep the login page gained a “Skip to sign in” link, and the shared workspace skip-link targets were made programmatically focusable across workspace, patient-record, and visit pages. A browser-driven Tab pass traversed login (11 targets), doctor home (63), patient home (28), and doctor patient record (12); every reached element had visible bounds and matched `:focus-visible`. In an isolated current-source local stack, the caregiver page rendered its active sharing scope and its five app controls, and the visit page's 13 app controls matched `:focus-visible`; both skip links focused the overview. The visit was an empty draft in isolated in-memory H2 and disappeared when the API stopped. The doctor motion control paused motion (`data-motion=off`), but system-level reduced-motion behavior was not tested. A development-only caregiver check against the older shared local API remains fail-closed and is not current-backend permission evidence. Details and limits are in [`docs/DEMO_RUN_2026-09-25_LOCAL.md`](docs/DEMO_RUN_2026-09-25_LOCAL.md). Remaining route states, OS reduced-motion settings, a real screen-reader review, and representative accessibility testing remain open. Localized patient, caregiver, and clinician summary text now sets its `lang` attribute from the summary locale, constrained in the web type to the summary agent’s supported `en`, `ms`, `zh`, and `ta` codes. The clinician visit-note textarea has a visible associated label. Current-worktree lint, typecheck, and production build pass. Real sign-in, revocation in a fresh session, and deployment verification remain open.

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
- [ ] Verify `Condition` nodes in the deployed demo graph after the API containing `de97e75` or later is deployed and the fictional demo reset runs. Local `DemoData.resetFollowUp()` now restores Aminah's seeded intake with diabetes and hypertension and calls `graphSync.changed`; `DemoGraphTest.resettingTheFictionalDemoRestoresAminahsConditionsToTheGraph` clears the in-process graph, invokes that reset, and verifies both conditions are written back. The live demo graph has not been checked after this reset path, so deployment verification remains open.

**Done when:** the demo patient’s medication, allergy, herb, and condition context is written to and read from Neo4j using only the random graph ID.

### 1.2 Complete the real Supabase sign-in journey

**Status:** Built and wired on the live site (23 Sep): the web project has the Supabase URL and publishable key, Supabase has email sign-in on, and the live API refuses forged or missing tokens. **27 Sep 2026:** first real per-role sign-in completed. A new Supabase user was created manually in the dashboard (`Add user`, auto-confirmed, email/password), an existing demo-doctor session generated a one-time doctor-invite code via *Invite a doctor*, and the new account signed in with email+password and that invite code together on the live site (`khabar-landing-six.vercel.app`). The doctor home loaded with the entered display name in the sidebar (role "Doctor") and real clinic data (call list, follow-up counts, patient priorities) — this is a genuine Supabase-authenticated session, not the demo-token shortcut. Patient email OTP sign-in was then attempted and found blocked (no custom SMTP configured; Supabase's default, non-editable template's sign-in link pointed to `localhost:3000` and never displayed a code). **Fixed the same day, in two stages:** first connected a free-tier Resend account as custom SMTP (`smtp.resend.com`, sandbox sender `onboarding@resend.dev`), which unlocked template editing; added `{{ .Token }}` to the Magic Link/OTP template so a numeric code is shown alongside the existing `{{ .ConfirmationURL }}` link. Retested and reached the real patient home (guided intake, check-in, readings, medicines, my people) under the entered display name, role "Patient" — a genuine Supabase-authenticated session. Resend's free sandbox sender turned out to deliver only to the account owner's own address (confirmed by testing a second, unrelated real address, which failed); since no domain was available to verify, **replaced Resend with the project owner's own Gmail account as the custom SMTP relay** (`smtp.gmail.com:587`, a Gmail App Password, sends to any recipient, free, no domain needed). Doctor sign-in was real from the start; patient and caregiver sign-in depend on this SMTP path and are now both confirmed (see below) using two different real addresses — Gmail delivery is not restricted to one recipient.

**Required work**

- [x] Configure Supabase Auth for doctor email/password and patient/caregiver email OTP (27 Sep 2026): doctor email/password confirmed working via manual `Add user` + invite code; patient/caregiver email OTP required fixing the project's custom SMTP (see below) before it worked end to end.
- [x] Configure the web app with the public Supabase URL and publishable key (already set on the `khabar-landing` project).
- [x] Validate issuer, audience, signing keys, expiry, and role mapping in the deployed API. Local code derives the expected issuer from `SUPABASE_JWKS_URL` and validates it on asymmetric Supabase tokens; the separate demo HS256 path continues to validate audience/expiry against its own secret. `SupabaseTokenDecoderTest` now has 9 passing cases, including a correctly signed token with a wrong issuer, another project's key, the wrong secret, expired tokens, the wrong audience, unsigned and malformed tokens, both supported token kinds, and refusing to start with neither key configured. The current full API suite passed locally; deployed role behavior confirmed 27 Sep for all three roles (doctor, patient, caregiver — see below), each reaching the correct role-specific home with real data.
- [x] Test doctor sign-in on the deployed web app (27 Sep 2026): real Supabase account, doctor-invite code, live site, reached the doctor home with clinic data.
- [x] **Diagnosed and fixed a real Supabase email-delivery gap, then tested patient sign-in (27 Sep 2026).** First attempt: `POST /auth/v1/otp` to the project owner's own address (a confirmed team member) sent Supabase's default, non-editable "Magic Link or OTP" template — no custom SMTP was configured, so the dashboard itself refused template edits ("Set up custom SMTP to edit templates"). That default template's sign-in link pointed to `http://localhost:3000` (`ERR_CONNECTION_REFUSED` from the tester's phone) and showed no code; Site URL (`https://khabar-landing-six.vercel.app`) and the (empty) Redirect URLs allow-list were checked and ruled out as the cause. **First fix attempt:** connected Resend as custom SMTP (sandbox sender), added `{{ .Token }}` to the Magic Link template. **Retest:** full patient flow completed on the live site (invite code + emailed OTP), reaching the real patient home, role "Patient".
- [x] **Tested caregiver sign-in with a second, distinct real address (27 Sep 2026); found and fixed a second SMTP problem along the way.** Reused the patient's own address for a caregiver invite first, which just re-signed-in the same identity (not a real distinct-identity test). Retried with an unrelated real address (`@graduate.utm.my`): failed twice with Supabase Auth Logs showing `535 "Invalid username"` from `smtp.resend.com` — Resend's free sandbox sender in fact only delivers to the account owner's own address (confirmed as the cause, not a credentials typo, by testing a second address and reproducing the exact rejection). Switched custom SMTP from Resend to the project owner's own Gmail account (`smtp.gmail.com:587`, Gmail App Password) — no domain purchase needed, no sandbox recipient restriction. First retest still failed with the same `535 Invalid username`; Auth Logs plus a direct field-by-field re-check of the SMTP form found the **Host field had been left at `smtp.resend.com`** while Sender/Username/Password had already been switched to Gmail — Supabase was authenticating a Gmail login against Resend's server. Corrected Host to `smtp.gmail.com`; the very next attempt reached the Gmail relay but a first-time signup for that address used Supabase's separate **"Confirm signup"** template (not Magic Link/OTP), which didn't yet have `{{ .Token }}` either — added it there too. **Final retest:** full caregiver flow completed on the live site with a real, distinct address, landing on the caregiver home ("Consented caregiver access", "Supporting → Shared patient", "Read-only view: You cannot change the clinical record", "Consent active") viewing the patient's ("chicken lee") shared data read-only. All three roles (doctor, patient, caregiver) are now confirmed with real, independent Supabase-authenticated sessions on the live site. A separate, apparently non-blocking "The request could not be completed" toast appeared on the caregiver home and has not yet been investigated.
- [x] Test revoked caregiver consent immediately blocks access in the deployed environment (27 Sep 2026). From the real patient session, removed the caregiver in *My people* — the list changed to "No caregiver access". Without signing out or re-authenticating, refreshing the already-open real caregiver session on the live site replaced the "Shared patient / Read-only view" card with "Your account is ready for an invitation... Access begins only after the patient grants consent." Confirms the local API regression (below) also holds for a real, already-rendered browser session on the deployed site, closing the last open item in this section.
- Local API regression added 25 Sep: a caregiver first reads the fictional patient's record, the same consent row is revoked, and the next request is denied with HTTP 403 (`PatientRecordAccessTest.revokingConsentImmediatelyBlocksTheCaregiversNextRecordRequest`). This verifies server-side request-time revocation.
- [x] Enforce caregiver consent scope: `SUMMARY` can access only the approved summary endpoint; `SUMMARY_AND_ALERTS` also grants the shared patient card, medication list and readings. `/api/me` reports the per-patient scope and the caregiver UI hides those details for summary-only consent. `PatientRecordAccessTest` and `OnboardingTest` cover denied detail/medication/reading access, allowed summary access and scope visibility. The 27 Sep caregiver test above used `SUMMARY` scope and confirmed the UI hid detail/medication/reading views on the deployed site, matching the automated tests.

**Done when:** all three roles can sign in without a developer token and can access only the records allowed by their role. **Met 27 Sep 2026** — doctor, patient, and caregiver each completed a real, independent Supabase-authenticated sign-in on the live site and reached the correct role-specific home; caregiver access scope and immediate post-revocation blocking were both confirmed on the deployed environment, not just locally. One unrelated finding from this work remains open: a "The request could not be completed" toast appeared once on the caregiver home and has not been investigated; Resend's account-owner-only sandbox restriction (a viable free fallback for future single-recipient testing, but not for arbitrary patient addresses) is documented above.

### 1.3 Verify the deployed end-to-end product

**Status:** Deployed rehearsal with demo sign-in passed on 23 Sep (see the end of [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md)). On 25 Sep, read-only requests returned web 200, API `/api/health` 200, and 404 for both agent health routes. **27 Sep 2026:** the user redeployed `khabar-api`/agents from commit `f12cc9b` (`npx vercel deploy --prod`, aliased to `https://khabar-api.vercel.app`, confirmed via the CLI's own `Inspect`/`Aliased` output, not a new/disconnected project). `node scripts/check-health.mjs` returned `API: healthy` and `Agents: healthy` on retry after an initial cold-start timeout — the agent-health 404 is resolved on this deployment. Still open: repeat the public rehearsal with real Supabase sign-in and AuraDB connected, per-role permission checks, and re-confirming Vercel project build settings.

**Required work**

- [ ] Confirm deployed web, API, agent service, Supabase, and Neo4j environment variables are configured correctly.
- [x] Deploy and verify the current backend after current V1–V6 migrations pass hosted PostgreSQL/backup checks. Redeployed 27 Sep 2026 from commit `f12cc9b` (hosted CI green on run [36320590174](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36320590174), including the PostgreSQL 16 migration/backup steps inside the API test job); `node scripts/check-health.mjs` confirmed `API: healthy` and `Agents: healthy` post-deploy, resolving the earlier `/agents/health` 404. Vercel project build settings (Framework Preset/root directory) have not been separately re-verified since the 25 Sep CLI inspection recorded them as differing from the documented Services/root expectation — recheck before relying on them.
- [x] Perform an end-to-end rehearsal using fake data on the public URLs, with demo sign-in: pre-visit → draft → safety review → finalise → summary → follow-up reply → call list (booking and intake were run locally the same day).
- [ ] Repeat the rehearsal for doctor, patient, and caregiver permissions. **Mostly done 27–28 Sep 2026:** full core story (intake → pre-visit → shorthand notes → draft → safety check with CRITICAL gate → override → finalise → patient summary → urgent Malay reply → call list position 01) passed on the public deployment with real Supabase doctor and patient accounts; see [`docs/DEMO_RUN_2026-09-28_DEPLOYED.md`](docs/DEMO_RUN_2026-09-28_DEPLOYED.md). Still open: caregiver view of a populated summary, and cross-clinic / cross-patient denial on the deployment.
- [ ] Record defects and fix only issues that affect the core demo. Five findings recorded in [`docs/DEMO_RUN_2026-09-28_DEPLOYED.md`](docs/DEMO_RUN_2026-09-28_DEPLOYED.md): AuraDB unreachable (**fixed 28 Sep**: the free instance had auto-paused; resumed and re-synced, 32 patients written; graph writes made while paused are dropped without retry, so resume and re-sync before any demo), patient summary omits the free-text plan and specific warning signs (needs a clinician decision), misleading "draft ready" message (**fixed and live 28 Sep** via PR #1), invitation-code errors shown as if the email code failed (**fixed in PR #3**), and a caregiver-home toast (not reproduced locally; the earlier "summary 404" guess is ruled out; reproduce on the live site and read the logs within the hour). The summary-content finding still needs a clinician decision. Separately, `main` CI went red after PR #1 because three tests seeded dates in the machine's time zone instead of the clinic clock; fixed in PR #2 (production logic was unaffected).
- [x] Keep a concise demo-run checklist and result: [`docs/DEMO_RUN_CHECKLIST.md`](docs/DEMO_RUN_CHECKLIST.md) and [`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md).
- [x] Add a repeatable public API/agents health check (`services/scripts/check-health.mjs`) so a deploy is not considered healthy when the agents route returns 404.

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

- [x] Check Favoriot's current published plan limits (25 Sep 2026): the pricing page lists a lifetime RM0 tier with 1 device, 500 daily data points, 1 dashboard, 1 rule, and 1-month data retention ([official pricing](https://www.favoriot.com/iotplatform/pricing)); its 1 Sep 2026 notice says the subscription plans were restructured ([official notice](https://www.favoriot.com/category/press-release/)). The older free-plan signup page still lists unlimited devices, 500 API calls/day, and 1-year retention ([official signup page](https://www.favoriot.com/subscribe-free-plan)), so confirm which limits the actual new account receives before relying on the service.
- [ ] Verify the selected free-tier limits in the actual test account and confirm a one-device integration test is still available.
- [ ] Configure a test device or forwarding rule using the per-device secret.
- [ ] Send one blood pressure and one glucose reading through Favoriot.
- [ ] Verify a worrying reading affects the doctor call list.

**Decision:** Published pricing currently offers a limited one-device free tier; keep simulated readings as the demo fallback until account limits and a successful webhook/call-list test are confirmed. Do not use Favoriot readings for clinical care.

### 2.3 LLM and transcription evaluation

**Status:** Gemini and Groq integration points exist, but the formal selection experiments in `plan.md` are not documented as complete. **28 Sep:** a Gemini key was checked locally (key accepted, `gemini-3.6-flash` available, test call answered) and set as a Secret `GEMINI_API_KEY` on the `khabar-api` production project. The first live check-in then failed on every opening turn (`ValueError: contents are required`: the model received only a system message); fixed with a regression test and verified against the real API in PR #4, merged, **awaiting an API redeploy**. `GROQ_API_KEY` is still unset, so speech-to-text is unavailable in production.

**Required work**

- [x] Re-run the independent held-out word-list baseline locally (25 Sep 2026): **2/12 red replies caught, 16/22 replies under-triaged, 0 false alarms on expected-ok replies**. This is not the planned model comparison and does not validate emergency detection; report: [`docs/evals/triage-wordlist-current.md`](docs/evals/triage-wordlist-current.md).
- [x] Clarify the planned evaluation: the ten-case planted-error suite checks the deterministic evaluator and cannot select a model. The suite and clean-draft check remain automated regression coverage (`services/agents/tests/test_planted_errors.py`).
- [ ] Compare candidate models only on tasks where they are used, including the independent holdout plus clinician-authored triage set; requires a provider key and qualified clinician-authored examples. Do not claim emergency detection performance before this work and review are complete.
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

**Status:** Implemented locally and tested end to end with a stand-in model (23 Sep 2026); first real Gemini read on the live site passed on 28 Sep (see below). Patient/clinic-authorized users can upload an explicitly consented packet image through the API to the configured Gemini service for ephemeral label extraction. Results are mapped to known generics where possible and require user review before the existing medication-list add flow. The API and agent do not persist the image. A real-provider test remains open.

**Required work**

- [x] Add a consent-gated, size/MIME-checked image upload flow; uploads are not stored by Khabar and the UI warns to use fake/demo packets unless real-patient use is approved.
- [x] Add packet-label extraction behind the authenticated agent service (`services/agents/agents/packet_reader.py`).
- [x] Map extracted ingredient/brand text against known generics; present confidence/evidence and require explicit review before adding.
- [x] Connect reviewed candidate selection to the existing medication list.
- [x] Test duplicate and herb-clash findings end-to-end from a photo-derived item (`services/agents/tests/test_packet_to_findings.py`), and once by hand through the real API and agents with a local stand-in for Gemini (`GEMINI_API_BASE`): the doctor's pre-visit check named the packet photo in the CRITICAL duplicate.
- [x] Run a real Gemini test on a fictional packet image (28 Sep, live site, patient account). A rendered fictional box ("BRAND A", "Metformin Hydrochloride Tablets 500 mg", "FICTIONAL DEMO PACKET - NOT A REAL PRODUCT") was uploaded after the consent tick. The label was read verbatim, "Brand A" was mapped to the generic metformin, and the result showed 500 mg, film-coated tablets and high confidence, with "this is not a safety check" and nothing added until *Add after review*. After adding it, the doctor's record listed it as "metformin 500 mg · Packet photo — please verify" and showed **Critical · duplicate**: "Metformin is taken 2 times: 'metformin 500mg' from Told Khabar at intake and 'metformin 500 mg' from Packet photo — please verify", alongside the bitter gourd herb check. The photo → review → list → duplicate warning chain is confirmed with the real provider. One clean, printed, fictional image is not an OCR quality measurement; photos of real packets (glare, curved boxes, handwriting, Chinese/Tamil text) are still untested.

**Scope note:** This is a P1 feature and may be reduced to the existing typed medicine list if time is limited.

### 3.2 Voice-note summaries (A3)

**Status:** Partial, in PR #6 (open). A "Listen to this plan" button on the patient and caregiver homes reads the approved summary aloud with the device's own speech engine. It speaks the approved text only, and only with a voice in the summary's language; with no such voice it says so instead of using another language's voice. Checked locally with a fictional Malay summary (no-voice note; stub engine chose `ms-MY` over `id-ID`; stop and end handling). There is no provider-generated audio and no WhatsApp audio message.

**Required work**

- [ ] Select a text-to-speech provider after testing BM, Chinese, and Tamil quality. (Device voices are used for now; their quality varies by phone.)
- [x] Speak the doctor-approved summary only (in-app read-aloud, PR #6). A generated audio file for messaging is still open.
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

- [x] Prepare a draft facilitation protocol and low-data score sheet ([`docs/PATIENT_UNDERSTANDING_TEST_DRAFT.md`](docs/PATIENT_UNDERSTANDING_TEST_DRAFT.md)). It requires owner, ethics/event, clinician, fluent-reader, and privacy review before recruitment; preparation is not participant evidence.
- [ ] Recruit five people using their preferred languages.
- [ ] Compare English-only and Khabar summaries using the balanced two-case method in `plan.md`.
- [ ] Ask the three planned questions: medicine, timing/dose, and warning symptom.
- [ ] Report all results honestly as a five-person pilot; do not overstate the conclusion.

### 4.2 Demo, pitch, and reliability work

**Status:** Future scheduled work; not evidenced as complete.

- [x] Bug bash the core workflow locally ([`docs/BUG_BASH_2026-09-23.md`](docs/BUG_BASH_2026-09-23.md)). Repeat on the deployed site once it is redeployed.
- [x] Prepare stable fake demo data, including Mak Cik Aminah’s full story. The local demo profile seeds fictional patients, and `POST /dev/demo/reset` restores Aminah’s intake, conditions, exact three medication/remedy entries, caregiver consent, and booked appointment; `services/README.md` documents the reset. The 25 Sep local rehearsal used the seeded story. This does not seed or alter production data.
- [ ] Move services to reliable / always-on hosting before a live demo.
- [x] Prepare first drafts of 3-, 5-, and 7-minute pitch versions (`docs/PITCH_SCRIPTS.md`). Personalization, factual check against the live demo environment, and timed rehearsal remain open.
- [ ] Record and review a demo video.
- [ ] Rehearse the demo with provider-failure fallbacks. Local checks have covered API outage and retry, a five-second delayed recovery-update response, a browser-simulated request failure before the API received it followed by one retry, browser-context offline/reconnect and Chromium-throttled recovery update, and lost-response-after-commit/reload for recovery updates and visit finalisation (see `docs/DEMO_RUN_2026-09-25_LOCAL.md`, `docs/RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`, and `docs/FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`). API tests verify a repeated request does not triage or notify twice. A local API-only cold-start sample returned healthy in 11.97 seconds from launch (details in `docs/COLD_START_CHECK_2026-09-25_LOCAL.md`); this is not deployed performance evidence. Device-wide/mobile network loss, WhatsApp/provider outage, PostgreSQL concurrency, and deployed rehearsal remain open.
- [x] Create a repeatable rehearsal checklist with core workflow, role/access checks, failure fallbacks, and a result template (`docs/DEMO_RUN_CHECKLIST.md`). This is preparation only; no rehearsal result is implied.
- [x] Run a local fictional-data rehearsal on 25 Sep, including a completed guided intake visible in the doctor pre-visit report, visit safety gate, final summary/outbox, patient urgent reply/call-list update, linked/unlinked caregiver access and immediate revocation, the agent-down reply fallback, and local outage/retry ([record](docs/DEMO_RUN_2026-09-25_LOCAL.md)). Recovery update retries are idempotent by client request ID and preserve only that opaque ID across reloads. Lost-response/reload recovery and browser-context offline/throttled-network behavior were checked locally for updates; finalisation lost-response/reload was also checked. Graph-backed context, device-wide network failure, provider outage, PostgreSQL concurrency, and deployed rehearsal remain open.

### 4.3 Documentation habit

**Status:** `docs/EXPLAIN.md` exists, but daily coverage has not been verified.

- [x] Add a dated entry explaining the DDInter evaluator and sign-in changes in plain language (`docs/EXPLAIN.md`, 23 Sep 2026). Ongoing daily coverage remains the builder's responsibility.
- [x] Record architecture decisions, integration setup steps without secrets, and test results (`docs/DECISIONS.md`, `docs/SETUP_INTEGRATIONS.md`, `docs/TEST_RESULTS.md`, and `services/README.md`). Keep these records current as changes are made.
- [x] Prepare a short code-area study list for the SDC review (`docs/CODE_TO_EXPLAIN.md`). The checklist is intentionally unticked: the builder still needs to read each area and confirm they can explain it without notes.

---

## 5. SDC decisions and administration

These are not implementation tasks, but they are still open in `plan.md`.

- [x] Check the official handbook: all SDGs 1–17 are in scope; teams are randomly assigned a domain, while its challenge brief and judging weights are supplied in the post-registration Participant Handbook (§§7.1–7.3 and 3).
- [ ] Ask organisers whether a pre-existing-code declaration can be amended after registration and how to declare additional reused code; §8.2.1 requires approval.
- [ ] Confirm final pitch duration and operational judging details when the Participant Handbook is issued.
- [ ] Decide available working hours for the SDC week alongside classes.
- [x] Create a separate local Git repository at `../starter-skeleton/` containing the generic skeleton. It currently has no remote configured; publish/configure the intended repository and confirm its contents before registration.
- [x] The tracked starter-skeleton files contain the generic example flow rather than Khabar clinical features, screens, patient data, or prompts. Review the full tracked history again before publication.
- [x] Compare the starter declaration with the official handbook §§8.2.1, 8.4.1, and 8.5; the draft now states organiser approval is required and separates starter disclosure from project-wide tool/resource disclosure (`../starter-skeleton/DECLARATION_DRAFT.md`). Prepare a separate project-wide disclosure draft (`docs/PROJECT_DISCLOSURE_DRAFT.md`) from code and workspace evidence without credentials.
- [ ] Still open: publish the starter, confirm the exact AI tools and provider use with the project owner, finalize the resource list, submit during registration, and obtain organiser approval. The starter repository's separate checklist is [`../starter-skeleton/UNDONE_WORK.md`](../starter-skeleton/UNDONE_WORK.md).

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

- [x] Browser-check lost-response recovery on 25 Sep using a local one-shot proxy: the API returned 200 upstream, the proxy dropped the browser response, and after reload the same fictional update received the stored acknowledgement on retry. The draft text was not persisted. See [`docs/RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](docs/RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md). API tests separately confirm retry idempotency (one reply, one triage, one notice); provider and deployed-network checks remain open.

\n

\n

\n
