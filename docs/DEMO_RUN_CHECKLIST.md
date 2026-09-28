# Khabar demo run checklist

Use fictional records only. This checklist is a reusable rehearsal aid. A historical partial
deployed run is recorded in [DEMO_RUN_2026-09-23.md](DEMO_RUN_2026-09-23.md); it does not
verify the current branch or deployment. Record each new run's date, commit, environment, and outcome below.

## Before the run

- [x] Local run: environment and base URLs recorded; H2 and fictional data were used without mixing production data. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md). A deployed run remains open.
- [x] Local API and agent health checks passed at startup. This does not establish current public deployment health; see [`WORKSPACE_VERIFICATION_2026-09-25.md`](WORKSPACE_VERIFICATION_2026-09-25.md).
- [x] The seeded fictional clinic/patients and usable appointment were confirmed in the local rehearsal; the seed included a condition and medicine context.
- [x] The local safety review surfaced a planted duplicate-medicine critical finding and an herb warning. The complete clinically reviewed allergy/medicine context check remains open.
- [x] Local demo-only identities were used. Real role sign-in remains open.
- [x] Local messaging used the outbox and the rehearsal explicitly recorded that no external message was sent. Real WhatsApp requires approved templates and a designated test number.
- [x] The patient view, visit flow, and doctor call list were opened during the local rehearsal; see the recorded outcomes in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).

## Core story

- [x] Open the fictional patient's existing booked appointment.
- [x] Complete the four-question fictional intake and confirm the pre-visit report reflects the answers and medicine-list items.
- [x] Review the seeded fictional medicine/herb context through the pre-visit report and safety findings. This is not clinical validation of the source data.
- [x] Enter a clearly labelled fictional rehearsal note and generate the visit draft.
- [x] The UI notice was corrected to distinguish an empty agent result from a structured draft; lint, typecheck, and production build passed.
- [x] Post-fix browser check: an intentionally unstructured fictional note produced the “no structured draft fields” message; the page still showed “No draft yet” and kept safety check/finalisation disabled.
- [x] Run the safety review; a planted duplicate medicine blocked finalisation and a herb finding appeared as a warning.
- [x] Record a rehearsal-only override reason and finalise the visit. This was not a clinical decision.
- [x] Review the patient summary in the selected language and verify medicine, dose, timing, and warning signs against the approved visit. Deployed run 28 Sep ([record](DEMO_RUN_2026-09-28_DEPLOYED.md)): medicine, strength, dose, frequency, food timing and follow-up interval matched exactly (EN). Warning signs are a fixed generic sentence, and the doctor's free-text plan ("stop bitter gourd juice") and specific warning signs were not included; this is by design and is recorded as a finding needing a clinician decision.
- [x] Confirm finalisation started the follow-up plan.
- [x] Confirm the summary was queued in the local outbox; no WhatsApp delivery was claimed or attempted.
- [x] Submit a fictional urgent follow-up reply, verify the precautionary response, and confirm it appeared at the top of the doctor call list.

## Role and access checks

Run each check with a separate doctor, patient, and caregiver session in an isolated test environment.

- [ ] Doctor can open only their clinic's assigned records and complete the intended visit workflow. Deployed 28 Sep with a real doctor account: the visit workflow completed ([record](DEMO_RUN_2026-09-28_DEPLOYED.md)). Cross-clinic denial is not exercised on the deployment: the public demo seeds a single clinic, so testing it means creating a second clinic in production data first (covered locally by `ClinicPatientListTest` and `PatientRecordAccessTest`).
- [x] Patient can open their own record and manage consent, but cannot access another patient's record. Deployed 27–28 Sep with a real patient account: own record, summary and consent management (invite and revoke a caregiver) worked. **Cross-patient denial on the deployment (28 Sep, read-only requests):** the demo patient's token got 200 for her own record and **403** for another patient's record, summary, medicines, readings, access log and intake.
- [x] Caregiver can see only the linked patient's consented scope. Deployed 27 Sep with a real caregiver account: `SUMMARY` scope showed a read-only view and revocation blocked the open session on refresh. **28 Sep, read-only requests:** the demo caregiver (linked to a different patient) got **403** for the unlinked patient's record, summary, medicines and readings. Still to see on the deployment: a caregiver viewing a populated summary.
- [x] Unauthenticated and forged requests are refused on the deployment (28 Sep): no token and an HS256 token with an invalid signature both got **401** for a patient record.
- [x] Local current-worktree UI check: a fictional caregiver with `SUMMARY` scope saw the summary-only state with no medication or reading details. The test patient had no finalised summary; deployed access and populated-summary rendering remain unverified. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Local H2 check: after consent was revoked, a fresh caregiver request was denied (403). This is not a deployed or real-account check. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Allowed and denied caregiver outcomes were recorded using fictional records: linked patient 200, unrelated patient 403, and revoked-consent fresh request 403. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md). No real person's records were used.

## Failure and fallback checks

