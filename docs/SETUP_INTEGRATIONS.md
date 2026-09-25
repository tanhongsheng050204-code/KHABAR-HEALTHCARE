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

This is a dated configuration-presence snapshot, not confirmation of the current deployment or variable values. Never record secret contents here. On 25 Sep, `/api/health` returned 200 but `/agents/health` returned 404; see [the verification record](WORKSPACE_VERIFICATION_2026-09-25.md). Recheck service configuration after the branch is deployed.

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

1. Confirm the free tier at favoriot.com. If it isn't free, keep the simulated readings (backlog 2.2).
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
