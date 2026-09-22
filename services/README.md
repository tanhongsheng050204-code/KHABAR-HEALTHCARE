# Khabar backend

Two services. Spring Boot owns every piece of patient identity; the Python agents service only ever sees a random `graphId` and text with names, IC and phone numbers removed.

| Service | Folder | Port | Job |
|---|---|---|---|
| Clinical API | `api/` (Spring Boot 3, Java 21) | 8080 | Sign-in checks, access rules, encryption, audit log, visits, follow-up, messaging, talks to the agents |
| Agents | `agents/` (FastAPI + LangGraph, Python) | 8000 | Intake chat and pre-visit report, report drafting, safety checks, patient summary, reply triage |

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

- Dr Priya's clinic, with four patients in follow-up and three replies waiting for a call (one red, one watch, one for a person).
- Aminah (the demo patient) has a finished intake and a medication list: metformin from a klinik kesihatan, the same drug as "Brand A" from a GP, and bitter gourd juice. Prescribing her metformin shows the duplicate and herb checks.
- Nurul is Aminah's caregiver.

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

### Try the WhatsApp webhook locally

The local profile sets the verify token to `local-verify-token` and the app secret to `local-app-secret`. Meta signs each delivery with the app secret; you can do the same:

```bash
BODY='{"entry":[{"changes":[{"value":{"messages":[{"from":"60300000001","type":"text","text":{"body":"pening sikit"}}]}}]}]}'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac local-app-secret | sed 's/^.* //')"
curl -s -X POST localhost:8080/api/webhooks/whatsapp -H "X-Hub-Signature-256: $SIG" -H "Content-Type: application/json" -d "$BODY"
```

`60300000001` is Aminah's demo number (03-0000 0001; every demo number uses 03-0000, which no real line has), so the reply lands on the doctor's call list.

## Tests

```bash
cd services/agents && .venv/Scripts/python.exe -m pytest -q      # 103 tests
cd services/api && ./mvnw test                                    # 98 tests
```

## Endpoints

**API (needs a Supabase sign-in token unless noted)**

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

Patient record

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/patients/{id}` | doctor at the clinic, the patient, a consented caregiver | Record with IC masked; every non-patient view is written to the audit log |
| GET | `/api/patients/{id}/access-log` | the patient, doctor at the clinic | "Who viewed my record", newest first |
| GET / POST | `/api/patients/{id}/medications` | doctor at the clinic, the patient, a consented caregiver | "What I take": medicines and herbs from other places. The safety check reads this list |
| DELETE | `/api/patients/{id}/medications/{itemId}` | same | Marks an item as stopped (the row is kept) |

Before the visit

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/intake/chat` | patient | One intake turn. The patient's name, IC and phone are removed before anything reaches the AI, and the chat is saved as it goes |
| GET | `/api/patients/{id}/intake` | doctor at the clinic, the patient | The latest finished intake: the pre-visit report and the transcript. Medicines and herbs from it join the medication list; allergies from it join the safety check |

The visit

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/patients/{id}/encounters` | doctor at the clinic | Starts a visit |
| GET | `/api/encounters/{id}` | doctor at the clinic | The visit, its draft report and findings |
| PUT | `/api/encounters/{id}/notes` | doctor | The doctor's notes (shorthand is fine: `T. Metformin 500mg 1/1 BD PC`, `TCA 2/52`) become a structured draft. 503 if the agents service is down |
| POST | `/api/encounters/{id}/check` | doctor | Runs the safety checks against the patient's allergies, pregnancy, medication list and intake |
| POST | `/api/encounters/{id}/findings/{findingId}/override` | doctor | Overrides one finding; needs a written reason of 10+ characters and goes in the audit log |
| POST | `/api/encounters/{id}/finalise` | doctor | 409 while a critical finding is open or the check is out of date. Otherwise starts the 30-day follow-up and sends the patient their summary |
| GET | `/api/patients/{id}/summary` | the patient, a consented caregiver, doctor at the clinic | The latest plain-language summary, in the patient's language |

After the visit

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/followup/replies` | patient | A follow-up reply from the app. Triaged (identity removed first) and stored encrypted; if triage is down it still goes to a person |
| GET / POST | `/api/webhooks/whatsapp` | Meta (no sign-in; checked by verify token and signature) | The webhook handshake, and replies arriving on WhatsApp. Matched to a patient by a keyed hash of the phone number |
| GET | `/api/clinic/call-list` | doctor | "Call these patients today": unhandled replies and check-ins with no reply after 48 hours, most urgent first |
| POST | `/api/clinic/call-list/{patientId}/called` | doctor at the clinic | Marks the patient's replies as handled and writes it to their access log |

**Agents (`X-Internal-Service-Key` header required, except `/health`)**

| Method | Path | What |
|---|---|---|
| POST | `/agents/intake/chat` | One intake turn (Gemini, or scripted without a key) |
| POST | `/agents/intake/report` | The pre-visit report from a finished chat: answers by topic, medicines (with the generic behind a brand), herbs, remedies to ask about, allergies, warning symptoms. Rules only, no AI |
| POST | `/agents/report/draft` | Doctor's notes to a structured draft: diagnosis, plan, follow-up, warning signs, parsed prescription |
| POST | `/agents/evaluator/check` | The data-based safety checks: allergy, interaction, duplicate, herb, dose, pregnancy, grounding (a drug the notes never mention), completeness, unrecognised drug. `blocking: true` when anything is CRITICAL |
| POST | `/agents/summary/build` | The patient's summary in BM, English, Chinese or Tamil, with Ramadan timings when fasting |
| POST | `/agents/followup/triage` | A reply as red / watch / ok / review. The word lists always run; with Gemini configured the model reads it too, and the more urgent level wins |

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
| `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET` | For the webhook handshake and signature check |

Agents: `INTERNAL_SERVICE_KEY`, `GEMINI_API_KEY`, `GEMINI_MODEL` (default `gemini-3.6-flash`), `GROQ_API_KEY`.

## Data files are seed data

`agents/data/drugs.json` and `agents/data/triage_words.json` are small hand-made lists for development. "Brand A" and "Brand B" are made-up brand names. The interaction list must be replaced by the DDInter 2.0 subset, and a doctor or pharmacist must review the dose limits, herb list and red-flag words before any real use.
