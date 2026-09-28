"use client";

import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
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

const FilmContext = createContext<Film>({ motion: false, lang: "ms", setLang: () => {} });
export const useFilm = () => useContext(FilmContext);

const REDUCED = "(prefers-reduced-motion: reduce)";
function subscribe(onChange: () => void) {
  const list = window.matchMedia(REDUCED);
  list.addEventListener("change", onChange);
  return () => list.removeEventListener("change", onChange);
}

export function FilmProvider({ children, className }: { children: ReactNode; className?: string }) {
  // The server cannot see the device setting, so it renders every act's static end frame (motion off);
  // the scenes switch to their animated layout once the browser confirms motion is allowed.
  const reduced = useSyncExternalStore(subscribe, () => window.matchMedia(REDUCED).matches, () => true);
  const [paused, setPaused] = useState(false);
  const [lang, setLang] = useState<Lang>("ms");
  const motion = !reduced && !paused;
  const value = useMemo(() => ({ motion, lang, setLang }), [motion, lang]);

  // A visitor who arrives by a link such as #act3 was scrolled there using the static layout. Pinning then
  // adds scroll length above the target, so once the acts' timelines exist (child effects run first),
  // scroll to the target again.
  useEffect(() => {
    if (!motion || !window.location.hash) return;
    const target = document.getElementById(decodeURIComponent(window.location.hash.slice(1)));
    if (!target) return;
    ScrollTrigger.refresh();
    target.scrollIntoView({ block: "start" });
  }, [motion]);

  return (
    <FilmContext.Provider value={value}>
      <div className={`${styles.film} ${className ?? ""}`} data-motion={motion ? "on" : "off"}>
        {children}
        <button
          type="button"
          className={styles.motionToggle}
          onClick={() => setPaused((was) => !was)}
          disabled={reduced}
          aria-pressed={!motion}
        >
          {motion ? <Pause size={13} aria-hidden /> : <Play size={13} aria-hidden />}
          <span>Pause motion</span>
          {reduced ? <small>Reduced motion is on</small> : null}
        </button>
      </div>
    </FilmContext.Provider>
  );
}
