"use client";

import { useRef, useState, type CSSProperties } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { useInView } from "../use-in-view";
import { useTilt } from "../use-tilt";
import { htmlLang } from "../messages";
import { CLINIC, HOMES, type Home, type TownStatus } from "../town";
import { SCRUB, ease } from "../tokens";
import styles from "../film.module.css";

/** What each reply led to, in Act 3's words. Never a promised call. */
const STATUS: Record<TownStatus, { label: string; detail: string }> = {
  ok: {
    label: "Doing well",
    detail: "Replied with nothing of concern, and Khabar thanked them.",
  },
  watch: {
    label: "On the follow-up list",
    detail:
      "Khabar said the clinic may not have seen it yet and gave the 999 advice. The reply went onto the clinic’s follow-up list.",
  },
  red: {
    label: "Emergency advice given",
    detail:
      "Khabar gave the 999 advice at once and said the clinic may not have seen it yet. The reply went to the top of the clinic’s follow-up list.",
  },
};
const ORDER: TownStatus[] = ["ok", "watch", "red"];
const ROOFS = ["#f4d9b8", "#e9c9a6", "#f7e6cf", "#e3cfb5"];
const CENTRE = CLINIC.x + CLINIC.w / 2;

/** One curved thread from the clinic to a home's doorstep (the prototype's curve). */
function threadPath(home: Home, i: number) {
  const x = home.x + 20;
  const y = home.y + 20;
  const bend = i % 2 ? 40 : -40;
  return `M ${CENTRE} ${CENTRE} Q ${(CENTRE + x) / 2 + bend} ${(CENTRE + y) / 2} ${x} ${y}`;
}

const box = (
  <>
    <i className={styles.faceTop} />
    <i className={styles.faceFront} />
    <i className={styles.faceSide} />
  </>
);

/**
 * Act 5: one clinic and every home it cares for, as a small CSS 3D town. Each thread is one patient's
 * follow-up, coloured by their latest reply; selecting a home shows that reply and what Khabar did. The
 * server render is the end frame (every home built, every thread drawn); scrolling in raises the homes.
 */
