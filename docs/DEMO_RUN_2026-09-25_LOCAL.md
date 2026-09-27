# Local demo rehearsal — 25 September 2026

This is a fresh, local-only rehearsal of the current Khabar branch. It supplements the
historical deployed run in [DEMO_RUN_2026-09-23.md](DEMO_RUN_2026-09-23.md) and does not
verify the public deployment. Fictional demo records and the in-memory H2 database were used.

## Run details

- **Date:** 25 September 2026 (Asia/Singapore)
- **Branch / application code tested:** `pilot-foundations`, application-code commit `1bbdd10`; later commits through `40188c1` changed documentation only. The final intake → visit → reply chain was rerun from a clean H2 seed after restarting the local API.
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
| Messaging outage / retry | PARTIAL / NOT RUN | WhatsApp was unconfigured and the outbox channel was visible. No provider failure, network throttle, browser-level lost-response finalisation retry, or duplicate-message retry was simulated in that original rehearsal. Later local checks cover finalise replay; see `FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`. |

## Defects and follow-up

- No new application defect was confirmed in this rehearsal.
- The demo profile's CORS configuration accepts `localhost:3000`; opening the same app at `127.0.0.1:3000` was rejected. Reopening at the documented `localhost` origin resolved the local setup mismatch.
- The local call list exposed the absence of an on-duty rota. Opt-in queue routing to a rostered backup has since been implemented, but this rehearsal did not enable or exercise it. It sends no staff notification and does not imply a real response owner.
- A fresh current-branch **deployed** rehearsal remains open. Real role sign-in, current public agent routing, provider integrations, full graph-backed context, network/retry behavior, and real screen-reader/participant reviews remain unverified.

## Reproduction

Follow the non-Docker local startup instructions in [`../services/README.md`](../services/README.md).
Use only the `local` profile, its fictional seed data, and demo sign-in buttons. The critical override
in this run was explicitly marked as rehearsal-only. Do not use the synthetic note or this local
record as a clinical example or as evidence of clinical safety.

## Follow-on local outage/retry check — 25 September 2026

This check used the local profile and a fresh in-memory H2 database; it did not touch public services or real records.

- Stopped the local Spring API while the patient recovery-update form held a fictional, non-urgent draft. Sending showed the recoverable “Khabar cannot reach” message and left the draft text in the field.
- Restarted Spring with the documented `local` profile. Before the successful retry, the local outbox contained zero messages and the doctor call list had its three seeded items.
- Retried once. The patient page cleared the draft and showed the fixed acknowledgement, including that the clinic may not have read the message. The local outbox then contained one `NOTICE`, and the doctor call list contained exactly one new recent `WATCH` reply.

This verifies a full API-unavailable-before-send failure followed by one successful retry. It did not verify throttled/slow connections; a separate browser-emulated throttle check is recorded below. Recovery after a finalise response was lost following server commit was checked later on 25 Sep as recorded below. Same-session finalise replay and reload recovery after both responses were dropped passed later on 25 Sep (see `FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`); the API repeat-request behavior is covered by `EncounterFlowTest`.

- **Recovery-update lost response after commit (25 Sep):** The local manual browser check is recorded in [`RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md). The API committed the fictional update, a local proxy dropped its response, and retry after reload displayed the stored acknowledgement. The web form cleared its text on reload; the patient re-entered the same text. Automated API coverage confirms the retry does not duplicate reply, triage, or notice. Slow-network, provider-outage, and deployed checks remain open. Finalisation lost-response/reload recovery was checked separately (see [`FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](FINALISE_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md)).

\n
- **Finalise retry safeguard (25 Sep):** the endpoint now locks the encounter and returns its already-final state on a repeat request, without repeating follow-up or summary side effects. The H2 API regression test and local browser lost-response/reload rehearsal pass. PostgreSQL concurrency verification remains open.

## Slow-response browser check — 25 September 2026

