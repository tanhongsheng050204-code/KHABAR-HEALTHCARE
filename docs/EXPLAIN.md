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
