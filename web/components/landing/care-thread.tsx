"use client";

import { useRef } from "react";
import { motion, useScroll } from "motion/react";
import {
  CalendarCheck,
  ClipboardCheck,
  HeartHandshake,
  Check,
  ArrowUpRight,
} from "lucide-react";
import { useLandingMotion } from "./landing-motion";
import styles from "@/app/landing.module.css";

const moments = [
  {
    number: "01",
    icon: CalendarCheck,
    title: "Before the visit",
    heading: "Arrive with the story already started.",
    copy: "The patient books, answers a calm guided intake, and shares medicines from every source. The clinic sees the important context before the room door opens.",
    proof: "Identity removed before AI assistance",
    side: "A little preparation. A more personal visit.",
    steps: [
      "Appointment booked",
      "Medicines shared",
      "Pre-visit context ready",
    ],
  },
  {
    number: "02",
    icon: ClipboardCheck,
    title: "During the visit",
    heading: "Turn clinical shorthand into something safe and clear.",
    copy: "The doctor speaks or types notes. Khabar structures the draft, checks medicines and allergies, and keeps critical concerns visible until they are resolved.",
    proof: "Clinician reviews and finalises every plan",
    side: "A second look, with the doctor in control.",
    steps: [
      "Notes structured",
      "Safety concerns surfaced",
      "Doctor reviews the plan",
    ],
  },
  {
    number: "03",
    icon: HeartHandshake,
    title: "After the visit",
    heading: "Know who needs a person, before they disappear.",
    copy: "Patients receive a plain-language summary and check-ins. Replies and home readings are prioritised so the clinic can call the right person first.",
    proof: "Escalates to people—never diagnoses",
    side: "The conversation carries on at home.",
    steps: [
      "Care summary ready",
      "Check-ins planned",
      "Replies reach the clinic",
    ],
  },
];

export function CareThread() {
  const ref = useRef<HTMLElement>(null);
  const enabled = useLandingMotion();
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start 75%", "end 55%"],
  });
  return (
    <section className={styles.journey} id="journey" ref={ref}>
      <div className={styles.journeyHeader}>
        <p>Three moments. One continuous picture.</p>
        <h2>
          Nothing important gets
          <br />
          left at the clinic door.
        </h2>
      </div>
      <div className={styles.moments}>
        <div className={styles.threadTrack} aria-hidden="true">
          <motion.i
            style={{
              scaleY: enabled ? scrollYProgress : 1,
              transformOrigin: "top",
            }}
          />
        </div>
        {moments.map((moment, index) => {
          const Icon = moment.icon;
          return (
            <motion.article
              key={moment.number}
              className={styles.moment}
              initial={false}
              whileInView={
                enabled
                  ? { opacity: [0.5, 1], y: [28, 0] }
                  : { opacity: 1, y: 0 }
              }
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: enabled ? 0.65 : 0 }}
            >
              <div className={styles.momentNumber}>{moment.number}</div>
              <div className={styles.momentCopy}>
                <p>
                  <Icon size={18} /> {moment.title}
                </p>
                <h3>{moment.heading}</h3>
                <span>{moment.copy}</span>
                <small>
                  <i /> {moment.proof}
                </small>
              </div>
              <div
                className={`${styles.momentScene} ${styles[`scene${index + 1}`]}`}
              >
                <div className={styles.sceneIcon}>
                  <Icon size={23} />
                </div>
                <p>{moment.side}</p>
                <div className={styles.sceneSteps}>
                  {moment.steps.map((step, stepIndex) => (
                    <motion.span
                      key={step}
                      initial={false}
                      whileInView={
                        enabled
                          ? { opacity: [0.25, 1], x: [10, 0] }
                          : { opacity: 1, x: 0 }
                      }
                      viewport={{ once: true }}
                      transition={{
                        duration: enabled ? 0.45 : 0,
                        delay: enabled ? stepIndex * 0.12 : 0,
                      }}
                    >
                      <Check size={13} />
                      {step}
                    </motion.span>
                  ))}
                </div>
              </div>
            </motion.article>
          );
        })}
      </div>
      <a className={styles.journeyContinue} href="#follow-up">
        And after the appointment? Follow Aminah’s story{" "}
        <ArrowUpRight size={17} />
      </a>
    </section>
  );
}