export function Act5Town() {
  const { motion } = useFilm();
  const root = useRef<HTMLElement>(null);
  const inView = useInView(root);
  const [selected, setSelected] = useState<number | null>(null);
  const chosen = HOMES.find((h) => h.id === selected);
  const stage = useRef<HTMLDivElement>(null);
  const world = useRef<HTMLDivElement>(null);
  const { canTilt, tilting, refused, toggleDeviceTilt, turn } = useTilt(
    stage,
    world,
  );

  useGSAP(
    () => {
      if (!motion) return;
      gsap
        .timeline({
          defaults: { duration: 1, ease: ease.scrub },
          scrollTrigger: {
            trigger: root.current!.querySelector("[data-town]"),
            start: "top 85%",
            end: "center 55%",
            scrub: SCRUB,
          },
        })
        .fromTo(
          "[data-clinic], [data-house]",
          { z: -90 },
          { z: 0, stagger: 0.08 },
          0,
        )
        .fromTo(
          "[data-town-thread]",
          { strokeDashoffset: 1 },
          { strokeDashoffset: 0, autoRound: false, stagger: 0.05 },
          0.5,
        )
        .fromTo("[data-ping]", { opacity: 0 }, { opacity: 1 }, ">");
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act5"
      data-anchor=""
      data-inview={inView}
      className={styles.act5}
      aria-labelledby="act5-title"
    >
      <p className={styles.srOnly}>
        Illustration: a small town seen from above, with a thread from the
        clinic to each of twelve homes, coloured by how each patient is doing.
      </p>
      <div className={styles.act5Head}>
        <p className={styles.eyebrow}>The whole town</p>
        <h2 id="act5-title">
          One clinic.
          <br />
          <em>Every home it cares for.</em>
        </h2>
        <p className={styles.lede}>
          Each thread is one patient&rsquo;s follow-up, coloured by their latest
          reply.
        </p>
        <ul className={styles.townKey} data-town-key="">
          {ORDER.map((status) => (
            <li key={status} data-state={status}>
              {STATUS[status].label} ·{" "}
              {HOMES.filter((h) => h.status === status).length}
            </li>
          ))}
        </ul>
        <p className={styles.illustrative}>
          Illustrative: fictional homes, names and replies.
        </p>
      </div>
      <div ref={stage} className={styles.townStage} data-town="">
        <div
          ref={world}
          className={styles.world}
          data-world=""
          role="group"
          aria-label="The town: twelve fictional homes"
        >
          <div className={styles.board} aria-hidden="true" />
          <div
            className={styles.road}
            style={{ left: 0, top: 227, width: 480, height: 22 }}
            aria-hidden="true"
          />
          <div
            className={styles.road}
            style={{ left: 227, top: 0, width: 22, height: 480 }}
            aria-hidden="true"
          />
          <svg
            className={styles.townLinks}
            viewBox="0 0 480 480"
            aria-hidden="true"
          >
            {HOMES.map((h, i) => (
              <path
                key={h.id}
                data-town-thread=""
                data-state={h.status}
                className={styles.townThread}
                pathLength={1}
                d={threadPath(h, i)}
              />
            ))}
          </svg>
          <div
            className={styles.clinic}
            data-clinic=""
            style={
              {
                left: CLINIC.x,
                top: CLINIC.y,
                "--h": `${CLINIC.h}px`,
              } as CSSProperties
            }
            aria-hidden="true"
          >
            {box}
          </div>
          {HOMES.map((h, i) => (
            <button
              key={h.id}
              type="button"
              className={styles.house}
              data-house={h.id}
              data-state={h.status}
              style={
                {
                  left: h.x,
                  top: h.y,
                  "--h": `${h.h}px`,
                  "--top": ROOFS[i % ROOFS.length],
                } as CSSProperties
              }
              aria-pressed={selected === h.id}
              aria-label={`${h.name}’s home`}
              onClick={() => setSelected(h.id)}
            >
              {box}
            </button>
          ))}
          {HOMES.filter((h) => h.status !== "ok").map((h) => (
            <span
              key={h.id}
              className={styles.ping}
              data-ping=""
              data-state={h.status}
              style={
                {
                  left: h.x + 20,
                  top: h.y + 20,
                  "--z": `${h.h + 34}px`,
                } as CSSProperties
              }
              aria-hidden="true"
            >
              {h.status === "red" ? "!" : ""}
            </span>
          ))}
        </div>
      </div>
      {/* After the town, so on a phone the reply a reader taps for shows up just below it. */}
      <div className={styles.townAside}>
        <div
          className={styles.homeStatus}
          data-home-status=""
          aria-live="polite"
        >
          {chosen ? (
            <>
              <strong>
                {chosen.name} · {STATUS[chosen.status].label}
              </strong>
              <p>
                Replied <q lang={htmlLang(chosen.lang)}>{chosen.reply}</q>
              </p>
              <p>{STATUS[chosen.status].detail}</p>
            </>
          ) : (
            <p>Select a home to see its latest reply.</p>
          )}
        </div>
        <p className={styles.townHint}>
          {canTilt
            ? "Drag sideways to turn the town, or tilt your phone."
            : "Drag to turn the town."}
        </p>
        <div
          className={styles.turnButtons}
          role="group"
          aria-label="Turn the town"
        >
          <button type="button" onClick={() => turn(-0.5)}>
            Turn left
          </button>
          <button type="button" onClick={() => turn(0.5)}>
            Turn right
          </button>
        </div>
        {canTilt ? (
          <>
            <button
              type="button"
              className={styles.tiltButton}
              aria-pressed={tilting}
              onClick={toggleDeviceTilt}
            >
              Tilt to explore
            </button>
            <p className={styles.townHint} aria-live="polite">
              {refused ? "Tilt is off. Drag the town to turn it instead." : ""}
            </p>
          </>
        ) : null}
      </div>
    </section>
  );
}
