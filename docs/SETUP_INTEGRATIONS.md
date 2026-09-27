# Setting up the real providers

How to connect each outside service to the deployed demo, in the order that unblocks the most.
No secret appears here: every value goes straight into Vercel, GitHub or a local `.env` that git ignores.
Never paste one into a chat, an issue or a commit.

**Where things run**

| Vercel project | Folder | Deploys |
|---|---|---|
| `khabar-landing` | `web/` | By itself on every push to `main` that changes `web/` (GitHub Actions) |
| `khabar-api` | `services/` (Java API + Python agents) | By hand: `cd services` then `npx vercel deploy --prod` |

To add a value: `cd services` then `npx vercel env add NAME production`, and paste it when asked.
To add a GitHub secret: `gh secret set NAME --body "value"`. Don't pipe it in PowerShell: that added an
invisible byte-order mark once and broke the deploy.

**Last recorded production-variable presence check (24 Sep 2026):** database, `FIELD_ENCRYPTION_KEY`,
`INTERNAL_SERVICE_KEY`, `SUPABASE_JWKS_URL`, `SUPABASE_JWT_SECRET`, `NEO4J_*`, `WEB_ALLOWED_ORIGINS`,
`WEB_APP_URL`, `AGENTS_SERVICE_URL`. **Not set yet:** `GEMINI_API_KEY`, `GROQ_API_KEY`, `WHATSAPP_*`,
`FAVORIOT_DEVICE_SECRET`.

This is a dated configuration-presence snapshot, not confirmation of current deployment variable values. Never record secret contents here. On the latest read-only check on 25 Sep, the web and API `/api/health` returned HTTP 200; both `/agents/health` and `/agents/agents/health` returned 404, and `/agents` returned 401 (not a health check). The repository health-check script passed the API check and failed the agent check. The connected Vercel integration still returns “Authentication required,” but the local Vercel CLI session is authenticated and can inspect the project read-only; it reports the project framework as Other and `rootDirectory` as unset. See [the verification record](WORKSPACE_VERIFICATION_2026-09-25.md).

