"use client";

import {
  createContext,
  useContext,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { MotionConfig, useReducedMotion } from "motion/react";
import { Pause, Play } from "lucide-react";
import styles from "./experience-motion.module.css";

const Context = createContext({
  enabled: true,
  paused: false,
  reduced: false,
  toggle: () => {},
});
const subscribeHydration = () => () => {};
export function ExperienceMotion({ children }: { children: ReactNode }) {
  const [paused, setPaused] = useState(false);
  const hydrated = useSyncExternalStore(
    subscribeHydration,
    () => true,
    () => false,
  );
  const preference = useReducedMotion();
  const reduced = hydrated && !!preference;
  const enabled = !paused && !reduced;
  return (
    <Context.Provider
      value={{ enabled, paused, reduced, toggle: () => setPaused(!paused) }}
    >
      <MotionConfig
        reducedMotion={enabled ? "user" : "always"}
        transition={{ duration: enabled ? 0.4 : 0 }}
      >
        <div className={styles.root} data-motion={enabled ? "on" : "off"}>
          {children}
        </div>
      </MotionConfig>
    </Context.Provider>
  );
}
export const useExperienceMotion = () => useContext(Context);
export function MotionButton() {
  const { enabled, reduced, toggle } = useExperienceMotion();
  return (
    <button
      className={styles.toggle}
      onClick={toggle}
      disabled={reduced}
      aria-pressed={!enabled}
      aria-label={
        reduced
          ? "Reduced motion enabled by your device"
          : enabled
            ? "Pause animations"
            : "Resume animations"
      }
    >
      {enabled ? <Pause size={14} /> : <Play size={14} />}
      <span>
        {reduced ? "Reduced motion" : enabled ? "Motion on" : "Motion paused"}
      </span>
    </button>
  );
}
