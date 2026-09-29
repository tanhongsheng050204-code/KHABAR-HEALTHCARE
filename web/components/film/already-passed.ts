/**
 * For a scroll reveal's first client render: whether the element the server already drew sits above the
 * reveal line (a reload part-way down the page), so the reveal starts shown instead of hiding what the
 * reader is looking at and fading it back in. On the server, or before the element exists, it has not.
 */
export function alreadyPassed(selector: string, line: number): boolean {
  if (typeof document === "undefined") return false;
  const el = document.querySelector(selector);
  return !!el && el.getBoundingClientRect().top < window.innerHeight * line;
}
