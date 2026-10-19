import type { Metadata } from "next";
import Link from "next/link";
import {
  Activity,
  ArrowRight,
  Camera,
  ClipboardCheck,
  HeartHandshake,
  Languages,
  Lock,
  Mic,
  PenLine,
  Plus,
  Send,
  ShieldCheck,
  Stethoscope,
  Unplug,
  UserRound,
  UsersRound,
} from "lucide-react";
import { Brand } from "@/components/brand";
import { DayTimeline } from "@/components/site/day-timeline";
import { heroFontVariable } from "@/components/site/hero-font";
import { ProductReplay } from "@/components/site/product-replay";
import styles from "@/components/site/site.module.css";

export const metadata: Metadata = {
  title: { absolute: "Khabar · Aftercare for Malaysian clinics" },
  description:
    "Khabar follows patients home after the visit: plain-language plans in their own language, check-ins on days 1 to 30, and a call list that shows the clinic who needs them first.",
};

const DOCTOR_DEMO = "/login?demo=doctor";
const PATIENT_DEMO = "/login?demo=patient";

const channels = [
  { icon: Send, name: "Telegram", text: "Plans, check-ins and replies, on the phone she already uses", status: "live" },
  { icon: Languages, name: "Four languages", text: "Bahasa Melayu, English, 中文 and தமிழ்", status: "live" },
  { icon: Camera, name: "Packet photo", text: "A medicine packet, read into her list with her consent", status: "live" },
  { icon: PenLine, name: "Doctor's shorthand", text: "Typed notes structured into a draft report", status: "live" },
  { icon: Mic, name: "Voice dictation", text: "The doctor speaks; Khabar transcribes the notes", status: "live" },
  { icon: HeartHandshake, name: "Family access", text: "Granted by the patient, and taken back by her too", status: "live" },
  { icon: Activity, name: "Home readings", text: "Blood pressure and glucose; device readings are simulated", status: "prototype" },
  { icon: Unplug, name: "WhatsApp and clinic EMRs", text: "Not connected yet; Telegram carries messages for now", status: "off" },
] as const;

const statusLabel = { live: "Live", prototype: "Prototype", off: "Not connected" };

const faqs = [
  {
    q: "Is this a real product, or a demo?",
    a: "A working prototype. The demo clinic, its patients and their messages are fictional, and Khabar is not a medical device or for clinical use. Everything on this page can be opened and tried in the demo.",
  },
  {
    q: "Does Khabar give medical advice?",
    a: "No. It relays what the doctor decided, in words the patient can follow. Anything new goes back to the clinic, and a person decides.",
  },
  {
    q: "Will the clinic be alerted the moment a reply looks worrying?",
    a: "Not automatically. Replies that need a closer look go into the clinic's review list and call list, and urgent-sounding replies get precautionary 999 wording. Reply routing is a prototype: staff are not paged, and a response is not guaranteed.",
  },
  {
    q: "What does the AI actually see?",
    a: "Text with the patient's registered name, IC and phone numbers replaced by [NAME], [IC] and [PHONE] before it leaves the server. Other names typed into free text are not detected, so patients are asked not to type them.",
  },
  {
    q: "Which languages does it support?",
    a: "Bahasa Melayu, English, Chinese and Tamil, for plans and check-ins. Wording stays within what the doctor approved.",
  },
  {
    q: "Can family members see everything?",
    a: "Only what the patient shares with them, and only while she allows it. Every view of the record appears in her “who viewed my record” list.",
  },
  {
    q: "Why Telegram and not WhatsApp?",
    a: "Telegram's bot platform lets a 30-day follow-up run without a paid business account. WhatsApp is the obvious next channel, and it is not connected yet.",
  },
] as const;

