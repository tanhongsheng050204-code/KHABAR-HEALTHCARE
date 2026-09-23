# Khabar Future Product Plan

**Status:** Proposed direction for future product discovery; not a commitment to build every phase.
**Audience:** Current Khabar patients, their consented caregivers, and their clinic teams.
**Relationship to existing plans:** This describes a possible next product direction. It does not replace the build priorities or SDC scope in [`plan.md`](plan.md), or the implementation backlog in [`UNDONE_WORK.md`](UNDONE_WORK.md).

## Product decision

Explore a connected follow-up experience for people already receiving care through Khabar. Patients can share blood-pressure and blood-glucose readings and answer follow-up check-ins. Khabar can put concerning information into the clinic's existing review workflow. A patient may also give a caregiver access to specifically selected information.

The larger vision may eventually include wearable data, a shared family dashboard, and optional assistants for everyday wellbeing. Build and validate one useful care workflow before expanding to those features.

**The first version centers on clinic-led follow-up for current Khabar patients. Validate a broader family wellness service separately.**

## Decisions agreed so far

- The first audience is current Khabar patients and families connected to their care.
- The longer-term vision includes proactive tracking, a shared family view, and specialist wellbeing support.
- The first signal types to explore are both blood pressure and blood glucose.
- Use one clinic review workflow for both; let clinics approve different criteria for each measurement.
- A concerning reading should flag the clinic's existing review list.
- The first version's data source is still open. Start by validating the manual reading and check-in workflow; consider wearables only after user and clinic feedback.

## Why this fits Khabar

Khabar already centers on care after the appointment: plain-language summaries, scheduled check-ins, a clinic call list, patient-recorded readings, and caregiver access with patient consent. The Favoriot device-reading path has been exercised with simulated data; live device testing remains open. Details are in [`services/README.md`](services/README.md) and [`UNDONE_WORK.md`](UNDONE_WORK.md).

This idea extends that care thread. Tracker, family view, and follow-up assistance can begin as coordinated capabilities within the existing patient, caregiver, and clinic experience.

## Intended users and value

- **Patient:** Understand the follow-up plan, share a reading or update, see what happens next, and choose whether to share anything with family.
- **Caregiver:** See only the details the patient has chosen to share and help with agreed follow-up tasks.
- **Clinic team:** See important readings and replies in one review queue, with enough context to decide who needs attention.

## Phased roadmap

### Phase 0 — Confirm the problem and workflow

Before adding integrations, walk through the proposed workflow with current patients, caregivers, and clinic staff. Use fictional data for mock-ups and demonstrations.

- Ask patients and caregivers what follow-up is difficult today and what information they would want to share.
- Ask clinic staff which kinds of readings and missed check-ins are actionable, and who should review them.
- Map patient, caregiver, and clinic permissions for each view and notification.
- Demonstrate the workflow using Khabar's existing check-ins and manual BP/glucose reading entry.
- Record confusing, unwanted, or alarming messages before deciding what to build.

**Gate to Phase 1:** Patients find the follow-up flow understandable, and clinic staff agree that its review list fits their work.

### Phase 1 — One clinic-review workflow for readings and check-ins

Support both blood pressure and blood glucose through one clinic review experience. Reuse Khabar's reading and call-list foundations where possible.

- Show each reading's type, value, unit, time, and source (for example, patient-entered or connected device).
- Keep review criteria specific to each measurement and approved by the clinic, while using one shared clinic-review workflow.
- Let clinic staff review and resolve flags. Preserve enough context to understand the patient's recent check-ins and readings.
- Show the patient that an item was received and whether it is waiting for clinic review.
- Handle missing, stale, duplicated, or out-of-order readings clearly; never present old data as current.
- Keep the existing red-flag response path visible. A flag must not silently replace an urgent, already-approved instruction to contact emergency help.

**Phase boundary:** This phase covers routing information for review. Diagnosis, treatment or medication changes, automatic workout plans, decisions based on a single wearable score, and continuous-monitoring claims require separate evidence and review before consideration.

**Gate to Phase 2:** A small, supervised pilot using fake or explicitly approved test data shows that the clinic can review flags reliably and that patients understand the messages.

### Phase 2 — Patient-controlled family view

Make caregiver participation useful without giving a family member blanket access to a household's health data.

- Let the patient grant a named caregiver a clearly described access scope.
- Distinguish information the caregiver can view from actions they can take.
- Let the patient see who has access, review access history where available, and revoke access.
- Check authorization whenever data is requested; withdrawing consent must stop future access promptly.
- Notify a caregiver about a health-related issue only when the patient has separately agreed to that sharing and the destination is clear.

**Gate to Phase 3:** Patients understand and can control family sharing, and access changes behave as expected in end-to-end tests.

### Phase 3 — Evaluate one wearable connection

