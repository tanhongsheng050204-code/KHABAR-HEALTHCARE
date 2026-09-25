# Khabar Frontend, UX and Backend Implementation Plan

**Status:** Implementation in progress under the user's direct authorization; not a pilot-readiness claim  
**Prepared:** 24 September 2026  
**Product stage:** Practice build / fictional data only  
**Target:** A safe, measurable, clinician-led Malaysian clinic pilot, followed by country-by-country expansion

> This plan does not declare Khabar clinically safe, legally compliant, or ready for real patient data. It translates the agreed product direction into a staged build and validation plan. The current project documentation explicitly describes a practice build and says it is not for clinical use. Keep that boundary visible until the appropriate clinical, privacy, regulatory, operational, and security gates are met.

## 1. Product direction and scope

Khabar's first production ambition should be clinic-led continuity after a visit: understandable patient instructions, consented caregiver visibility, structured check-ins, and a clinic-owned queue for follow-up. The system should help a care team notice and act on follow-up needs; it should not diagnose, prescribe, or independently change treatment in the first pilot.

The long-term vision may include clinician decision support and patient-facing clinical features, but each capability must be added as a separately defined, validated, and reviewed phase. Do not silently expand the intended use through UI copy or marketing claims.

### Pilot scope to operationalize before clinical use

Use the following as the planning default, not a clinical decision:

- Begin with a small number of Malaysian clinics and an agreed post-visit follow-up workflow.
- Define the eligible patient population and exclusions with participating clinicians. A bounded routine-care group is the recommended first evaluation cohort.
- If the pilot is intended to include all patients and conditions seen by a clinic from day one, create a separate validation and safety plan for each materially different population, language, data source, acuity level, and follow-up pathway. “All patients” is not itself a testable cohort definition.
- Clinics must configure a named flag owner or rota, backup coverage, operating hours, and escalation route before onboarding.
- During the first clinical phase, AI-generated output is draft/supporting material only. A clinician or authorized clinic worker remains responsible for care decisions and patient-specific advice.
- Keep fake data in development, demos, and public preview environments. Do not load real patient data until legal, regulatory, clinical, security, and operational gates are approved.

### Decisions confirmed by the product owner

- Start in Malaysia, with a production-ready clinic pilot as the target; expand country by country only after separate local reviews.
- Treat diagnosis and treatment as long-term possibilities. Begin with clinician-led follow-up, then add separately validated support in phases.
- Assess both a bounded routine-care pathway and the added burden/risk of including all patients and conditions from day one. This is a scope decision to evaluate, not evidence that the broadest cohort is safe to launch.
- Each clinic must name the staff/rota responsible for reviewing and closing flags, plus backup coverage and escalation rules, before onboarding.
- Permission intent: doctors make clinical decisions; nurses manage follow-up/call-list tasks; clinic administrators manage staff/settings/audit but do not see patient data unless separately granted a doctor capability. A clinic-scoped grant model now implements the core doctor/nurse/admin boundaries locally; audit-history/settings screens and live-role verification remain open.

### Local implementation status

The first engineering foundations are implemented locally: queue contact is bounded by a server-generated snapshot; the queue tracks a reading's receive time separately from its clinical measurement time; controller errors carry a safe request reference; a guarded `pilot` profile excludes local demo behavior, runs Flyway migrations, and validates the resulting schema; and CI now runs the three service suites plus a PostgreSQL 16 schema smoke test. Clinic-scoped doctor/nurse/admin grants, invitation and revocation endpoints, a nurse follow-up/roster view, and a clinic staff-management view are also implemented with local access tests. H2 PostgreSQL-mode migration/backfill tests and pilot-profile validation pass locally. Hosted CI, including PostgreSQL 16 migration/schema validation, passed for branch head `5dc92ed` on 25 Sep 2026 ([run 36103667074](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36103667074)). These changes do **not** make this project pilot-ready.

