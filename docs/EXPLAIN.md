# Khabar — Technical Explanation & Architecture Notes

> **Daily Log:** What the code merged today does and why. (Defense for SDC Handbook §8.5.7 / §8.5.3).

---

## 22 Sep 2026: Multi-Service Scaffold & Thin Slice Wiring

### 1. Architecture Overview

We established the dual-backend architecture required to isolate clinical health records and PII from generative AI agents:

1. **`services/api` (Spring Boot 3, Java 21)**:
   - **Role**: System of record for all clinical data.
   - **Why Java/Spring Boot**: Strict type safety, robust JPA data mapping, mature RBAC, and centralized encryption. Only Spring Boot holds the AES-256 field encryption key and talks to Supabase PostgreSQL.
   - **Communication**: Interacts with the Python AI agent service over HTTP using Spring 6 `RestClient` with a shared secret header (`X-Internal-Service-Key`).

2. **`services/agents` (FastAPI, Python 3.14)**:
   - **Role**: AI orchestration engine hosting 4 LangGraph agents (Intake, Report, Evaluator, Follow-up).
   - **Why Python/FastAPI**: Native ecosystem for LangGraph, LangChain Google GenAI (Gemini), and audio/vision models.
   - **Security Guarantee**: Agents NEVER touch PostgreSQL directly and NEVER receive patient names, IC numbers, or phone numbers. All state uses de-identified `graph_id` strings and queries Neo4j for clinical Graph-RAG context.

3. **`infra/` (Docker & Environment Config)**:
   - `docker-compose.yml`: Enables running the multi-service system locally.
   - `.env.example`: Centralizes secrets for Supabase, Neo4j AuraDB, Gemini, and internal service tokens.

### 2. Endpoints Implemented in the Scaffold

- `GET /health` (`services/agents`): Public liveness and service metadata.
- `POST /agents/intake/chat` (`services/agents`): Authenticated intake chat node executed via LangGraph. Rejects calls without valid `X-Internal-Service-Key`.
- `GET /api/health` (`services/api`): Clinical API health status and target agent service verification.
- `POST /api/intake/chat` (`services/api`): Clinical proxy endpoint bridging client intake sessions to the AI agent service.

---

## 22 Sep 2026 (later): The Whole Visit Loop

Everything from "before the visit" to "30 days after" now runs end to end on a laptop with no accounts. Eleven commits; what each part does and why it is built that way:

### 1. One clock for the whole app

`AdjustableClock` is the only source of "now". Check-ins, invite expiry and the call list all ask it. In the `local` profile, `POST /dev/clock/advance?days=7` moves it forward, so the demo can jump to day 7 of follow-up without waiting a week.

### 2. The visit (`encounters/`)

- **Notes to draft.** The doctor types shorthand (`T. Metformin 500mg 1/1 BD PC`, `TCA 2/52`). The agents' report agent parses it with rules, not the LLM, so a dose is never guessed.
- **Safety check.** The evaluator gets the draft plus everything known about the patient: allergies (from the record and from intake), pregnancy, the "what I take" list and herbs. The **grounding** check flags any drug in the report that the doctor's notes never mention: the most dangerous kind of AI slip.
- **Three-layer block.** A critical finding stops finalising in the domain object (`Encounter.finalise` throws), in the API (409) and in the database (a `CHECK` constraint: status cannot be `FINAL` while critical findings are open). Even a bug in the Java code cannot write a blocked report. The screen is the planned third place in the UI.
- **Overrides** need a written reason of 10+ characters and go in the audit log.

### 3. The summary

Built from a template per language (BM, English, Chinese, Tamil), never free text from the LLM, so the dose in the summary is exactly the dose prescribed. When fasting, morning maps to sahur and night to berbuka; anything that doesn't fit (three times a day) says "ask your doctor" instead of guessing.

### 4. Follow-up and WhatsApp (`followup/`, `messaging/`)

