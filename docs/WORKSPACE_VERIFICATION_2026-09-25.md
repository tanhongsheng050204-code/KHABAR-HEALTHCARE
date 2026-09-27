# Workspace verification record — 25 September 2026

This record captures checks run while implementing the remaining-work items. It covers local code and build checks only; it does not establish that the current branch is deployed or ready for clinical use.

## Khabar (`AI-IN-HEALTHCARE`)

| Area | Check | Result |
|---|---|---|
| API | `services/api`: `./mvnw.cmd test` | Latest full suite: **239 executed: 238 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally. H2 PostgreSQL-mode migration suite includes V5 legacy-reply compatibility and per-patient uniqueness plus V6 routing schema; caregiver scope and request-time revocation are covered. A focused `DemoGraphTest` run passed 4/4 for reset-to-condition-graph coverage. |
| Agents | `services/agents`: `.venv/Scripts/python.exe -m pytest -q` | **205 passed**, 1 third-party deprecation warning. |
| Web | `web`: `npm run lint` | Passed. |
| Web | `web`: `npm run typecheck` | Passed. |
| Web | `web`: `npm run build` | Passed; static and dynamic routes generated. |

The current `pilot-foundations` branch has not been redeployed or smoke-checked at the public URLs during this verification. The real-role sign-in, provider, screen-reader/caregiver accessibility, human comprehension, and clinical/privacy/security checks remain open in [`UNDONE_WORK.md`](../UNDONE_WORK.md).

After that run, a local browser check against an isolated current-worktree API verified that a fictional caregiver with `SUMMARY` scope sees the summary-only state and no medication or reading details. The test patient had no finalised summary, so populated-summary rendering remains unverified; deployed and real-account checks remain open. Details are in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).

GitHub Actions recorded a successful `Verify Khabar` run for branch head commit `dd29a762faed1605d71a810b9ddd428e5ada9476` on 25 Sep 2026 ([run 36107956075](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36107956075)). Agent tests, API tests, PostgreSQL 16 migration/schema validation, the point-in-time PostgreSQL backup/restore check, and web lint/typecheck/production build all passed. Application code is unchanged since `954b5c9`; this run does not deploy the public services.

Repeated read-only checks on 25 Sep returned HTTP 200 for the public web home, timed out at `https://khabar-api.vercel.app/api/health`, and returned 404 for both `/agents/health` and `/agents/agents/health`; an additional `/agents` request also timed out. Earlier API probes had returned 200, so the timeouts indicate intermittent or cold-start behavior, not sustained API unavailability. The connected Vercel integration required authentication, preventing inspection of project settings and runtime logs. These simple GETs do not validate auth, roles, or the full clinical workflow; inspect the deployment after Vercel access is connected.

## Starter skeleton (`starter-skeleton`)

| Area | Check | Result |
|---|---|---|
| API | `services/api`: `./mvnw.cmd -q test` | **31 passed**, 0 failures/errors. This full run includes the updated audit assertions. |
| Agents | `services/agents`: `.venv/Scripts/python.exe -m pytest -q` | **17 passed**. |
| Web | `web`: `npm run lint` | Passed. |
| Web | `web`: `npm run typecheck` | Passed. |
| Web | `web`: `npm run build` | Passed. |
| Audit behavior | `NotesTest` covers `/check` when the agent fails, then confirms both owner and admin `CHECKED` entries persist in the access log. | Passed in the full API suite. |
| Agent health route | Khabar FastAPI `/agents/health` and Java agent health caller use the Vercel service prefix; agents tests (205 total) and `AgentClientServiceTest` (5) cover both sides. | Passed locally; not yet redeployed. |
| Deployment secret guard | With `VERCEL=1`, absent key / `dev-internal-secret` fails import; with a configured dummy key import succeeds; without `VERCEL`, local default config imports. | All three checks behaved as expected. |

The starter has no Git remote configured, and no hosted CI run or deployment check was performed. Set a strong unique `INTERNAL_SERVICE_KEY` for both hosted services before deployment.

Latest hosted workflow refresh: GitHub Actions run [36112985600](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36112985600) passed on branch head `01be0f6` on 25 Sep. The API, agents, web lint/typecheck/build, PostgreSQL 16 migration/schema check, and disposable backup/restore jobs all passed for that commit. It predates the current uncommitted V5/V6 migrations and finalise replay changes, so it is not PostgreSQL verification of the current worktree. It did not deploy the feature branch or change the open public route and real-role checks.

Current public route recheck (25 Sep, read-only `curl` GET): web HTTP 200, API `/api/health` HTTP 200, `/agents/health` HTTP 404, `/agents/agents/health` HTTP 404, and `/agents` HTTP 401. The current `services/vercel.json` sends `/agents/*` to the agent service, which exposes both `/health` and `/agents/health`; neither expected health path answered successfully in this probe. The root `/agents` 401 is not a health check. Deployment history: web workflow run [35976458448](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/35976458448) succeeded on `main` commit `79924b3` on 24 Sep; current remote `main` is `5a1056b`, with changes since that run including agent-service code. `.github/workflows/deploy-vercel.yml` deploys the web app only; API and agents are deployed separately. The feature branch remains undeployed.