- **Environment:** Chrome with the local Next.js app at `localhost:3000`, Spring API at `localhost:8080`, the local `local` profile, demo patient Aminah, and fictional data only.
- **Method:** wrapped the browser's `fetch` for this tab to delay the next `POST /api/followup/replies` by five seconds, then submitted one non-urgent fictional recovery update.
- **Result:** while the request was pending, the send button was disabled and the entered text remained visible. The delayed request returned HTTP 200 once; afterward the form cleared and the fixed patient-facing acknowledgement appeared. The browser-side counter and network log both showed exactly one POST; no retry or duplicate submission occurred.
- **Limits:** this injected response delay checks pending UI behavior only. It does not reproduce packet loss, bandwidth throttling, a disconnect, provider outage, or deployed behavior. The login session showed one expected 404 for a seeded patient with no take-home summary; that request is outside this recovery-update result.

## Recovery-update network-failure retry — 25 September 2026

- **Environment:** local browser, Spring API at `localhost:8080`, local fictional demo patient.
- **Method:** in the browser tab, the first `POST /api/followup/replies` was made to fail with a fetch network error before it reached Spring. The same non-urgent fictional draft was then submitted again through the UI.
- **Result:** after the first failure, the UI displayed the connection error and retained the draft. The browser recorded the same opaque `clientMessageId` for both attempts. The retry reached the API once, returned HTTP 200, cleared the draft, and displayed the fixed acknowledgement. The browser network log contains one POST, matching the one request that reached Spring.
- **Limits:** the injected browser failure does not cut the machine's network connection or test a response lost after server commit; the latter is documented separately in [`RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md`](RECOVERY_RETRY_BROWSER_CHECK_2026-09-25_LOCAL.md). A separate browser-emulated bandwidth throttle is recorded below; device-wide/mobile behavior, provider outage, and deployed behavior remain open.
- **Cleanup:** afterward, `POST /dev/demo/reset` returned HTTP 200 and restored the local fictional follow-up seed (four replies).

## Browser offline and throttled-network recovery — 25 September 2026

- **Environment:** isolated current-source Next.js at `localhost:3002`, Spring `local` profile at `localhost:8081` with in-memory H2, and FastAPI at `localhost:8001`; the browser used only the fictional patient Aminah. No shared local service, hosted endpoint, provider, or real record was used.
- **Offline attempt:** used Chromium network emulation to take the browser context offline, then submitted one non-urgent fictional recovery update. `navigator.onLine` became false, the POST failed with `net::ERR_INTERNET_DISCONNECTED`, the page showed “Khabar cannot reach,” and the draft text remained in the field.
- **Throttled retry:** restored the browser connection and applied Chromium CDP network emulation at 1,200 ms latency, 64 KiB/s download, and 8 KiB/s upload. At 350 ms the send button was still disabled and the text remained visible. The request then returned HTTP 200, the field cleared, and the fixed acknowledgement appeared, including that the clinic may not have read it. The browser network log showed exactly two POSTs: one failed while offline and one successful after reconnect.
- **Limits:** this verifies browser-context offline/reconnect and browser-emulated network throttling for one local recovery update. It does not simulate device-wide connectivity loss, packet loss over a live connection, hardware/mobile network behavior, provider outage, PostgreSQL concurrency, or a deployed service. The fresh H2 database was discarded when the isolated API stopped.
- **Cleanup:** restored normal browser network conditions and stopped the isolated services on ports 3002, 8081, and 8001 after the check.

## Caregiver summary-only permission view — 25 September 2026

