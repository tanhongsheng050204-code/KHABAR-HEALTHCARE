"use client"

import { useState } from "react"
import { Camera } from "lucide-react"
import { apiRoot } from "@/lib/api"
import { getToken } from "@/lib/session"
import { useUi } from "@/lib/i18n"
import { SectionHeading } from "@/components/ui"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

/**
 * The patient photographs a meal and gets back their clinic's own diet tips for what is on the plate. The photo
 * is sent only after they tick the box, and it is not kept.
 */
export function MealCheckPanel() {
  const { t } = useUi()
  const [file, setFile] = useState<File | null>(null)
  const [consent, setConsent] = useState(false)
  const [reply, setReply] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  async function send(e: React.FormEvent) {
    e.preventDefault()
    if (!file || !consent) return
    setBusy(true)
    setError("")
    try {
      const body = new FormData()
      body.append("image", file)
      body.append("consentConfirmed", "true")
      const response = await fetch(`${apiRoot}/api/me/meal-photo`, { method: "POST", body, headers: { Authorization: `Bearer ${getToken() ?? ""}` } })
      if (response.status === 404) { setError(t("Meal checks are not available yet.")); return }
      if (!response.ok) throw new Error(`(${response.status})`)
      setReply((await response.json()).message)
    } catch {
      setError(t("Try again"))
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="meal-title">
      <SectionHeading eyebrow={t("Meals")} title={t("Check a meal")} />
      <div className={styles.panel}>
        <p className={styles.lead} id="meal-title">{t("Take a photo of your meal. Khabar names the food groups and sends back your clinic's own diet tips. No calorie counts.")}</p>
        <form className={styles.releaseForm} onSubmit={send}>
          <label className={styles.field}>{t("Meal photo")}
            <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setReply(null) }} />
          </label>
          <label className={styles.switch}><input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            {t("I agree to send this photo to the AI to name the food. It is not kept.")}</label>
          <button className="button-primary" disabled={busy || !file || !consent}><Camera size={15} aria-hidden="true" /> {busy ? t("Checking…") : t("Check my meal")}</button>
        </form>
        {error && <p role="alert">{error}</p>}
        {reply && <pre className={styles.sent} aria-live="polite">{reply}</pre>}
      </div>
    </section>
  )
}
