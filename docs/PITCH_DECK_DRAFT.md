# Khabar presentation deck draft

**Status:** Browser-viewable local draft. Not a public deck, event submission, or clinical-readiness claim. Adapt it to the official challenge brief and the verified demo environment before presenting.

**Visual preview:** [Open the local browser deck](PITCH_DECK_DRAFT.html).

**Audience:** Hackathon judges / prototype reviewers  
**Target:** 5 minutes plus a live demo  
**Personalize before use:** team name and members, event-specific problem statement, final demo URL, and demo-day evidence.

---

## Slide 1 — Khabar: care continues after the visit

**On-slide copy**

> A clinic follow-up prototype for clearer care plans and organized follow-up.

**Footer:** Fictional-data practice build · Not for clinical use

**Visual:** Khabar wordmark with a calm clinic-to-home path graphic.

**Speaker note:** “We’re [team name]. Khabar explores how a clinic can help a patient understand the plan after leaving an appointment, while keeping the clinician in control.”

---

## Slide 2 — The gap we are exploring

**On-slide copy**

- Patients leave with medicines, instructions, and warning signs to remember.
- Their medicine and remedy context may come from more than one place.
- Clinics need a clear way to review follow-up needs.

**Callout:** These are the workflow assumptions behind this prototype. They are not findings from a completed patient study.

**Visual:** A simple timeline: clinic visit → home → question or follow-up need → clinic review.

**Speaker note:** Keep the framing as a problem hypothesis. Do not cite prevalence, adherence, or outcome statistics unless a source has been checked and added.

---

## Slide 3 — One clinician-led follow-up loop

**On-slide copy**

1. Patient shares visit context and current medicines.
2. Khabar organizes a draft and flags items for clinician review.
3. The clinician resolves findings and approves the plan.
4. The patient sees a plain-language summary; replies needing review enter a clinic queue.

**Visual:** Four-step loop with the clinician approval step emphasized.

**Speaker note:** A language model does not prescribe or set a dose. Patient summaries are built from the approved prescription and fixed templates. A queue entry does not notify staff or guarantee it has been seen.

---

## Slide 4 — Safety boundaries are part of the design

**On-slide copy**

- Critical findings block finalization until resolved or explicitly overridden with a reason.
- Access is role- and consent-scoped; identity is removed before agent processing.
- A red or unclassified follow-up reply receives fixed precautionary 999 wording.
- The triage classifier is not a reliable emergency screen; staff alerting is not implemented.

**Visual:** A three-layer gate: clinician UI → API rule → database constraint.

**Speaker note:** The word-list evaluation reached 22/22 on the examples it was tuned against and 2/12 on held-out replies. These results do not establish emergency detection. See [the evaluation notes](evals/README.md). Khabar is a fictional-data prototype, not a medical device and not for clinical use.

---

## Slide 5 — What has been built and checked

**On-slide copy**

- Next.js web app, Spring Boot API, FastAPI agent service, Supabase auth wiring, and Neo4j graph integration.
- Local verification: API 226 passed (one optional local PostgreSQL test skipped), agents 205 passed, and web lint, type-check, and build passed.
- Hosted CI passed all jobs, including PostgreSQL migration validation and a disposable backup/restore recovery-point check.

**Visual:** Architecture diagram with the API owning records and access, and the agent service receiving de-identified task context.

**Speaker note:** The tests and CI establish software checks on this branch. They do not establish clinical correctness, real-provider behavior, or deployment readiness. See [test results](TEST_RESULTS.md) and [workspace verification](WORKSPACE_VERIFICATION_2026-09-25.md).

---

## Slide 6 — What remains to prove

**On-slide copy**

- Deploy and rehearse the current branch; the latest public agent health route still returns 404.
- Verify real sign-in and access revocation for each role.
- Complete provider tests and qualified clinical/pharmacy review.
- Test accessibility and patient understanding with representative people.
- Design and evaluate a clinic-owned notification and escalation process before any pilot.

**Visual:** Evidence ladder: software checks → deployment checks → provider checks → human and clinical review.

**Speaker note:** Do not imply that replies reach a clinician. The current system places review-needed replies in a queue, without staff notification. Keep all demonstrations on fictional data.

---

## Slide 7 — Next step

**On-slide copy**

> Validate the workflow with clinicians and patients before making claims about benefit.

**Footer:** [team contact] · [verified demo URL] · Fictional data only

**Speaker note:** Adapt this closing to the official challenge brief after the domains and problem statement are announced. Do not present the project as an SDC entry or claim impact without evidence.

---

## Presentation checks still required

- Replace placeholders with the actual team details and event-specific framing.
- Export or rebuild it in the required public format and inspect that version after team/event personalization.
- Verify every demo screen against the exact deployed branch and environment.
- State whether messaging is simulated or has passed a real provider test.
- Rehearse aloud, time the talk, and adjust after the official challenge brief is known.
- Record and review the demo video separately; this draft does not satisfy that task.