- **Environment:** current worktree web app at `localhost:3000` and a separate Spring `local` profile at `localhost:8081`, using an isolated in-memory H2 database. The pre-existing API on `localhost:8080` did not include the current caregiver `patientScopes` response field, so this check used the current source on 8081. The existing API was left running and untouched.
- **Setup:** created and accepted a fictional caregiver invitation with `SUMMARY` scope for a seeded fictional patient. The caregiver's `/api/me` response exposed that patient's scope as `SUMMARY`.
- **Result:** signed in through the caregiver demo flow. The page showed “Summary-only access” and “The patient has shared the care summary only.” No medication or reading details were shown. The test patient had no finalised take-home summary, so the empty-summary state appeared; rendering of a populated summary was not established by this check.
- **Limits:** this verifies the current local UI's summary-only permission state against the current local API. It does not verify a real account, other caregiver scope, fresh-session revocation, deployed behavior, or screen-reader/device accessibility.
- **Cleanup:** the isolated API used only fictional H2 data and was stopped after the check; no production or shared database was modified.

## Current-worktree graph-disabled visit check — 25 September 2026

- **Environment:** current web app at `localhost:3001`, current Spring `local` profile at `localhost:8081`, and current FastAPI agents at `localhost:8001`; all three used isolated processes, and the API used an in-memory H2 database with no `NEO4J_URI`. The existing services on ports 3000, 8080, and 8000 were not used for this check.
- **Method:** signed in with the fictional doctor demo view, opened fictional patient Aminah's record, started a draft visit, entered the app's sample fictional note format, generated the structured draft, and ran the safety check.
- **Result:** the visit and safety check remained usable. The safety card showed a non-blocking `patient graph context` warning: the graph was not configured, graph-derived medicines/allergies/pregnancy/herb facts were not checked, and the doctor should review the current record directly. A separate seeded duplicate-medicine finding remained critical and blocked finalisation, demonstrating that the graph warning did not weaken the existing gate. The visit was not overridden or finalised.
- **Limits:** this verifies the current local web → API → agents path with Neo4j disabled and fictional data. It does not test Neo4j recovery, hosted graph health, real records, clinical appropriateness, or deployment.
- **Cleanup:** signed out and stopped the isolated web/API/agent processes. The H2 database disappeared with the API process; no shared or production data was changed.

## Empty structured-draft feedback check — 25 September 2026

- **Environment:** current web/API/agents on isolated ports `3001`/`8081`/`8001`, local Spring profile, in-memory H2, fictional data only.
- **Method:** opened a new fictional visit, entered an intentionally unstructured note that contains no diagnosis, plan, follow-up, or medicine, and selected “Turn notes into draft.”
- **Result:** the UI said the notes were saved but no structured draft fields were returned; the visit still showed “No draft yet,” with safety-check and finalisation controls disabled. It no longer announced that a structured draft was ready.
- **Limits:** this validates the empty-response feedback path only. It does not validate clinical content or agent output quality.
- **Cleanup:** signed out and stopped the isolated services; no visit was finalised or externally sent.

## Keyboard skip-link spot check — 25 September 2026

- **Environment:** Chrome and the production-built current web source at `localhost:3001`; the doctor view contained fictional demo data. This was a web preview check, not a verified end-to-end check against the current API source.
- **Method and result:** used Tab and Enter only. On `/login`, Tab focused “Skip to sign in” with a visible focus ring; Enter moved focus to the `sign-in` section. In the fictional doctor view, Tab focused “Skip to workspace”; Enter moved focus to the `overview` main landmark, and the next Tab reached “Add patient.” On Aminah's fictional patient-record route, the same keyboard sequence activated “Skip to workspace,” then focused “Back to clinic.” In the production-built patient view, Enter from “Skip to workspace” focused the overview; the next Tab reached “Send a new check-in.” In the development-only caregiver view, the Next.js Dev Tools button was first in tab order; the following Tab focused “Skip to workspace,” and Enter focused the overview.
- **Limits:** the caregiver route showed the fail-closed permissions-unavailable state because the connected local API predates the sharing-scope response field; it showed no shared details and does not count as current-backend permission coverage. Visit, loading states, full tab order, reduced-motion behavior, and screen-reader output remain unverified. These checks are spot checks and do not complete the all-route keyboard review.

