"use client";

import { useEffect, useRef, useState } from "react";
import { Check, HeartPulse, RotateCcw } from "lucide-react";
import styles from "./site.module.css";

/** The four moments the replay steps through, in order. */
const steps = [
  { label: "Morning list", hint: "The clinic opens Khabar" },
  { label: "A reply comes in", hint: "Aminah answers day 7" },
  { label: "Someone owns it", hint: "Dr Priya takes the case" },
  { label: "Call and record", hint: "Reached, noted, closed" },
] as const;

const STEP_MS = 2800;

/**
 * A replay of the clinic workspace with the fictional demo clinic: the same screens and wording as
 * /home, reduced to the moment a check-in reply reaches the clinic. Plays once when it scrolls into
 * view (not at all under reduced motion); the stepper and Replay button work either way.
 */
export function ProductReplay() {
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = root.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        observer.disconnect();
        // Under reduced motion, show the finished story at once instead of playing it.
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) setStep(steps.length - 1);
        else setPlaying(true);
      },
      { threshold: 0.35 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const advancing = playing && step < steps.length - 1;
  useEffect(() => {
    if (!advancing) return;
    const timer = window.setTimeout(() => setStep((current) => current + 1), STEP_MS);
    return () => window.clearTimeout(timer);
  }, [advancing, step]);

  function choose(next: number) {
    setPlaying(false);
    setStep(next);
  }

  function replay() {
    setStep(0);
    setPlaying(true);
  }

  const replied = step >= 1;
  const owned = step >= 2;
  const done = step >= 3;

  return (
    <div className={styles.replay} ref={root}>
      <div
        className={styles.frame}
        role="img"
        aria-label={`Khabar clinic workspace, step ${step + 1} of ${steps.length}: ${steps[step].hint}.`}
      >
        <div className={styles.frameRail} aria-hidden="true">
          <div className={styles.frameClinic}>
            <i>
              <HeartPulse size={14} />
            </i>
            Klinik Dr Priya
          </div>
          <span data-active="">
            Overview {replied && !done && <b>1</b>}
          </span>
          <span>Patients</span>
          <span>Schedule</span>
          <span>Staff &amp; access</span>
        </div>

        <div className={styles.frameMain} aria-hidden="true">
          <h3>Good morning, Dr Priya.</h3>
          <div className={styles.metrics}>
            <div>
              <small>Call now</small>
              <strong data-urgent="">2</strong>
            </div>
            <div>
              <small>Check today</small>
              <strong>{replied && !done ? 2 : 1}</strong>
            </div>
            <div>
              <small>Appointments</small>
              <strong>4</strong>
            </div>
            <div>
              <small>In follow-up</small>
              <strong>6</strong>
            </div>
          </div>
          <div className={styles.patientCard}>
            <header>
              <strong>Aminah N., 64</strong>
              <span>Type 2 diabetes · day 7 of 30 · replies in BM</span>
            </header>
            <ol className={styles.days}>
              {[1, 3, 7, 14, 30].map((day) => (
                <li
                  key={day}
                  data-state={
                    day < 7 ? "ok" : day === 7 && replied ? (done ? "ok" : "watch") : undefined
                  }
                >
                  <i>{(day < 7 || (day === 7 && done)) && <Check size={11} strokeWidth={3} />}</i>
                  Day {day}
                </li>
              ))}
            </ol>
            <p>
              {done
                ? "Reached by phone. Dizziness after a missed meal; plan unchanged. The note is on her record."
                : replied
                  ? "Her day 7 reply needs a closer look. It is in the clinic's review list."
                  : "Days 1 and 3 answered. Next check-in goes out this morning, in Bahasa Melayu."}
            </p>
          </div>
          <div className={styles.patientCard}>
            <header>
              <strong>Today</strong>
              <span>4 appointments</span>
            </header>
            <div>
              {[
                ["09:30", "Ravi K.", "Blood pressure review"],
                ["11:00", "Siti R.", "New patient intake"],
                ["14:30", "Lim T.", "Diabetes follow-up"],
              ].map(([time, name, why]) => (
                <div className={styles.mockRow} key={time}>
                  {time} · {name} <span>{why}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className={styles.frameSide} aria-hidden="true">
          <p>Who needs you first</p>
          <div
            className={styles.task}
            data-enter={replied ? undefined : ""}
            data-highlight={replied && !done ? "" : undefined}
          >
            <small>Check-in reply · day 7</small>
            <strong>Aminah N.</strong>
            <q>Pening sikit lepas makan ubat pagi tadi.</q>
            <footer>
              <span>{owned ? "Assigned to Dr Priya" : "Unassigned"}</span>
              {done ? (
                <span className={`${styles.chip} ${styles.chipQuiet}`}>
                  <Check size={12} /> Reached
                </span>
              ) : (
                <span className={styles.chip}>{owned ? "Reached" : "Assign to me"}</span>
              )}
            </footer>
          </div>
          <div className={styles.task}>
            <small>Home reading</small>
            <strong>Ravi K.</strong>
            <footer>
              <span>Blood pressure 158/96</span>
              <span className={`${styles.chip} ${styles.chipQuiet}`}>Review</span>
            </footer>
          </div>
          <div className={styles.task}>
            <small>No reply · day 3</small>
            <strong>Lim T.</strong>
            <footer>
              <span>Waiting since yesterday</span>
              <span className={`${styles.chip} ${styles.chipQuiet}`}>Call</span>
            </footer>
          </div>
          <p className={styles.sideNote}>
            {done
              ? "Recorded on the case history. Nothing was sent to Aminah without the clinic."
              : "Khabar sorts the list. People decide what happens next."}
          </p>
        </div>
      </div>

      <div className={styles.stepper} role="group" aria-label="Replay steps">
        {steps.map((item, index) => (
          <button
            key={item.label}
            type="button"
            aria-pressed={step === index}
            onClick={() => choose(index)}
          >
            <b>
              {index + 1}. {item.label}
            </b>
            {item.hint}
          </button>
        ))}
      </div>
      <p className={styles.replayCaption}>
        A replay of the clinic&apos;s own screens, with the fictional demo clinic.
        <button type="button" onClick={replay}>
          <RotateCcw size={14} /> Replay
        </button>
      </p>
    </div>
  );
}
