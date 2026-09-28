"use client";

import { useRef } from "react";
import Link from "next/link";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { ArrowRight } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread } from "../thread";
import { Aminah } from "../characters/aminah";
import { Bubble } from "../characters/phone";
import { GREETING, LANGS, htmlLang } from "../messages";
import { ease, media, SCRUB } from "../tokens";
import styles from "../film.module.css";

/**
 * The thread leaves the clinic door and runs along the street, in the hero's 1440×900 frame.
 * Tune these points to the act0 background once the AI art is in (placeholder: the door at about 36%, 71%, the street along the bottom tenth).
 */
const HERO_THREAD =
  "M 520 640 C 600 760, 700 812, 860 816 S 1200 812, 1480 818";
/** The same thread for a phone's tall frame (390×844): from beside Aminah, bottom left, along the ground. */
const HERO_THREAD_TALL =
  "M 30 700 C 60 790, 150 812, 240 812 S 360 806, 420 810";

export function Act0Hero() {
  const { motion, lang, setLang } = useFilm();
  const root = useRef<HTMLElement>(null);
  const spacer = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      if (!motion) return;
      const mm = gsap.matchMedia();
      mm.add({ desktop: media.desktop, mobile: media.mobile }, (context) => {
        const { mobile } = context.conditions as { mobile: boolean };
        const tl = gsap.timeline({
          // Every move spans the whole pin (duration 1), like a camera on rails.
          defaults: { ease: ease.scrub, duration: 1 },
          scrollTrigger: {
            trigger: root.current,
            start: "top top",
            end: mobile ? "+=60%" : "+=110%",
            scrub: SCRUB,
            pin: true,
            // Our own spacer: ScrollTrigger would otherwise wrap (move) the section, and Chrome re-counts moved
            // text as a new, late Largest Contentful Paint.
            pinSpacer: spacer.current,
          },
        });
        tl.to("[data-layer='backdrop']", { scale: 1, yPercent: -3 }, 0)
          .to("[data-layer='aminah']", { xPercent: mobile ? 45 : 120 }, 0)
          .to(
            "[data-part='body']",
            {
              y: -5,
              repeat: 7,
              yoyo: true,
              ease: "sine.inOut",
              duration: 1 / 8,
            },
            0,
          )
          // autoRound off: GSAP rounds px values by default, which would snap this 0–1 dash offset to 0 or 1.
          .to(
            "[data-thread='hero'] [data-draw]",
            { strokeDashoffset: 0, autoRound: false },
            0,
          )
          .to(
            "[data-layer='copy']",
            { yPercent: -10, opacity: 0.4, duration: 0.45 },
            0.55,
          );
      });
      return () => mm.revert();
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <div ref={spacer} data-pin-for="act0">
      <section
        ref={root}
        id="act0"
        className={styles.act0}
        aria-labelledby="act0-title"
      >
        <p className={styles.srOnly}>
          Illustration: Aminah walks out of her clinic one morning, and a thread
          of light follows her home.
        </p>
        <Backdrop act="act0" priority />
        <Thread
          name="hero"
          shape="wide"
          d={HERO_THREAD}
          viewBox="0 0 1440 900"
          state="ok"
        />
        <Thread
          name="hero"
          shape="tall"
          d={HERO_THREAD_TALL}
          viewBox="0 0 390 844"
          state="ok"
        />
        <div className={styles.act0Aminah} data-layer="aminah">
          <Bubble from="khabar" lang={lang} className={styles.act0Greeting}>
            {GREETING[lang]}
          </Bubble>
          <Aminah />
        </div>
        <div className={styles.act0Copy} data-layer="copy">
          <p className={styles.eyebrow}>Clinic-led continuity</p>
          <h1 id="act0-title">
            The visit ends.
            <br />
            <em>Care should not.</em>
          </h1>
          <p className={styles.lede}>
            Khabar carries the important details from first question to
            recovery—so patients feel clear, families stay informed, and
            clinicians see who needs them next.
          </p>
          <div
            className={styles.langs}
            role="group"
            aria-label="Khabar speaks her language"
          >
            {LANGS.map((l) => (
              <button
                key={l.id}
                type="button"
                lang={htmlLang(l.id)}
                aria-pressed={lang === l.id}
                onClick={() => setLang(l.id)}
              >
                {l.label}
              </button>
            ))}
          </div>
          <div className={styles.actions}>
            <Link className="button-primary" href="/login">
              Find your care space <ArrowRight size={17} aria-hidden />
            </Link>
            <a className="button-quiet" href="#act3">
              Follow Aminah home
            </a>
          </div>
        </div>
        <p className={styles.scrollCue} aria-hidden="true">
          <i /> Scroll to follow Aminah
        </p>
      </section>
    </div>
  );
}
