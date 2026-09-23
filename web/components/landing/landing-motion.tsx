"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import {
  MotionConfig,
  motion,
  useReducedMotion,
  useScroll,
} from "motion/react";
import { Pause, Play } from "lucide-react";
import styles from "@/app/landing.module.css";

const MotionContext = createContext(true);
export const useLandingMotion = () => useContext(MotionContext);

export function LandingMotion({ children }: { children: ReactNode }) {
  const reduced = useReducedMotion();
  const [paused, setPaused] = useState(false);
  const enabled = !reduced && !paused;
  const { scrollYProgress } = useScroll();
  return (
    <MotionContext.Provider value={enabled}>
      <MotionConfig
        reducedMotion={enabled ? "user" : "always"}
        transition={{ duration: enabled ? 0.35 : 0 }}
      >
        <div className={styles.motionRoot} data-paused={!enabled}>
          <motion.div
            className={styles.readingProgress}
            style={{ scaleX: scrollYProgress }}
            aria-hidden="true"
          />
          {children}
          <button
            className={styles.motionToggle}
            onClick={() => setPaused(!paused)}
            disabled={!!reduced}
            aria-pressed={!enabled}
            aria-label={
              reduced
                ? "Reduced motion enabled by your device"
                : paused
                  ? "Resume animations"
                  : "Pause animations"
            }
          >
            {enabled ? <Pause size={13} /> : <Play size={13} />}
            <span>
              {reduced
                ? "Reduced motion"
                : paused
                  ? "Motion paused"
                  : "Pause motion"}
            </span>
          </button>
        </div>
      </MotionConfig>
    </MotionContext.Provider>
  );
}

export function Reveal({ children }: { children: ReactNode }) {
  const enabled = useLandingMotion();
  return (
    <motion.div
      initial={false}
      whileInView={
        enabled ? { y: [24, 0], opacity: [0.45, 1] } : { y: 0, opacity: 1 }
      }
      viewport={{ once: true, amount: 0.12 }}
      transition={{ duration: enabled ? 0.7 : 0 }}
    >
      {children}
    </motion.div>
  );
}

export function CareOrbit() {
  return (
    <div className={styles.careOrbit} aria-hidden="true">
      <svg viewBox="0 0 900 740" fill="none">
        <ellipse cx="450" cy="370" rx="405" ry="290" />
        <ellipse
          cx="450"
          cy="370"
          rx="335"
          ry="350"
          transform="rotate(-28 450 370)"
        />
        <path d="M0 470C150 470 140 180 330 220S570 650 900 260" />
      </svg>
      <span className={styles.orbitDot} />
      <span className={styles.orbitDotTwo} />
    </div>
  );
}
