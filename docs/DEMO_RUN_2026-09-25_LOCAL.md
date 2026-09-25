# Local demo rehearsal — 25 September 2026

This is a fresh, local-only rehearsal of the current Khabar branch. It supplements the
historical deployed run in [DEMO_RUN_2026-09-23.md](DEMO_RUN_2026-09-23.md) and does not
verify the public deployment. Fictional demo records and the in-memory H2 database were used.

## Run details

- **Date:** 25 September 2026 (Asia/Singapore)
- **Branch / application code tested:** `pilot-foundations`, application-code commit `1bbdd10`; later commits through `6141adf` changed documentation only. The final intake → visit → reply chain was rerun from a clean H2 seed after restarting the local API.
- **Environment:** Next.js on `localhost:3000`, Spring `local` profile on `localhost:8080`, FastAPI agents on `localhost:8000`.
- **Data / integrations:** fresh seeded fictional records; H2 in-memory database; no Neo4j URI, Gemini key, Supabase sign-in, or WhatsApp provider configured. Messaging used the local outbox.
- **Startup checks:** agent `/health` returned `healthy`, API `/api/health` returned `UP`, and the web home returned HTTP 200.

## Outcomes

| Area | Outcome | Evidence / limit |
|---|---|---|
| Demo identities | PASS locally | Doctor, patient, and caregiver demo tokens were exercised; the web UI was opened as doctor and patient. This does not test real Supabase accounts. |
| Patient setup | PASS locally | Completed all four scripted BM intake questions with fictional sample answers. The API reported a completed intake; the doctor pre-visit report was non-empty and included the submitted reason/condition answers and three medicine-list items. The existing appointment was `BOOKED`. |
| End-to-end sequence | PASS locally | In the same clean H2 run: guided intake → doctor pre-visit view → visit draft → safety gate → final report → patient-facing summary → urgent patient reply → doctor call list. |
| Visit draft | PASS locally | A clearly labelled fictional rehearsal note was parsed into a structured draft; no real clinical diagnosis or plan was entered. |
| Safety gate | PASS locally | The duplicate medicine finding was critical and blocked finalisation. The herb interaction appeared as a warning. A demo-only override reason was recorded in the local audit log to exercise the control; it is not a clinical decision. |
| Final report | PASS locally | Confirmation finalised the report, showed a Bahasa Melayu summary, and started follow-up. One summary entry appeared in the local `outbox` channel. No external message was sent. |
| Appointment and patient view | PASS locally | The patient demo view showed the summary and the existing booking. `/api/appointments/mine` returned `BOOKED` for the patient demo token. |
| Follow-up response | PASS locally | A reassuring fictional reply received the fixed acknowledgement. A separate fictional urgent reply received the precautionary 999 wording and the notice that the clinic may not have seen it. |
| Clinic queue | PASS locally | After the urgent reply, the doctor view showed it at the top of the urgent queue. The screen also stated that nobody was rostered and no one was assigned to call. No staff notification is implemented or implied. |
| Caregiver access | PASS locally | In a separate local H2 session, the seeded caregiver could fetch the linked fictional patient's record (200); a different patient's record was denied (403). After patient consent was revoked, a fresh caregiver request was denied (403). These are local demo-profile checks only. |
| Agent outage fallback | PASS locally | In a separate local H2 session, FastAPI was stopped while Spring remained available. A new fictional, unclassified reply was stored as `REVIEW`; the response said the clinic might not have seen it and included precautionary 999 / emergency-department guidance. |
| Graph availability | PARTIAL PASS | Neo4j was not configured. The patient record and visit workflow remained usable without graph context; no graph-backed condition lookup was tested. |
| Messaging outage / retry | PARTIAL / NOT RUN | WhatsApp was unconfigured and the outbox channel was visible. No provider failure, network throttle, duplicate-finalisation retry, or duplicate-message retry was simulated. |

## Defects and follow-up

- No new application defect was confirmed in this rehearsal.
- The demo profile's CORS configuration accepts `localhost:3000`; opening the same app at `127.0.0.1:3000` was rejected. Reopening at the documented `localhost` origin resolved the local setup mismatch.
- The local call list exposed the absence of an on-duty rota. This is tracked separately with automatic escalation and notification work; this run did not configure or imply a real response owner.
- A fresh current-branch **deployed** rehearsal remains open. Real role sign-in, current public agent routing, provider integrations, full graph-backed context, network/retry behavior, and real screen-reader/participant reviews remain unverified.

## Reproduction

Follow the non-Docker local startup instructions in [`../services/README.md`](../services/README.md).
Use only the `local` profile, its fictional seed data, and demo sign-in buttons. The critical override
in this run was explicitly marked as rehearsal-only. Do not use the synthetic note or this local
record as a clinical example or as evidence of clinical safety.