### Live endpoint refresh — 25 September 2026

- Re-ran the repository's own `node scripts/check-health.mjs` from `services/`: API health passed at HTTP 200 with status `UP`; agents health failed at HTTP 404 for `/agents/health`.
- Independent GETs returned web HTTP 200, API `/api/health` HTTP 200 (0.18 s), `/agents/health` HTTP 404, `/agents/agents/health` HTTP 404, and `/agents` HTTP 401. The root `/agents` response is not health evidence.
- Vercel's current services guide says Services framework selection in project settings and a `services` declaration are both needed for service builds. The checked-in config declares the two services and rewrites, but project settings and the deployed build remain uninspected because Vercel authentication is unavailable in this workspace. Sources: [Vercel Services guide](https://vercel.com/kb/guide/vercel-services), [Services routing](https://vercel.com/docs/services/routing).
- The API and web are responding now. Agent service health and the full deployed app flow are still unverified; no deployment was initiated.

### Authenticated Vercel CLI inspection — 25 September 2026

- The connected Vercel app integration still requires authentication, but the local Vercel CLI session is authenticated. Read-only `vercel inspect https://khabar-api.vercel.app` identified the active production deployment as **Ready**, created 24 Sep, with both `services/agents/fastapi` and `services/api/container` in its build.
- `vercel project inspect khabar-api --json` reported `framework: null` (the dashboard displays **Other**) and `rootDirectory: null` (repository root). The documented intended setup is Vercel Services with project root `services/`. Project settings therefore do not match the documented configuration, even though the inspected deployment built both services.
- Request logs for the active deployment show the recent `/agents/health` and `/agents/agents/health` responses as HTTP 404. Public `/api/health` is HTTP 200. A Ready deployment does not establish working agent health.
- The active production deployment was created on 24 Sep at 15:31. Commit `da5658a` (“Fix agent health check routing on Vercel”) was made on 25 Sep at 14:15 and added the `/agents/health` alias to FastAPI; it is an ancestor of the current `pilot-foundations` HEAD. The deployment predates that route fix, making stale deployed code the leading explanation for the 404. This remains an inference until a safe current-source deployment is performed and probed.
- No project settings were changed and no deployment was started. Vercel documents that the Services framework must be selected and that build-setting changes apply on the next deployment ([Services guide](https://vercel.com/kb/guide/vercel-services), [Configure a build](https://vercel.com/docs/builds/configure-a-build)).

## Current-worktree rerun (25 September 2026)

After the finalise retry safeguard was added, an earlier full API suite ran 234 tests with 233 passed, zero failed, zero errored, and the optional PostgreSQL-only smoke test skipped. The current web tree also passed lint, typecheck, and production build. These local checks do not replace hosted PostgreSQL 16 verification or deployment of this uncommitted tree.

## Latest full local rerun (25 September 2026)

- API: 239 tests executed, 238 passed, 0 failed, 0 errored, 1 optional PostgreSQL-only test skipped. The focused V4-to-V6 idempotency migration test passes under H2 PostgreSQL mode, including a legacy reply and per-patient uniqueness check. Caregiver scope and request-time revocation are included.
- Agents: 205 passed with one third-party Starlette/AnyIO deprecation warning.
- Web: lint, typecheck, and production build passed after the final-visit summary reload fix.
- The starter repository was also rerun independently: API 31, agents 17, and web checks passed after clean `npm ci`. A starter-specific `.github/workflows/verify.yml` is now present; its YAML parsed locally and its API/agent/web commands passed. The hosted workflow remains unverified until a Git remote is configured and pushed. See [`../starter-skeleton/VERIFICATION_2026-09-25.md`](../starter-skeleton/VERIFICATION_2026-09-25.md).
- `git diff --check` passes in both repositories; Git reports only line-ending normalization warnings. All work remains uncommitted. These local checks do not resolve provider, publication, deployed agent health, owner-disclosure, or human/clinical gates.

## Current-worktree follow-up — graph context warning (25 September 2026)

- Agents: **209 passed**, 0 failures, with one third-party Starlette/AnyIO deprecation warning. Evaluator tests cover missing graph ID, unconfigured or unreachable Neo4j, and a missing patient node.
- Web: lint, typecheck, and production build passed with the visible `WARN` finding type accepted.
- The evaluator now emits a non-blocking `patient_graph_context` finding when graph-derived information was not available and tells the clinician to review medicines, allergies, pregnancy status, and herbs directly. This prevents the visit safety card from showing an unqualified “No safety problems found” result for the graph-disabled case.
- A current-worktree browser rehearsal on isolated ports with Neo4j disabled created a structured fictional draft, ran the safety review, displayed the new warning in the visit card, and retained a separate critical duplicate-medicine block. It did not override or finalise the visit. The work remains uncommitted and has not been deployed; see [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- A separate browser attempt with intentionally unstructured fictional notes exposed an inaccurate success notice when the agent returned no structured fields. The UI now distinguishes that response from a usable draft; a post-fix browser recheck confirmed the no-draft message and disabled safety/finalisation controls. Lint, typecheck, and production build pass.

## Fresh test rerun — 25 September 2026

- Khabar API: `./mvnw.cmd -q test` exited successfully; Surefire XML reports sum to **239 tests, 0 failures, 0 errors, 1 skipped** (the optional PostgreSQL smoke test). Migrations V1–V6 and pilot profile validation ran locally against H2; PostgreSQL 16 validation remains a hosted CI gate.
- Khabar agents: `.venv/Scripts/python.exe -m pytest -q` — **209 passed**, one third-party deprecation warning.
- Khabar web: lint, typecheck, and production build passed.
- Starter skeleton: API **31 passed**, agents **17 passed**, web lint/typecheck/build passed. See [`../starter-skeleton/VERIFICATION_2026-09-25.md`](../starter-skeleton/VERIFICATION_2026-09-25.md).
- Both repositories still have uncommitted changes. Hosted CI/deployment, provider credentials, deployed permissions, and participant/clinical evidence remain open; this local rerun does not satisfy those gates.
- Accessibility follow-up: the login page now has a “Skip to sign in” link, and the shared “Skip to workspace” link targets focusable main landmarks (`tabIndex=-1`) on all relevant workspace routes. Localized patient, caregiver, and clinician summary text is tagged with its language, and the web summary type is constrained to the four locales emitted by the summary agent (`en`, `ms`, `zh`, `ta`). The clinician note textarea now has a visible associated label. Lint, typecheck, and production build passed; the local accessibility tree exposes the new login link and target. Full keyboard and screen-reader review remains open.
- Earlier fresh local verification rerun: Khabar API **239 total / 0 failures / 0 errors / 1 optional PostgreSQL skip**, agents **209 passed** (one third-party deprecation warning), and web lint/typecheck/build all passed. Starter API **31 passed**, agents **17 passed**, and web lint/typecheck/build all passed. The later 240-test API rerun is recorded below; hosted PostgreSQL on current V1–V6 code, hosted starter CI, and deployment remain unverified.
- Chrome keyboard spot checks on the current production-built preview confirmed login, doctor overview, and patient-record skip links transfer focus to their named targets. Patient-record review showed no finalised visit summary, so selected-language summary content could not be compared with an approved visit. Details and scope limits are in [`DEMO_RUN_2026-09-25_LOCAL.md`](DEMO_RUN_2026-09-25_LOCAL.md).
- Keyboard checks now also include the patient overview. The development caregiver shortcut reached its permissions-unavailable state because the connected API on port 8080 predates the current sharing-scope response; the UI exposed no shared details and the skip link reached the main landmark. This is fail-closed/error-state evidence only, not current-backend permission verification. Next.js Dev Tools appeared as the first tab stop in that development-only route. The full tab-order and screen-reader reviews remain open.
- Vercel account access was rechecked through the connected Vercel tools: `list_teams` returned “Authentication required.” Project settings, deployment framework/root, and runtime logs therefore remain uninspected; no deployment configuration was changed.
- Local startup timing: the repeatable isolated API measurement script returned `/api/health` `UP` **11.97 seconds** after launch; Spring startup was **6.557 seconds**. The JVM stopped and port 8081 was released after the sample. See [`COLD_START_CHECK_2026-09-25_LOCAL.md`](COLD_START_CHECK_2026-09-25_LOCAL.md); this is not deployed availability evidence.

## Repeat run in this continuation — 25 September 2026

- Khabar API: `./mvnw.cmd -q test` exited 0; Surefire XML reports **240 tests: 239 passed, 0 failed, 0 errored, 1 optional PostgreSQL smoke test skipped**. H2 migration and pilot-profile checks reached V6. PostgreSQL 16 verification against this exact worktree remains open.
- Khabar agents: `.venv/Scripts/python.exe -m pytest -q` — **209 passed**, with one third-party Starlette/AnyIO deprecation warning.
- Khabar web: lint, typecheck, and production build all exited 0.
- Starter API: `services/api/mvnw.cmd -q -f services/api/pom.xml test` exited 0; Surefire reports total **31 tests**. This reruns its API suite only; the unchanged starter agent and web sources retain the earlier local results in [`../starter-skeleton/VERIFICATION_2026-09-25.md`](../starter-skeleton/VERIFICATION_2026-09-25.md).
- These checks ran against current local worktrees. Hosted starter CI, current-tree PostgreSQL 16 validation, deployment, provider tests, and human/clinical gates remain open.
- Read-only public health check rerun: API `/api/health` timed out after the script's 15-second limit and agents `/agents/health` returned HTTP 404. This confirms current deployment health is still not established; no deployment or project setting was changed.
- Hosted-state recheck: GitHub Actions still lists the latest successful `pilot-foundations` run as `36112985600` on commit `01be0f6`; it does not include the current uncommitted V1–V6 worktree. The Vercel connector returned “Authentication required,” and `vercel` is not available as a local command in this shell, so current project settings and deployment logs could not be re-inspected. No remote or deployment configuration was changed.
\n
