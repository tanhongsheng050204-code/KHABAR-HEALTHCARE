# Khabar frontend (Antigravity screens)

The main UI for now is the set of static pages in this folder, designed in Antigravity. They use Tailwind from a CDN and fake data; they are not wired to the backend yet. Open `docs/index.html` in a browser to start.

| Page | What it shows |
|---|---|
| `index.html` | Launcher linking every screen |
| `landing_page.html` | Public landing page |
| `login.html` | Doctor and nurse sign-in (demo) |
| `clinical_handshake.html` | Secure-session animation after sign-in |
| `clinic_command.html` | Doctor's triage desk |
| `telemetry_chat.html` | Patient reply and chat console |
| `polypharmacy_guard.html` | Cross-clinic medicine clash radar |
| `recovery_arc.html` | 30-day recovery view and Ramadan timing |
| `moh_audit_logs.html` | Audit log and "who viewed" ledger (file name kept so existing links work) |
| `khabar_full_prototype.html` | Step-by-step interactive demo |
| `app_portal.html` | Portal overview |

`../prototype/khabar-landing.html` is the earlier landing page. It is still what the live Vercel production URL serves until you promote this frontend.

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
