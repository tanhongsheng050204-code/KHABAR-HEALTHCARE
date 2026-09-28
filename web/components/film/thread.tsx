import type { Ref } from "react";
import styles from "./film.module.css";

export type ThreadState = "ok" | "watch" | "red";

/**
 * The thread of light. Paths use pathLength 1, so stroke-dashoffset 1 hides the thread and 0 draws it
 * fully; acts tween the [data-draw] paths. The colour follows data-state: green, amber or red.
 */
export function Thread({
  d, viewBox, state, name, pulse = false, svgRef,
}: {
  d: string; viewBox: string; state: ThreadState; name: string; pulse?: boolean; svgRef?: Ref<SVGSVGElement>;
}) {
  return (
    <svg
      ref={svgRef}
      className={styles.thread}
      viewBox={viewBox}
      preserveAspectRatio="none"
      data-thread={name}
      data-state={state}
      data-pulse={pulse || undefined}
      aria-hidden="true"
      focusable="false"
    >
      <path className={styles.threadGlow} d={d} pathLength={1} data-draw="" />
      <path className={styles.threadLine} d={d} pathLength={1} data-draw="" />
      {pulse ? <path className={styles.threadPulse} d={d} pathLength={1} /> : null}
    </svg>
  );
}
