"use client";

import { useRef, useState } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread, type ThreadState } from "../thread";
import { Bubble, Phone } from "../characters/phone";
import {
  CHECK_IN,
  FEELING_BETTER,
  MEDICINE_TAKEN,
  REPLY_TEXT,
  THANK_YOU,
  answer,
  type Lang,
  type Reply,
} from "../messages";
import { duration, ease, media, SCRUB } from "../tokens";
import styles from "../film.module.css";

type Day = {
  day: number;
  reply: Record<Lang, string>;
  state: ThreadState;
  caption: string;
};

// Fictional. Captions never promise a call: a reply goes onto the clinic's follow-up list.
const DAYS: Day[] = [
  {
    day: 1,
    reply: MEDICINE_TAKEN,
    state: "ok",
    caption: "The first check-in arrives in the language she thinks in.",
  },
  {
    day: 3,
    reply: REPLY_TEXT.ok,
    state: "ok",
    caption: "A two-word reply is enough. The thread stays green.",
  },
  {
    day: 7,
    reply: REPLY_TEXT.dizzy,
    state: "watch",
    caption:
      "“Pening” turns the thread amber, and her reply goes onto the clinic’s follow-up list.",
  },
  {
    day: 14,
    reply: FEELING_BETTER,
    state: "ok",
    caption:
      "A week later she is feeling better. Every reply stays on her record.",
  },
  {
    day: 30,
    reply: THANK_YOU,
    state: "ok",
    caption: "Thirty days, one thread. Her follow-up closes on her record.",
  },
];

const CHOICES: {
  id: Reply;
  label: string;
  state: ThreadState;
  outcome: string;
}[] = [
  {
    id: "ok",
    label: "I'm okay",
    state: "ok",
    outcome:
      "Khabar thanks her. Nothing else is needed, so the thread stays green.",
  },
  {
    id: "dizzy",
    label: "A bit dizzy",
    state: "watch",
    outcome:
      "Khabar tells her the clinic may not have seen it yet and gives the 999 advice. Her reply goes onto the clinic’s follow-up list, and the thread turns amber.",
  },
  {
    id: "chest",
    label: "Chest pain",
    state: "red",
    outcome:
      "Chest pain gets the 999 advice straight away. Her reply goes to the top of the clinic’s follow-up list, and the thread turns red.",
  },
];

export function Act3ThirtyDays() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
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
            autoAlpha: 0,
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
        autoAlpha: 0,
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
        <div ref={track} className={styles.act3Track}>
          {DAYS.map((d) => (
            <article
              key={d.day}
              className={styles.day}
              data-day={d.day}
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
              <p className={styles.dayCaption}>{d.caption}</p>
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

      <div ref={lab} className={styles.lab} data-lab="">
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
              pulse
            />
          </div>
        </div>
      </div>
    </section>
  );
}
