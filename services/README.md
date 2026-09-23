# Khabar backend

Two services. Spring Boot owns every piece of patient identity; the Python agents service only ever sees a random `graphId` and text with names, IC and phone numbers removed.

| Service | Folder | Port | Job |
|---|---|---|---|
| Clinical API | `api/` (Spring Boot 3, Java 21) | 8080 | Sign-in checks, access rules, encryption, audit log, visits, follow-up, messaging, talks to the agents |
| Agents | `agents/` (FastAPI + LangGraph, Python) | 8000 | Intake chat and pre-visit report, report drafting, safety checks, patient summary, reply triage, approved-answer matching, speech to text |
| Patient graph (optional) | Neo4j / AuraDB | 7687 | De-identified facts per patient under a random `graphId`: conditions, allergies, medicines (brand and generic), herbs, visits, warning symptoms, readings. Spring Boot writes it after every change; the agents only read it |

## Live

| What | Where |
|---|---|
| The app | https://khabar-landing-six.vercel.app |
| **Every endpoint, browsable** | **https://khabar-api.vercel.app/docs** |
| Is the API awake? | https://khabar-api.vercel.app/api/health |

Both services run in one Vercel project (`khabar-api`) in Singapore: the API as a container, the agents
as a Python service, with a Supabase Postgres database. Everything there is fictional, and anyone can
sign in as a demo person. The backend sleeps after five idle minutes, so the first request takes about
15 seconds.

On the docs page, press **Authorize** and paste a token from `POST /dev/token?as=doctor` to try the
endpoints as the demo doctor. From the command line:

```bash
TOKEN=$(curl -s -X POST "https://khabar-api.vercel.app/dev/token?as=doctor" | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s https://khabar-api.vercel.app/api/clinic/call-list -H "Authorization: Bearer $TOKEN"
```

### Deploying it again

```bash
cd services && npx vercel deploy --prod      # the backend (it is not connected to Git)
git push                                      # the app, through .github/workflows/deploy-vercel.yml
```

The backend reads these from the Vercel project: `SPRING_PROFILES_ACTIVE=local,demo`, the Supabase
database (connected from the Marketplace with the `DB_` prefix), `INTERNAL_SERVICE_KEY`,
`SUPABASE_JWT_SECRET`, `FIELD_ENCRYPTION_KEY`, `AGENTS_SERVICE_URL`, `WEB_ALLOWED_ORIGINS` and
`WEB_APP_URL`. `services/api/src/main/resources/application-demo.yml` explains what the demo profile
changes; `services/vercel.json` and `services/api/Dockerfile.vercel` are how Vercel builds it.

## Run it on your machine (no accounts needed)

Shortcut on Windows: `powershell -ExecutionPolicy Bypass -File scripts/run-local.ps1` starts both services and the screens (see `docs/FRONTEND.md`).

Needs Java 21 and Python 3.11+. On this PC a portable JDK lives in `%USERPROFILE%\.jdks\jdk-21.0.12.1+1`.

