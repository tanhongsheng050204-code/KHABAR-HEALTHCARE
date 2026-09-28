/** Motion values shared by every act, so the whole film moves with one rhythm. */
export const ease = {
  /** Scroll-scrubbed moves: linear progress feels like a camera on rails. */
  scrub: "none",
  /** Things arriving: quick start, long settle. */
  enter: "power3.out",
  /** Bubbles and taps: a small overshoot. */
  pop: "back.out(1.7)",
} as const;

export const duration = { tap: 0.25, enter: 0.7, pop: 0.5 } as const;

/** gsap.matchMedia conditions; the 760 px split matches film.module.css. */
export const media = {
  desktop: "(min-width: 761px)",
  mobile: "(max-width: 760px)",
} as const;

/** Seconds of catch-up smoothing on scrubbed timelines; small enough to feel attached to the finger. */
export const SCRUB = 0.6;
