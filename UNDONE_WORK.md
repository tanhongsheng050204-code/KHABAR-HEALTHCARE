# Khabar — Remaining Work

**Reviewed:** 23 September 2026  
**Source of truth:** [`plan.md`](plan.md), checked against the current codebase and project documentation.  
**Scope:** This is an implementation and readiness backlog. It does not replace the SDC planning decisions in `plan.md`.

## Current position

The local core product is largely implemented and tested:

- API: **161 automated tests passing**.
- Next.js web app: linting and TypeScript checks pass.
- The main clinical workflow, access rules, encryption, de-identification, follow-up logic, and demo data are implemented locally.

The remaining work is primarily real-provider integration, missing stretch features, evidence-gathering, and demo/public-readiness work.

---

## 1. Highest priority — make the claimed architecture real

### 1.1 Connect the Neo4j patient graph

**Status:** Not implemented end to end.

Neo4j exists in `infra/docker-compose.yml`, the Python service has Neo4j settings and the dependency is installed, but no API or agent code currently opens a Neo4j driver, writes patient facts, or queries the graph.

**Required work**

- [ ] Add a graph client with credentials supplied only by environment variables.
- [ ] Write de-identified patient facts from Spring Boot using only `graph_id`; never write names, IC numbers, or phone numbers.
- [ ] Implement the planned nodes and relationships needed for the demo: patient, conditions, medicines, brands, allergies, herbs, encounters, symptoms, and readings.
- [ ] Give the agents read-only graph-query tools for intake, report, evaluator, and follow-up context.
- [ ] Add tests proving that personally identifiable information cannot enter Neo4j.
- [ ] Run the deployed flow against Neo4j/AuraDB, not just local Docker.

**Done when:** the demo patient’s medication, allergy, herb, and condition context is written to and read from Neo4j using only the random graph ID.

### 1.2 Complete the real Supabase sign-in journey

**Status:** Backend JWT validation and role onboarding are implemented; the visible sign-in experience still relies on local/demo-token paths for the documented demo.

**Required work**

- [ ] Configure Supabase Auth for doctor email/password and patient/caregiver email OTP.
- [ ] Configure the web app with the public Supabase URL and publishable key.
- [ ] Validate issuer, audience, signing keys, expiry, and role mapping in the deployed API.
- [ ] Test doctor, patient, and caregiver sign-in on the deployed web app.
- [ ] Test revoked caregiver consent immediately blocks access in the deployed environment.

**Done when:** all three roles can sign in without a developer token and can access only the records allowed by their role.

### 1.3 Verify the deployed end-to-end product

**Status:** Services are deployable and individual screens exist, but a full deployed acceptance run is not documented.

**Required work**

- [ ] Confirm deployed web, API, agent service, Supabase, and Neo4j environment variables are configured correctly.
- [ ] Perform an end-to-end rehearsal using fake data: booking → intake → pre-visit → draft → safety review → finalise → summary → follow-up reply → call list.
- [ ] Repeat the rehearsal for doctor, patient, and caregiver permissions.
- [ ] Record defects and fix only issues that affect the core demo.
- [ ] Keep a concise demo-run checklist and result in `docs/EXPLAIN.md` or a dedicated test record.

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

---

## 3. Missing or intentionally deferred product features

### 3.1 Medicine-packet photo reading (B4)

**Status:** Not implemented. The patient can maintain a typed medicine/remedy list, but no photo upload, OCR, image understanding, or reconciliation-from-image flow exists.

**Required work**

- [ ] Add a privacy-safe image upload flow and retention policy for fake data.
- [ ] Add packet-label extraction / vision processing behind the agent service.
- [ ] Match brand names to generics and ask for confirmation when confidence is low.
- [ ] Add extracted items to the existing medication reconciliation workflow.
- [ ] Test duplicate and herb-clash findings originating from the photo-derived list.

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

**Status:** The safety checker works with seed data. Replacing it with the planned limited DDInter subset remains unfinished.

**Required work**

- [ ] Build a small, reproducible subset covering the fake patients’ medicines only.
- [ ] Preserve DDInter attribution and the CC BY-NC 4.0 notice in the README.
- [ ] Add source references for the Malaysian herb-interaction list.
- [ ] Re-run the planted-error tests against the refreshed data.

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

- [ ] Bug bash the core workflow.
- [ ] Prepare stable fake demo data, including Mak Cik Aminah’s full story.
- [ ] Move services to reliable / always-on hosting before a live demo.
- [ ] Prepare 3-, 5-, and 7-minute pitch versions.
- [ ] Record and review a demo video.
- [ ] Rehearse the demo with poor-network and provider-failure fallbacks.

### 4.3 Documentation habit

**Status:** `docs/EXPLAIN.md` exists, but daily coverage has not been verified.

- [ ] Add a brief daily entry explaining major code changes in your own words.
- [ ] Record architecture decisions, integration credentials setup steps without secrets, and test results.
- [ ] Keep a short list of code areas you can personally explain for the SDC review.

---

## 5. SDC decisions and administration

These are not implementation tasks, but they are still open in `plan.md`.

- [ ] Get organisers’ answers on SDGs, declaration changes, pitch length, and judging weights.
- [ ] Decide available working hours for the SDC week alongside classes.
- [ ] Create a **separate repository** containing only the declared generic skeleton.
- [ ] Remove all Khabar-specific prompts, data, screens, names, and features from that skeleton repository.
- [ ] Write and submit the declaration before registration.

---

## Recommended next sequence

1. Implement and test **Neo4j graph writes and reads**.
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