```bash
# 1. Agents (terminal 1)
cd services/agents
.venv/Scripts/python.exe -m uvicorn main:app --port 8000     # Windows venv path

# 2. API with the zero-setup `local` profile (terminal 2)
cd services/api
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"                # PowerShell: $env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile uses an in-memory database and seeds fake demo people:

- Dr Priya's clinic with 30 made-up patients (Malay, Chinese, Indian and East Malaysian names, the four languages, medicines from more than one clinic, some herbs and allergies). Four are in follow-up, with three replies waiting for a call (one red, one watch, one for a person).
- Aminah (the demo patient) has a finished intake and a medication list: metformin from a klinik kesihatan, the same drug as "Brand A" from a GP, and bitter gourd juice. Prescribing her metformin shows the duplicate and herb checks.
- Nurul is Aminah's caregiver.
- Three approved answers (missed a dose, medicine running out, before or after food) in all four languages.
- Every demo phone number is 03-0000 xxxx, which no real line has.

It also adds `POST /dev/token?as=doctor|patient|caregiver` so you can sign in without Supabase:

```bash
TOKEN=$(curl -s -X POST "localhost:8080/dev/token?as=patient" | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/intake/chat -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"messages":[]}'
```

Without `GEMINI_API_KEY` the intake agent runs a scripted four-question interview and triage uses the word lists only, so the whole flow works offline.

### Demo helpers (local profile only)

| Method | Path | What |
|---|---|---|
| POST | `/dev/token?as=doctor\|patient\|caregiver` | A sign-in token for a demo person |
| GET | `/dev/clock` | What "today" is for the app |
| POST | `/dev/clock/advance?days=N` | Fast-forward the app's clock, e.g. to day 7 of follow-up |
| POST | `/dev/clock/reset` | Back to the real date |
| POST | `/dev/check-ins/run` | Send every check-in that is due now |
| GET | `/dev/outbox` | Messages that would have gone out on WhatsApp |
| POST | `/dev/demo/reset` | Puts the follow-up demo back as seeded: clock to today, the four story patients back on their follow-up day with their four replies, readings cleared |
| POST | `/dev/graph/sync` | Writes every patient to the patient graph (after emptying it, or if it was down) |
| GET | `/dev/graph/patients/{patientId}` | That patient's graph context, fetched through the agents service exactly as the agents read it |

### Try the patient graph locally (no Docker)

The graph is off unless `NEO4J_URI` is set. To try it, start a throwaway Neo4j from the API's test tools, then both services pointed at it:

```bash
cd services/api && ./mvnw -q test-compile exec:java -Dexec.mainClass=com.khabar.api.graph.LocalNeo4j -Dexec.classpathScope=test   # terminal 1
cd services/agents && NEO4J_URI=bolt://localhost:7687 NEO4J_PASSWORD=local .venv/Scripts/python.exe -m uvicorn main:app --port 8000   # terminal 2
cd services/api && NEO4J_URI=bolt://localhost:7687 NEO4J_PASSWORD=local ./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # terminal 3
```

Or `scripts/run-local.ps1 -WithGraph`. Then `curl -s localhost:8080/dev/graph/patients/<Aminah's patientId>` shows her diabetes, hypertension,
metformin twice (once as "Brand A"), bitter gourd and "pening", read back by graph ID alone. If the API started before Neo4j was up,
`POST /dev/graph/sync` fills it. For AuraDB, set `NEO4J_URI=neo4j+s://<id>.databases.neo4j.io` and its username and password in both services.

### Try the WhatsApp webhook locally

The local profile sets the verify token to `local-verify-token` and the app secret to `local-app-secret`. Meta signs each delivery with the app secret; you can do the same:

```bash
BODY='{"entry":[{"changes":[{"value":{"messages":[{"from":"60300000001","type":"text","text":{"body":"pening sikit"}}]}}]}]}'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac local-app-secret | sed 's/^.* //')"
curl -s -X POST localhost:8080/api/webhooks/whatsapp -H "X-Hub-Signature-256: $SIG" -H "Content-Type: application/json" -d "$BODY"
```

`60300000001` is Aminah's demo number (03-0000 0001; every demo number uses 03-0000, which no real line has), so the reply lands on the doctor's call list.

### Try a home device reading locally

Link a device to a patient as the doctor, then send a reading the way Favoriot's HTTP forwarding would (the local device secret is `local-device-secret`):

```bash
curl -s -X PUT localhost:8080/api/patients/$PATIENT_ID/device -H "Authorization: Bearer $DOCTOR" -H "Content-Type: application/json" -d '{"deviceId":"glucometer-aminah@demo"}'
curl -s -X POST localhost:8080/api/webhooks/favoriot -H "X-Khabar-Device-Secret: local-device-secret" -H "Content-Type: application/json" \
  -d '{"device_developer_id":"glucometer-aminah@demo","data":{"glucose":"2.8"}}'
```

