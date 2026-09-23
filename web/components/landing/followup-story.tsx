"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, Check, HeartHandshake, MessageCircle } from "lucide-react";
import { useLandingMotion } from "./landing-motion";
import styles from "@/app/landing.module.css";

const days = [
  {
    day: 1,
    title: "A plan to come home with.",
    copy: "The visit is approved. Aminah has her care summary, and her clinic has a follow-up plan.",
    message:
      "Your care summary is ready. Your clinic will check in with you along the way.",
    reply: "It helps to have everything in one place.",
    status: "Summary ready",
    foot: "The doctor-approved plan is the starting point.",
  },
  {
    day: 3,
    title: "A small question opens a door.",
    copy: "A simple check-in gives Aminah a way to tell the clinic what has changed since her appointment.",
    message: "Apa khabar, Aminah? How have you been feeling since your visit?",
    reply: "I have a question about my care plan.",
    status: "Reply received",
    foot: "Questions stay connected to the original visit.",
  },
  {
    day: 7,
    title: "The right context for a real call.",
    copy: "Her reply reaches the follow-up workflow. The clinic can review her context and decide who needs a personal call.",
    message:
      "Thank you for telling us. Your update is ready for your clinic to review.",
    reply: "Thank you. I’d like to talk to someone.",
    status: "Clinic review",
    foot: "The care team decides the next step.",
  },
  {
    day: 14,
    title: "Stay connected between visits.",
    copy: "Another check-in keeps the conversation open. With consent, a family caregiver can see the approved summary too.",
    message:
      "Checking in again. Is there anything you would like your clinic to know?",
    reply: "My family and I have read the summary together.",
    status: "Care stays connected",
    foot: "Family access always follows patient consent.",
  },
  {
    day: 30,
    title: "A fuller picture at the next visit.",
    copy: "The clinic can see the follow-up history alongside the original visit, ready for the next conversation.",
    message: "Your follow-up history is here for your next appointment.",
    reply: "I’m ready to discuss how the month has been.",
    status: "History in one place",
    foot: "Thirty days of context. One continuous story.",
  },
];

export function FollowupStory() {
  const [active, setActive] = useState(0);
  const enabled = useLandingMotion();
  const current = days[active];
  return (
    <section
      className={styles.followupStory}
      id="follow-up"
      aria-labelledby="followup-title"
    >
      <div className={styles.storyIntro}>
        <p className={styles.eyebrow}>
          <span /> Beyond the appointment
        </p>
        <h2 id="followup-title">
          Care is a conversation.
          <br />
          <em>Keep it going.</em>
        </h2>
        <p>
          Follow one fictional patient through 30 days of connected care. Choose
          a day to see what happens next.
        </p>
      </div>
      <div
        className={styles.dayPicker}
        role="group"
        aria-label="Explore a follow-up day"
      >
        <div className={styles.dayTrack} aria-hidden="true">
          <motion.i
            animate={{ width: `${(active / (days.length - 1)) * 100}%` }}
            transition={{ duration: enabled ? 0.5 : 0 }}
          />
        </div>
        {days.map((day, index) => (
          <button
            key={day.day}
            aria-pressed={active === index}
            aria-controls="followup-scene"
            onClick={() => setActive(index)}
            className={index <= active ? styles.dayReached : ""}
          >
            <span>
              {index < active ? (
                <Check size={17} />
              ) : (
                String(day.day).padStart(2, "0")
              )}
            </span>
            <small>Day {day.day}</small>
          </button>
        ))}
      </div>
      <div
        id="followup-scene"
        className={styles.storyGrid}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className={styles.storyCopy}>
          <span className={styles.storyChapter}>
            THE CARE THREAD / {String(active + 1).padStart(2, "0")}
          </span>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={current.day}
              initial={{ opacity: enabled ? 0 : 1, y: enabled ? 12 : 0 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: enabled ? 0 : 1 }}
              transition={{ duration: enabled ? 0.2 : 0 }}
            >
              <h3>{current.title}</h3>
              <p>{current.copy}</p>
            </motion.div>
          </AnimatePresence>
          <button
            className="button-quiet"
            onClick={() => setActive((active + 1) % days.length)}
          >
            {active === days.length - 1
              ? "Start the story again"
              : "Follow the next step"}
            <ArrowRight size={17} />
          </button>
        </div>
        <div className={styles.conversation}>
          <div className={styles.conversationTop}>
            <span>
              <HeartHandshake size={22} />
            </span>
            <div>
              <strong>Aminah’s care thread</strong>
              <small>Illustrative conversation · Day {current.day}</small>
            </div>
            <i aria-hidden="true" />
          </div>
          <div className={styles.conversationMessages}>
            <motion.div
              key={`message-${active}`}
              initial={{ opacity: enabled ? 0 : 1, y: enabled ? 10 : 0 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: enabled ? 0.35 : 0 }}
              className={styles.clinicBubble}
            >
              <small>Khabar · Clinic follow-up</small>
              <p>{current.message}</p>
            </motion.div>
            <motion.div
              key={`reply-${active}`}
              initial={{ opacity: enabled ? 0 : 1, y: enabled ? 10 : 0 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{
                delay: enabled ? 0.12 : 0,
                duration: enabled ? 0.35 : 0,
              }}
              className={styles.replyBubble}
            >
              <p>{current.reply}</p>
              <small>
                Aminah <Check size={12} />
              </small>
            </motion.div>
          </div>
          <div className={styles.storyStatus}>
            <MessageCircle size={16} />
            <span>
              <strong>{current.status}</strong>
              <small>{current.foot}</small>
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
