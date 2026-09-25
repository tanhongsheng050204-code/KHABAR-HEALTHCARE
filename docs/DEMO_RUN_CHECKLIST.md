# Khabar demo run checklist

Use fictional records only. This checklist is a reusable rehearsal aid. A historical partial
deployed run is recorded in [DEMO_RUN_2026-09-23.md](DEMO_RUN_2026-09-23.md); it does not
verify the current branch or deployment. Record each new run's date, commit, environment, and outcome below.

## Before the run

- [ ] Choose local or deployed environment and record its base URLs; do not mix local and production data.
- [ ] Confirm API and agent health checks pass.
- [ ] Confirm the selected clinic and fictional patient exist. If rebuilding local data, use the demo profile's fake-patient generator; never enter a real person's data.
- [ ] Confirm the selected patient has a fictional condition, medication list, allergy/herb context, and a usable appointment.
- [ ] Confirm demo-only auth is in use locally, or real role sign-in is configured in the deployed environment. Never paste tokens into this document.
- [ ] Confirm messaging fallback: local runs may use the outbox; live WhatsApp runs require approved templates and a designated test number.
- [ ] Open the patient record, visit workspace, and call list before starting.

## Core story

- [ ] Book or open the fictional patient's appointment.
- [ ] Complete intake and confirm the pre-visit report reflects the patient's answers.
- [ ] Review the patient's current medicines, allergy, and herb context.
- [ ] Enter fictional consultation notes and generate the draft.
- [ ] Run the safety review; confirm planted duplicate/interactions are visible and critical findings block finalisation.
- [ ] Resolve or document the required override reason; finalise the visit.
- [ ] Review the patient summary in the selected language and verify medicine, dose, timing, and warning signs against the approved visit.
- [ ] Confirm the follow-up plan/check-in is created.
- [ ] Send through the local outbox or approved WhatsApp test configuration; do not message a real patient.
- [ ] Submit a fictional follow-up reply, verify its triage, and confirm the call list reflects any urgent item.

## Role and access checks

Run each check with a separate doctor, patient, and caregiver session in an isolated test environment.

- [ ] Doctor can open only their clinic's assigned records and complete the intended visit workflow.
- [ ] Patient can open their own record and manage consent, but cannot access another patient's record.
- [ ] Caregiver can see only the linked patient's consented scope.
- [ ] Revoke caregiver consent and verify access is denied immediately, including on a fresh request/session.
- [ ] Record both allowed and denied outcomes. Do not use a real person's records.

## Failure and fallback checks

- [ ] With the agent service unavailable, confirm the API gives a clear recoverable error and does not lose already-saved clinical data.
- [ ] With WhatsApp unavailable/unconfigured, confirm the local outbox/fallback is visible and no message is claimed as delivered.
- [ ] With Neo4j unavailable locally, confirm the core record remains usable and graph-dependent context is clearly treated as unavailable.
- [ ] Throttle or disconnect the network during a non-destructive screen action; confirm the user can retry and no duplicate finalisation/message is created.
- [ ] Note cold-start delay separately from functional failure.

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
