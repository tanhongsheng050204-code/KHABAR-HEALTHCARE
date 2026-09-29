"use client";

import { useRef, useState } from "react";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { Check, LockKeyhole, ShieldCheck } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import styles from "../film.module.css";

/** The current landing page's three safety principles, word for word (a test compares them with "/"). */
const PRINCIPLES = [
  {
    icon: ShieldCheck,
    title: "Human decisions stay human",
    text: "Urgent replies and safety concerns go to a clinician. Khabar never presents itself as a diagnosis.",
  },
  {
    icon: LockKeyhole,
    title: "Privacy is part of the workflow",
    text: "Your registered name, IC and phone number are removed before AI-assisted intake and triage. Record access is logged and visible.",
  },
  {
    icon: Check,
    title: "Safety has a hard stop",
    text: "Critical findings block finalisation until the clinician records a clear reason to proceed.",
  },
];

/**
 * Act 6: a quiet night, and the three promises the product keeps. Each principle arrives as the reader
 * reaches it; the server render (and reduced motion, and Pause) shows all three.
 */
export function Act6Trust() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [reached, setReached] = useState<boolean[]>(() =>
    PRINCIPLES.map(() => false),
  );

  useGSAP(
    () => {
      if (!motion) return;
      root
        .current!.querySelectorAll<HTMLElement>("[data-principle]")
        .forEach((el, i) => {
          ScrollTrigger.create({
            trigger: el,
            start: "top 85%",
            once: true,
            onEnter: () =>
              setReached((was) => was.map((r, j) => (j === i ? true : r))),
          });
        });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act6"
      data-anchor=""
      className={styles.act6}
      aria-labelledby="act6-title"
    >
      <p className={styles.srOnly}>
        Illustration: a quiet kampung road at night, the town asleep.
      </p>
      <Backdrop act="act6" />
      <header className={styles.act6Head}>
        <p className={styles.eyebrow}>Designed for trust</p>
        <h2 id="act6-title">
          AI supports the care team.
          <br />
          <em>It does not replace one.</em>
        </h2>
      </header>
      <div className={styles.principles}>
        {PRINCIPLES.map(({ icon: Icon, title, text }, i) => (
          <article
            key={title}
            className={styles.principle}
            data-principle=""
            data-shown={!motion || reached[i]}
          >
            <span>{String(i + 1).padStart(2, "0")}</span>
            <Icon size={21} aria-hidden />
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
