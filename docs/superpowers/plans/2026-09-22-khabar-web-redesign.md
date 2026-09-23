# Khabar Web Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a live, reviewable unified Next.js frontend connected to the existing Khabar backend.

**Architecture:** Separate the three primary routes, centralize API/session contracts, and render focused role workspaces with nested clinical workflows. Preserve legacy pages until browser and build verification proves parity.

**Tech Stack:** Next.js 16, React 19, TypeScript, Motion, Lucide React, CSS Modules, Spring Boot, Supabase OTP.

**Spec:** `docs/superpowers/specs/2026-09-22-khabar-web-redesign.md`

## Global Constraints

- Do not commit or push before live-prototype approval.
- Preserve unrelated existing work.
- Use API-provided human-readable error messages.
- Support keyboard access, visible focus, reduced motion, mobile layout, and non-color urgency cues.

### Task 1: Shared foundation

**Files:** `lib/api.ts`, `lib/session.ts`, `lib/types.ts`, `app/layout.tsx`, `app/globals.css`, shared UI components.

- [x] Centralize token handling, typed requests, normalized errors, brand, status, empty, toast, and design tokens.
- [x] Verify TypeScript and ESLint.

### Task 2: Landing and login

**Files:** `app/page.tsx`, `app/landing.module.css`, `app/login/*`, landing/auth components.

- [x] Build the cinematic landing story with one scroll-driven care thread and interactive product preview.
- [x] Build OTP, demo, manual-token, and invitation access flows.
- [x] Complete desktop/mobile/reduced-motion browser verification (23 Sep bug bash; see `docs/BUG_BASH_2026-09-23.md`).

### Task 3: Role-aware home

**Files:** `app/home/*`, `components/home/*`.

- [x] Build protected shell, doctor dashboard, patient dashboard, and caregiver workspace.
- [x] Integrate scheduling, intake, medicines, readings, follow-up, consent, access-log, onboarding, and clinic priority endpoints.
- [x] Complete browser action verification.

### Task 4: Clinical workflows

**Files:** `app/home/patients/[id]`, `app/home/visits/[id]`, `components/clinical/*`.

- [x] Build patient record, pre-visit context, device link, visit notes/audio, draft, safety, override, finalisation, approved answers, and doctor invites.
- [x] Resolve caregiver record discovery in `/api/me` and add its backend test.
- [x] Exercise consultation draft/check/finalisation behavior in the browser.

### Task 5: Verification and handoff

- [x] Run frontend typecheck, lint, and production build.
- [x] Run the full API and agent test suites (184 and 188 passing).
- [x] Review desktop, mobile, reduced-motion, browser console, and primary API requests.
- [ ] Run `git diff --check` and inspect status without staging, committing, or pushing.
- [ ] Leave the live prototype running at `http://localhost:3001` and request user approval.
