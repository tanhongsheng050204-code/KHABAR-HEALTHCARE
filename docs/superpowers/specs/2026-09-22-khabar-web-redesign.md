# Khabar Web Redesign Specification

## Objective

Unify Khabar's fragmented static screens and monolithic Next.js prototype into three primary routes: public landing (`/`), focused sign-in (`/login`), and a role-aware authenticated workspace (`/home`). No commit or push is allowed until the user reviews the live local prototype.

## Experience

The direction is **calm clinical cinema**: editorial headlines, precise clinical interfaces, deep green and sea-glass tones, generous space, and one animated care thread that explains before-visit, consultation, and recovery continuity. Motion must support meaning, respect `prefers-reduced-motion`, and never distract from clinical urgency.

## Architecture

- Next.js App Router owns the frontend.
- Server route shells are separated from client session and API interactions.
- `lib/api.ts`, `lib/session.ts`, and `lib/types.ts` provide one request path, token lifecycle, and shared contracts.
- `/home` renders dedicated doctor, patient, or caregiver workspaces.
- Nested patient-record and visit routes remain inside the authenticated shell.
- Legacy `docs/*.html` screens remain available as parity references during validation.

## Required workflows

- Doctor: priority call list, appointments, patients, onboarding, pre-visit record, access log, device link, consultation notes/audio, structured draft, safety checks, audited override, finalisation, colleague invites, and approved answers.
- Patient: summary, appointments, intake, follow-up replies, medicines, readings, caregiver consent, and access history.
- Caregiver: discover consented records and view allowed summary, medicines, and readings with clear read-only boundaries.

## Acceptance criteria

- Landing, login, and home are distinct, responsive, accessible routes.
- Local doctor and patient demos work against the Spring API.
- Loading, empty, offline, expired-session, forbidden, and clinical blocking states are understandable.
- Typecheck, lint, production build, backend tests, desktop/mobile browser checks, and Git diff checks pass.
- The live prototype remains uncommitted until explicit user approval.
