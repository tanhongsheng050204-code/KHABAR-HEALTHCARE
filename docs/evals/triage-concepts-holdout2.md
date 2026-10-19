# Triage comparison: holdout2 (80 fictional replies, 18 Oct 2026)

| Triage | Red caught | Under-triaged | False alarms on 'ok' | Notes |
|---|---|---|---|---|
| Word lists + concept rules | 31/32 | 2 | 0 | no model, no network |

## Word lists + concept rules
- red not raised: I'm gasping for air even sitting still
- under-triaged: My sugar was 15 this morning


## Update, 19 Oct 2026: one miss fixed, so this set is no longer unseen

The one urgent reply missed above, "I'm gasping for air even sitting still", is now caught: "gasping for air" and
similar phrases (BM: "tak cukup udara") were added to the breathing rule. Re-run: **32/32 red caught, 0 false alarms**.

Because the fix was made after reading this set's result, holdout v2 now measures tuning, not unseen performance,
the same as v1. A fresh set written without looking at the rules is needed before quoting a new unseen figure.
