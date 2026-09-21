# Khabar backend

Two services. Spring Boot owns every piece of patient identity; the Python agents service only ever sees a random `graphId`.

| Service | Folder | Port | Job |
|---|---|---|---|
| Clinical API | `api/` (Spring Boot 3, Java 21) | 8080 | Sign-in checks, access rules, encryption, audit log, talks to the agents |
| Agents | `agents/` (FastAPI + LangGraph, Python) | 8000 | Intake chat, safety checks on draft reports, follow-up reply triage |

## Run it on your machine (no accounts needed)

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

The `local` profile uses an in-memory database, seeds fake demo people, and adds `POST /dev/token?as=doctor|patient|caregiver` so you can sign in without Supabase:

```bash
TOKEN=$(curl -s -X POST "localhost:8080/dev/token?as=patient" | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/intake/chat -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"messages":[]}'
```

Without `GEMINI_API_KEY` the intake agent runs a scripted four-question interview, so the whole flow works offline.

## Tests

```bash
cd services/agents && .venv/Scripts/python.exe -m pytest -q      # 54 tests
cd services/api && ./mvnw test                                    # 32 tests
```

## Endpoints

**API (`/api`, needs a Supabase sign-in token unless noted)**

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/health` | anyone | Liveness |
| GET | `/api/me` | any registered user | Role, clinic, and the patient's own record id |
| GET | `/api/patients/{id}` | doctor at the clinic, the patient, a consented caregiver | Record with IC masked; every non-patient view is written to the audit log |
| GET | `/api/patients/{id}/access-log` | the patient, doctor at the clinic | "Who viewed my record", newest first |
| POST | `/api/intake/chat` | patient | Pre-visit intake; the patient's name, IC and phone are removed before anything reaches the AI |

**Agents (`X-Internal-Service-Key` header required, except `/health`)**

| Method | Path | What |
|---|---|---|
| POST | `/agents/intake/chat` | One intake turn (Gemini, or scripted without a key) |
| POST | `/agents/evaluator/check` | The data-based safety checks: allergy, interaction, duplicate, herb, dose, pregnancy, completeness, unrecognised drug. `blocking: true` when anything is CRITICAL |
| POST | `/agents/followup/triage` | Classifies a patient's reply as red / watch / ok / review |

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

Agents: `INTERNAL_SERVICE_KEY`, `GEMINI_API_KEY`, `GEMINI_MODEL` (default `gemini-3.6-flash`), `GROQ_API_KEY`.

## Data files are seed data

`agents/data/drugs.json` and `agents/data/triage_words.json` are small hand-made lists for development. The interaction list must be replaced by the DDInter 2.0 subset, and a doctor or pharmacist must review the dose limits and red-flag words before any real use.
