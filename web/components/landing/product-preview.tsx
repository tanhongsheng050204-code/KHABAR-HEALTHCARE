"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  Activity,
  ArrowUpRight,
  BellRing,
  CalendarDays,
  Check,
  HeartPulse,
  MessageCircle,
  ShieldCheck,
} from "lucide-react";
import { useLandingMotion } from "./landing-motion";
import styles from "@/app/landing.module.css";

const people = [
  {
    initials: "AN",
    name: "Aminah N.",
    note: "A reply needs a closer look",
    tone: "red",
    detail:
      "Aminah has sent a new recovery update. Her clinic can review the reply alongside her approved care plan.",
    action: "Open recovery update",
  },
  {
    initials: "RK",
    name: "Ravi K.",
    note: "New home reading received",
    tone: "amber",
    detail:
      "Ravi’s home reading is ready for the clinic to review with his previous readings and visit notes.",
    action: "Review reading history",
  },
  {
    initials: "LT",
    name: "Lim T.",
    note: "Waiting for a check-in reply",
    tone: "blue",
    detail:
      "Lim hasn’t replied to the latest check-in. The clinic can decide whether a personal call would help.",
    action: "Review check-in history",
  },
];

export function ProductPreview() {
  const enabled = useLandingMotion();
  const [view, setView] = useState<"clinic" | "patient">("clinic");
  const [selected, setSelected] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  function changeView(next: typeof view) {
    setView(next);
    setSelected(null);
    setNotice("");
  }
  return (
    <div className={styles.previewExperience}>
      <div
        className={styles.previewSwitcher}
        role="group"
        aria-label="Choose a product preview"
      >
        <span>Take a look inside</span>
        {(["clinic", "patient"] as const).map((role) => (
          <button
            key={role}
            onClick={() => changeView(role)}
            aria-pressed={view === role}
          >
            {view === role && (
              <motion.i
                layoutId="preview-selection"
                transition={{ duration: enabled ? 0.3 : 0 }}
              />
            )}
            <span>{role === "clinic" ? "For clinics" : "For patients"}</span>
          </button>
        ))}
      </div>
      <div className={styles.productFrame}>
        <div className={styles.frameTop}>
          <div className={styles.windowDots} aria-hidden="true">
            <i />
            <i />
            <i />
          </div>
          <span>Explore Khabar</span>
          <div className={styles.framePulse}>
            <i /> Sample workspace
          </div>
        </div>
        <div className={styles.previewBody}>
          <aside className={styles.previewRail} aria-label="Preview views">
            <span className={styles.miniMark}>
              <HeartPulse size={16} />
            </span>
            <button
              className={view === "clinic" ? styles.activeIcon : ""}
              onClick={() => changeView("clinic")}
              aria-label="Show clinic view"
              aria-pressed={view === "clinic"}
            >
              <Activity size={17} />
            </button>
            <button
              className={view === "patient" ? styles.activeIcon : ""}
              onClick={() => changeView("patient")}
              aria-label="Show patient view"
              aria-pressed={view === "patient"}
            >
              <MessageCircle size={17} />
            </button>
          </aside>
          <div className={styles.previewContent}>
            <div className={styles.previewHeading}>
              <div>
                <small>
                  {view === "clinic"
                    ? "Today · Clinic view"
                    : "Day 4 · Patient view"}
                </small>
                <strong>
                  {view === "clinic"
                    ? "Good morning, Dr. Lee."
                    : "Good morning, Aminah."}
                </strong>
              </div>
              <button
                aria-label="Show sample notifications"
                aria-expanded={!!notice}
                onClick={() =>
                  setNotice(
                    notice
                      ? ""
                      : "Two fictional patient updates are ready for review. In Khabar, the clinic sees them in its call list.",
                  )
                }
              >
                <BellRing size={16} />
                <i />
              </button>
            </div>
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={view}
                initial={{ opacity: enabled ? 0 : 1, y: enabled ? 10 : 0 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: enabled ? 0 : 1 }}
                transition={{ duration: enabled ? 0.22 : 0 }}
              >
                {view === "clinic" ? (
                  <>
                    <div className={styles.previewMetrics}>
                      <div>
                        <span className={styles.redDot} />
                        Needs a call<strong>2</strong>
                      </div>
                      <div>
                        <span className={styles.amberDot} />
                        For review<strong>5</strong>
                      </div>
                      <div>
                        <CalendarDays size={14} />
                        Visits today<strong>8</strong>
                      </div>
                    </div>
                    <div className={styles.previewList}>
                      <div className={styles.listTitle}>
                        <span>Who needs you first</span>
                        <small>Select a patient ↓</small>
                      </div>
                      {people.map((person, index) => (
                        <button
                          key={person.initials}
                          className={styles.previewPerson}
                          aria-expanded={selected === index}
                          aria-controls="sample-patient-detail"
                          onClick={() =>
                            setSelected(selected === index ? null : index)
                          }
                        >
                          <span className={styles.avatar}>
                            {person.initials}
                          </span>
                          <span className={styles.personText}>
                            <strong>{person.name}</strong>
                            <small>{person.note}</small>
                          </span>
                          <i className={styles[person.tone]} />
                          <ArrowUpRight size={15} />
                        </button>
                      ))}
                    </div>
                    <div
                      id="sample-patient-detail"
                      className={styles.previewDetail}
                      aria-live="polite"
                    >
                      {selected === null ? (
                        <>
                          <ShieldCheck size={15} />
                          <p>A little context. A more personal follow-up.</p>
                        </>
                      ) : (
                        <>
                          <MessageCircle size={15} />
                          <p>
                            <strong>{people[selected].action}</strong>
                            {people[selected].detail}
                          </p>
                        </>
                      )}
                    </div>
                  </>
                ) : (
                  <>
                    <div className={styles.careSummary}>
                      <span>
                        <Check size={15} /> Your clinic-approved plan
                      </span>
                      <h3>
                        Your next step,
                        <br />
                        in words that make sense.
                      </h3>
                      <p>
                        Your medicines, appointment, and a way to stay in touch.
                        Together in one place.
                      </p>
                    </div>
                    <div className={styles.nextStep}>
                      <CalendarDays size={18} />
                      <div>
                        <small>Next appointment</small>
                        <strong>Thursday, 10:30 AM</strong>
                      </div>
                      <ArrowUpRight size={17} />
                    </div>
                    <div className={styles.patientActions}>
                      <button
                        onClick={() =>
                          setNotice(
                            "In the patient workspace, you can record a home reading for your clinic to review. This preview doesn’t save any health data.",
                          )
                        }
                      >
                        Record a reading
                      </button>
                      <button
                        onClick={() =>
                          setNotice(
                            "In Khabar, recovery updates go to your clinic’s follow-up workflow. This is a fictional preview; no message has been sent.",
                          )
                        }
                      >
                        Ask the clinic
                      </button>
                    </div>
                  </>
                )}
              </motion.div>
            </AnimatePresence>
            {notice && (
              <div className={styles.previewNotice} role="status">
                <p>{notice}</p>
                <button
                  onClick={() => setNotice("")}
                  aria-label="Dismiss preview message"
                >
                  ×
                </button>
              </div>
            )}
          </div>
        </div>
        <div className={styles.previewFoot}>
          <span>
            <i /> Fictional data · Interactive preview
          </span>
          <span>Try a different view ↗</span>
        </div>
      </div>
      <div className={styles.floatingNote}>
        <span>
          <Check size={16} />
        </span>
        <div>
          <strong>Every next step, connected.</strong>
          <small>From the clinic to everyday life</small>
        </div>
      </div>
    </div>
  );
}