- Finalising plans check-ins for days 1, 3, 7, 14 and 30. A scheduler sends the ones that are due.
- **Sending:** WhatsApp Cloud API when configured (check-ins use an approved template, as WhatsApp requires for messages the clinic starts); otherwise messages go to an outbox table you can read at `/dev/outbox`.
- **Receiving:** the webhook checks Meta's `X-Hub-Signature-256` (an HMAC of the body with the app secret), so nobody can post fake replies.
- **Finding the patient by phone:** phone numbers are encrypted, so the database can't search them. Each patient also stores a **blind index**: an HMAC of the normalised number. The webhook computes the same HMAC and looks that up. The number itself is never stored in plain text.
- **Triage:** word lists in four languages always run. When Gemini is configured it reads the reply too, and **the more urgent level wins**: the model can raise a reply but never lower one. If the model is down, the word lists still decide.
- **Call list:** unhandled replies, plus patients who haven't replied to a check-in for 48 hours.

### 5. Onboarding (`onboarding/`)

Supabase proves who someone is; Khabar decides what they are. A doctor registers a patient and gets a 10-character code (no 0/O or 1/I). The patient signs in and types it, which links their sign-in to their record. Patients invite caregivers the same way (that is the consent) and can revoke them. Codes are single-use, expire after 7 days, and only a SHA-256 hash is stored, so a database leak doesn't leak working codes. The very first clinic is created with a bootstrap token from the environment, compared in constant time.

### 6. Before the visit: intake, pre-visit report, "what I take"

- Intake chats are saved as they go, with the patient's identifiers already removed, and encrypted.
- When the chat completes, the agents build the **pre-visit report** with rules and the drug data: each answer by topic, medicines (with the generic behind a brand name), herbs, remedies to ask about ("jamu" with no name), allergies and warning symptoms. No LLM, so every line traces to the patient's own words.
- Medicines and herbs from the intake join the **medication list**, which the patient, a caregiver or the clinic can also edit. The safety check reads that list, so metformin from a klinik kesihatan plus the same drug under another brand from a GP is caught as one duplicate naming both places.

### 7. How it is tested

