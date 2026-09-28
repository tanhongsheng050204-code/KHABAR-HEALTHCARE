import { Fraunces, Noto_Sans_SC, Noto_Sans_Tamil, Plus_Jakarta_Sans } from "next/font/google";

// Self-hosted by next/font. The Chinese and Tamil faces are not preloaded: the browser only downloads
// the glyph ranges a page actually shows, so visitors who never switch language never load them.
const display = Fraunces({ subsets: ["latin"], style: ["normal", "italic"], variable: "--film-display", display: "swap" });
const body = Plus_Jakarta_Sans({ subsets: ["latin"], variable: "--film-body", display: "swap" });
const chinese = Noto_Sans_SC({ weight: ["400", "600"], preload: false, variable: "--film-zh", display: "swap" });
const tamil = Noto_Sans_Tamil({ subsets: ["tamil"], weight: ["400", "600"], preload: false, variable: "--film-ta", display: "swap" });

export const fontVariables = [display.variable, body.variable, chinese.variable, tamil.variable].join(" ");
