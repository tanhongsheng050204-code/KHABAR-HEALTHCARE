/**
 * The town in Act 5 and the sky in Act 7: twelve fictional homes around one clinic, on a 480 × 480 board.
 * Each reply is one the film already shows elsewhere, and scripts/triage-consistency.test.mjs runs every
 * one through the product's triage word lists, so a home shown green really is triaged "ok". No imports:
 * that test loads this file directly with Node.
 */
export type TownStatus = "ok" | "watch" | "red";
export type TownLang = "ms" | "en" | "zh" | "ta";
export type Home = {
  id: number;
  name: string;
  lang: TownLang;
  reply: string;
  status: TownStatus;
  /** Top-left corner of the 40 × 40 footprint on the board, in px. */
  x: number;
  y: number;
  /** Wall height in px. */
  h: number;
};

export const CLINIC = { x: 200, y: 200, w: 76, d: 76, h: 70 } as const;

export const HOMES: Home[] = [
  {
    id: 1,
    name: "Mak Cik Aminah",
    lang: "ms",
    reply: "Sihat, terima kasih Khabar.",
    status: "ok",
    x: 20,
    y: 20,
    h: 26,
  },
  {
    id: 2,
    name: "Mr David Tan",
    lang: "en",
    reply: "I'm okay.",
    status: "ok",
    x: 140,
    y: 20,
    h: 34,
  },
  {
    id: 3,
    name: "Pak Cik Rahim",
    lang: "ms",
    reply: "Pening sikit hari ini.",
    status: "watch",
    x: 280,
    y: 20,
    h: 42,
  },
  {
    id: 4,
    name: "Mdm Chong",
    lang: "zh",
    reply: "我很好。",
    status: "ok",
    x: 400,
    y: 20,
    h: 26,
  },
  {
    id: 5,
    name: "Mrs Letchumi",
    lang: "ta",
    reply: "நான் நலம்.",
    status: "ok",
    x: 400,
    y: 140,
    h: 34,
  },
  {
    id: 6,
    name: "Mr Muthu",
    lang: "ta",
    reply: "நெஞ்சு வலி.",
    status: "red",
    x: 400,
    y: 280,
    h: 42,
  },
  {
    id: 7,
    name: "Encik Azman",
    lang: "ms",
    reply: "Dah makan ubat.",
    status: "ok",
    x: 400,
    y: 400,
    h: 26,
  },
  {
    id: 8,
    name: "Mdm Wong",
    lang: "zh",
    reply: "今天有点头晕。",
    status: "watch",
    x: 280,
    y: 400,
    h: 34,
  },
  {
    id: 9,
    name: "Mrs Fernandez",
    lang: "en",
    reply: "All good, thank you Khabar.",
    status: "ok",
    x: 140,
    y: 400,
    h: 42,
  },
  {
    id: 10,
    name: "Puan Rosnah",
    lang: "ms",
    reply: "Okay, sihat.",
    status: "ok",
    x: 20,
    y: 400,
    h: 26,
  },
  {
    id: 11,
    name: "Mr Gopal",
    lang: "en",
    reply: "A bit dizzy today.",
    status: "watch",
    x: 20,
    y: 280,
    h: 34,
  },
  {
    id: 12,
    name: "Mr Lim",
    lang: "zh",
    reply: "吃了药，很好。",
    status: "ok",
    x: 20,
    y: 140,
    h: 42,
  },
];
