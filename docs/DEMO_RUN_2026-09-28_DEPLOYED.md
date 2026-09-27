# Deployed demo run — 27–28 September 2026

First complete core-story rehearsal on the **public deployment** using **real Supabase accounts**
for each role (not the demo-token buttons). Fictional data only.

## Result record

- **Date/time:** 27 Sep 2026 ~23:40 to 28 Sep 2026 ~00:30, MYT (UTC+8)
- **Environment:** production. Web `khabar-landing-six.vercel.app` (deployed from `main` at `79924b3`, 24 Sep); API and agents `khabar-api.vercel.app`, deployed manually on 27 Sep from `pilot-foundations` at `f12cc9b` (hosted CI run 36320590174 green).
- **Accounts (role labels only):** one real Supabase doctor account (email/password, linked by doctor-invite code); one real patient account (email OTP, linked by patient-invite code); one real caregiver account on a separate address (email OTP, linked by caregiver-invite code; consent revoked before this run). Email delivery through Gmail SMTP configured the same day; see `UNDONE_WORK.md` §1.2.
- **Fictional patient:** a new patient registered by the doctor through *Add patient* on 27 Sep (display name only, no real identity).
- **Core story:** PASS, with findings below.
- **Role/access checks:** PARTIAL. Real sign-in for all three roles, caregiver scope and immediate revocation passed on 27 Sep (`UNDONE_WORK.md` §1.2). Cross-clinic and cross-patient denial were not exercised on the deployment.
- **Failure/fallback checks:** one unplanned fallback observed (patient graph unreachable; see finding 1).

## Core story steps and outcomes

| Step | Outcome |
| --- | --- |
| Patient completes guided intake (scripted, 4 questions) | PASS. Reason, medicines (metformin 500mg, bitter gourd juice) and allergies captured. |
| Doctor opens the patient record | PASS. Reason for visit, "worth asking about: dizzy", both medicines tagged "Told Khabar at intake", a herb warning (bitter gourd with metformin), masked IC number, "access logged". |
| Start today's visit, pre-visit context | PASS. Reason, allergies (none), everything they take. |
| Notes to structured draft | PASS on the second attempt. A one-paragraph prose note produced no fields (expected: the report parser is deterministic and reads clinic shorthand, one item per line). A shorthand note (`Dx:`, `Plan:`, `T. Metformin 500mg 1/1 BD PC`, `TCA 2/52 FBS`, `RTC if ...`) produced diagnosis, plan, follow-up and a correct prescription row (500 mg, 1, 2/day, after food). See finding 3. |
| Safety check | PASS. CRITICAL duplicate (metformin already taken per intake), herb warning (bitter gourd with metformin), and a graph-context warning. Final gate showed "1 critical finding still needs a written reason" and finalisation was blocked. |
| Record override reason and finalise | PASS. Rehearsal-only reason recorded; "This report is final." |
| Patient summary | PASS for medicine, strength, dose, frequency, food timing and follow-up interval; matches the approved prescription exactly, labelled EN. See finding 2 for what it omits. |
| Patient home after finalisation | PASS. Status changed to "Plan active — your clinic is following your recovery"; same summary shown. |
| Urgent follow-up reply (Malay: chest pain since morning, feeling faint) | PASS. Reply in the patient's language (EN): added to the follow-up list, "the clinic may not have seen it yet", call 999 for chest pain / breathing trouble / fainting. No callback promised. |
| Doctor call list | PASS. The reply appeared at position 01, labelled `urgent · reply`, above seeded urgent items. |

## Findings

1. **Patient graph (AuraDB) unreachable from the deployed agents — resolved 28 Sep.** The safety check returned the `unreachable` graph-context state (configured but connection failed), not `not_configured`. The system degraded as designed: the duplicate was still caught from the intake medicine list and a visible warning told the doctor to review the record manually. **Cause confirmed:** the Neo4j console showed the AuraDB Free instance `ebc9e325` as PAUSED (data intact: 53 nodes, 69 relationships), after four days without use. **Fix:** resumed the instance (RUNNING), then `POST /dev/graph/sync` on the deployed API returned `enabled: true, written: 32` (31 seeded patients plus the new rehearsal patient). **Follow-up gap:** `PatientGraphSync.sync` only logs a warning when a write fails and never retries, so every patient change made while the instance was paused (here: the new patient, their intake medicines and the finalised visit) silently went missing from the graph until the manual re-sync. Before any live demo: check the Aura console, resume if paused, and run the sync. A longer-term fix would be a retry or a periodic reconciliation, plus surfacing graph write failures on the admin integration-health view.
2. **Patient summary omits the doctor's free-text plan and specific warning signs.** "Stop bitter gourd juice" and "RTC if chest pain or fainting" did not reach the patient. This is by design (`agents/summary.py` builds summaries only from the parsed prescription plus fixed, reviewed phrases, so doses and times cannot be mistranslated), but it means an important instruction is not given to the patient in writing. **Action:** clinician/product decision; options include reviewed phrase templates for common instructions, or showing the doctor exactly what the patient will and will not receive before finalising.
3. **Deployed web shows "The structured draft is ready for your review" when the draft is empty.** Already fixed on `pilot-foundations` (`visit-workspace.tsx` checks for returned fields and otherwise explains what to change), but the web deploys only from `main`, so production still had the old message. **Shipped 28 Sep:** PR #1 merged `pilot-foundations` into `main` and the web deploy workflow published it. Still worth considering: a placeholder or hint in the notes box showing the expected shorthand.
4. **Sign-in error blames the email code when the invitation code is the problem.** An already-used patient invite code left in the *Have an invitation code?* field caused `/api/invites/{code}/accept` to return 410 ("That code has already been used or has expired. Ask for a new one.") after the email OTP had already been accepted. The message reads as an OTP failure, and the OTP is consumed. **Fixed in PR #3:** the sign-in panel now says the invitation code was the problem and keeps the verified session. Already-registered accounts get *Continue without the code*; others can retry the invitation without a new email code. Browser-checked locally for the registered path only.
5. **Caregiver home showed a "The request could not be completed" toast** once on 27 Sep; not yet confirmed. **Likely lead (28 Sep):** in a local check, a patient with no finalised visit produced `404` from `GET /api/patients/{id}/summary`. The caregiver on 27 Sep was viewing a patient who had no finalised summary yet, so the home page probably treats "no summary yet" (404) as a failed request. Confirm by opening the caregiver home for a patient without a summary, then show an empty state instead of an error.

## Not covered in this run

- Caregiver view of a **populated** summary (the caregiver's consent was revoked before the visit was finalised).
- Cross-clinic doctor denial and cross-patient denial on the deployment.
- Booking an appointment through the patient UI.
- WhatsApp delivery (summary and check-in went to the outbox; no external message claimed).