Test first, every time: 103 tests in the agents service and 98 in the API. LLM calls are replaced by small fakes in tests, so the suite runs offline and checks what we send (for example, that IC numbers never reach the model). One API test uses a real tiny HTTP server, because the bug it guards against (Java's HTTP client asking uvicorn to upgrade to HTTP/2 and losing the body) only shows up on the wire.

---

## 23 Sep 2026: Live, and browsable

Khabar now runs on the internet, not only on a laptop.

### 1. One project for the app, one for the backend
The Next.js app deploys to Vercel on every push to `main` (`.github/workflows/deploy-vercel.yml`). The
backend is a second Vercel project in Singapore holding both services at once (`services/vercel.json`):
the Spring Boot API as a **container** built from `services/api/Dockerfile.vercel`, and the FastAPI
agents as a Python service. `/agents/*` goes to the agents; everything else to the API, which calls the
agents over the same domain with the internal service key. Both sleep when idle, so the first request
after a quiet spell takes about 15 seconds.

### 2. A demo that is honest about being a demo
The deployed API runs `SPRING_PROFILES_ACTIVE=local,demo`: the fictional clinic and the "Doctor view /
Patient view" sign-in of the `local` profile, but with a real Postgres database (Supabase, Singapore)
and its own secrets from the environment. `application-demo.yml` overrides the development values in
the repository, and a test proves a token signed with the repository's development secret is refused
online, and that only the deployed site may call the API.

### 3. The database, without pasting passwords
The container's start script turns whatever database the platform provides into a JDBC URL: a Supabase
session-pooler URI, Neon's `PG*` variables, or a plain `SPRING_DATASOURCE_URL`. The Supabase database is
connected to the project from Vercel's Marketplace, so its password lives only in Vercel and never in
the repository or in a chat.

### 4. Every endpoint, readable and callable
`/docs` serves a browsable reference generated from the code itself, so it cannot drift from what the
API does: press Authorize, paste a token from `/dev/token`, and try any endpoint as the demo doctor.
Opening the API's own address in a browser now redirects to the app instead of showing a bare 401.

---

## 23 Sep 2026 (night): The patient graph is real

The architecture always said "Spring Boot writes a Neo4j graph with no names, and the agents read it".
Until tonight nothing opened a Neo4j connection. Now it does, end to end.

### 1. What goes in, and what never does
Each patient is one `Patient` node with a random `graph_id` and a pregnancy flag. Nothing else about who
they are. Around it: `HAS_CONDITION` (diabetes, hypertension... picked out of the intake answer by a word
list in four languages), `ALLERGIC_TO`, `TAKES {name, source}` to a `Medication` keyed by its generic, with
`BRAND_OF` from a brand, `USES` to a `Herb`, `HAD` visits with what was `PRESCRIBED`, `REPORTED` warning
words, and `RECORDED` readings. Every piece of text goes through the same `Redactor` used before the AI,
and a last check refuses to write anything that still contains the patient's name, IC or phone.

### 2. When it is written
Postgres stays the record. Whenever something changes what the graph would show (a medicine added or
stopped, an intake finished, a visit finalised, a reply, a reading, a new patient), the code calls
`graphSync.changed(patientId)`. After that transaction commits, the patient's facts are rebuilt and the
patient's part of the graph is replaced in one Neo4j transaction. If Neo4j is down, the change still
stands and only a warning is logged; the next change, or `POST /dev/graph/sync`, catches up.

### 3. Who reads it
The agents open Neo4j in read-only sessions, with queries kept in `agents/data/graph_queries.json`.
Intake uses it to confirm what the patient already takes; the safety check adds anything it knows that
the request did not send (it can only add, so it can raise a finding but never hide one); triage tells
the model the patient's conditions and medicines, so "berpeluh" from a diabetic on gliclazide reads as
possible low sugar.

### 4. How it is tested without Docker
The API tests start a real Neo4j inside the test (`neo4j-harness`), write to it, and read back with the
agents' own query file, so the writer and the reader cannot drift apart. One test adds medicines whose
names and sources contain the patient's name, IC and phone, then scans every property in the graph.

---

## 23 Sep 2026: Interaction data and sign-in paths

### 1. Drug interaction data

The evaluator now reads a reproducible, demo-sized subset from the official DDInter 2.0 CSV files instead of
the hand-picked interaction pairs previously embedded in `drugs.json`. `services/agents/scripts/import_ddinter.py`
keeps the import reproducible and filters the official data to the generic medicines used by the demo. The
resulting data retains the source/license notice and reports its coverage. DDInter's `Major` findings are
critical; other levels, including `Unknown`, are warnings. Missing pairs are not treated as evidence of safety.
The local herb rules carry source links and evidence caveats, but still need pharmacist review.

### 2. Sign-in UI

The login panel now presents clinic staff password sign-in and patient/caregiver email OTP as distinct paths.
Both paths obtain a Supabase access token, accept an invitation before requesting the user's Khabar profile,
and store the token only after profile loading succeeds. This is an implementation change, not proof that
Supabase Auth is configured or that the deployed roles have passed an end-to-end access test.

### 3. Packet-photo reading (partial B4)

The patient medicine panel can submit a JPEG, PNG, or WebP image after the user checks an explicit Gemini
processing notice. Spring Boot checks that the user is the patient or a doctor at that clinic, confirms consent,
checks the actual image signature and 8 MB limit, then forwards the bytes to the internal agent service. The
agent sends the image inline to Gemini and keeps no file; the model is instructed to extract only visible text,
not infer a dose or recommend treatment. Brand/ingredient text is matched against the local known-generic data.
The UI shows evidence and confidence, asks the user to choose medicine vs herb/remedy, and only adds after an
explicit click. This does not prove OCR accuracy or authorize real-patient image processing; use fictional packets
until the clinic has approved its provider and privacy arrangements.

### 4. Verification performed

The full agent suite passes (183 tests, run from the project virtual environment) and the full API suite passed
(181 tests, including the Neo4j-backed tests and packet upload access/consent checks); the added raw-byte packet
transport test passes in its focused service suite, bringing the passing API test count to 182. The web TypeScript check
and lint pass. One Starlette/AnyIO deprecation warning remains. Real Gemini OCR quality, deployed sign-in, live
provider loops, and full demo rehearsal remain unverified; use
`docs/DEMO_RUN_CHECKLIST.md` to record those results rather than assuming local checks prove production readiness.

The first 3-, 5-, and 7-minute pitch drafts are in `docs/PITCH_SCRIPTS.md`. They are explicitly framed as a
prototype and avoid clinical-outcome claims; the presenter still needs to tailor and time them against the
actual demo setup.
