"use client";

import { useRef, useState } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { useInView } from "../use-in-view";
import { Backdrop } from "../backdrop";
import { Thread, type ThreadState } from "../thread";
import { Bubble, Phone } from "../characters/phone";
import {
  CHECK_IN,
  DAY_REPLIES,
  REPLY_STATE,
  REPLY_TEXT,
  answer,
  htmlLang,
  type Reply,
} from "../messages";
import { duration, ease, media, SCRUB } from "../tokens";
import styles from "../film.module.css";

// Fictional. Captions never promise a call: a reply goes onto the clinic's follow-up list. Replies and the
// thread colour for each day come from DAY_REPLIES, which a test checks against the triage word lists.
const CAPTIONS: Record<number, string> = {
  1: "The first check-in arrives in the language she thinks in.",
  3: "A two-word reply is enough. The thread stays green.",
  14: "A week later she is feeling better. Every reply stays on her record.",
  30: "Thirty days, one thread. Her follow-up closes on her record.",
};
const DAYS = DAY_REPLIES.map((d) => ({ ...d, caption: CAPTIONS[d.day] }));

const CHOICES: {
  id: Reply;
  label: string;
  state: ThreadState;
  outcome: string;
}[] = [
  {
    id: "ok",
    label: "I'm okay",
    state: REPLY_STATE.ok,
    outcome:
      "Khabar thanks her. Nothing else is needed, so the thread stays green.",
  },
  {
    id: "dizzy",
    label: "A bit dizzy",
    state: REPLY_STATE.dizzy,
    outcome:
      "Khabar tells her the clinic may not have seen it yet and gives the 999 advice. Her reply goes onto the clinic’s follow-up list, and the thread turns amber.",
  },
  {
    id: "chest",
    label: "Chest pain",
    state: REPLY_STATE.chest,
    outcome:
      "Chest pain gets the 999 advice straight away. Her reply goes to the top of the clinic’s follow-up list, and the thread turns red.",
  },
];

