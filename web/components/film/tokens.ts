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

/**
 * gsap.matchMedia conditions, matching film.module.css. "desktop" (the pinned sideways pan) needs height as
 * well as width: a short laptop or a phone turned sideways cannot fit a pinned scene, so it gets the
 * stacked "mobile" layout instead.
 */
export const media = {
  desktop: "(min-width: 761px) and (min-height: 600px)",
  mobile: "(max-width: 760px), (max-height: 599px)",
  /** The finale's tall sky: phones, and any window taller than it is wide (film.module.css matches). */
  tallSky: "(max-width: 760px), (max-aspect-ratio: 1/1)",
  wideSky: "(min-width: 761px) and (min-aspect-ratio: 1/1)",
} as const;

/** Seconds of catch-up smoothing on scrubbed timelines; small enough to feel attached to the finger. */
export const SCRUB = 0.6;