## Patient-summary review availability — 25 September 2026

- **Environment:** current production-built web preview at `localhost:3001`, fictional doctor demo view, and its configured local demo backend. The backend's exact source revision was not verified for this read-only check.
- **Result:** opening Aminah's patient record showed “First visit with Khabar” and no finalised visit summary to compare with the selected language. The summary content check therefore remains not run; no clinical or language-quality claim is made.

## Browser keyboard pass — 25 September 2026

- **Environment:** Chrome tab on the production-built local web preview at `localhost:3001`; API port `8080` process reported Spring profile `local`; only fictional doctor/patient demo accounts were used. The API process's exact source revision and datasource override state were not verified. No hosted URLs or real records were used.
- **Method:** sent real Tab key events through the browser extension and recorded the active element, `:focus-visible`, and rendered bounds after each key. Traversed each page until the tab sequence returned to its first focusable target. This is a browser-driven keyboard check, not a human screen-reader review.
- **Results:** `/login` reached 11 focusable targets in order and returned to its first target; Tab focused “Skip to sign in” and Enter moved focus to the `sign-in` section. Doctor `/home` traversed 63 focusable targets, patient `/home` 28, and the doctor patient-record route 12. Every visited target matched `:focus-visible` and had non-zero rendered bounds. The doctor motion control switched the root `data-motion` state to `off`; system-level `prefers-reduced-motion` was false in this browser. Earlier spot checks confirmed the doctor, patient, and record skip links focus their named targets.
- **Limits:** caregiver and visit routes, loading/error routes, other role/route combinations, OS-level reduced-motion behavior, slow-network/device-offline behavior, screen-reader output, and language pronunciation remain unverified. The patient record had no finalised summary available for the content comparison. This browser pass does not complete the all-role/all-route keyboard checklist or a representative accessibility review.
- **Data handling:** only page navigation, sign-in/out through fictional local demo buttons, Tab/Enter, and the local motion toggle were used; no clinical workflow form was submitted and no provider message was sent.

## Current-source caregiver and visit keyboard check — 25 September 2026

- **Environment:** the current web source was copied to a temporary local folder and run at `localhost:3002`; the current Spring source ran on `localhost:8081` with the `local` profile and isolated in-memory H2; current agents ran on `localhost:8001`. Startup probes returned web HTTP 200 and API `UP`. The shared services on 3000/8080 were not used for these role checks. No public URL, credential, real patient, or provider was used.
- **Caregiver:** signed in through the development-only fictional caregiver button. The current API returned active sharing scope; the view showed that Aminah had shared the summary, medicines, and home readings. It displayed her three shared medicines/remedies, “No care summary yet,” and “No readings shared”; no other patient content was shown. Tab reached all five app controls, each matched `:focus-visible`, and Enter on “Skip to workspace” focused the overview. The Next.js development toolbar added three controls to the browser sequence and is not part of the product UI.
- **Visit:** from the doctor's local record, “Start today’s visit” created one draft encounter in the isolated H2 database. Tab reached all 13 app controls, each matched `:focus-visible`; Enter on “Skip to workspace” focused the visit overview. The notes textarea's value was empty (the visible example was only its placeholder), and no notes, safety check, finalisation, or message submission occurred.
- **Cleanup:** stopped the isolated API, agent, and Next.js processes. Ports 8081, 8001, and 3002 no longer had listeners, so the in-memory encounter and demo data were discarded. The temporary copied web source folder is still present at `../../.tmp-khabar-accessibility-web`; the shell policy rejected the cleanup delete command, so remove that temporary folder manually when convenient. Its `node_modules` entry is a junction to the repository's web dependencies; it was not followed or removed.
- **Limits:** this is a local browser keyboard check only. It is not a screen-reader review, representative accessibility study, OS-level reduced-motion test, provider check, clinical review, or deployed permission test. The patient had no finalized summary and no home readings, so neither content could be reviewed.

\n
