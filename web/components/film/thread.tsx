import type { Ref } from "react";
import styles from "./film.module.css";

export type ThreadState = "ok" | "watch" | "red";

/**
 * The thread of light. Paths use pathLength 1, so where a dash pattern is set (the hero), stroke-dashoffset
 * 1 hides the thread and 0 draws it fully; acts tween the [data-draw] paths. The colour follows data-state:
 * green, amber or red; with pulse, the glow breathes.
 */
export function Thread({
  d,
  viewBox,
  state,
  name,
  pulse = false,
  shape,
  fit = "stretch",
  svgRef,
}: {
  d: string;
  viewBox: string;
  state: ThreadState;
  name: string;
  pulse?: boolean;
  /** For a thread drawn twice, once per screen shape: CSS shows only the one that fits. */
  shape?: "wide" | "tall";
  /**
   * stretch: fills its box exactly (small solid threads). cover: scales evenly and crops like a background
   * photo; a drawn-on thread needs this, because its pathLength dash only matches the screen when the
   * SVG is not stretched and the stroke scales with it.
   */
  fit?: "stretch" | "cover";
  svgRef?: Ref<SVGSVGElement>;
}) {
  return (
    <svg
      ref={svgRef}
      className={styles.thread}
      viewBox={viewBox}
      preserveAspectRatio={fit === "cover" ? "xMidYMax slice" : "none"}
      data-thread={name}
      data-state={state}
      data-pulse={pulse || undefined}
      data-shape={shape}
      data-fit={fit}
      aria-hidden="true"
      focusable="false"
    >
      <path className={styles.threadGlow} d={d} pathLength={1} data-draw="" />
      <path className={styles.threadLine} d={d} pathLength={1} data-draw="" />
    </svg>
  );
}
