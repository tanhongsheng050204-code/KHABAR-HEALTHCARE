# Khabar intended-use and claims boundary — draft

**Status:** Internal draft prepared 25 September 2026. Not approved by the project owner, a clinician, a privacy reviewer, or a Malaysian regulatory adviser. It does not authorize clinical use, a pilot, or use of real patient data.

## Current prototype use

Khabar is currently a fictional-data practice and demonstration build. Its purpose is to let the project team and reviewers explore a proposed clinic follow-up workflow using seeded fictional records. It is not intended to be used to provide care, make or change a diagnosis or treatment decision, or communicate clinical instructions to a real patient.

The demonstration may show intake and pre-visit context, deterministic structuring of clinician-style example notes, rule-based medication checks, a templated summary, prototype follow-up reply routing, a clinic review queue, and simulated or optional provider integrations. These demonstrations do not establish clinical safety, provider reliability, or real-world effectiveness. The queue does not notify staff automatically, and a patient message or reading is not guaranteed to be seen or acted on.

## People and data

- **Current audience:** the project team and demonstration reviewers using the fictional demo environment.
- **Current cohort:** no real patients or care cohort. Seeded people and interactions are fictional.
- **Current exclusions:** real patient data, active care, clinical decision-making, emergency response, and use as a substitute for a clinic's procedures or qualified judgment.
- **Future pilot cohort:** not defined. Any pilot would require a separate approved protocol specifying the clinic workflow, eligibility, exclusions, languages, data sources, responsibilities, and stop criteria before recruitment or use.

## Claims not supported by current evidence

Do not claim that Khabar:

- reliably detects emergencies or replaces triage, clinical escalation, or emergency services;
- diagnoses, selects treatment, or determines that a medication or dose is safe for a specific patient;
- reduces adverse events, improves clinical outcomes, or is clinically effective;
- continuously monitors patients, guarantees that staff see an alert, or guarantees delivery, reading, or response to a message;
- is accurate or validated for every condition, language, patient group, device, provider, or clinic workflow.

The current word-list fallback scored **2/12** on the independent held-out red-reply set. That measurement is limited and is not a validation of emergency detection. See [`evals/triage-wordlist-current.md`](evals/triage-wordlist-current.md).

## Decisions and reviews still required

- [ ] Project owner confirms this accurately describes the actual demo and intended submission.
- [ ] Participating clinicians define and approve any proposed care workflow, eligible cohort, exclusions, patient-facing wording, and operational response responsibilities.
- [ ] Qualified Malaysian regulatory advice determines applicable product classification and required reviews before any clinical operation.
- [ ] Privacy and security reviewers approve data flows, notices, consent, vendors, retention, and incident handling before any real data is considered.
- [ ] Any pilot protocol defines measurable outcomes, monitoring, stop criteria, and accountable clinical safety ownership before recruitment.

Until those decisions are recorded, keep the public demo fictional-data-only and do not present it as ready for clinical or pilot use. This is a project safety boundary, not a legal or regulatory determination.
