"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { Pause, Play } from "lucide-react";
import type { Lang } from "./messages";
import styles from "./film.module.css";

if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger, useGSAP);
}

type Film = {
  /** True when scenes may animate: the device allows motion and the visitor has not paused it. */
  motion: boolean;
  lang: Lang;
  setLang: (lang: Lang) => void;
};

const FilmContext = createContext<Film>({
  motion: false,
  lang: "ms",
  setLang: () => {},
});
export const useFilm = () => useContext(FilmContext);

const REDUCED = "(prefers-reduced-motion: reduce)";
function subscribe(onChange: () => void) {
  const list = window.matchMedia(REDUCED);
  list.addEventListener("change", onChange);
  return () => list.removeEventListener("change", onChange);
}

export function FilmProvider({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  // The server cannot see the device setting, so it renders every act's static end frame (motion off);
  // the scenes switch to their animated layout once the browser confirms motion is allowed.
  const reduced = useSyncExternalStore(
    subscribe,
    () => window.matchMedia(REDUCED).matches,
    () => true,
  );
  const [paused, setPaused] = useState(false);
  const [lang, setLang] = useState<Lang>("ms");
  const motion = !reduced && !paused;
  const value = useMemo(() => ({ motion, lang, setLang }), [motion, lang]);

  // Switching motion re-lays out the page (pins added or removed), so the reader's place is kept: the
  // anchor most on screen before the switch is brought back into view after it. On the first switch to
  // motion, a visitor who arrived by a link such as #act3 is taken there again, because pinning added
  // scroll length above it after the browser's own jump. Child effects (the acts' timelines) run first.
  const keep = useRef<HTMLElement | null>(null);
  // The anchor last brought back, and the scroll position it left: toggling again without scrolling in
  // between keeps that same anchor, instead of re-reading a layout that places it differently.
  const restored = useRef<{ el: HTMLElement; y: number } | null>(null);
  const arrived = useRef(false);
  // Until motion first switches on, note what a reader who scrolls early is looking at: on a slow phone
  // the pins attach seconds after the page appears, and would otherwise push them somewhere else.
  const early = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (motion || arrived.current) return;
    // On a reload or back/forward the browser restores the scroll position of the page as it was (pins
    // included), so that position, not the static layout under it, is the reader's place.
    const navigation = performance.getEntriesByType("navigation")[0] as
      PerformanceNavigationTiming | undefined;
    if (navigation && navigation.type !== "navigate") return;
    const note = () => {
      early.current = window.scrollY > 0 ? mostVisibleAnchor() : null;
    };
    note();
    window.addEventListener("scroll", note, { passive: true });
    return () => window.removeEventListener("scroll", note);
  }, [motion]);
  useEffect(() => {
    if (!motion && !keep.current) return;
    ScrollTrigger.refresh();
    const kept = keep.current;
    keep.current = null;
    if (kept) {
      bringBack(kept);
      restored.current = { el: kept, y: window.scrollY };
    } else if (motion && !arrived.current) {
      arrived.current = true;
      const target = hashTarget();
      if (target) target.scrollIntoView({ block: "start" });
      else if (early.current) bringBack(early.current);
    }
  }, [motion]);

  return (
    <FilmContext.Provider value={value}>
      <div
        className={`${styles.film} ${className ?? ""}`}
        data-motion={motion ? "on" : "off"}
      >
        {children}
        <button
          type="button"
          className={styles.motionToggle}
          onClick={() => {
            const last = restored.current;
            keep.current =
              last && Math.abs(window.scrollY - last.y) < 2
                ? last.el
                : mostVisibleAnchor();
            setPaused((was) => !was);
          }}
          disabled={reduced}
          aria-pressed={!motion}
        >
          {motion ? (
            <Pause size={13} aria-hidden />
          ) : (
            <Play size={13} aria-hidden />
          )}
          <span>Pause motion</span>
          {reduced ? <small>Reduced motion is on</small> : null}
        </button>
      </div>
    </FilmContext.Provider>
  );
}

/**
 * The [data-anchor] element the reader is looking at: the one under the middle of the screen (the smallest,
 * if several are), else the visible one nearest the middle. Area alone ties when two day cards are both
 * fully on screen, and then picks the wrong one.
 */
function mostVisibleAnchor(): HTMLElement | null {
  const cx = window.innerWidth / 2;
  const cy = window.innerHeight / 2;
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const el of document.querySelectorAll<HTMLElement>("[data-anchor]")) {
    const r = el.getBoundingClientRect();
    const left = Math.max(r.left, 0);
    const right = Math.min(r.right, window.innerWidth);
    const top = Math.max(r.top, 0);
    const bottom = Math.min(r.bottom, window.innerHeight);
    if (right <= left || bottom <= top) continue;
    const covers =
      r.left <= cx && cx <= r.right && r.top <= cy && cy <= r.bottom;
    // Covering the middle always beats not covering it; then smaller (more specific) or nearer wins.
    const score = covers
      ? -1e12 + r.width * r.height
      : Math.hypot((left + right) / 2 - cx, (top + bottom) / 2 - cy);
    if (score < bestScore) {
      best = el;
      bestScore = score;
    }
  }
  return best;
}

/**
 * Scrolls an anchor back into view. A day card inside the pinned sideways pan is reached by scrolling to
 * the point of the pan where that card is centred (the pan runs linearly over its ScrollTrigger).
 */
function bringBack(el: HTMLElement) {
  const track = el.closest<HTMLElement>("[data-pan-track]");
  const pan = ScrollTrigger.getById("thirty-days");
  if (track && pan && track.parentElement) {
    const distance = track.scrollWidth - track.parentElement.clientWidth;
    const centred =
      el.offsetLeft + el.offsetWidth / 2 - track.parentElement.clientWidth / 2;
    const progress = Math.min(1, Math.max(0, centred / distance));
    window.scrollTo(0, pan.start + progress * (pan.end - pan.start));
    return;
  }
  el.scrollIntoView({
    block: el.hasAttribute("data-day") ? "center" : "start",
  });
}

/** The element named by the URL fragment; a malformed fragment such as #100% is ignored, not fatal. */
function hashTarget(): HTMLElement | null {
  const raw = window.location.hash.slice(1);
  if (!raw) return null;
  try {
    return document.getElementById(decodeURIComponent(raw));
  } catch {
    return document.getElementById(raw);
  }
}
