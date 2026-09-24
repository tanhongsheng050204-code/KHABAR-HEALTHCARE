import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowRight,
  Check,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  UsersRound,
} from "lucide-react";
import { Brand } from "@/components/brand";
import { CareThread } from "@/components/landing/care-thread";
import { ProductPreview } from "@/components/landing/product-preview";
import { CareStory } from "@/components/landing/care-story";
import {
  LandingMotion,
  CareOrbit,
  Reveal,
} from "@/components/landing/landing-motion";
import { FollowupStory } from "@/components/landing/followup-story";
import styles from "./landing.module.css";

export const metadata: Metadata = { title: "Care that carries on" };

export default function LandingPage() {
  return (
    <LandingMotion>
      <main className={styles.page} id="main-content">
        <a className={styles.skipLink} href="#journey">
          Skip to the care journey
        </a>
        <header className={styles.header}>
          <Brand />
          <nav aria-label="Main navigation">
            <a href="#journey">How it works</a>
            <a href="#preview">Explore the app</a>
            <a href="#safety">Safety</a>
            <Link className="button-secondary" href="/login">
              Sign in
            </Link>
          </nav>
        </header>
        <section className={styles.hero}>
          <div className={styles.heroGlow} aria-hidden="true" />
          <CareOrbit />
          <div className={styles.heroCopy}>
            <p className={styles.eyebrow}>
              <span /> Clinic-led continuity
            </p>
            <h1>
              The visit ends.
              <br />
              <em>Care should not.</em>
            </h1>
            <p className={styles.lede}>
              Khabar carries the important details from first question to
              recovery—so patients feel clear, families stay informed, and
              clinicians see who needs them next.
            </p>
            <div className={styles.heroActions}>
              <Link className="button-primary" href="/login">
                Find your care space <ArrowRight size={17} />
              </Link>
              <a className="button-quiet" href="#journey">
                Follow the care journey
              </a>
            </div>
            <div className={styles.trustLine}>
              <span>
                <ShieldCheck size={16} /> Clinician in control
              </span>
              <span>
                <LockKeyhole size={16} /> Consent-led access
              </span>
              <span>
                <UsersRound size={16} /> Built around people
              </span>
            </div>
          </div>
          <div className={styles.previewWrap}>
            <CareStory />
          </div>
        </section>
        <section
          className={styles.transition}
          aria-label="Khabar in one sentence"
        >
          <span>One calm thread</span>
          <p>
            From the question before the appointment to the check-in after it.
          </p>
        </section>
        <section className={styles.productTour} id="preview">
          <Reveal>
            <div className={styles.tourHeading}>
              <p className={styles.eyebrow}>
                <span /> One story. Two perspectives.
              </p>
              <h2>
                A clearer day.
                <br />
                <em>On both sides of care.</em>
              </h2>
              <p>
                Switch between the clinic and patient experience. Select a card
                to see how the details connect.
              </p>
            </div>
            <ProductPreview />
          </Reveal>
        </section>
        <CareThread />
        <Reveal>
          <FollowupStory />
        </Reveal>
        <section className={styles.safety} id="safety">
          <Reveal>
            <div className={styles.safetyIntro}>
              <p className={styles.eyebrow}>
                <span /> Designed for trust
              </p>
              <h2>
                AI supports the care team.
                <br />
                It does not replace one.
              </h2>
            </div>
            <div className={styles.safetyRules}>
              <article>
                <span>01</span>
                <div>
                  <ShieldCheck size={21} />
                  <h3>Human decisions stay human</h3>
                  <p>
                    Urgent replies and safety concerns go to a clinician. Khabar
                    never presents itself as a diagnosis.
                  </p>
                </div>
              </article>
              <article>
                <span>02</span>
                <div>
                  <LockKeyhole size={21} />
                  <h3>Privacy is part of the workflow</h3>
                  <p>
                    Identity is removed before AI-assisted intake and triage.
                    Record access is logged and visible.
                  </p>
                </div>
              </article>
              <article>
                <span>03</span>
                <div>
                  <Check size={21} />
                  <h3>Safety has a hard stop</h3>
                  <p>
                    Critical findings block finalisation until the clinician
                    records a clear reason to proceed.
                  </p>
                </div>
              </article>
            </div>
          </Reveal>
        </section>
        <Reveal>
          <section className={styles.finalCta}>
            <div className={styles.ctaOrb} aria-hidden="true">
              <Sparkles size={28} />
            </div>
            <p>Care that carries on</p>
            <h2>
              See the whole story,
              <br />
              not just the appointment.
            </h2>
            <Link className="button-primary" href="/login">
              Enter the live prototype <ArrowRight size={17} />
            </Link>
            <a className={styles.backToStory} href="#follow-up">
              Explore the 30-day story <ArrowRight size={15} />
            </a>
          </section>
        </Reveal>
        <footer className={styles.footer}>
          <Brand compact />
          <p>Concept prototype using fictional patient data.</p>
          <span>
            <Stethoscope size={15} /> Made for clinic-led care
          </span>
        </footer>
      </main>
    </LandingMotion>
  );
}
