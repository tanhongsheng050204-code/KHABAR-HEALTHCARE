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

/**
 * Each home in the town sends its thread up from its roof to its own light. The lights are spread with a
 * fixed shuffle (7 and 5 share no factor with 12 and 6), so none overlap. Every thread is the same soft
 * green: a red thread rising into the sky could read as a death, so status colours stay in Act 5. Desktops
 * get a wide sky and phones a tall one, so every home and its thread stays on screen.
 */
function sky(W: number, H: number, half: number, rise: number) {
  const ground = H - 60;
  const margin = half + 8;
  return {
    W,
    H,
    ground,
    half,
    homes: HOMES.map((home, i) => {
      const x = margin + i * ((W - 2 * margin) / (HOMES.length - 1));
      const roof = ground - 44 - (home.h - 26);
      const sx = margin + 20 + ((i * 7) % 12) * ((W - 2 * margin - 40) / 11);
      const sy = 70 + ((i * 5) % 6) * (H / 26);
      return {
        id: home.id,
        x,
        roof,
        sx,
        sy,
        d: `M ${x} ${roof} C ${x} ${roof - rise}, ${sx} ${sy + rise}, ${sx} ${sy}`,
      };
    }),
  };
}
const SKIES = {
  wide: sky(1440, 900, 26, 240),
  tall: sky(400, 900, 12, 200),
} as const;

function Sky({ shape }: { shape: keyof typeof SKIES }) {
  const { W, H, ground, half, homes } = SKIES[shape];
  return (
    <svg
      className={styles.sky}
      data-shape={shape}
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
    >
      {homes.map(({ id, d }) => (
        <path
          key={id}
          className={styles.skyThread}
          data-sky-thread=""
          pathLength={1}
          d={d}
        />
      ))}
      {homes.map(({ id, sx, sy }) => (
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
        {homes.map(({ id, x, roof }) => (
          <path
            key={id}
            data-sky-home=""
            d={`M ${x - half} ${ground} V ${roof + 20} L ${x} ${roof} L ${x + half} ${roof + 20} V ${ground} Z`}
          />
        ))}
        <rect x={0} y={ground} width={W} height={H - ground} />
      </g>
      {homes.map(({ id, x, roof }) => (
        <rect
          key={id}
          className={styles.skyWindow}
          x={x - half / 3}
          y={roof + 28}
          width={(half * 2) / 3}
          height={(half * 2) / 3}
          rx={2}
        />
      ))}
    </svg>
  );
}

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
        Illustration: a row of homes at night, each one&rsquo;s thread rising
        into a sky of lights.
      </p>
      <Backdrop act="act7" />
      <Sky shape="wide" />
      <Sky shape="tall" />
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
