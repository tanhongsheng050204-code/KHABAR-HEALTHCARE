# Workspace verification record — 25 September 2026

This record captures checks run while implementing the remaining-work items. It covers local code and build checks only; it does not establish that the current branch is deployed or ready for clinical use.

## Khabar (`AI-IN-HEALTHCARE`)

| Area | Check | Result |
|---|---|---|
| API | `services/api`: `./mvnw --batch-mode --no-transfer-progress test` | **226 passed**, 0 failures/errors, 1 optional PostgreSQL smoke test skipped locally. The hosted PostgreSQL 16 smoke job is separately recorded as passing on 24 Sep. |
| Agents | `services/agents`: `.venv/Scripts/python.exe -m pytest -q` | **205 passed**, 1 third-party deprecation warning. |
| Web | `web`: `npm run lint` | Passed. |
| Web | `web`: `npm run typecheck` | Passed. |
| Web | `web`: `npm run build` | Passed; static and dynamic routes generated. |

The current `pilot-foundations` branch has not been redeployed or smoke-checked at the public URLs during this verification. The real-role sign-in, provider, screen-reader/caregiver accessibility, human comprehension, and clinical/privacy/security checks remain open in [`UNDONE_WORK.md`](../UNDONE_WORK.md).

GitHub Actions recorded a successful `Verify Khabar` run for branch head commit `954b5c9be10c059a2066bde24220ff7175e8fdb6` on 25 Sep 2026 ([run 36105880641](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36105880641)). Agent tests, API tests, PostgreSQL 16 migration/schema validation, PostgreSQL backup/restore, and web lint/typecheck/production build all passed. This commit updates policy/status documentation; it does not deploy the public services.

Fresh read-only checks on 25 Sep returned HTTP 200 for the public web home, timed out on `https://khabar-api.vercel.app/api/health`, and returned 404 for `https://khabar-api.vercel.app/agents/health`. These simple GETs do not validate auth, roles, or the full clinical workflow; the API timeout and route 404 require a deployed-service investigation.

## Starter skeleton (`starter-skeleton`)

| Area | Check | Result |
|---|---|---|
| API | `services/api`: `./mvnw.cmd --batch-mode --no-transfer-progress test` | **31 passed**, 0 failures/errors. This full run includes the updated audit assertions. |
| Agents | `services/agents`: `.venv/Scripts/python.exe -m pytest -q` | **17 passed**. |
| Web | `web`: `npm run lint` | Passed. |
| Web | `web`: `npm run typecheck` | Passed. |
| Web | `web`: `npm run build` | Passed. |
| Audit behavior | `NotesTest` covers `/check` when the agent fails, then confirms both owner and admin `CHECKED` entries persist in the access log. | Passed in the full API suite. |
| Agent health route | Khabar FastAPI `/agents/health` and Java agent health caller use the Vercel service prefix; agents tests (205 total) and `AgentClientServiceTest` (5) cover both sides. | Passed locally; not yet redeployed. |
| Deployment secret guard | With `VERCEL=1`, absent key / `dev-internal-secret` fails import; with a configured dummy key import succeeds; without `VERCEL`, local default config imports. | All three checks behaved as expected. |

The starter has no Git remote configured, and no hosted CI run or deployment check was performed. Set a strong unique `INTERNAL_SERVICE_KEY` for both hosted services before deployment.