/** The landing page: what Khabar does, shown with the product's own screens (fictional data). */
export default function LandingPage() {
  return (
    <div className={`${styles.page} ${heroFontVariable}`}>
      <a className={styles.skipLink} href="#main-content">
        Skip to content
      </a>
      <aside aria-label="For judges">
        <Link className={styles.banner} href={DOCTOR_DEMO}>
          <span>Judging Khabar? Open the demo clinic in one click</span>
          <ArrowRight size={16} aria-hidden="true" />
        </Link>
      </aside>
      <header className={styles.header}>
        <div className={`${styles.wrap} ${styles.headerInner}`}>
          <Brand />
          <nav className={styles.nav} aria-label="Page sections">
            <a href="#how">How it works</a>
            <a href="#day">A day with Aminah</a>
            <a href="#safety">Safety</a>
            <a href="#faq">FAQ</a>
            <Link href="/story">Her story</Link>
            <Link href="/pricing">Pricing</Link>
          </nav>
          <div className={styles.headerActions}>
            <Link className="button-secondary" href="/login">
              Sign in
            </Link>
            <Link className="button-primary" href={DOCTOR_DEMO}>
              Open the demo
            </Link>
          </div>
        </div>
      </header>

      <main id="main-content">
        <section className={styles.hero} aria-labelledby="hero-title">
          <div className={styles.wrap}>
            <div className={styles.heroCopy}>
              <span className={styles.eyebrow}>Aftercare for Malaysian clinics</span>
              <h1 className={styles.h1} id="hero-title">
                Know who is slipping before they are back in the queue.
              </h1>
              <p className={styles.lead}>
                Khabar follows each patient home. Their plan arrives in their own language, check-ins
                go out from day 1 to day 30, and the replies that need a closer look rise to the top of
                the clinic&apos;s call list. It relays what the doctor decided and never adds advice.
              </p>
              <div className={styles.ctaRow}>
                <Link className="button-primary" href={DOCTOR_DEMO}>
                  Open the demo clinic <ArrowRight size={16} aria-hidden="true" />
                </Link>
                <a className="button-secondary" href="#day">
                  See how it works
                </a>
              </div>
              <p className={styles.heroNote}>Fictional patients. No sign-up needed.</p>
            </div>
            <ProductReplay />
          </div>
        </section>

        <section className={styles.section} id="how" aria-labelledby="who-title">
          <div className={styles.wrap}>
            <div className={styles.sectionHead}>
              <h2 className={styles.h2} id="who-title">
                Made for everyone around the patient.{" "}
                <span>The visit is ten minutes; recovery is thirty days.</span>
              </h2>
            </div>
            <div className={styles.audience}>
              <article>
                <span>
                  <UserRound size={20} aria-hidden="true" />
                </span>
                <h3 className={styles.h3}>The patient</h3>
                <p>A plan she can read, reminders that fit her day, and one place for every medicine she takes.</p>
                <small>Mak Cik Aminah, 64, type 2 diabetes</small>
              </article>
              <article>
                <span>
                  <UsersRound size={20} aria-hidden="true" />
                </span>
                <h3 className={styles.h3}>The family</h3>
                <p>The daughter who helps with medicines sees the plan and the next appointment, because her mother said yes.</p>
                <small>Nurul, who lives in KL</small>
              </article>
              <article>
                <span>
                  <Stethoscope size={20} aria-hidden="true" />
                </span>
                <h3 className={styles.h3}>The clinic</h3>
                <p>A call list that says who needs a human today, instead of a pile of messages to read.</p>
                <small>Dr Priya&apos;s clinic, 80 patients a day</small>
              </article>
            </div>
          </div>
        </section>

        <section className={`${styles.section} ${styles.sectionTight}`} aria-labelledby="channels-title">
          <div className={styles.wrap}>
            <div className={styles.sectionHead}>
              <h2 className={styles.h2} id="channels-title">
                Works with what people already use.{" "}
                <span>Every status below is honest about what is connected today.</span>
              </h2>
            </div>
            <div className={styles.grid}>
              {channels.map(({ icon: Icon, name, text, status }) => (
                <div key={name}>
                  <Icon size={24} strokeWidth={1.6} aria-hidden="true" />
                  <h3 className={styles.h3}>{name}</h3>
                  <p>{text}</p>
                  <span className={styles.pill} data-status={status}>
                    {statusLabel[status]}
                  </span>
                </div>
              ))}
            </div>

            <div className={styles.redact} aria-label="What the AI reads">
              <div>
                <header>
                  <strong>What Aminah types</strong>
                  <span>Check-in reply</span>
                </header>
                <p>
                  Saya{" "}
                  <mark className={styles.pii} data-kind="Name">
                    Aminah
                  </mark>
                  , IC{" "}
                  <mark className={styles.pii} data-kind="IC">
                    590312-10-5566
                  </mark>
                  . Pening sikit lepas makan ubat. Telefon saya{" "}
                  <mark className={styles.pii} data-kind="Phone">
                    012-345 6789
                  </mark>
                  .
                </p>
              </div>
              <span className={styles.redactArrow} aria-hidden="true">
                <ArrowRight size={18} />
              </span>
              <div className={styles.redactAiSide}>
                <header>
                  <strong>What the AI reads</strong>
                  <span>Removed on our server first</span>
                </header>
                <p>
                  Saya <code className={styles.token}>[NAME]</code>, IC <code className={styles.token}>[IC]</code>.
                  Pening sikit lepas makan ubat. Telefon saya <code className={styles.token}>[PHONE]</code>.
                </p>
              </div>
            </div>
            <p className={styles.footnote}>
              <Lock size={13} aria-hidden="true" /> The patient&apos;s registered name, IC and phone numbers are
              replaced before any AI call. Other names typed in free text are not detected, so patients are asked
              not to type them.
            </p>
          </div>
        </section>

        <section className={`${styles.section} ${styles.sectionAlt}`} id="day" aria-labelledby="day-title">
          <div className={styles.wrap}>
            <div className={styles.sectionHead}>
              <h2 className={styles.h2} id="day-title">
                One day with Aminah.{" "}
                <span>Plan, check-in, call list and family, all from the same record.</span>
              </h2>
            </div>
            <DayTimeline />
          </div>
        </section>

        <section className={styles.section} id="safety" aria-labelledby="safety-title">
          <div className={styles.wrap}>
            <div className={styles.sectionHead}>
              <h2 className={styles.h2} id="safety-title">
                Nothing clinical happens on its own.{" "}
                <span>Khabar sorts, translates and reminds. People decide.</span>
              </h2>
            </div>
            <div className={styles.safety}>
              <div>
                <ShieldCheck size={22} aria-hidden="true" />
                <h3 className={styles.h3}>Relays, never prescribes</h3>
                <p>Patients get the doctor&apos;s own plan in plain words. Questions about treatment go back to the clinic.</p>
              </div>
              <div>
                <ClipboardCheck size={22} aria-hidden="true" />
                <h3 className={styles.h3}>A hard stop on critical findings</h3>
                <p>
                  Allergies, interactions, duplicates across clinics and brands, herb clashes and doses are checked.
                  A critical finding cannot be finalised without a written reason.
                </p>
              </div>
              <div>
                <Lock size={22} aria-hidden="true" />
                <h3 className={styles.h3}>Access that leaves a trail</h3>
                <p>Each role sees only what it needs, sensitive fields are encrypted, and every view of a record is logged.</p>
              </div>
            </div>
          </div>
        </section>

        <section className={`${styles.section} ${styles.sectionTight}`} id="faq" aria-labelledby="faq-title">
          <div className={`${styles.wrap} ${styles.faq}`}>
            <div className={styles.sectionHead}>
              <h2 className={styles.h2} id="faq-title">
                Questions clinics ask.
              </h2>
              <p className={styles.lead}>
                Want the long version? <Link href="/story">Watch Aminah&apos;s story</Link>, thirty days in eight scenes.
              </p>
            </div>
            <div className={styles.faqList}>
              {faqs.map(({ q, a }) => (
                <details key={q}>
                  <summary>
                    {q}
                    <Plus size={18} aria-hidden="true" />
                  </summary>
                  <p>{a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.band} aria-labelledby="band-title">
          <div className={`${styles.wrap} ${styles.bandInner}`}>
            <h2 className={styles.h2} id="band-title">
              Care that carries on after the visit.
            </h2>
            <p className={styles.lead}>Open the demo clinic, or see the same day from Aminah&apos;s phone.</p>
            <div className={styles.ctaRow}>
              <Link className="button-primary" href={DOCTOR_DEMO}>
                Open the demo clinic <ArrowRight size={16} aria-hidden="true" />
              </Link>
              <Link className="button-secondary" href={PATIENT_DEMO}>
                Try the patient view
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.wrap}>
          <div className={styles.footerGrid}>
            <div>
              <Brand compact />
              <p>
                From <i>apa khabar?</i>, “how are you?”: the question Khabar asks every patient after the visit.
              </p>
            </div>
            <div>
              <h2>Product</h2>
              <ul>
                <li><a href="#how">How it works</a></li>
                <li><a href="#day">A day with Aminah</a></li>
                <li><a href="#safety">Safety</a></li>
                <li><a href="#faq">FAQ</a></li>
              </ul>
            </div>
            <div>
              <h2>Try it</h2>
              <ul>
                <li><Link href={DOCTOR_DEMO}>Doctor demo</Link></li>
                <li><Link href={PATIENT_DEMO}>Patient demo</Link></li>
                <li><Link href="/login">Sign in</Link></li>
              </ul>
            </div>
            <div>
              <h2>Story</h2>
              <ul>
                <li><Link href="/story">Aminah&apos;s thirty days</Link></li>
                <li><Link href="/pricing">Pricing and value calculator</Link></li>
              </ul>
            </div>
          </div>
          <p className={styles.legal}>
            Concept prototype using fictional patient data. Not a medical device and not for clinical use.
          </p>
        </div>
      </footer>
    </div>
  );
}
