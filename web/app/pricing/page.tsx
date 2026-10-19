import type { Metadata } from "next"
import Link from "next/link"
import { Check } from "lucide-react"
import { Brand } from "@/components/brand"
import { RoiCalculator } from "@/components/site/roi-calculator"
import styles from "@/components/site/site.module.css"

export const metadata: Metadata = {
  title: "Pricing",
  description: "Example pricing for Khabar, and a calculator for what follow-up could be worth to your clinic, using your own numbers.",
}

const plans = [
  {
    name: "Pilot",
    price: "Free",
    per: "for 3 months",
    points: ["Up to 100 patients in follow-up", "Telegram check-ins, reminders and the call list", "Monthly outcomes report", "Help setting up with your team"],
  },
  {
    name: "Clinic",
    price: "RM 3",
    per: "per patient followed, per month",
    points: ["Every feature, for one clinic", "Ask Khabar, risk score and call scripts", "Staff chat, briefing and handover", "Your data stays yours: export at any time"],
  },
  {
    name: "Group",
    price: "Talk to us",
    per: "for several clinics",
    points: ["Shared reports across clinics", "Your own diet tips and approved answers per clinic", "Single sign-on for staff", "Help with your PDPA paperwork"],
  },
]

export default function PricingPage() {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={`${styles.wrap} ${styles.headerInner}`}>
          <Brand />
          <nav className={styles.nav} aria-label="Pages">
            <Link href="/">Product</Link>
            <Link href="/story">Her story</Link>
            <a href="#calculator">Calculator</a>
          </nav>
          <div className={styles.headerActions}>
            <Link className="button-secondary" href="/login">Sign in</Link>
            <Link className="button-primary" href="/login?demo=doctor">Open the demo</Link>
          </div>
        </div>
      </header>
      <main id="main-content">
        <section className={styles.section} aria-labelledby="pricing-title">
          <div className={styles.wrap}>
            <div className={`${styles.sectionHead} ${styles.pricingHead}`}>
              <span className={styles.eyebrow}>Pricing</span>
              <h1 className={styles.h2} id="pricing-title">Pay for the patients you follow, not for seats</h1>
              <p className={styles.lead}>Example pricing for the prototype, to show how Khabar would be sold. Not a quote.</p>
            </div>
            <div className={styles.plans}>
              {plans.map((p) => <article key={p.name} className={styles.plan}>
                <h2>{p.name}</h2>
                <p className={styles.planPrice}><strong>{p.price}</strong> <span>{p.per}</span></p>
                <ul>{p.points.map((pt) => <li key={pt}><Check size={15} aria-hidden="true" /> {pt}</li>)}</ul>
              </article>)}
            </div>
          </div>
        </section>
        <section className={`${styles.section} ${styles.sectionAlt}`} id="calculator" aria-labelledby="calculator-title">
          <div className={styles.wrap}>
            <div className={`${styles.sectionHead} ${styles.pricingHead}`}>
              <span className={styles.eyebrow}>What it could be worth</span>
              <h2 className={styles.h2} id="calculator-title">Put in your clinic&apos;s numbers</h2>
              <p className={styles.lead}>Every number starts as a placeholder. Replace them with yours; the result is your estimate, not our promise.</p>
            </div>
            <RoiCalculator />
          </div>
        </section>
      </main>
      <footer className={styles.footer}>
        <div className={styles.wrap}>
          <p className={styles.legal}>Concept prototype using fictional patient data. Not a medical device and not for clinical use. Prices are examples.</p>
        </div>
      </footer>
    </div>
  )
}
