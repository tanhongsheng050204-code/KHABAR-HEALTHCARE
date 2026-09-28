// Written by scripts/film-assets.mjs; run that script instead of editing by hand.
// An act listed here has its AI background in public/film/; an act missing from it shows its placeholder.
export type ActId = "act0" | "act1" | "act2" | "act3" | "act4" | "act6" | "act7";

export const BACKDROPS: Partial<Record<ActId, { width: number; height: number }>> = {};
