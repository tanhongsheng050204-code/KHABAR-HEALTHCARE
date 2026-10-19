# Triage concept rules (18 Oct 2026)

## What changed

The word lists match whole phrases, so a patient who described an emergency in other words was missed: on the
first held-out set they caught **2 of 12** red replies. `services/agents/agents/triage_concepts.py` adds a second
deterministic layer. A concept is either a standalone sign ("pengsan", "叫不醒") or a pair found close together
in one reply: a place in the body and something wrong with it ("dada" + "berat", "呼吸" + "困难"). The concepts
cover chest pain, breathlessness, blue lips, collapse, bleeding, face droop, speech trouble, one-sided weakness,
low blood sugar and confusion (red), and swelling, rash, dizziness, stomach upset, cough and high sugar (watch).
The more urgent of the word lists, the sugar reading and the concepts wins, and the optional model can still only
raise the level.

## How it was measured

1. A second held-out set (`services/agents/evals/triage_holdout_v2.json`, 80 replies: 32 red, 24 watch, 24 ok, in
   BM, English, Chinese and Tamil) was written and **committed before** the concept rules existed.
2. The rules were written from common red-flag lists and checked against the tuning set and unit tests.
3. Both held-out sets were then measured with `python -m scripts.compare_triage_models --set holdout2`.

| Set | Before (word lists) | After (lists + concepts) | False alarms on 'ok' |
|---|---|---|---|
| Tuning set (44) | 22/22 red | 22/22 red | 0 |
| First held-out set (22) | 2/12 red | 12/12 red* | 0 |
| **Second held-out set (80)** | **14/32 red** | **31/32 red** | **0** |

\* Not independent any more: its misses were read while the concepts were written.

The one red reply missed on the second set was "I'm gasping for air even sitting still". The 31/32 above was
measured before it was fixed. On 19 Oct it was fixed (32/32 on a re-run), which means the second set is no longer
unseen either; the next unseen figure needs a fresh set.
Full output: [triage-concepts-holdout2.md](triage-concepts-holdout2.md).

## Limits

- All replies are fictional and were written by the same author as the rules, which flatters any rule set.
  Neither set has been labelled by a clinician.
- 31/32 on 80 short replies is not evidence of safe emergency detection. Real replies are longer, misspelled,
  mixed-language and sometimes sarcastic.
- The rules ignore negation on purpose ("tak sakit dada" still raises a flag); expect false alarms on real text.
- The fixed 999 advice on every unchecked or flagged reply, and a person reviewing the call list, remain the
  safety net. Khabar is not a medical device.

## Next

Replace both sets with de-identified real replies labelled by two clinicians, measure the lists, concepts and
model separately, and keep a frozen set for each future change.
