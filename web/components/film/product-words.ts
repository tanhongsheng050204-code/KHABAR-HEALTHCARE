import words from "./product-words.json";
import type { Lang } from "./messages";

/**
 * Words generated from the real agents code by services/agents/scripts/make_film_words.py (checked by
 * tests/test_film_words.py): the summary lines, the parsed draft and the safety finding the film shows.
 */
export const PRODUCT_WORDS = words as {
  rx: { shorthand: string; medicine: string; how: Record<Lang, string> }[];
  visit: {
    notes: string;
    draft: {
      name: string;
      strengthMg: number | null;
      timesPerDay: number | null;
      timing: string | null;
    }[];
    finding: { severity: string; detail: string };
  };
};