Add a wearable only if patients in the pilot ask for it and the clinic can use the additional information. Select one platform first based on the patient group's devices, signal usefulness, integration terms, and the quality of its sync and consent experience. Do not promise real-time monitoring; first measure delays and failures from the device to any user or clinic notification.

- Re-check the provider's current API rules, data scopes, approval steps, and service limits before committing to an integration.
- Ask each patient to connect their own account and grant only the data scopes the feature needs.
- Display the source and time of each sync. Make it clear when syncing is delayed or disconnected.
- Provide a simple way to disconnect. Stop future imports when access is withdrawn and follow the documented data-retention policy.
- Begin with viewing and clinic review. Do not promise live alerts until the full path has been measured and tested.

**Platform considerations to verify during discovery:** Apple HealthKit uses per-data-type user authorization and offers background-delivery mechanisms. Android Health Connect requires additional permission for background reads; its guidance says apps should check periodically because the store does not notify apps each time new data arrives. WHOOP and Oura use per-user authorization and scoped data access; Oura documents an approval step for releasing an application to a wider user group. Review the current [HealthKit authorization documentation](https://developer.apple.com/documentation/HealthKit/authorizing-access-to-health-data?changes=_2), [HealthKit background-delivery documentation](https://developer.apple.com/documentation/healthkit/executing-observer-queries), [Health Connect sync guidance](https://developer.android.com/health-and-fitness/health-connect/sync-data), [WHOOP OAuth guide](https://developer.whoop.com/docs/developing/oauth/), and [Oura API documentation](https://cloud.ouraring.com/docs/authentication) when this phase becomes active.

### Phase 4 — Consider optional wellbeing assistants

Consider specialist assistants only after the patient-and-clinic follow-up workflow is useful and trusted. Test one bounded job that patients actually request before expanding into Tracker, Dash, Fit, Calm, nutrition, sleep, and mindfulness capabilities.

- Explain summaries and approved general information in plain language.
- Offer optional, dismissible reminders that follow the patient's preferences.
- Show which information informed a suggestion and when that information was last updated.
- Keep clinicians responsible for medical assessment and treatment decisions.
- Do not diagnose, change a care plan or medication, or make a safety promise from a consumer wearable score.
- For exercise, nutrition, sleep, or mindfulness suggestions, define the allowed advice, exclusions, escalation route, and evaluation plan before exposing it to patients.

## Product and safety principles

1. **Clinic remains accountable for clinical review.** Khabar can organize information and route it; it should not silently decide a patient's treatment.
2. **Patient permission is specific and revocable.** Connecting a device or inviting one caregiver does not grant household-wide access.
3. **Readings keep their source and context.** Preserve measurement type, units, timestamp, and whether the data is stale, edited, or device-synced.
4. **Separate measurement rules.** Blood pressure, blood glucose, and any later wearable signals need their own clinic-reviewed criteria and appropriate interpretation.
5. **No “live monitoring” claim without evidence.** Document sync delays, missed data, device disconnections, and notification delivery before describing timing to users.
6. **Use the least data necessary.** Ask only for the data needed for the selected feature, explain why, and provide a clear disconnect and consent-management path.
7. **Design for no data.** The product remains useful if a person owns no wearable, declines access, revokes permission, or misses a check-in.
8. **Treat the current website as a concept demo.** Use fictional data in demonstrations. This proposal does not make Khabar a clinical service or claim that its health-data workflows are ready for real-patient use.

## How to judge a pilot

Choose a small set of measures with pilot clinics before setting targets. Useful questions include:

- Do patients complete check-ins and understand the confirmation they receive?
- Can clinic staff identify, review, and resolve flagged readings in a way that fits their normal work?
- How often are alerts useful, duplicated, stale, or missing necessary context?
- Do patients understand caregiver access and feel able to change it?
- Does the experience reduce follow-up effort or improve continuity enough for patients and clinics to keep using it?

Do not claim improved health outcomes from message volume, wearable sync counts, or a short demo alone.

## Decisions to revisit before building

- Which pilot clinic and patient cohort will evaluate the first workflow?
- Which clinic-approved BP and glucose review criteria will the pilot use?
- Who in each clinic owns reviewing the call list, and how will an unreviewed flag be handled?
- Which family details can a patient share, and how are consent changes recorded and enforced?
- What data is retained, for how long, and what happens when a patient disconnects a provider?
- Do pilot patients want a wearable connection enough to justify the separate mobile, provider, and support work?
- If yes, which single integration best matches their actual devices and the clinic's needs?

## Recommended next step

Do not begin by building a set of Bots or connecting every wearable. First document and prototype the shared BP/glucose review workflow for a current Khabar patient, clinic, and consented caregiver. Validate that workflow with prospective users using fictional data. Continue to a supervised pilot only when the clinic review process, permissions, and remaining deployment prerequisites are clear.
