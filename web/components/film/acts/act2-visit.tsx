"use client";

import { useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import { ShieldAlert } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { PRODUCT_WORDS } from "../product-words";
import styles from "../film.module.css";

// The preview brings the Motion library; loading it only near the reader keeps the first load light
// (with it in the first load, Lighthouse's mobile score fell from 91 to 88).
const ScopedProductPreview = dynamic(
  () => import("../scoped-product-preview").then((m) => m.ScopedProductPreview),
  { ssr: false },
);

const { notes, draft, finding } = PRODUCT_WORDS.visit;
const TIMING: Record<string, string> = {
  after_food: "after food",
  before_food: "before food",
};

/**
 * Act 2: the doctor's notes become a structured draft, and the safety check blocks a slip of one zero. The
 * words are the agents' own output. Reaching the desk plays the reveal once; with motion off everything is
 * shown (the end frame). The existing clinic/patient preview follows.
 */
export function Act2Visit() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [reached, setReached] = useState(false);
  const previewSlot = useRef<HTMLDivElement>(null);
  const [previewNear, setPreviewNear] = useState(false);

  useEffect(() => {
    const slot = previewSlot.current;
    if (!slot) return;
    const watch = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setPreviewNear(true);
          watch.disconnect();
        }
      },
      { rootMargin: "600px 0px" },
    );
    watch.observe(slot);
    return () => watch.disconnect();
  }, []);

  useGSAP(
    () => {
      if (!motion) return;
      ScrollTrigger.create({
        trigger: root.current!.querySelector("[data-visit]"),
        start: "top 70%",
        once: true,
        onEnter: () => setReached(true),
      });
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  const revealed = !motion || reached;

  return (
    <section
      ref={root}
      id="act2"
      data-anchor=""
      data-revealed={revealed}
      className={styles.act2}
      aria-labelledby="act2-title"
    >
      <p className={styles.srOnly}>
        Illustration: the doctor&rsquo;s desk during Aminah&rsquo;s visit, with
        the notes on one side and Khabar&rsquo;s draft on the other.
      </p>
      <Backdrop act="act2" />
      <header className={styles.act2Head}>
        <p className={styles.eyebrow}>The fifteen minutes</p>
        <h2 id="act2-title">
          Notes in, a safe draft out.
          <br />
          <em>The doctor stays in charge.</em>
        </h2>
        <p className={styles.lede}>
          The doctor writes the way they always do. Khabar structures the notes
          and checks every dose, allergy and interaction before the visit can be
          finalised.
        </p>
      </header>
      <div className={styles.visit} data-visit="">
        <figure className={styles.notesCard}>
          <figcaption>The doctor&rsquo;s notes</figcaption>
          <pre>{notes}</pre>
        </figure>
        <figure className={styles.draftCard}>
          <figcaption>Khabar&rsquo;s draft</figcaption>
          <table>
            <thead>
              <tr>
                <th>Medicine</th>
                <th>Strength</th>
                <th>Times a day</th>
                <th>Timing</th>
              </tr>
            </thead>
            <tbody>
              {draft.map((row, i) => (
                <tr
                  key={row.name}
                  data-draft-row=""
                  style={{ transitionDelay: `${0.25 + i * 0.2}s` }}
                >
                  <td>{row.name}</td>
                  <td>
                    {row.strengthMg === null ? "—" : `${row.strengthMg} mg`}
                  </td>
                  <td>{row.timesPerDay ?? "—"}</td>
                  <td>{row.timing ? TIMING[row.timing] : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </figure>
        <div className={styles.stamp} data-stamp="" role="status">
          <ShieldAlert size={22} aria-hidden />
          <div>
            <strong>
              Safety check ·{" "}
              {finding.severity === "CRITICAL" ? "Blocked" : "Warning"}
            </strong>
            <p>{finding.detail}</p>
            <small>
              A slip of one zero. Finalising stays blocked until the doctor
              corrects it or records a reason.
            </small>
          </div>
        </div>
      </div>
      <div
        ref={previewSlot}
        className={styles.act2Preview}
        data-product-preview=""
        data-loaded={previewNear}
      >
        <h3>Explore the app</h3>
        <p>Switch between the clinic and the patient view.</p>
        <div className={styles.previewSlot}>
          {previewNear ? <ScopedProductPreview enabled={motion} /> : null}
        </div>
      </div>
    </section>
  );
}