export function Act3ThirtyDays() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  const inView = useInView(root);
  const stage = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const lab = useRef<HTMLDivElement>(null);
  const [choice, setChoice] = useState<Reply | null>(null);
  const picked = CHOICES.find((c) => c.id === choice) ?? null;

  useGSAP(
    () => {
      if (!motion) return;
      const mm = gsap.matchMedia();
      mm.add(media.desktop, () => {
        const distance = () =>
          track.current!.scrollWidth - stage.current!.clientWidth;
        gsap
          .timeline({
            defaults: { ease: ease.scrub, duration: 1 },
            scrollTrigger: {
              id: "thirty-days",
              trigger: stage.current,
              start: "top top",
              end: () => `+=${distance() * 1.15}`,
              scrub: SCRUB,
              pin: true,
              invalidateOnRefresh: true,
              anticipatePin: 1,
            },
          })
          .to(track.current, { x: () => -distance() }, 0)
          .to("[data-layer='dusk']", { opacity: 1 }, 0)
          .to("[data-layer='backdrop']", { xPercent: -4 }, 0);
      });
      mm.add(media.mobile, () => {
        gsap.utils.toArray<HTMLElement>("article[data-day]").forEach((card) => {
          gsap.from(card, {
            y: 40,
            opacity: 0, // opacity only: autoAlpha would set visibility:hidden and hide the card from screen readers
            duration: duration.enter,
            ease: ease.enter,
            scrollTrigger: { trigger: card, start: "top 85%", once: true },
          });
        });
      });
      return () => mm.revert();
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  useGSAP(
    () => {
      if (!motion || !choice) return;
      gsap.from("[data-new]", {
        scale: 0.7,
        opacity: 0,
        duration: duration.pop,
        ease: ease.pop,
        stagger: 0.18,
        transformOrigin: "bottom left",
      });
    },
    { scope: lab, dependencies: [choice, lang, motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act3"
      data-inview={inView}
      className={styles.act3}
      data-mode={motion ? "animated" : "static"}
      aria-labelledby="act3-title"
    >
      <div ref={stage} className={styles.act3Stage}>
        <p className={styles.srOnly}>
          Illustration: the same window at home from morning to dusk, while
          Aminah&rsquo;s phone receives five check-ins.
        </p>
        <Backdrop act="act3" />
        <div className={styles.act3Dusk} data-layer="dusk" aria-hidden="true" />
        <header className={styles.act3Head}>
          <p className={styles.eyebrow}>The thirty days after the visit</p>
          <h2 id="act3-title">
            Care is a conversation.
            <br />
            <em>Keep it going.</em>
          </h2>
        </header>
        <div ref={track} className={styles.act3Track} data-pan-track="">
          {DAYS.map((d) => (
            <article
              key={d.day}
              className={styles.day}
              data-day={d.day}
              data-anchor=""
              aria-labelledby={`day-${d.day}`}
            >
              <h3 id={`day-${d.day}`}>Day {d.day}</h3>
              <Phone label={`Aminah’s phone on day ${d.day}`}>
                <Bubble from="khabar" lang={lang}>
                  {CHECK_IN[lang]}
                </Bubble>
                <Bubble from="aminah" lang={lang}>
                  {d.reply[lang]}
                </Bubble>
              </Phone>
              <p className={styles.dayCaption}>
                {d.day === 7 ? (
                  <>
                    “<span lang={htmlLang(lang)}>{d.reply[lang]}</span>” turns
                    the thread amber, and her reply goes onto the clinic’s
                    follow-up list.
                  </>
                ) : (
                  d.caption
                )}
              </p>
              <div className={styles.dayThread}>
                <Thread
                  name={`day-${d.day}`}
                  d="M 0 20 L 100 20"
                  viewBox="0 0 100 40"
                  state={d.state}
                  pulse={d.state !== "ok"}
                />
              </div>
            </article>
          ))}
        </div>
      </div>

      <div ref={lab} className={styles.lab} data-lab="" data-anchor="">
        <div className={styles.labCopy}>
          <h3 id="lab-title">Reply for Aminah</h3>
          <p>
            Choose what she says. You will see exactly what Khabar sends back,
            and where her reply goes.
          </p>
          <div
            className={styles.choices}
            role="group"
            aria-labelledby="lab-title"
          >
            {CHOICES.map((c) => (
              <button
                key={c.id}
                type="button"
                data-state={c.state}
                aria-pressed={choice === c.id}
                onClick={() => setChoice(c.id)}
              >
                {c.label}
              </button>
            ))}
          </div>
          <p
            className={styles.outcome}
            data-outcome=""
            data-state={picked?.state ?? "ok"}
            aria-live="polite"
          >
            {picked ? picked.outcome : "Choose a reply to see what happens."}
          </p>
        </div>
        <div className={styles.labPhone}>
          <Phone label="Aminah’s phone">
            <Bubble from="khabar" lang={lang}>
              {CHECK_IN[lang]}
            </Bubble>
            {picked ? (
              <>
                <Bubble
                  key={`r-${picked.id}-${lang}`}
                  from="aminah"
                  lang={lang}
                  isNew
                >
                  {REPLY_TEXT[picked.id][lang]}
                </Bubble>
                <Bubble
                  key={`a-${picked.id}-${lang}`}
                  from="khabar"
                  lang={lang}
                  tone={picked.state}
                  isNew
                >
                  {answer(picked.id, lang)}
                </Bubble>
              </>
            ) : null}
          </Phone>
          <div className={styles.labThread}>
            <Thread
              name="lab"
              d="M 0 20 C 30 0, 70 40, 100 20"
              viewBox="0 0 100 40"
              state={picked?.state ?? "ok"}
              pulse={(picked?.state ?? "ok") !== "ok"}
            />
          </div>
        </div>
      </div>
    </section>
  );
}