Also implemented (24 Sep, night): a follow-up case lifecycle (owner, acknowledgement deadline from the clinic's own settings, call attempts, escalation, structured closure, append-only history; cases stay listed until closed), clinic settings with a weekly rota and backup shown as today's cover on the call list, an administrator activity log that names cases by reference only, and an integration-health view. Hosted CI, including the PostgreSQL job, is current through the 25 Sep branch head run above; public deployment of the branch is still open.

Still not implemented or verified: review/baseline of any existing database and backup/restore/forward-recovery rehearsal; staff notification and automatic escalation when a case goes overdue; clinician review of closure reasons and default acknowledgement times; current-branch public deployment verification; real sign-in and provider end-to-end tests; accessibility and usability evidence with representative people; and clinical/privacy/regulatory/security approvals. Keep those items open until the relevant implementation or evidence exists.

### Not in the first pilot by default

- Autonomous diagnosis, treatment recommendations, or dose changes.
- Unreviewed medication-interaction or herbal-remedy advice presented as definitive.
- Wearable/device alerts treated as continuous clinical monitoring unless the full operating and safety model is explicitly designed and validated.
- Voice-note summaries, doctor-style learning, or other stretch AI features ahead of reliable core follow-up.
- Public claims of clinical outcomes, certification, ministry endorsement, or global readiness without evidence and authorization.

## 2. Current starting point

This plan builds on the current project rather than replacing its architecture. The repo already contains a Next.js application in `web/`, a Spring Boot API in `services/api/`, a Python/FastAPI agents service in `services/agents/`, and docs for the approved teal-and-lavender redesign. The current UI redesign includes role-specific home experiences, loading/error states, responsive behavior, motion controls, and substantial automated/manual checks. The API and agent services also have local workflows, tests, privacy boundaries, demo data, and integration scaffolding.

The remaining production gap is evidence and operation, not merely adding screens. The project backlog identifies live sign-in and role verification, real caregiver revocation checks, a deployed end-to-end rehearsal, real WhatsApp setup, live device validation, and further AI/provider evaluation as open work. Accessibility checks in the redesign document do not amount to full conformance or a real screen-reader pass. Re-verify all status against the latest code and deployment before using this plan as a release checklist.

Relevant project references:

- [Implementation context and intended product](../plan.md)
- [Remaining work and integration gaps](../UNDONE_WORK.md)
- [UI redesign and verification status](UI_REDESIGN_2026-09-24.md)
- [Frontend architecture specification](superpowers/specs/2026-09-22-khabar-web-redesign.md)
- [Backend setup and API notes](../services/README.md)
- [Database migration and baseline procedure](DATABASE_MIGRATIONS.md)
- [Current frontend notes](FRONTEND.md)

## 3. Experience principles

1. **Calm, clear, and clinically legible.** Keep deep teal as the identity, use lavender as a restrained accent, and reserve high-salience colors for meaningful status—not decoration.
2. **Motion explains care, never urgency.** Keep expressive storytelling on public/product-preview surfaces; use brief, optional feedback in clinical workflows. Respect `prefers-reduced-motion` and provide a visible control where motion is present.
3. **Show the source and state of information.** Every reading, message, AI draft, or alert should expose when it was received, where it came from, and whether it is new, stale, reviewed, or failed.
4. **A human action must be visible.** Make assignment, acknowledgement, escalation, override, and closure attributable to an authenticated person with timestamps.
5. **Accessible and multilingual by design.** Design for small screens, keyboard and assistive technology, older adults, varied literacy, and BM, English, Chinese, and Tamil content. Translate and clinically review patient-facing content; do not rely on literal machine translation for safety-critical instructions.
6. **No false reassurance.** A green status must never imply that a patient is medically safe. Explain what Khabar did and what the clinic still needs to do.
7. **Fail visibly and safely.** If a provider is unavailable, data is stale, a message was not delivered, or an AI task failed, show the failure and the safe next action. Never represent an attempted action as completed.

## 4. Frontend and UI/UX work plan

### 4.1 Public landing page

**Purpose:** Explain the narrow current product honestly and help a clinic understand the workflow.

- Retain the care-story direction: patient check-in → information reaches the clinic → a human reviews and follows up.
- Label animated product previews as illustrative; do not imply the displayed person, reading, alert, or outcome is real.
- State intended users, what the pilot does, what it does not do, and that clinical availability depends on a clinic's configured workflow.
- Replace unsupported claims with measured, sourced evidence only after evidence exists. No certification, endorsement, outcome, or “always monitoring” claims without authorization and proof.
- Keep primary actions simple: product overview, clinic contact/demo request, and sign-in. Avoid making patient medical care appear available from the marketing page.
- Test hero motion pause/resume, reduced-motion mode, focus order, text scaling, and 320 px+ layouts.

### 4.2 Authentication and onboarding

- Complete real Supabase login/OTP/invitation flows in a non-production test environment first, including expired codes, retry, wrong-role, sign-out, session expiry, and email delivery failure.
- Route users only after the server confirms their role and clinic/patient relationships. Never rely on a role selected in the browser as authorization.
- Use distinct, plain-language login and recovery paths for clinic staff, patients, and caregivers.
- Explain who invited a caregiver, what information the caregiver can see, how long access lasts if applicable, and how the patient can revoke it.
- Show loading, rate-limit, invalid/expired code, network, and server errors without clearing user input unnecessarily.
- Do not put a demo credential or demo-token flow in a production build. Public demo must use only fake records and explicit demo mode.

### 4.3 Clinic workspace: doctor and nurse

**Priority queue**

- Sort by the clinic-approved priority rule and clearly show the rule/source behind each priority.
- Each item should show minimum necessary patient identity, follow-up day, last contact/reading time, reason for review, source data, and current owner/status.
- Support explicit states: `New`, `Assigned`, `Acknowledged`, `In progress`, `Escalated`, `Resolved`, and `Unable to contact` (final terminology to be validated with clinic users).
- Provide assignment, acknowledgement, escalation, call outcome, retry, and closure actions. Require structured closure reasons for safety-significant flags.
- Provide filters for assigned-to-me, unassigned, overdue, severity, follow-up stage, and language; keep search and empty states usable on mobile.
- Make stale data, failed synchronization, duplicate flags, and closed items visually distinct. Avoid color-only meaning.

**Patient record and visit workflow**

- Present a concise timeline of check-ins, clinician contacts, readings, encounters, medicines, consent, and access events with source and timestamp.
- Distinguish patient-reported, caregiver-reported, device-reported, clinic-entered, and AI-drafted information.
- Keep medication reconciliation and allergy data reviewable; show uncertainty and provenance rather than silently normalizing uncertain medicine names.
- AI drafts must be visibly labeled as drafts, expose source material, and provide edit/reject controls. A clinician's approval must be a distinct, auditable action.
- For critical safety findings, explain the underlying finding, required action, and permitted override path. Preserve a reason and actor for every override.
- Prevent finalization from appearing successful until the API confirms it and the resulting patient communication has a known status.

**Clinic settings**

- Configure staff membership/roles, operating hours, call-list ownership/rota, escalation contacts, supported languages, approved answer content, and notification behavior.
- Require a documented coverage plan and tested escalation route before enabling follow-up for a clinic.
- Show audit and integration health to authorized administrators without exposing unrelated patient details.

### 4.4 Patient home

- Lead with “what happens next”: next check-in, approved care summary, appointment, or clinic contact route.
- Use short sentences and familiar terms; support language switching without losing a draft.
- Make check-in questions and response status clear. Confirm when a reply was received and whether a person needs to review it; do not imply that an AI response is a clinician's approval.
- Show readings with unit, source, timestamp, and instructions for correcting an entry. Never infer device reliability from a successful upload alone.
- Explain consent and access history in understandable language, with a visible caregiver list and revoke control.
- Provide a clear urgent-help instruction approved by the participating clinic. Do not claim Khabar is an emergency-response service.
- Keep privacy-sensitive information out of lock-screen/browser notifications by default.

### 4.5 Caregiver home

- Show only the patient records and data scopes the patient granted.
- Mark the view as read-only wherever that is the permission model; identify when content is shared and by whom.
- Provide clear patient identity selection if the caregiver supports multiple family members; never infer the active patient silently.
- Make revoked/expired access immediately visible and ensure cached or back-button views do not preserve access after revocation.
- Never allow a caregiver to change treatment, consent on behalf of a competent patient, or invite another caregiver unless a separately approved authority model explicitly permits it.

### 4.6 Shared design system and accessibility

- Consolidate typography, spacing, color tokens, status semantics, buttons, forms, dialogs, tables, cards, banners, and notification patterns.
- Define loading, empty, offline, stale-data, forbidden, expired-session, validation-error, retry, and success patterns once and reuse them.
- Meet WCAG 2.2 AA as the product target, verified through automated checks and manual keyboard, screen-reader, zoom, focus, and contrast testing. An automated axe pass alone is not conformance.
- Test real screen-reader flows (NVDA or VoiceOver) for login, adding a patient, reviewing/closing a flag, patient consent/revocation, and error recovery.
- Check focus visibility, dialog focus trapping/restoration, labels, status announcements, target size, contrast, zoom/reflow, reduced motion, and no color-only cues.
- Conduct moderated usability sessions with clinic staff, patients across the intended age/language groups, and caregivers. Include low-literacy and low-bandwidth scenarios.

## 5. Backend, data, and integration work plan

### 5.1 Preserve service boundaries

- **Next.js (`web/`)**: UI composition, session presentation, and calls to the clinical API. It is not an authorization boundary and must not hold clinical encryption keys or service credentials.
- **Spring Boot (`services/api/`)**: source of truth for clinical records, identity/role checks, clinic/patient/caregiver authorization, consent, audit, follow-up state, and integration event handling.
- **Agents (`services/agents/`)**: bounded AI tasks called through authenticated internal APIs. No direct Postgres access. Receive only necessary de-identified context; return structured outputs with provenance, status, and uncertainty.
- **Postgres/Supabase**: durable identity and clinical workflow storage, subject to data minimization, access controls, backup, retention, and deletion policies.
- **Neo4j**: optional derived/de-identified context store. Define reconciliation, rebuild, deletion propagation, and outage behavior; do not make the graph the only copy of authoritative clinical state.

### 5.2 Identity, authorization, and consent

- Complete and repeatedly test actual Supabase sign-in for clinic staff, patients, and caregivers. Include role collision, disabled account, expired token, token refresh, invitation expiry/reuse, and cross-clinic attempts.
- Enforce authorization in Spring Boot on every record and action. Verify clinic tenancy and object ownership server-side; do not trust frontend route guards or IDs.
- Test caregiver scopes at endpoint and data-field level. Revocation must invalidate subsequent API reads/writes immediately; test stale tokens, open browser tabs, cached UI, and downloaded/exported copies.
- Record consent purpose/scope, patient actor, caregiver identity, timestamp, collection channel, version of notice, and revocation. Define what revocation changes and what records must be retained under applicable rules.
- Add negative authorization tests for patient A → patient B, clinic A → clinic B, wrong role, revoked caregiver, and altered object IDs for every sensitive endpoint family.

### 5.3 Clinic alert lifecycle and human operations

- Model flag lifecycle explicitly, including owner, severity/reason, source event, creation time, acknowledgement time, status history, escalation, closure, and outcome.
- Make assignment and closure idempotent and audited. Prevent a flag from disappearing merely because it was opened or refreshed.
- Add configurable clinic hours, named owner/rota, backup, overdue thresholds, and escalation rules. Do not ship a universal response-time promise; have each clinic define and approve its policy.
- Route urgent content through a clearly defined clinic-approved pathway. If nobody is on duty or an integration is down, surface the limitation rather than pretending continuous coverage exists.
- Establish a process for false positives, missed flags, duplicate alerts, patient correction, complaint handling, and clinical incident review.

### 5.4 Messaging, devices, and reliable event processing

- **WhatsApp:** finish approved templates and production credentials; verify webhook signatures; map sender identity safely; handle retries, duplicates, out-of-order delivery, opt-out, delivery failure, template rejection, and provider outage. Avoid sensitive content in previews. Maintain a clinic-approved alternative contact workflow.
- **Favoriot/readings:** validate real device payloads and authentication, patient/device association, units, timestamps/time zones, missing or malformed values, duplicate readings, and delayed delivery. Display the source and age of every reading. Simulated data must be visibly labeled.
- Store both the time a reading was measured and the time Khabar received it; use receive time for queue snapshot/closure boundaries so a late-arriving reading cannot be cleared as already seen. Backfill legacy rows before pilot schema validation and separately validate provider-supplied measurement timestamps.
- Use an outbox/inbox or equivalent durable event pattern for outbound messages and inbound webhooks. Give each external event a stable idempotency key; persist delivery and processing status; provide safe replay/dead-letter handling and an operator-visible error queue.
- Test provider sandbox and failure scenarios before enabling an integration for any clinic. Keep integration secrets outside source control and separate test from production credentials.

### 5.5 AI service controls

- Define each AI feature's intended purpose, allowed inputs/outputs, unacceptable failure modes, human reviewer, fallback behavior, and version before enabling it.
- Restrict outputs to a typed schema. Validate constraints and content before returning to the UI; malformed or unsupported output must fail closed into a clear human workflow.
- Separate deterministic rules from probabilistic model output. Keep thresholds and clinical rules owned/reviewed by qualified clinical stakeholders; do not let an LLM silently set or change them.
- Keep source references and model/provider/version metadata for AI-generated drafts. Log minimum necessary metadata without copying unnecessary sensitive text into general logs.
- Build evaluation sets approved by clinical reviewers for languages, accents/Manglish, age/health-literacy groups, conditions, medicine names, negation, urgent phrases, transcription noise, and adversarial/ambiguous input.
- Measure false negatives and false positives separately. Define abstention and fallback thresholds with a clinical safety owner. Monitor drift and provider/model changes; require re-evaluation before a change reaches pilot users.
- Keep unreviewed AI-generated advice disabled. For patient replies without a clinician-approved answer, return only an approved safe response such as clinic follow-up instructions.

### 5.6 Security, privacy, and operations

- Produce a data-flow and threat model covering browser, Next.js host, API, database, graph, agent service, LLM/transcription/OCR vendors, messaging provider, device provider, logs, backups, and support access.
- Complete qualified Malaysian privacy and regulatory review before real data. Determine controller/processor roles, notices/consent, retention, data-subject request process, vendor terms, breach procedure, DPO applicability, impact assessment needs, and cross-border transfer conditions for actual hosting/provider locations.
- Test encryption at rest and in transit, key storage/rotation, least-privilege service accounts, dependency and container scanning, rate limiting, brute-force protection, CORS/CSRF/session configuration, webhook validation, and secret rotation.
- Ensure logs/analytics never receive credentials, full IC numbers, unnecessary phone numbers, or unredacted health narratives. Define audit-log access and tamper-resistance expectations.
- Separate development, test, staging, demo, and production data and secrets. Prevent demo token routes, fake patient generators, adjustable clocks, and reset endpoints from being enabled in production.
- Implement backups and prove restoration; document RPO/RTO targets with operators. Add API/provider/queue/AI health dashboards, alert ownership, on-call/contact process, deployment rollback, and incident runbooks.

### 5.7 Data model and API contracts

Before expanding APIs, review contracts for:

- Clinic, staff role/membership, patient identity, caregiver link/scope, consent, audit event, encounter, medication source, check-in, reply, reading, alert, ownership/status history, and communication delivery.
- Stable identifiers, timezone conventions, language tags, clinical units, source/provenance, timestamps, data freshness, null/unknown values, soft deletion/retention, and idempotency.
- A consistent API error envelope with machine-readable code, safe human message, correlation ID, and retryability.
- Pagination/filtering for clinic queues; optimistic concurrency/versioning for assignment and consent changes; rate limits on authentication and patient-facing submission paths.
- Backwards-compatible schema and API migrations, tested in staging with rollback procedures.

## 6. Testing and evidence plan

### Automated engineering tests

- Frontend: lint, typecheck, production build, component tests for critical states, and end-to-end tests across desktop/mobile breakpoints.
- API: unit/integration tests, authorization matrix, tenant isolation, consent/revocation, audit, alert lifecycle, idempotency, webhooks, and migration tests.
- Agents: schema validation, de-identification, prompt injection and unsafe output tests, model/provider failure, multilingual holdout evaluation, and regressions by model/version.
- Security: dependency/container scanning, secret scanning, dynamic/API authorization tests, and a documented penetration test before broader rollout.
- Reliability: database restore drill, provider outage/replay, queue backlog, rate limit, deployment rollback, and load test based on the pilot's expected clinic volume.

### Human and clinical validation

- Observe clinicians completing the complete workflow without coaching; record task success, time, workarounds, alert burden, and failure recovery.
- Test patients' understanding of instructions, privacy notices, consent, check-ins, and what to do when symptoms worsen.
- Test caregiver understanding of scope and read-only limits.
- Clinically review approved answers, thresholds, exclusions, escalation language, translation, and red-flag evaluation sets.
- Have an accountable clinical safety owner review serious incidents, near misses, missed flags, overrides, and changes to rules or AI providers.
- Define pilot metrics and stop conditions in advance. Example measures: successful follow-up completion, time to acknowledgement, unresolved/overdue flags, delivery failures, rate of human overrides, patient comprehension, staff workload, subgroup/language performance, and safety incidents. Do not advertise these as outcomes until measured and appropriately reviewed.

## 7. Phases, gates, and deliverables

### Phase 0 — Product and governance definition

**Deliverables:** approved intended-use statement; pilot clinic and cohort definition; excluded cases; clinic flag-ownership/coverage plan; clinical safety owner; data-flow/vendor inventory; privacy and regulatory advice; measurable pilot protocol.

**Gate:** no production patient data or clinical claims until accountable stakeholders approve the scope and controls.

### Phase 1 — Close current integration and access gaps

**Deliverables:** real test-account flows for every role; caregiver scope/revocation verification; end-to-end deployed role rehearsal; real WhatsApp sandbox flow and provider-failure behavior; device payload validation or explicit deferment; production-vs-demo environment separation.

**Gate:** complete cross-role authorization tests and prove all integrations either work safely or remain visibly disabled.

### Phase 2 — Clinic workflow and UI hardening

**Deliverables:** alert assignment/acknowledgement/escalation/closure model; clinic setup for rota/hours; refreshed doctor, patient, caregiver, and admin journeys; stale-data and provider-failure UX; WCAG 2.2 AA target audit; screen-reader and moderated usability results.

**Gate:** representative clinic staff, patient, and caregiver users can complete critical workflows and recover from common failures.

### Phase 3 — Security, AI, reliability, and operational readiness

**Deliverables:** security/privacy reviews; restore and rollback drills; logs/monitoring/runbooks; AI task evaluations; multilingual content review; validated escalation/fallback behavior; incident and complaint process.

**Gate:** documented risk acceptance by named technical and clinical owners; no unresolved critical security or safety issues.

### Phase 4 — Controlled Malaysian pilot

**Deliverables:** clinic onboarding and training; consent/notice materials; support and incident channel; bounded pilot cohort; weekly safety/workload review; end-of-pilot analysis.

**Gate:** expand only when pre-agreed safety, usability, reliability, and operational criteria are met and reviewed.

### Phase 5 — Broaden population and capabilities

**Deliverables:** separate evidence for additional conditions, patient groups, languages, devices, and workflows; updated risk and regulatory assessment for each material change.

**Gate:** no broad “all conditions” or continuous-monitoring claims based on evidence from a narrower cohort.

### Phase 6 — Country-by-country expansion

For each target country, create a local readiness pack: product classification and intended-use review, privacy/data-transfer and hosting review, clinical workflow/guideline review, languages and cultural adaptation, local integration/providers, support/escalation operations, security assessment, and local user validation. Reuse engineering components where appropriate, but do not assume legal or clinical rules transfer across borders.

## 8. Release acceptance checklist

Do not call a build pilot-ready until all applicable items are evidenced and signed off:

- [ ] Intended use, cohort, exclusions, and prohibited claims are documented.
- [ ] MDA/product classification and other required Malaysian reviews have been completed by qualified parties.
- [ ] Privacy roles, notices, consent, vendors, data locations/transfers, retention, and incident process are approved.
- [ ] Real test identities for doctor/nurse, patient, caregiver, and administrator work as intended.
- [ ] Cross-tenant and cross-patient access tests pass; caregiver revocation is immediate and verified.
- [ ] Clinic alert owners, backup coverage, operating hours, and escalation rules are configured and tested.
- [ ] Messaging and device integrations pass success, failure, retry, duplicate, delay, and outage scenarios—or are visibly disabled.
- [ ] UI exposes source, freshness, ownership, status, and safe next action for every critical data/alert state.
- [ ] AI features have approved tasks, human controls, evaluation evidence, fallback/abstention, and version monitoring—or are disabled.
- [ ] Accessibility testing includes automation and real keyboard/screen-reader/user checks.
- [ ] Backup restore, deployment rollback, monitoring, incident response, and support ownership are demonstrated.
- [ ] Pilot metrics, review cadence, stop criteria, and clinical incident ownership are written before enrollment.
- [ ] No real data or clinical effectiveness claims appear in the public demo unless separately approved.

## 9. Decisions to record before clinical operation

The product owner has already confirmed the country sequence (Malaysia first), phased clinical ambition (clinician-led follow-up first), assessment of both bounded and all-patient cohorts, clinic-owned alert coverage, and intended doctor/nurse/administrator permission boundaries (see Section 1). Record the following with each participating clinic and qualified reviewers before clinical operation:

1. Which clinics and routine-care workflows make up the first pilot, and who approves eligible patients/exclusions?
2. Are device readings in the first pilot, or explicitly deferred until end-to-end validation is complete?
3. What are each clinic's operating hours, alert owners, backup, and escalation path?
4. Which patient-facing content is clinician-approved, and who owns translation and clinical review?
5. Which AI tasks, if any, are enabled in the pilot, and what are their stop/abstention thresholds?
6. Which jurisdictions and vendors process or store each data category?
7. Who owns clinical safety, privacy, security, product operations, and incident decisions?
8. What permission grants may a clinic administrator hold alongside a clinical role, and who approves, audits, and revokes each grant?

Record decisions in [DECISIONS.md](DECISIONS.md), and keep implementation status in [UNDONE_WORK.md](../UNDONE_WORK.md) so this document remains the plan rather than a duplicate live checklist.

## 10. Reference standards and official guidance

These references inform the work; they do not by themselves establish compliance or readiness. Re-check current requirements with qualified professionals before pilot or market entry.

- Malaysia Medical Device Authority: [Definition of a medical device under Act 737](https://www.mda.gov.my/index.php/component/content/article/1768-appendix-1-definition-of-a-medical-device-as-outlined-under-section-2-of-act-737?Itemid=277&catid=170%3Aprofessional) and [product classification process](https://portal.mda.gov.my/index.php/industry/classification/product-classification).
- Malaysia Personal Data Protection Commissioner: [official PDPA guidance index](https://www.pdp.gov.my/ppdpv1/en/akta/personal-data-protection-guidelines-on-data-breach-notification-dbn/) and [cross-border personal data transfer guidance](https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2025/08/GP_CBPDT_EN.pdf).
- World Health Organization: [Ethics and governance of AI for health: guidance on large multi-modal models](https://www.who.int/publications/i/item/9789240084759).
- W3C: [Web Content Accessibility Guidelines (WCAG) 2.2](https://www.w3.org/TR/wcag/).
