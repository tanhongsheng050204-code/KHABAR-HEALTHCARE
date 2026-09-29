import { Fraunces, Plus_Jakarta_Sans } from "next/font/google";

// Self-hosted by next/font. Chinese and Tamil use the fonts every phone and computer already has
// (see film.module.css): web fonts for them cost ~170 KB and a 68 KB render-blocking stylesheet, because
// the language chips put both scripts on the first screen, and pushed mobile LCP to 5.1 s.
// Headings only use weight 400, so the static 400 files are far smaller than the variable font.
const display = Fraunces({
  subsets: ["latin"],
  weight: "400",
  style: ["normal", "italic"],
  variable: "--film-display",
  display: "swap",
});
const body = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--film-body",
  display: "swap",
});

export const fontVariables = [display.variable, body.variable].join(" ");