A blood sugar of 2.8 mmol/L puts Aminah at the top of the call list.

## Tests

```bash
cd services/agents && .venv/Scripts/python.exe -m pytest -q      # 183 tests
cd services/api && ./mvnw clean test                              # 182 tests, some against a real in-process Neo4j
```

## Endpoints

**API (needs a Supabase sign-in token unless noted).** The same list, with request and response shapes,
is browsable at [/docs](https://khabar-api.vercel.app/docs).

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/docs` · `/v3/api-docs` | anyone | The browsable reference, and the OpenAPI description behind it |


Sign-in and onboarding

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/health` | anyone | Liveness |
| GET | `/api/me` | any registered user | Role, clinic, and the patient's own record id |
| POST | `/api/onboarding/clinic` | anyone signed in, with the bootstrap token | Creates the first clinic and makes the caller its doctor. Off unless `KHABAR_BOOTSTRAP_TOKEN` is set |
| POST | `/api/clinic/patients` | doctor | Registers a patient and returns a one-time code (valid 7 days) that links the patient's own sign-in to the record |
| POST | `/api/clinic/doctor-invites` | doctor | A one-time code that makes a colleague a doctor at the same clinic |
| POST | `/api/invites/{code}/accept` | anyone signed in | Uses a code. 404 unknown, 410 used or expired, 409 if the sign-in already has a different role |
| POST | `/api/patients/me/caregiver-invites` | patient | A code for a family member, with the scope the patient consents to |
| GET | `/api/patients/me/caregivers` | patient | Who can see my information |
| DELETE | `/api/patients/me/caregivers/{linkId}` | patient | Withdraws consent; that caregiver loses access at once |
| GET | `/api/clinic/patients` | doctor | The clinic's patients by name, IC masked, with their follow-up day |

Patient record

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/patients/{id}` | doctor at the clinic, the patient, a consented caregiver | Record with IC masked; every non-patient view is written to the audit log |
| GET | `/api/patients/{id}/access-log` | the patient, doctor at the clinic | "Who viewed my record", newest first |
| GET / POST | `/api/patients/{id}/medications` | doctor at the clinic, the patient, a consented caregiver | "What I take": medicines and herbs from other places. The safety check reads this list |
| DELETE | `/api/patients/{id}/medications/{itemId}` | same | Marks an item as stopped (the row is kept) |
| GET / POST | `/api/patients/{id}/readings` | same | Home blood pressure or blood sugar. Each reading is rated; a worrying one goes on the call list |
| PUT | `/api/patients/{id}/device` | doctor at the clinic | Links a home device (its Favoriot developer id) to the patient |

Before the visit

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/clinic/slots` | patient, doctor | The clinic's open 15-minute slots for the next two weeks (weekdays 9-12 and 2-5, Saturday morning) |
| POST | `/api/appointments` | patient | Books a slot with a reason. One upcoming booking at a time; the database refuses double bookings |
| GET | `/api/appointments/mine` · DELETE `/api/appointments/{id}` | patient (or the clinic, to cancel) | The upcoming booking; cancelling frees the slot |
| GET | `/api/clinic/appointments?date=YYYY-MM-DD` | doctor | The day's bookings |
| POST | `/api/intake/chat` | patient | One intake turn. The patient's name, IC and phone are removed before anything reaches the AI, and the chat is saved as it goes |
| POST | `/api/patients/{id}/medications/packet-photo` | patient or doctor at the clinic | Consented JPEG/PNG/WebP packet image (up to 8 MB) sent to configured Gemini for temporary label reading; returns review-only candidates and never saves the image or medicine automatically |
| GET | `/api/patients/{id}/intake` | doctor at the clinic, the patient | The latest finished intake: the pre-visit report and the transcript. Medicines and herbs from it join the medication list; allergies from it join the safety check |
| GET | `/api/patients/{id}/previsit` | doctor at the clinic | One page before the consultation: latest intake, last visit, the medicine list checked for duplicates, clashes, herbs and allergies, and the last five replies. Logged |

The visit

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/patients/{id}/encounters` | doctor at the clinic | Starts a visit |
| POST | `/api/encounters/{id}/audio` | doctor | A voice recording (multipart field `audio`, up to 25 MB) turned into text for the doctor to check. 503 when speech to text is not set up |
| GET | `/api/encounters/{id}` | doctor at the clinic | The visit, its draft report and findings |
| PUT | `/api/encounters/{id}/notes` | doctor | The doctor's notes (shorthand is fine: `T. Metformin 500mg 1/1 BD PC`, `TCA 2/52`) become a structured draft. 503 if the agents service is down |
| POST | `/api/encounters/{id}/check` | doctor | Runs the safety checks against the patient's allergies, pregnancy, medication list and intake |
| POST | `/api/encounters/{id}/findings/{findingId}/override` | doctor | Overrides one finding; needs a written reason of 10+ characters and goes in the audit log |
| POST | `/api/encounters/{id}/finalise` | doctor | 409 while a critical finding is open or the check is out of date. Otherwise starts the 30-day follow-up and sends the patient their summary |
| GET | `/api/patients/{id}/summary` | the patient, a consented caregiver, doctor at the clinic | The latest plain-language summary, in the patient's language |

After the visit

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/followup/replies` | patient | A follow-up reply from the app. Triaged (identity removed first) and stored encrypted; if triage is down it still goes to a person. The patient hears back only in approved words: a red flag gets fixed advice to call 999, a question with an approved answer gets the doctor's answer, anything else is acknowledged |
| GET / POST | `/api/clinic/answers` · DELETE `/api/clinic/answers/{id}` | doctor | The clinic's approved answers: a title, trigger phrases, and the answer in ms / en / zh / ta. Retiring keeps the record |
| POST | `/api/webhooks/favoriot` | a home device via Favoriot (no sign-in; checked by the `X-Khabar-Device-Secret` header) | A reading from a linked device. Unknown devices are acknowledged and ignored |
| GET / POST | `/api/webhooks/whatsapp` | Meta (no sign-in; checked by verify token and signature) | The webhook handshake, and replies arriving on WhatsApp. Matched to a patient by a keyed hash of the phone number |
| GET | `/api/clinic/call-list` | doctor | "Call these patients today", most urgent first. Within a level: replies, then home readings, then missed doses, then patients with no reply for 48 hours |
| POST | `/api/clinic/call-list/{patientId}/called` | doctor at the clinic | Marks the patient's replies and readings as handled and writes it to their access log |

Errors the screens should show come back as `{"status": 409, "message": "..."}`, with a sentence written for people.

**Agents (`X-Internal-Service-Key` header required, except `/health`)**

| Method | Path | What |
|---|---|---|
| POST | `/agents/intake/chat` | One intake turn (Gemini, or scripted without a key) |
| POST | `/agents/intake/report` | The pre-visit report from a finished chat: answers by topic, medicines (with the generic behind a brand), herbs, remedies to ask about, allergies, warning symptoms. Rules only, no AI |
| POST | `/agents/transcribe?language=ms` | Speech to text with Groq Whisper, hinted with clinic shorthand and drug names. The recording is the raw request body. 503 without `GROQ_API_KEY` |
| POST | `/agents/packet/read` | Internal-only, consent-confirmed inline image read via Gemini. Returns visible label candidates for human review; does not persist the image or add medication records |
| POST | `/agents/report/draft` | Doctor's notes to a structured draft: diagnosis, plan, follow-up, warning signs, parsed prescription |
| POST | `/agents/evaluator/check` | The data-based safety checks: allergy, interaction, duplicate, herb, dose, pregnancy, grounding (a drug or a symptom the notes never mention), completeness, unrecognised drug. `blocking: true` when anything is CRITICAL |
| POST | `/agents/evaluator/normalise` | For the graph writer: the generic (and brand) behind each medicine name, and the herb in each remedy name, from the same drug data |
| GET | `/agents/graph/{graph_id}/context` | What the patient graph holds for one patient. 503 with no graph configured, 404 for an unknown graph ID |
| POST | `/agents/evaluator/reconcile` | The patient's own medicine list checked on its own: the same drug from two places, clashes, herbs, allergies |
| POST | `/agents/summary/build` | The patient's summary in BM, English, Chinese or Tamil, with Ramadan timings when fasting |
| POST | `/agents/followup/triage` | A reply as red / watch / ok / review, plus whether it reports a missed dose. The word lists always run; with Gemini configured the model reads it too, and the more urgent level wins |
| POST | `/agents/followup/answer` | Which approved answer a reply is asking for: by trigger phrase, or with Gemini by choosing from the same list. Returns an id, never text |

## Configuration (production-style profile)

The API has **no working defaults for secrets** and refuses to start without them:

| Variable | Notes |
|---|---|
| `FIELD_ENCRYPTION_KEY` | 64 hex chars: `openssl rand -hex 32`. The example placeholder is rejected. |
| `SUPABASE_JWKS_URL` | `https://<project>.supabase.co/auth/v1/.well-known/jwks.json` (new projects, ES256/RS256) |
| `SUPABASE_JWT_SECRET` | Only for legacy HS256 projects, instead of the JWKS URL |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Supabase Postgres (`?sslmode=require`) |
| `INTERNAL_SERVICE_KEY` | Same value in both services |
| `AGENTS_SERVICE_URL`, `WEB_ALLOWED_ORIGINS` | Where the agents run; which web origins may call the API |
| `KHABAR_BOOTSTRAP_TOKEN` | Lets the first doctor create a clinic. Clear it once the clinic exists |
| `KHABAR_TIME_ZONE` | Default `Asia/Kuala_Lumpur`; decides when check-ins are due |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_TOKEN` | Leave empty to keep outgoing messages in the outbox table |
| `WHATSAPP_CHECKIN_TEMPLATE` | Your approved check-in template (WhatsApp requires a template to start a conversation) |
| `WHATSAPP_SUMMARY_TEMPLATE` | Your approved summary template with one body parameter `{{1}}`. Long summaries are split between lines into several messages |
| `FAVORIOT_DEVICE_SECRET` | The header value your Favoriot forwarding rule sends. Leave empty to switch device readings off |
| `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET` | For the webhook handshake and signature check |
| `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` | The patient graph (AuraDB: `neo4j+s://...`). Leave `NEO4J_URI` empty to switch it off. Set the same values in the agents service |

Agents: `INTERNAL_SERVICE_KEY`, `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` (read-only use of the patient graph; empty means none), `GEMINI_API_KEY`, `GEMINI_MODEL` (default `gemini-3.6-flash`), `GROQ_API_KEY` (speech to text; recordings can contain names, so use it only with fake patients until you have an agreement with a transcription provider).

## Data files are seed data

`agents/data/ddinter_interactions.json` is a reproducible subset generated by `agents/scripts/import_ddinter.py` from the eight official DDInter 2.0 CSV downloads. It contains pairs between the generics in `agents/data/drugs.json`; no matching row is not evidence of safety. DDInter is licensed CC BY-NC-SA 4.0. The remaining drug classes, dose limits, herb list and red-flag words are small demo seeds; a doctor or pharmacist must review them before any real use. "Brand A" and "Brand B" are made-up brand names.

`agents/tests/data/planted_errors.json` is the planted-error set: ten drafts with one known mistake each. The evaluator must catch all ten (it does, in `test_planted_errors.py`); the same file can score drafts written by candidate LLMs.
