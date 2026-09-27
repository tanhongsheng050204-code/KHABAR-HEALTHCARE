# Finalise lost-response retry browser check — 25 September 2026

This local manual browser run used the seeded fictional patient Aminah and an isolated local API process on port 8082 with a fresh in-memory database. A temporary web copy on port 3001 used a one-shot local proxy on port 8083. The existing local app and services were not stopped or modified.

## Scenario

- Opened Aminah's record using the local demo doctor session, created a visit, entered a clearly fictional noncritical follow-up note, and ran the safety check. The test note contained no prescription; one nonblocking completeness warning was shown.
- Confirmed the final report. The proxy observed two POST requests for the same encounter. It let the first request finish at the API (HTTP 200) and dropped the browser response; it forwarded the repeated request and returned HTTP 200.
- The browser displayed the encounter as **Final**, showed the finalisation success notice, and rendered one patient-facing summary.

## Result

- **PASS:** A repeated finalise request after the first response was lost completed in the browser, and the visit showed as final.
- **PASS:** The API integration regression `EncounterFlowTest.retryingFinaliseAfterACompletedRequestReturnsTheExistingVisitWithoutRepeatingSideEffects` verifies one follow-up plan (five check-ins), one summary, one outbound message, and one summary-agent call after two requests.

## Limits

## Reload after a lost response

A second isolated run used a proxy that dropped every finalise response after the API completed it. The browser sent two POST requests for the same encounter; both completed upstream with HTTP 200, and both responses were dropped. The page displayed a recoverable connection error. After reloading, it fetched the encounter as **Final**, with editing disabled and the final report visible. This verifies recovery after the commit even when neither response reaches the browser.

That run exposed that the doctor visit page did not reload the patient summary. The page now fetches the latest summary for final encounters and renders it only when its `encounterId` matches the open visit, preventing an older visit's summary from appearing. The isolated test API had no summary-agent service, so summary recovery after reload was not visually verified in that run.

Slow/throttled network behavior, PostgreSQL concurrent-request behavior, real providers, and deployment remain untested.
