"use client";

import { useEffect, useState } from "react";
import { STORY } from "./story";
import styles from "./film.module.css";

/**
 * A slim rail of the eight acts at the right edge (desktop only). The act under the middle of the screen is
 * marked current, which also holds while Act 3 is pinned, because its section contains the pin. Its name
 * shows while the reader scrolls and for a moment after, then gets out of the way.
 */
export function ProgressRail() {
  const [current, setCurrent] = useState<string>(STORY[0].id);
  const [moving, setMoving] = useState(false);

  useEffect(() => {
    let frame = 0;
    let rest: number | undefined;
    const read = () => {
      frame = 0;
      const middle = window.innerHeight / 2;
      for (const { id } of STORY) {
        const r = document.getElementById(id)?.getBoundingClientRect();
        if (r && r.top <= middle && r.bottom >= middle) {
          setCurrent(id);
          return;
        }
      }
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(read);
      setMoving(true);
      window.clearTimeout(rest);
      rest = window.setTimeout(() => setMoving(false), 1200);
    };
    frame = requestAnimationFrame(read);
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      cancelAnimationFrame(frame);
      window.clearTimeout(rest);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  return (
    <nav className={styles.rail} aria-label="Story" data-moving={moving}>
      <ol>
        {STORY.map(({ id, name }) => (
          <li key={id}>
            <a
              href={`#${id}`}
              aria-current={current === id ? "step" : undefined}
            >
              <span className={styles.railDot} aria-hidden="true" />
              <span className={styles.railName}>{name}</span>
            </a>
          </li>
        ))}
      </ol>
    </nav>
  );
}
