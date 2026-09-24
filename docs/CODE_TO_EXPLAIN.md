# Code you should be able to explain

SDC judges can ask about any part of the code, including AI-written parts (handbook §8.5.3, §8.5.7).
This is a study list: the parts most likely to be asked about, where they live, and the question to be
ready for. Tick an item only when you can explain it without notes. Each area also has a plain-language
entry in [EXPLAIN.md](EXPLAIN.md).

## The safety story (most likely to be asked)

- [ ] **Why the AI can't invent a prescription.** Notes become a draft through a shorthand parser, not a
  model: `services/agents/agents/report_agent.py`, `prescription.py`.
  *Be ready for:* "What happens if the doctor writes something the parser doesn't understand?"
- [ ] **The safety checks.** `services/agents/agents/evaluator.py`: allergy, interaction (DDInter),
  duplicate across clinics and brands, herb, dose, pregnancy, grounding (invented drugs and symptoms),
  completeness. *Be ready for:* "Where does the interaction data come from, and what if a pair is missing?"
- [ ] **The three-layer block.** The screen (`web/components/clinical/visit-workspace.tsx`), the API
  (`encounters/EncounterController.java`) and the database: `@Check` on `encounters/Encounter.java`.
  *Be ready for:* "Why three places, not one?"
- [ ] **Triage.** `services/agents/agents/triage.py`: word lists or model, the more urgent wins.
  *Be ready for:* "What if the model is down?" and "Your word lists missed 10 of 12 held-out emergencies.
  What did you do about it?" (the 999 advice on every unread reply: `messaging/PatientMessages.java`).
- [ ] **What patients are told.** `followup/FollowUpService.java` and `messaging/PatientMessages.java`:
  fixed 999 advice, doctor-approved answers, or an acknowledgement, never free text from a model.

## Privacy and security

- [ ] **Who can see a record.** `patients/PatientAccessPolicy.java`: doctor, patient, caregiver, consent.
  *Be ready for:* "Show me the test that proves a caregiver loses access when consent is withdrawn."
- [ ] **Encrypted fields.** `crypto/FieldEncryptor.java`, `crypto/EncryptedStringConverter.java`.
  *Be ready for:* "Where is the key, and who can read it?" (only the API, from an environment variable).
- [ ] **Removing identity before AI.** `patients/Redactor.java` (API) and `services/agents/core/deid.py`.
- [ ] **The patient graph.** `graph/GraphFactsBuilder.java` → `graph/Neo4jPatientGraph.java`, random
  `graph_id` only. *Be ready for:* "Prove no name reaches Neo4j" (`PatientGraphSyncTest`, `DemoGraphTest`).
- [ ] **Sign-in.** `config/SecurityConfig.java`: how a token is verified, why two kinds of key, why each
  only against its own. `identity/CurrentUser.java`: from token to user and role.
- [ ] **Invite codes.** `onboarding/InviteCodes.java`, `onboarding/OnboardingController.java`: hashed,
  one use, 7 days. *Be ready for:* "Why can't a doctor just sign up?"
- [ ] **The audit log.** `audit/AuditLog.java`: what "who viewed my record" is built on.

## How the pieces fit

- [ ] **The five services and why each exists.** [README §5](../README.md#5-technical-architecture--feasibility)
  and [DECISIONS.md](DECISIONS.md). *Be ready for:* "Why Java and Python, not one language?"
- [ ] **API ↔ agents.** `service/AgentClientService.java` calls the agents with an internal service key
  (`services/agents/core/security.py`). The agents never touch Postgres.
- [ ] **Follow-up timing.** `followup/CheckInPlanner.java` (days 1, 3, 7, 14, 30; fasting times),
  `messaging/CheckInScheduler.java`, and the demo clock (`config/AdjustableClock.java`).
- [ ] **WhatsApp.** `messaging/WhatsAppCloudMessenger.java` (templates), `WhatsAppWebhookController.java`
  (signature check). *Be ready for:* "Why templates?" (WhatsApp only allows them outside a 24-hour window).
- [ ] **Deployment.** `services/vercel.json` (two services, one project), `.github/workflows/deploy-vercel.yml`.

## Honest limits to say out loud

- The word lists and the herb and dose tables need a doctor's and a pharmacist's review.
- No real provider test yet for WhatsApp, Favoriot, Gemini packet reading or transcription.
- The understanding pilot has not been run; there is no impact number yet.
- AI tools wrote much of the code; each part above is one you have read and can explain.
