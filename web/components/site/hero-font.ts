import { Manrope } from "next/font/google";

// The same Manrope as app/fonts.ts, called from a module only the landing page imports, so next/font
// preloads it on / alone. It sets the hero headline: loading it late reflows the hero and shifts the
// product replay below it.
const display = Manrope({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
});

export const heroFontVariable = display.variable;
