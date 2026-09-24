import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft, LockKeyhole, ShieldCheck, Stethoscope } from "lucide-react";
import { Brand } from "@/components/brand";
import { LoginPanel } from "@/components/auth/login-panel";
import { ExperienceMotion, MotionButton } from "@/components/experience-motion";
import styles from "./login.module.css";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <ExperienceMotion>
      <main className={styles.page}>
        <section className={styles.story}>
          <div className={styles.storyTop}>
            <Brand />
            <Link href="/">
              <ArrowLeft size={16} /> Back to the story
            </Link>
          </div>
          <div className={styles.storyCopy}>
            <p>Secure continuity</p>
            <h1>
              A familiar place.
              <br />
              <em>A clearer next step.</em>
            </h1>
            <span>
              One sign-in brings the right context to each person—clinician,
              patient, or invited family member.
            </span>
          </div>
          <div className={styles.careNote} aria-hidden="true">
            <div>
              <span>♡</span>
              <p>
                A LITTLE CONTINUITY
                <strong>
                  From your clinic.
                  <br />
                  To your everyday.
                </strong>
              </p>
            </div>
            <div className={styles.careNoteSteps}>
              <span>Check in</span>
              <i />
              <span>Stay connected</span>
              <i />
              <span>Carry on</span>
            </div>
          </div>
          <div className={styles.assurance}>
            <div>
              <ShieldCheck size={19} />
              <span>
                <strong>Clinician-led</strong>Important decisions always return
                to the care team.
              </span>
            </div>
            <div>
              <LockKeyhole size={19} />
              <span>
                <strong>Consent-led</strong>Family access stays within the
                patient’s chosen scope.
              </span>
            </div>
            <div>
              <Stethoscope size={19} />
              <span>
                <strong>Built for clarity</strong>Every role sees the next
                useful action, not the whole system.
              </span>
            </div>
          </div>
          <div className={styles.storyThread} aria-hidden="true">
            <i />
            <i />
            <i />
          </div>
        </section>
        <section className={styles.formSide}>
          <div className={styles.formUtility}>
            <span>Your Khabar space</span>
            <MotionButton />
          </div>
          <LoginPanel />
        </section>
      </main>
    </ExperienceMotion>
  );
}
