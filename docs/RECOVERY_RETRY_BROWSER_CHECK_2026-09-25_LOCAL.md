# Recovery update lost-response and reload check — 25 September 2026

This manual browser check used only the seeded fictional patient Aminah and the local Khabar stack. It verifies the recovery-update retry path when the API commits the request but the browser never receives its response.

## Setup

- Existing local API, agent service, and web development server remained running.
- A second local web instance ran at `http://localhost:3001` with the API URL directed through a temporary local proxy at `http://localhost:8081`.
- The proxy forwarded the recovery-update POST to the local API, consumed the completed upstream response, then closed the browser connection. Its log recorded `DROPPED_AFTER_UPSTREAM_COMMIT status=200`.
- No real patient data or external provider was used.

## Result

- **PASS:** After the proxy dropped the response, the draft text remained in the form before reload. The response error itself was not captured in a separate screenshot or log.
- **PASS:** Reload cleared the message text from the form; the UI does not persist message content in browser storage.
- **PASS:** The same fictional text was re-entered and submitted after reload. The browser received the API's stored acknowledgement, and the form cleared.
- The browser retains the opaque client request ID in `sessionStorage`; backend tests verify that reusing the ID and identical text returns the previous result without creating another reply, re-running triage, or sending another outbound notice. A reused ID with different text is rejected and the UI creates a fresh ID.

## Limits

This is a local fictional-data rehearsal, not deployed-network or provider verification. It does not cover slow/throttled connections, finalisation retries, provider outage, real role sign-in, or staff notification. Production and clinical readiness remain open.
