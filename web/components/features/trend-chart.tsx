"use client"

import { useId, useMemo, useState } from "react"
import type { TrendSeries } from "@/lib/types"
import styles from "./features.module.css"

const W = 560
const H = 180
const PAD = { top: 16, right: 16, bottom: 26, left: 40 }

/** English, with {placeholders} filled: the doctor's screens are not translated. */
function fill(phrase: string, vars?: Record<string, string | number>) {
  return Object.entries(vars ?? {}).reduce((text, [key, value]) => text.replaceAll(`{${key}}`, String(value)), phrase)
}

const DIRECTION: Record<string, string> = {
  IMPROVING: "improving",
  WORSENING: "worsening",
  FLAT: "steady",
  NOT_ENOUGH_DATA: "not enough readings",
}

function niceRange(values: number[]) {
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = (max - min) * 0.15 || 1
  return [Math.floor(min - pad), Math.ceil(max + pad)] as const
}

/**
 * One measure over time: a 2px line, a dashed target line when the pathway sets one, and a crosshair that snaps to
 * the nearest reading. A single series, so the title names it and there is no legend; a table view sits below.
 */
export function TrendChart({ series, locale = "en-MY", titleWord, t = fill }: {
  series: TrendSeries; locale?: string; titleWord?: string; t?: (phrase: string, vars?: Record<string, string | number>) => string
}) {
  const [hover, setHover] = useState<number | null>(null)
  const id = useId()
  const points = series.points
  const geometry = useMemo(() => {
    if (points.length === 0) return null
    const times = points.map((p) => new Date(p.at).getTime())
    const values = points.map((p) => p.value)
    const [lo, hi] = niceRange(series.target != null ? [...values, series.target] : values)
    const t0 = Math.min(...times)
    const t1 = Math.max(...times) || t0 + 1
    const x = (t: number) => PAD.left + ((t - t0) / Math.max(1, t1 - t0)) * (W - PAD.left - PAD.right)
    const y = (v: number) => PAD.top + (1 - (v - lo) / Math.max(1e-9, hi - lo)) * (H - PAD.top - PAD.bottom)
    const ticks = [lo, (lo + hi) / 2, hi].map((v) => Math.round(v * 10) / 10)
    return { xs: times.map(x), ys: values.map(y), y, ticks, t0, t1 }
  }, [points, series.target])

  const format = (iso: string) => new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" }).format(new Date(iso))
  if (!geometry) return null
  const { xs, ys, y, ticks } = geometry
  const path = xs.map((x, i) => `${i ? "L" : "M"}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(" ")
  const last = points.length - 1
  const active = hover ?? last

  function nearest(clientX: number, box: DOMRect) {
    const sx = ((clientX - box.left) / box.width) * W
    let best = 0
    xs.forEach((x, i) => { if (Math.abs(x - sx) < Math.abs(xs[best] - sx)) best = i })
    setHover(best)
  }

  return (
    <figure className={styles.trend} aria-labelledby={`${id}-title`}>
      <figcaption className={styles.rowBetween}>
        <strong id={`${id}-title`}>{titleWord ?? series.label}</strong>
        <span className={`${styles.direction} ${styles[`dir_${series.direction}`] ?? ""}`}>
          {t(DIRECTION[series.direction] ?? series.direction)}
          {series.recent != null && series.earlier != null && <> · {series.earlier} → {series.recent} {series.unit}</>}
        </span>
      </figcaption>
      <svg viewBox={`0 0 ${W} ${H}`} className={styles.trendSvg} role="img"
        aria-label={`${series.label}: ${points.length} readings, latest ${points[last].value} ${series.unit}`}
        onPointerMove={(e) => nearest(e.clientX, e.currentTarget.getBoundingClientRect())}
        onPointerLeave={() => setHover(null)}>
        {ticks.map((t) => <g key={t}>
          <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className={styles.grid} />
          <text x={PAD.left - 6} y={y(t) + 4} textAnchor="end" className={styles.axis}>{t}</text>
        </g>)}
        {series.target != null && <g>
          <line x1={PAD.left} x2={W - PAD.right} y1={y(series.target)} y2={y(series.target)} className={styles.targetLine} />
          <text x={W - PAD.right} y={y(series.target) - 5} textAnchor="end" className={styles.axis}>{t("target")} {series.target}</text>
        </g>}
        <text x={PAD.left} y={H - 6} className={styles.axis}>{format(points[0].at)}</text>
        <text x={W - PAD.right} y={H - 6} textAnchor="end" className={styles.axis}>{format(points[last].at)}</text>
        <path d={path} className={styles.trendLine} />
        {hover != null && <line x1={xs[hover]} x2={xs[hover]} y1={PAD.top} y2={H - PAD.bottom} className={styles.crosshair} />}
        <circle cx={xs[active]} cy={ys[active]} r={5} className={styles.trendDot} />
      </svg>
      <p className={styles.trendReadout} aria-live="polite">
        <strong>{points[active].value} {series.unit}</strong> <span>{format(points[active].at)}{hover == null ? ` · ${t("latest")}` : ""}</span>
        {series.target != null && <span> · {t("{n} of {total} above target", { n: series.outOfTarget, total: points.length })}</span>}
      </p>
      <details className={styles.tableView}>
        <summary>{t("Show as table")}</summary>
        <table>
          <thead><tr><th>{t("Date")}</th><th>{series.unit}</th></tr></thead>
          <tbody>{points.map((p) => <tr key={p.at}><td>{format(p.at)}</td><td>{p.value}</td></tr>)}</tbody>
        </table>
      </details>
    </figure>
  )
}
