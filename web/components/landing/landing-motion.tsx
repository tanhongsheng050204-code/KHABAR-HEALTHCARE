"use client";

import { createContext, useContext, type ReactNode } from "react";
import { MotionConfig } from "motion/react";
import styles from "@/app/landing.module.css";

const MotionContext = createContext(true);
export const useLandingMotion = () => useContext(MotionContext);

/** Lets a host page (the film) switch the landing components' motion on or off with its own setting. */
export function LandingMotionScope({
  enabled,
  children,
}: {
  enabled: boolean;
  children: ReactNode;
}) {
  return (
    <MotionContext.Provider value={enabled}>
      <MotionConfig reducedMotion={enabled ? "user" : "always"}>
        {/* The landing stylesheet pauses its CSS animations inside a paused motion root. */}
        <div className={styles.motionRoot} data-paused={!enabled}>
          {children}
        </div>
      </MotionConfig>
    </MotionContext.Provider>
  );
}