- [x] Stop FastAPI while Spring remains available: a fictional unclassified reply was stored as `REVIEW` and received cautious fallback wording. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] WhatsApp was unconfigured; the final summary appeared in the local outbox and the record states no external delivery occurred. This does not test an outage after provider configuration.
- [x] Current-worktree browser check with Neo4j disabled: the fictional visit and safety review remained usable and the safety card displayed a non-blocking graph-context warning directing manual record review; the duplicate-medicine critical gate remained in force. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Agent safety checks now return a visible, non-blocking `patient_graph_context` warning when the graph is unconfigured, unreachable, missing the patient, or missing a graph ID. The warning says graph-derived medicines, allergies, pregnancy status, and herbs were not checked and directs a manual record review. Agent tests cover those states; web lint, typecheck, and production build pass. See [`TEST_RESULTS.md`](TEST_RESULTS.md).
- [x] Simulate finalise responses being lost after the API commits; confirm a reload shows the visit as final. See [`FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md).
- [x] Delay a local fictional recovery-update response by five seconds; confirm the send control stays disabled while pending, one POST succeeds, the form clears, and the acknowledgement appears. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Simulate a recovery-update network failure before the API receives the first request; confirm the draft and client message ID are preserved, then retry and confirm exactly one request reaches the API. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Exercise connection-loss/recovery over HTTP: stop the local API while a fictional recovery-update draft is open, observe the failed request and retained draft, restart the API, then retry successfully. This simulates endpoint disconnection/recovery, not device-wide offline mode or bandwidth throttling. See the follow-on check in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Browser-emulate offline and a throttled connection on the current-source fictional patient recovery-update flow: the offline POST failed with the draft retained, then one retry at 1,200 ms latency and constrained throughput stayed pending, succeeded once, cleared the draft, and displayed the acknowledgement. This is browser-network emulation only; device-wide, mobile-network, packet-loss, provider, and deployed checks remain open. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Record local API cold-start separately from functional failure: 11.97 seconds from launch to first healthy HTTP response; Spring startup reported 6.557 seconds. This is one local API-only sample, not a web/agents or deployed cold-start check. See [`COLD_START_CHECK_2026-09-25_LOCAL.md`](COLD_START_CHECK_2026-09-25_LOCAL.md).

## Keyboard accessibility follow-up

- [x] Source fix: the login page has a “Skip to sign in” link, and the shared “Skip to workspace” link targets overview main landmarks that accept programmatic focus (`tabIndex=-1`) across doctor, patient, caregiver, record, visit, loading, and error views. The local sign-in accessibility tree exposes the link and named target.
- [x] Localized patient-facing summary text carries its BCP 47 language code for assistive-technology pronunciation; clinician, patient, and caregiver summary displays are covered in source.
- [x] The clinician visit-note textarea has a visible programmatic label; the remaining textareas in the web components are associated with wrapping labels.
- [x] Partial live keyboard check (25 Sep): in the current production-built web preview, Tab exposed a visible focus ring on “Skip to sign in”; Enter moved focus to the named sign-in section. In the fictional doctor demo home, Tab exposed “Skip to workspace”; Enter moved focus to the overview main landmark, and the next Tab reached “Add patient.” This covers those two skip links and the first post-landmark control only; the all-role/all-route pass below remains open.
- [x] Partial live keyboard check (25 Sep): on the fictional patient-record route, Tab and Enter activated “Skip to workspace”; focus then advanced to “Back to clinic.” This does not cover the visit route or every control in the record.
- [x] Partial live keyboard check (25 Sep): the production-built patient view also moved focus from “Skip to workspace” to the overview, then to “Send a new check-in.” In the development-only caregiver view, the Next.js Dev Tools button precedes the app in tab order; the next Tab focuses the workspace skip link and Enter focuses the main target. That caregiver view failed closed because the connected local API is older and did not return the current sharing-scope field; no shared details were exposed, and this is not a current-backend permission test.
- [x] Browser-driven Tab pass (25 Sep): login, doctor home, patient home, and doctor patient-record routes were each tabbed until the first target repeated. All 114 targets across those runs were visible and matched `:focus-visible`; login/doctor/patient/record skip-link behavior has also been checked. The doctor motion toggle set `data-motion=off`; system-level reduced motion was not enabled. Full scope and limits are in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- [x] Isolated current-source route check (25 Sep): caregiver and visit views ran against the current API in a separate in-memory H2 stack. Five caregiver and 13 visit app controls matched `:focus-visible`; both skip links focused their overview. The caregiver content reflected the active shared scope. The visit was left as an empty draft and disappeared when the isolated API stopped. See [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md); the remaining screen-reader and human accessibility review stays open.
- [ ] Manually tab through each role and route, confirm focus visibility and logical order, and verify the skip link moves focus to the main landmark.
- [ ] Complete and record a screen-reader review, including pronunciation across English, Bahasa Melayu, Chinese, and Tamil; source markup and Next.js lint do not substitute for assistive-technology testing.

## Result record

- Date/time and timezone:
- Commit / branch:
- Environment (local/staging/production):
- Tester and role accounts used (role labels only; no credentials):
- Fake patient identifier (non-PII demo ID only):
- Core story: PASS / FAIL / NOT RUN
- Role/access checks: PASS / FAIL / NOT RUN
- Failure/fallback checks: PASS / FAIL / NOT RUN
- Defects (steps to reproduce, expected vs actual):
- Follow-up owner and next action:
