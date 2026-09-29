"use client";

import { useRef, useState } from "react";
import gsap from "gsap";
import { useGSAP } from "@gsap/react";
import { LockKeyhole } from "lucide-react";
import { useFilm } from "../film-provider";
import { Backdrop } from "../backdrop";
import { Thread } from "../thread";
import { Phone } from "../characters/phone";
import { Nurul } from "../characters/nurul";
import { htmlLang } from "../messages";
import { PRODUCT_WORDS } from "../product-words";
import { ease } from "../tokens";
import styles from "../film.module.css";

/**
 * Act 4: the thread reaches Nurul in KL. What she sees depends on Aminah's consent: the approved care plan
 * (a caregiver with summary access sees the approved summary), or nothing once Aminah stops sharing.
 */
export function Act4Daughter() {
  const { motion, lang } = useFilm();
  const root = useRef<HTMLElement>(null);
  const [shared, setShared] = useState(true);

  useGSAP(
    () => {
      if (!motion) return;
      gsap.fromTo(
        "[data-thread='to-nurul'] [data-draw]",
        { strokeDashoffset: 1 },
        {
          strokeDashoffset: 0,
          autoRound: false,
          ease: ease.scrub,
          scrollTrigger: {
            trigger: root.current,
            start: "top 75%",
            end: "top 20%",
            scrub: 0.6,
          },
        },
      );
    },
    { scope: root, dependencies: [motion], revertOnUpdate: true },
  );

  return (
    <section
      ref={root}
      id="act4"
      data-anchor=""
      className={styles.act4}
      aria-labelledby="act4-title"
    >
      <Backdrop act="act4" />
      <Thread
        name="to-nurul"
        d="M 0 60 C 300 10, 700 110, 1000 60"
        viewBox="0 0 1000 120"
        state="ok"
        fit="cover"
      />
      <header className={styles.act4Head}>
        <p className={styles.eyebrow}>Her daughter in Kuala Lumpur</p>
        <h2 id="act4-title">
          Family sees what she
          <br />
          <em>chooses to share.</em>
        </h2>
      </header>
      <div className={styles.kl}>
        <div className={styles.nurulFigure}>
          <Nurul />
        </div>
        <div data-nurul-phone="">
          <Phone label="Nurul’s phone">
            {shared ? (
              <div className={styles.plan} lang={htmlLang(lang)}>
                <strong lang="en">Mak&rsquo;s care plan</strong>
                {PRODUCT_WORDS.rx.map((line) => (
                  <p key={line.shorthand}>
                    {line.medicine}: {line.how[lang]}
                  </p>
                ))}
              </div>
            ) : (
              <div className={styles.locked}>
                <LockKeyhole size={22} aria-hidden />
                <p>This care plan is no longer shared with you.</p>
              </div>
            )}
          </Phone>
        </div>
        <div className={styles.consent}>
          <button
            type="button"
            role="switch"
            aria-checked={shared}
            className={styles.switch}
            onClick={() => setShared((was) => !was)}
          >
            <span className={styles.switchTrack} aria-hidden="true">
              <span />
            </span>
            Aminah shares her care plan with Nurul
          </button>
          <p data-consent-note="" aria-live="polite">
            {shared
              ? "Nurul sees the care plan Aminah’s doctor approved. Aminah can stop sharing at any time."
              : "Aminah stopped sharing. Nurul can no longer open her care plan."}
          </p>
        </div>
      </div>
    </section>
  );
}