**Agent health route diagnosis:** [`services/vercel.json`](../services/vercel.json) defines `api` and `agents` as Vercel Services and rewrites `/agents/*` to `agents`; current FastAPI defines both `/health` and `/agents/health`. The active production deployment was created on 24 Sep, before commit `da5658a` (25 Sep) added the `/agents/health` route, so deployed code is stale relative to that fix; this is the leading explanation for the current 404, not yet confirmed by a new deployment. The production build did include both services. Project inspection also reported Framework Preset **Other** and an unset root directory. After the current V1–V6 migrations pass hosted PostgreSQL/backup verification, set/verify the `khabar-api` project framework as **Services** and root directory as `services/`, deploy the reviewed current source, and require `/agents/health` HTTP 200 with `status: healthy`. Vercel documents the Services selection, service rewrites, and that build-setting changes apply on the next deployment ([Vercel Services guide](https://vercel.com/kb/guide/vercel-services), [Configure a build](https://vercel.com/docs/builds/configure-a-build)). Do not treat `/agents` returning 401 as a health check. No settings were changed and no deployment was initiated.

Use `node scripts/check-health.mjs` from `services/` to check the API and public agent route together. It exits nonzero if either endpoint fails. The local API and agents can be checked without Vercel by passing their addresses explicitly; the successful local and failing public results are recorded in [the test results](TEST_RESULTS.md).

---

## 1. A model for triage and packet photos (Gemini): do this first

Why first: without it, follow-up triage runs on word lists alone, which miss most ways of describing an
emergency (see [evals](evals/README.md)).

1. Create a key at Google AI Studio (aistudio.google.com → Get API key).
2. `npx vercel env add GEMINI_API_KEY production` in `services/`, then redeploy the API.
3. Locally, put `GEMINI_API_KEY=...` in `services/agents/.env` and run the comparisons in
   [evals/README.md](evals/README.md). Change `GEMINI_MODEL` only if another model wins.

Free-tier data may be used by Google, so send fictional data only.

## 2. Real sign-in (Supabase Auth)

The API already accepts real Supabase sign-ins (ES256 keys from `SUPABASE_JWKS_URL`) next to the demo
buttons' tokens. What's left is configuration in the Supabase dashboard (Authentication):

1. **Providers → Email:** enabled. Keep "Confirm email" on.
2. **Emails → Templates:** the sign-in screen asks for the code, but Supabase's default emails only
   contain a link. Add the code to both the **Magic Link** and **Confirm signup** templates, for example:
   `Your Khabar code is {{ .Token }}. It expires in one hour.`
3. **SMTP:** Supabase's own sender only delivers to your project's team members. To reach anyone else,
   set a custom SMTP sender (Authentication → Emails → SMTP settings). Any free transactional provider
   works; use a sender address you control.
4. **URL configuration:** Site URL `https://khabar-landing-six.vercel.app`.

**What is configured now, and what went wrong on the way (27 Sep):**

- Until a custom SMTP sender is set, the templates cannot be edited, and the default email's link pointed to
  `http://localhost:3000` with no code, whatever the Site URL. Set SMTP first, then edit the templates (step 2).
- Resend's free sandbox sender (`onboarding@resend.dev`) only delivers to the Resend account owner's own
  address, so it cannot reach other patients without a verified domain.
- Current sender: a Gmail account through `smtp.gmail.com`, port `587`, username = the Gmail address,
  password = a Gmail **App Password** (needs 2-Step Verification). Gmail limits daily sending.
- When switching providers, change **every** field. A Host left at the old provider produced
  `535 "Invalid username"` in Authentication → Logs, because the Gmail login was being sent to Resend.
- A first-time address gets the **Confirm signup** template and an existing one gets **Magic Link**, so both
  need `{{ .Token }}`.
- Supabase limits how often one address can request a code; a second request within seconds returns `429`
  (the sign-in screen shows "could not send a code").

**Doctor accounts** (the screen has no staff sign-up, on purpose):
1. Supabase → Authentication → Users → Add user, with email and password, and "Auto confirm" on.
2. In the app, an existing doctor uses *Invite a doctor* (clinic tools, at the bottom of the doctor home), or calls
   `POST /api/clinic/doctor-invites`, and gets a one-time code.
3. The new doctor signs in with email and password, opens *Have an invitation code?*, and enters it.
   The code links their Supabase user to the clinic.

**Patients and caregivers:** the clinic registers the patient and hands over a one-time code. The patient
signs in with an email code and enters the invitation code once. A patient invites a caregiver from
*My people*, and can withdraw access there at any time.

**Check it:** one real sign-in per role, and then withdraw a caregiver and confirm their next request is
refused (backlog 1.2).

## 3. WhatsApp Cloud API

1. developers.facebook.com → Create app → Business → add **WhatsApp**. Note the test number's
   **Phone number ID**, and add up to five of your own phones as recipients.
2. Create a **permanent token** with a System User in Business settings (the temporary one expires in 24 h).
3. **Templates** (WhatsApp Manager → Message templates), one per language: `ms`, `en`, `zh_CN`, `ta`.
   Category *Utility*.
   - Check-in, no parameters, e.g. `khabar_checkin`: the text in `PatientMessages.CHECK_IN`
     ("Apa khabar hari ini? Dah makan ubat? Balas mesej ini untuk beritahu klinik.").
   - Summary, one body parameter `{{1}}`, e.g. `khabar_summary`: "Ringkasan lawatan anda: {{1}}"
     (and the same in each language).
4. Set on `khabar-api`: `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_TOKEN`, `WHATSAPP_CHECKIN_TEMPLATE`,
   `WHATSAPP_SUMMARY_TEMPLATE`, `WHATSAPP_APP_SECRET` (App settings → Basic), and a random
   `WHATSAPP_VERIFY_TOKEN` you make up. Redeploy.
5. Webhook (WhatsApp → Configuration): callback URL `https://khabar-api.vercel.app/api/webhooks/whatsapp`,
   verify token = the one you made up. Subscribe to **messages**. Every delivery is checked against
   `X-Hub-Signature-256`.
6. **Check it:** give a fictional patient your test phone's number, finalise a visit (summary arrives),
   send a check-in, reply "sakit dada", and confirm the patient tops the call list and gets the 999 advice.

Until this is set, messages go to the local outbox, which is fine for the demo.

## 4. Favoriot (home readings)

1. Favoriot's pricing page currently lists a lifetime RM0 plan with 1 device, 500 daily data points, 1 dashboard, 1 rule, and 1-month retention ([pricing](https://www.favoriot.com/iotplatform/pricing); plan changes announced 1 Sep 2026 in the [official notice](https://www.favoriot.com/category/press-release/)). Its separate [free-plan signup page](https://www.favoriot.com/subscribe-free-plan) still lists older/different limits (unlimited devices, 500 API calls/day, 1-year retention). Confirm the actual account's assigned limits before testing; if access or a free test account is unavailable, keep simulated readings.
2. Set a random `FAVORIOT_DEVICE_SECRET` on `khabar-api` and redeploy.
3. In Favoriot, add an HTTP forwarding rule to `https://khabar-api.vercel.app/api/webhooks/favoriot`
   with a header `X-Khabar-Device-Secret: <the same value>`. The body is Favoriot's stream JSON, e.g.
   `{"device_developer_id": "...", "data": {"glucose": 2.8}}` or `{"data": {"systolic": 185, "diastolic": 121}}`.
4. In the app, open a fictional patient's record → *Link a home device* → the device's developer ID.
5. **Check it:** send glucose 2.8; the patient should appear on the call list as red.

## 5. Transcription (Groq)

1. Create a key at console.groq.com.
2. `npx vercel env add GROQ_API_KEY production`, then redeploy. "Speak notes" on the visit screen starts working.
3. For the plan's test, record the five scripts in `services/agents/evals/recordings/` and run the
   scorer ([instructions](../services/agents/evals/recordings/README.md)).

## 6. After any change on `khabar-api`

1. Redeploy: `cd services` then `npx vercel deploy --prod`.
2. Refresh the demo story: `POST https://khabar-api.vercel.app/dev/demo/reset`. This also repairs data
   seeded by an older version, for example Aminah's conditions for the patient graph.
3. Refill the graph if AuraDB was recreated: `POST /dev/graph/sync`.
4. Open the live site, press **Doctor view**, and check the call list loads.
