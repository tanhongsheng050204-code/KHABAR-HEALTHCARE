"use client";

import { useRef } from "react";
import Link from "next/link";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { ArrowRight } from "lucide-react";
import { useFilm } from "../film-provider";
import { useInView } from "../use-in-view";
import { Backdrop } from "../backdrop";
import { HOMES } from "../town";
import { SCRUB, ease } from "../tokens";
import styles from "../film.module.css";

const W = 1440;
const H = 900;
const GROUND = H - 60;
/**
 * Each home in the town sends its thread up from its roof to its own light. The lights are spread with a
 * fixed shuffle (7 and 5 share no factor with 12 and 6), so none overlap. Every thread is the same soft
 * green: a red thread rising into the sky could read as a death, so status colours stay in Act 5.
 */
const SKY = HOMES.map((home, i) => {
  const x = 90 + i * ((W - 180) / (HOMES.length - 1));
  const roof = GROUND - 44 - (home.h - 26);
  const sx = 120 + ((i * 7) % 12) * ((W - 240) / 11);
  const sy = 80 + ((i * 5) % 6) * 34;
  return {
    id: home.id,
    x,
    roof,
    sx,
    sy,
    d: `M ${x} ${roof} C ${x} ${roof - 240}, ${sx} ${sy + 260}, ${sx} ${sy}`,
  };
});

/**
 * Act 7: night over the town. Every home's thread rises and joins a sky of lights, and the reader is
 * invited into the live prototype. The server render is the end frame: every thread risen, every light lit.
 */
export function Act7Finale() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const inView = useInView(root);

  useGSAP(
    () => {
      if (!motion) return;
      gsap
        .timeline({
          defaults: { duration: 1, ease: ease.scrub },
          scrollTrigger: {
            trigger: root.current,
            start: "top 70%",
            end: "bottom bottom",
            scrub: SCRUB,
          },
        })
        .fromTo(
          "[data-sky-thread]",
          { strokeDashoffset: 1 },
          { strokeDashoffset: 0, autoRound: false, stagger: 0.04 },
          0,
        )
        .fromTo(
          "[data-star]",
          { opacity: 0 },
          { opacity: 1, stagger: 0.04 },
          0.6,
        );
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act7"
      data-anchor=""
      data-inview={inView}
      className={styles.act7}
      aria-labelledby="act7-title"
    >
      <p className={styles.srOnly}>
        Illustration: the town at night from above, every home&rsquo;s thread
        rising into a sky of lights.
      </p>
      <Backdrop act="act7" />
      <svg
        className={styles.sky}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="xMidYMax slice"
        aria-hidden="true"
      >
        {SKY.map(({ id, d }) => (
          <path
            key={id}
            className={styles.skyThread}
            data-sky-thread=""
            pathLength={1}
            d={d}
          />
        ))}
        {SKY.map(({ id, sx, sy }) => (
          <circle
            key={id}
            className={styles.star}
            data-star=""
            cx={sx}
            cy={sy}
            r={4}
          />
        ))}
        <g className={styles.skyHome}>
          {SKY.map(({ id, x, roof }) => (
            <path
              key={id}
              d={`M ${x - 26} ${GROUND} V ${roof + 20} L ${x} ${roof} L ${x + 26} ${roof + 20} V ${GROUND} Z`}
            />
          ))}
          <rect x={0} y={GROUND} width={W} height={H - GROUND} />
        </g>
        {SKY.map(({ id, x, roof }) => (
          <rect
            key={id}
            className={styles.skyWindow}
            x={x - 5}
            y={roof + 28}
            width={10}
            height={10}
            rx={2}
          />
        ))}
      </svg>
      <div className={styles.act7Copy}>
        <p className={styles.eyebrow}>Care that carries on</p>
        <h2 id="act7-title">
          See the whole story,
          <br />
          <em>not just the appointment.</em>
        </h2>
        <div className={styles.act7Actions}>
          <Link className="button-primary" href="/login">
            Enter the live prototype <ArrowRight size={17} />
          </Link>
          <a className={styles.backToStory} href="#act3">
            Explore the 30-day story
          </a>
        </div>
      </div>
    </section>
  );
}
