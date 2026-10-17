"use client";

import { useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import styles from "./site.module.css";

type Moment = {
  time: string;
  title: string;
  who: string;
  body: string;
  mock: ReactNode;
};

const moments: Moment[] = [
  {
    time: "08:00",
    title: "Read the plan in her own words",
    who: "Aminah, at home",
    body: "The doctor's plan arrives on Telegram in plain Bahasa Melayu. Khabar rewrites only what the doctor approved; it never adds advice of its own.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Khabar</strong>
          <span>Telegram · 08:00</span>
        </header>
        <div className={styles.bubble}>
          Selamat pagi Mak Cik Aminah. Pelan doktor anda:
          <br />• Metformin 500 mg: 1 biji, pagi dan malam, selepas makan.
          <br />• Datang semula dalam 2 minggu.
        </div>
        <div className={`${styles.bubble} ${styles.bubbleMine}`}>Baik, terima kasih.</div>
      </div>
    ),
  },
  {
    time: "10:00",
    title: "Show the clinic everything she takes",
    who: "Aminah, with her daughter Nurul",
    body: "A photo of the medicine packet from her GP goes into her “everything I take” list, next to the klinik kesihatan's medicines and the bitter-gourd juice her sister recommended.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Everything I take</strong>
          <span>3 items</span>
        </header>
        <div className={styles.mockRow}>
          Metformin 500 mg <span>Klinik kesihatan</span>
        </div>
        <div className={styles.mockRow}>
          Glucophage 500 mg <span>From packet photo · GP</span>
        </div>
        <div className={styles.mockRow}>
          Jus peria (bitter gourd) <span>Added by Aminah</span>
        </div>
      </div>
    ),
  },
  {
    time: "11:30",
    title: "Catch the duplicate before it hurts",
    who: "Dr Priya, during the visit",
    body: "The safety check knows Glucophage is metformin. A critical finding cannot be signed off without a written reason, and that rule holds in the screen, the API and the database.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Safety check</strong>
          <span>Draft report</span>
        </header>
        <div className={styles.flag}>
          <strong>Duplicate medicine</strong>
          Metformin (klinik kesihatan) and Glucophage (GP) are the same drug. Combined with bitter gourd,
          low blood sugar is more likely.
        </div>
        <div className={styles.mockRow}>
          Finalise report <span>Needs a written reason</span>
        </div>
      </div>
    ),
  },
  {
    time: "14:00",
    title: "Answer a check-in, in any of four languages",
    who: "Aminah, day 7",
    body: "Check-ins go out on days 1, 3, 7, 14 and 30. A reply that needs a closer look goes into the clinic's review list, with precautionary 999 wording if it sounds urgent.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Khabar</strong>
          <span>Day 7 check-in</span>
        </header>
        <div className={styles.bubble}>Apa khabar hari ini, Mak Cik? Ada rasa pening atau lemah?</div>
        <div className={`${styles.bubble} ${styles.bubbleMine}`}>Pening sikit lepas makan ubat pagi tadi.</div>
        <div className={styles.bubble}>
          Terima kasih. Klinik anda akan menyemak jawapan ini. Jika rasa sangat teruk, hubungi 999.
        </div>
      </div>
    ),
  },
  {
    time: "16:00",
    title: "Call the right person first",
    who: "Dr Priya's clinic",
    body: "The clinic's call list puts urgent replies and readings at the top. Someone takes the case, calls, and records what happened, so nothing depends on memory.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Who needs you first</strong>
          <span>5 open</span>
        </header>
        <div className={styles.mockRow}>
          Aminah N. · dizzy after medicine <span>Assigned to Dr Priya</span>
        </div>
        <div className={styles.mockRow}>
          Ravi K. · BP 158/96 <span>Unassigned</span>
        </div>
        <div className={styles.mockRow}>
          Lim T. · no reply, day 3 <span>Overdue</span>
        </div>
      </div>
    ),
  },
  {
    time: "20:00",
    title: "Keep family in the loop, with consent",
    who: "Nurul, her daughter",
    body: "Nurul sees her mother's plan and appointments because Aminah invited her, and Aminah can take that access back. Every view of the record is logged where Aminah can see it.",
    mock: (
      <div className={styles.mock}>
        <header>
          <strong>Who viewed my record</strong>
          <span>Today</span>
        </header>
        <div className={styles.mockRow}>
          Nurul (caregiver) <span>Read plan · 20:04</span>
        </div>
        <div className={styles.mockRow}>
          Dr Priya <span>Case note · 16:12</span>
        </div>
        <div className={styles.mockRow}>
          Dr Priya <span>Draft report · 11:34</span>
        </div>
      </div>
    ),
  },
];

/** "One day with Aminah": a tab list of moments, each with a small mock of the screen involved. */
export function DayTimeline() {
  const [active, setActive] = useState(0);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const moment = moments[active];

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const keys: Record<string, number> = {
      ArrowDown: 1,
      ArrowRight: 1,
      ArrowUp: -1,
      ArrowLeft: -1,
    };
    let next: number | undefined;
    if (event.key in keys) next = (active + keys[event.key] + moments.length) % moments.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = moments.length - 1;
    if (next === undefined) return;
    event.preventDefault();
    setActive(next);
    tabs.current[next]?.focus();
  }

  return (
    <div className={styles.day}>
      <div
        className={styles.dayTabs}
        role="tablist"
        aria-label="One day with Aminah"
        aria-orientation="vertical"
        onKeyDown={onKeyDown}
      >
        {moments.map((item, index) => (
          <button
            key={item.time}
            ref={(node) => {
              tabs.current[index] = node;
            }}
            type="button"
            role="tab"
            id={`day-tab-${index}`}
            aria-selected={active === index}
            aria-controls="day-panel"
            tabIndex={active === index ? 0 : -1}
            onClick={() => setActive(index)}
          >
            <b>{item.time}</b>
            {item.title}
          </button>
        ))}
      </div>
      <div
        className={styles.dayPanel}
        role="tabpanel"
        id="day-panel"
        aria-labelledby={`day-tab-${active}`}
      >
        <div>
          <span className={styles.dayWho}>
            {moment.time} · {moment.who}
          </span>
          <h3 className={styles.h3}>{moment.title}</h3>
          <p>{moment.body}</p>
        </div>
        {moment.mock}
      </div>
    </div>
  );
}
