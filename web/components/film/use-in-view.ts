"use client";

import { useEffect, useState, type RefObject } from "react";

/** True while the element is on screen: looping lights (bobbing, twinkling) run only then (spec §8). */
export function useInView(ref: RefObject<Element | null>) {
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const watch = new IntersectionObserver(([entry]) =>
      setInView(entry.isIntersecting),
    );
    watch.observe(el);
    return () => watch.disconnect();
  }, [ref]);
  return inView;
}
