# Telegram replaces WhatsApp for patient messaging

**Date:** 28 September 2026
**Status:** design approved in conversation; awaiting spec review
**Decision owner:** project owner (chose "replace WhatsApp completely")

## Why

WhatsApp Cloud API needs Meta business setup, a test number that reaches only five recipients, and
Meta-approved templates for every clinic-initiated message (summaries, check-ins). Approval can take
from minutes to days. A Telegram bot needs no approval, no templates and no recipient limit, so the
live demo can run the full loop today: summary and check-ins sent, patient replies triaged onto the
clinic call list.

**Accepted trade-off:** Telegram is far less used than WhatsApp in Malaysia, especially by older
adults, and the README's "patients are already there" argument weakens. The README and pitch must say
this plainly and explain the choice (instant setup, no template approval, patient-initiated linking
that doubles as consent).

## Scope

In scope: every patient-facing message that WhatsApp carried (visit summary, day 1/3/7/14/30
check-ins, safety advice, clinic-approved answers, acknowledgements) and inbound patient replies.

Out of scope: messages to caregivers (none exist today), voice or photo replies over Telegram, and
any change to message wording, languages, triage or the check-in schedule.

## Design

### 1. Data

New migration `V7__patient_telegram_chat.sql` adds to `patient`:

| Column | Purpose |
| --- | --- |
| `telegram_chat_enc varchar(512)` | Telegram chat ID, encrypted with `EncryptedStringConverter` (same as `phone_enc`) |
| `telegram_chat_index varchar(64)` + index | keyed HMAC of the chat ID, to find the patient for an inbound message (same approach as `phone_index`) |

The public demo uses Hibernate `ddl-auto: update`, which adds the columns on deploy. The `pilot`
profile applies V7 through Flyway and validates the schema; CI runs it on PostgreSQL 16.

The phone number and phone index stay: linking matches the phone a patient shares with the one the
clinic registered.

### 2. Sending

- `Messenger.send(recipient, text, language, kind)`: the recipient is a Telegram chat ID (it was a
  phone number). `channel()` stays.
- New `TelegramMessenger`: `POST https://api.telegram.org/bot<token>/sendMessage` with `chat_id` and
  plain `text`. Never throws; failures come back in `Result`, as today.
- `MessagingConfig`: `TelegramMessenger` when `KHABAR_TELEGRAM_BOT_TOKEN` is set, otherwise the
  existing `OutboxMessenger`, so local runs and tests are unchanged.
- `PatientMessages.send(patient, text, kind)` (the single outbound funnel): with Telegram configured
  and no linked chat, the message is not sent and is recorded in `outbound_message` as
  not delivered with the error `Telegram not linked`. Nothing is dropped silently.

### 3. Inbound and linking

`POST /api/webhooks/telegram` (already public under `/api/webhooks/**`).

- **Authentication:** the webhook is registered with a random secret; Telegram sends it in
  `X-Telegram-Bot-Api-Secret-Token`. Compare in constant time; mismatch or missing → `401`.
- Otherwise always `200`, so Telegram does not retry on our errors; errors are logged.

| Update | Behaviour |
| --- | --- |
| `/start`, or any message from an unlinked chat | Reply in BM and English with a reply-keyboard button **Share my phone number** (`request_contact`). |
| Contact whose `user_id` ≠ sender's `from.id` | Refuse: "Please share your own number using the button." Nothing is linked. |
| Own contact, number matches a patient | Store the chat ID (encrypted + index) on that patient, replacing any earlier chat; confirm in the patient's language; then send the latest approved visit summary, if one exists. |
| Own contact, no matching patient | "We couldn't find a clinic record for this number. Please ask your clinic." |
| Text from a linked chat | `FollowUpService.receiveReply(patient, text, clientMessageId)` with `clientMessageId` derived from chat ID + message ID, so a Telegram retry is not triaged or answered twice. The reply appears on the call list; the patient gets the same acknowledgement / safety advice / approved answer as a web reply. |
| Photo, voice or other non-text from a linked chat | Reply in the patient's language: please reply in text. |

A shared number is Telegram-verified and belongs to the sender, so the "no matching patient" reply
tells them only about themselves.

**Webhook registration:** `services/scripts/telegram-set-webhook.mjs`, run once by the owner. It
reads the bot token and secret from hidden prompts (never printed) and calls `setWebhook` with the
URL and secret. Not done at API start-up, to avoid work on every serverless cold start.

### 4. Removal

Delete `WhatsAppCloudMessenger`, `WhatsAppWebhookController`, their tests and WhatsApp settings
(`khabar.whatsapp.*` in the YAML files). Git history keeps them.

### 5. Web

- Patient home: a **Get your care plan on Telegram** card with a link to `https://t.me/<bot>` and
  one line of instructions (open the bot, tap *Share my phone number*). Shows "Telegram connected"
  once linked; `/api/me` gains a `telegramLinked` flag for this. The bot username comes from
  `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME`; with it unset the card is hidden.
- Clinic admin integration health: the messaging row reports Telegram (configured or outbox).

### 6. Documentation

README (solution table, idea C rationale, comparison table, tech stack, "what's live"), `DECISIONS.md`
(new entry: Telegram replaces WhatsApp, reasons and risks), `SETUP_INTEGRATIONS.md` (BotFather,
secrets, webhook script), `UNDONE_WORK.md` §2.1, `PITCH_SCRIPTS.md`, `services/README.md`.

## Configuration

| Setting | Where | Secret? |
| --- | --- | --- |
| `KHABAR_TELEGRAM_BOT_TOKEN` | `khabar-api` production | yes |
| `KHABAR_TELEGRAM_WEBHOOK_SECRET` | `khabar-api` production | yes |
| `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME` | `khabar-landing` production | no |

## Testing

Test-first. API tests:

- `TelegramMessenger`: request shape (chat ID, text), success and failure results, against a stub server.
- `PatientMessages`: unlinked patient with Telegram configured → recorded, not delivered, `Telegram not linked`.
- Webhook: wrong or missing secret → 401; `/start` → share-number keyboard; own contact → linked
  (encrypted column set, index lookup works) and latest summary sent; someone else's contact → refused,
  nothing linked; unknown number → not-found reply; linked text → reply stored and triaged, on the
  call list; the same update delivered twice → one reply, one triage, one outbound acknowledgement;
  photo → text-only prompt; unlinked text → share prompt.
- Migration: V7 on H2 (existing Flyway tests) and PostgreSQL 16 in CI.
- Web: lint, typecheck, build; browser check of the card (hidden without the setting, link shown,
  connected state).

**Live test (owner):** create the bot with @BotFather, set the three settings, deploy, run the webhook
script. The demo patients have fictional phones (`03-0000 xxxx`), so the doctor registers a fictional
patient with the tester's own Telegram phone number to test linking.

## Risks

- Lower Telegram use among the target patients (accepted; documented).
- Telegram bot chats are not end-to-end encrypted; the same caution as WhatsApp applies: fictional
  data only until a privacy review.
- A patient whose Telegram account uses a different number from the one the clinic registered cannot
  link; the clinic must update the record.
