# Evaluations

Measurements behind the model and provider choices in [plan.md](../../plan.md) §7, with fictional data only.
Each has a script in `services/agents/scripts/`, so a result can be re-run and checked.

## What the choice of LLM actually affects

Drafting the report from the doctor's notes and every safety check are **rule-based**:
`report_agent.py` reads the shorthand with a parser, and `evaluator.py` uses DDInter and written rules.
So the ten-case planted-error set does not depend on the model, and it passes 10/10 whichever model is
configured (`tests/test_planted_errors.py`). The model matters in four places:

| Where | What the model does | How it's measured |
|---|---|---|
| Follow-up triage | A second reader after the word lists; it may raise a reply's urgency, never lower it | `compare_triage_models.py` (below) |
| Packet photos | Reads label text; the generic always comes from our own tables | `eval_packet_reader.py` on seven fictional packets |
| Intake chat | Asks the next question in the patient's language | By hand for now; no scripted measure yet |
| Approved answers | Picks which doctor-approved answer fits, if any | Unit tests with a stand-in model |

## Follow-up triage (24 Sep 2026): word lists alone

The live demo has no model configured, so the word lists are all that decides urgency there.

| Measure | Result |
|---|---|
| Red replies raised, labelled set, before widening ([report](triage-2026-09-24-before.md)) | **6 / 22** |
| Same set after widening the lists and adding a blood-sugar number rule ([report](triage-2026-09-24-after.md)) | 22 / 22, but tuned on these very cases |
| **Held-out replies, never used for tuning** ([report](triage-2026-09-24-holdout.md)) | **2 / 12** |
| False alarms on "I'm well" replies | 0 in both sets |

**What this means.** Word lists catch the phrases someone thought to write down, and people describe
emergencies in endless ways ("rasa macam ada benda berat hempap dada", "tercungap-cungap walaupun
baring"). They are a floor, not a triage. Two changes follow:

1. **Every reply a person still has to read now carries the 999 advice** (API `PatientMessages`), not just
   replies the lists read as red. Before, a missed emergency got only "the clinic will look at your message".
2. **Configure a model before relying on follow-up triage**, then run
   `python -m scripts.compare_triage_models --set holdout --models <candidates>` and keep the model that
   raises the most red replies, with false alarms as the tie-breaker. The held-out set should then be
   replaced or extended with replies written by a clinician, since I wrote both sets.

The widened lists still need a doctor's review, like the originals.

## Still to run (needs a provider key)

| Test | Command (from `services/agents`) | Needs |
|---|---|---|
| Triage model comparison | `python -m scripts.compare_triage_models --set holdout --models gemini-3.6-flash gemini-3.5-flash-lite --out ../../docs/evals/triage-models.md` | `GEMINI_API_KEY` |
| Packet photos | `python -m scripts.eval_packet_reader --models gemini-3.6-flash --out ../../docs/evals/packets.md` | `GEMINI_API_KEY` |
| Transcription | Record the five scripts in `evals/recordings/`, then `python -m scripts.score_transcripts --models whisper-large-v3-turbo whisper-large-v3 --out ../../docs/evals/transcription.md` | `GROQ_API_KEY` |

Keys go in `services/agents/.env`, which git ignores. Free-tier Gemini may use what it's sent to improve
its models, which is acceptable here only because everything sent is fictional.
