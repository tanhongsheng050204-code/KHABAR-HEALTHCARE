# Khabar frontend (Antigravity screens)

The main UI for now is the set of static pages in this folder, designed in Antigravity. They use Tailwind from a CDN and fake data; they are not wired to the backend yet. Open `docs/index.html` in a browser to start.

| Page | What it shows |
|---|---|
| `index.html` | Launcher linking every screen |
| `landing_page.html` | Public landing page |
| `login.html` | Doctor and nurse sign-in (demo) |
| `clinical_handshake.html` | Secure-session animation after sign-in |
| `clinic_command.html` | Doctor's triage desk |
| `visit.html` | The doctor's visit: pre-visit page, notes to report, safety check, finalise (works only with the local API) |
| `telemetry_chat.html` | Patient reply and chat console |
| `polypharmacy_guard.html` | Cross-clinic medicine clash radar |
| `recovery_arc.html` | 30-day recovery view and Ramadan timing |
| `moh_audit_logs.html` | Audit log and "who viewed" ledger (file name kept so existing links work) |
| `khabar_full_prototype.html` | Step-by-step interactive demo |
| `app_portal.html` | Portal overview |

**Live:** https://khabar-landing-six.vercel.app serves this folder. After changing a page, redeploy with `cd docs && npx vercel deploy --prod`. `.vercelignore` keeps the notes, drafts and redirect stubs off the site, and `vercel.json` tells Vercel there is no build step.

`../prototype/khabar-landing.html` is the earlier landing page and is no longer deployed. `prototype/` is still linked to the same Vercel project, so don't run `vercel deploy --prod` from there, or it will replace these screens.

## Live data: sign-in, triage desk and visit

Three screens talk to the real backend when it runs on your machine; everywhere else (including the public Vercel site) they stay in demo mode and never contact the visitor's computer.

| Screen | With the backend running |
|---|---|
| `login.html` | Any sign-in button signs you in as the demo doctor through the API (`/dev/token`). Supabase sign-in replaces this later. |
| `visit.html` | The whole visit on real data. Pick a patient (Aminah is chosen first). The left side is the pre-visit page: why they are coming in (from the intake chat), the last visit, what they take elsewhere with any duplicates, clashes or herb problems already flagged, and their replies since. On the right, type notes in shorthand, **Draft the report**, then **Run the safety check**. Each critical finding needs a written reason (10+ characters, saved in the audit log). The bar at the bottom stays shut, and says why, until every critical finding has a reason; then **Finalise and send the summary** shows the summary the patient receives. On the public site the page only explains that it needs the local API. |
| `clinic_command.html` | A **"Call these patients today"** panel appears above the sample cases, filled from `GET /api/clinic/call-list`: most urgent first, the patient's reply, follow-up day and language. **Mark as called** records the call (it shows in the patient's "who viewed my record" log) and removes them from the list. **Open visit** goes to that patient's visit screen. The acuity counts at the top become real. It refreshes every 15 seconds. |

**Run it:** `powershell -ExecutionPolicy Bypass -File scripts/run-local.ps1`, wait about 30 seconds, then open http://localhost:5500/login.html. (Or start the three parts by hand: see `services/README.md`, plus `python -m http.server 5500 --directory docs`.)

**Send a patient reply** to watch it arrive on the triage desk (it goes through the real triage in the agents service):
```bash
P=$(curl -s -X POST "localhost:8080/dev/token?as=patient" | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -X POST localhost:8080/api/followup/replies -H "Authorization: Bearer $P" -H "Content-Type: application/json" -d '{"text":"sakit dada sikit"}'
```

The code lives in `js/khabar-api.js` (API address, sign-in, requests), `js/triage-desk.js` (the live panel) and `js/visit.js` (the visit screen). The pages open the API at `http://localhost:8080` only when they are served from `localhost`; add `?api=<address>` to point them elsewhere, or `?api=off` to force demo mode.

## Rules for anyone editing these pages (people or AI tools)

Paste these into Antigravity or any other tool before it edits the frontend:

1. **Fictional names only.** No real hospitals, clinics, pharmacies, companies or apps (for example UMMC, HKL, Pantai, Caring, Mediviron, MySejahtera, Doctor2U). Use invented names such as "Klinik Kesihatan Taman Harmoni".
2. **No government affiliation or certification claims.** Do not say Khabar is certified, compliant, verified, sealed, registered or endorsed by the Ministry of Health (KKM/MOH), MMC, NPRA, JAKIM or any agency, and do not add official-looking IDs, seals or "© Ministry of Health" lines. Referencing a guideline as a design input ("CPG Heart Failure 2023") is fine.
3. **No real registration numbers.** Staff IDs look like `DEMO-0001`.
4. **No fake integrations with emergency services.** Buttons say "999 (demo)".
5. **Keep the demo note** (`id="khabar-demo-note"`) on every page.

## Clean-up done on 22 Sep 2026

Antigravity's versions named real hospitals and companies and claimed government certification (including a "© 2025 Ministry of Health Malaysia (KKM)" footer). Before publishing, every claim of affiliation, certification or official status was reworded, real organisation names were replaced with fictional ones, and a dismissible "concept demo" note was added to each page. The design, layout and interactions are unchanged.

The untouched originals are kept outside the repo in `AI IN HEALTHCARE/antigravity-originals-2026-09-22/`. Superseded drafts (`landing_page_v2.html`, `stitch_landing_page.html`, `triage_desk.html`) and the redirect stubs (`*_preview.html`, which point at files on one PC) are git-ignored.
