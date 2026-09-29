"use client";

import { useRef, useState } from "react";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { htmlLang } from "../messages";
import { PRODUCT_WORDS } from "../product-words";
import styles from "../film.module.css";

const LINES = PRODUCT_WORDS.rx;

/**
 * Act 1: the prescription as the doctor writes it, melting into the words Khabar sends her. Each line
 * reveals itself when it scrolls into view; a line the reader has clicked keeps the reader's choice. The
 * server render (and reduced motion) is the end frame: every line shown in her words, shorthand above.
 */
export function Act1Paper() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  // null: not yet decided by the reader, so the scroll reveal may set it.
  const [chosen, setChosen] = useState<(boolean | null)[]>(() =>
    LINES.map(() => null),
  );
  const [reached, setReached] = useState<boolean[]>(() =>
    LINES.map(() => false),
  );

  useGSAP(
    () => {
      if (!motion) return;
      root
        .current!.querySelectorAll<HTMLElement>("[data-rx-line]")
        .forEach((el, i) => {
          ScrollTrigger.create({
            trigger: el,
            start: "top 80%",
            once: true,
            onEnter: () =>
              setReached((was) => was.map((r, j) => (j === i ? true : r))),
          });
        });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  const shown = (i: number) => chosen[i] ?? (!motion || reached[i]);

  return (
    <section
      ref={root}
      id="act1"
      data-anchor=""
      className={styles.act1}
      aria-labelledby="act1-title"
    >
      <p className={styles.srOnly}>
        Illustration: Aminah&rsquo;s prescription on the clinic&rsquo;s paper,
        each line in the doctor&rsquo;s shorthand.
      </p>
      <Backdrop act="act1" />
      <header className={styles.act1Head}>
        <p className={styles.eyebrow}>At home, after the visit</p>
        <h2 id="act1-title">
          Instructions written for clinicians.
          <br />
          <em>Patients who think in BM, 中文 or தமிழ்.</em>
        </h2>
        <p className={styles.lede}>
          Khabar turns each prescription line into her own language from fixed,
          reviewed phrases, never a chatbot&rsquo;s guess, so the dose she reads
          is the dose her doctor wrote.
        </p>
      </header>
      <div
        className={styles.paper}
        role="group"
        aria-label="Aminah’s prescription"
      >
        {LINES.map((line, i) => (
          <button
            key={line.shorthand}
            type="button"
            className={styles.rxLine}
            data-rx-line={i}
            aria-pressed={shown(i)}
            onClick={() =>
              setChosen((was) => was.map((c, j) => (j === i ? !shown(i) : c)))
            }
          >
            <code className={styles.rxShort}>{line.shorthand}</code>
            <span className={styles.rxPlain} lang={htmlLang(lang)}>
              {line.medicine}: {line.how[lang]}
            </span>
          </button>
        ))}
      </div>
      <p className={styles.rxHint}>
        Tap a line to switch between the doctor&rsquo;s shorthand and her words.
      </p>
    </section>
  );
}
