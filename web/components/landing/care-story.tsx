"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useInView } from "motion/react";
import {
  ArrowUpRight,
  Check,
  HeartPulse,
  MessageCircle,
  Pause,
  Play,
  Stethoscope,
} from "lucide-react";
import { useLandingMotion } from "./landing-motion";
import styles from "./care-story.module.css";

const chapters = [
  {
    label: "A little check-in",
    title: "Care starts with a conversation.",
    message: "Apa khabar, Aminah? How have you been feeling since your visit?",
    reply: "I have a question about my care plan.",
    status: "A patient shares an update",
    detail: "A simple reply keeps the conversation going.",
  },
  {
    label: "The thread connects",
    title: "The important details travel together.",
    message: "Your update is on its way to your clinic.",
    reply: "Thank you. It helps to know who to ask.",
    status: "Context, ready for the clinic",
    detail: "The reply joins the patient’s existing care record.",
  },
  {
    label: "A human follows up",
    title: "A familiar team. A clearer next step.",
    message: "Your clinic can review your question with your care plan.",
    reply: "My care continues, even at home.",
    status: "Ready for a human review",
    detail: "The clinic decides what follow-up is needed.",
  },
];

export function CareStory() {
  const [chapter, setChapter] = useState(0);
  const [playing, setPlaying] = useState(true);
  const container = useRef<HTMLDivElement>(null);
  const inView = useInView(container, { amount: 0.25 });
  const allowed = useLandingMotion();
  const enabled = allowed;
  useEffect(() => {
    if (!enabled || !playing || !inView) return;
    const timer = window.setInterval(() => {
      if (!document.hidden)
        setChapter((current) => (current + 1) % chapters.length);
    }, 6500);
    return () => window.clearInterval(timer);
  }, [enabled, playing, inView]);
  const current = chapters[chapter];
  return (
    <div ref={container} className={styles.scene}>
      <div className={styles.sceneTop}>
        <span>
          <HeartPulse size={16} /> A connected care story
        </span>
        <small>Illustrative demo</small>
      </div>
      <div className={styles.orbit} aria-hidden="true">
        <i />
        <i />
        <i />
      </div>
      <div className={styles.people} aria-hidden="true">
        <div className={styles.person}>
          <div className={styles.portrait}>
            A<span>♡</span>
          </div>
          <span>Aminah · at home</span>
        </div>
        <div className={styles.connection}>
          <motion.div
            animate={
              enabled
                ? { scaleX: [0, 1], opacity: [0.4, 1] }
                : { scaleX: 1, opacity: 1 }
            }
            key={chapter}
            transition={{ duration: enabled ? 1.2 : 0 }}
          />
          <span>
            <HeartPulse size={25} />
          </span>
        </div>
        <div className={styles.person}>
          <div className={styles.clinician}>
            <Stethoscope size={37} />
          </div>
          <span>Her clinic team</span>
        </div>
      </div>
      <div className={styles.storyCards}>
        <div className={styles.phone}>
          <div className={styles.phoneTop}>
            <span>
              <MessageCircle size={17} />
            </span>
            <div>
              <strong>Khabar</strong>
              <small>Your clinic, a little closer</small>
            </div>
            <i />
          </div>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={chapter}
              className={styles.messages}
              initial={{ opacity: enabled ? 0 : 1, y: enabled ? 12 : 0 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: enabled ? 0 : 1 }}
              transition={{ duration: enabled ? 0.3 : 0 }}
            >
              <p>{current.message}</p>
              <p>
                {current.reply}
                <Check size={13} />
              </p>
            </motion.div>
          </AnimatePresence>
          <span className={styles.phoneFoot}>
            <span /> A conversation that carries on
          </span>
        </div>
        <motion.div
          className={styles.clinicCard}
          key={`clinic-${chapter}`}
          initial={false}
          animate={
            enabled
              ? { y: [12, 0], rotate: [-2, 0], opacity: [0.65, 1] }
              : { y: 0, rotate: 0, opacity: 1 }
          }
          transition={{ duration: enabled ? 0.65 : 0 }}
        >
          <span className={styles.cardIcon}>
            <Stethoscope size={20} />
          </span>
          <small>CLINIC WORKSPACE</small>
          <strong>{current.status}</strong>
          <p>{current.detail}</p>
          <span className={styles.cardFooter}>
            <span>AN</span>Aminah’s care thread
            <ArrowUpRight size={17} />
          </span>
        </motion.div>
      </div>
      <div className={styles.caption}>
        <span>0{chapter + 1} / 03</span>
        <p>{current.title}</p>
      </div>
      <div
        className={styles.controls}
        role="group"
        aria-label="Explore the care story"
      >
        {chapters.map((item, index) => (
          <button
            key={item.label}
            onClick={() => {
              setChapter(index);
              setPlaying(false);
            }}
            aria-pressed={chapter === index}
          >
            <span>0{index + 1}</span>
            {item.label}
          </button>
        ))}
        <button
          className={styles.play}
          onClick={() => setPlaying(!playing)}
          disabled={!enabled}
          aria-label={
            playing && enabled ? "Pause care story" : "Play care story"
          }
        >
          {playing && enabled ? <Pause size={16} /> : <Play size={16} />}
        </button>
      </div>
    </div>
  );
}
