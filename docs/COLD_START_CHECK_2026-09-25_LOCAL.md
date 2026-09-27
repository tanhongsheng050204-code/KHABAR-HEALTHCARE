# Local API cold-start check — 25 September 2026

This records local Spring startup separately from endpoint failure. It is not a deployment or end-user performance measurement.

## Run

- **Command:** `services/scripts/measure-local-api-cold-start.ps1 -Port 8081 -TimeoutSeconds 90`
- **Profile/data:** current API source, `local` profile, in-memory H2 and fictional seed data.
- **Isolation:** port 8081 was confirmed free before launch. No web app, agent service, Neo4j, Supabase, Gemini, WhatsApp, or hosted service was part of this timing.
- **Result:** first successful `GET http://localhost:8081/api/health` returned `{"status":"UP",...}` **11.97 seconds** after the measurement started. Spring logged `Started KhabarApiApplication in 6.557 seconds (process running for 6.849)`.
- **Cleanup:** the test JVM was stopped, port 8081 was released, and the temporary startup log was removed.

The total includes the Maven wrapper and local process startup. It is one run on this machine, not a percentile or service-level objective. It does not measure first user-page render, agents readiness, bandwidth limits, provider latency, or deployed cold starts. Repeat with the script to compare future local API startup changes.
