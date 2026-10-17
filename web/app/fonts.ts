import { Inter, Manrope } from "next/font/google";

// Self-hosted by next/font. Inter carries body text and controls; Manrope carries headings
// at heavy weights with tight tracking. Both are variable fonts, so one file serves every weight.
// Neither is preloaded here: a preload in the root layout reaches every route, and on /story (which uses
// the film's own fonts) it competes with the film's headline font and slows its LCP. The landing page
// preloads Manrope for itself (components/site/hero-font.ts). Fallbacks are size-adjusted, so a late swap
// barely moves text.
const sans = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
  preload: false,
});
const display = Manrope({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
  preload: false,
});

export const siteFontVariables = [sans.variable, display.variable].join(" ");
