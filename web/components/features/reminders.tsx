"use client"

import { useCallback, useEffect, useState } from "react"
import { BellRing, Check, X } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { useUi } from "@/lib/i18n"
import type { Adherence, DoseEntry, ReminderSettings, TeachBackAttempt } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

const SLOTS = ["sahur", "morning", "afternoon", "evening", "berbuka", "night"] as const

function recent(dose: DoseEntry) {
  const day = new Date(`${dose.day}T12:00:00`)
  return Date.now() - day.getTime() < 2.5 * 24 * 3600 * 1000
}

/**
 * The patient's own reminder switches, the doses still waiting for an answer (the same Taken / Missed as the
 * Telegram buttons), and two weeks of answers. Reminders repeat the doctor's plan; they never advise.
 */
export function RemindersPanel({ notify }: { notify: (n: Notice) => void }) {
  const { t, locale } = useUi()
  const [settings, setSettings] = useState<ReminderSettings | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const load = useCallback(async () => {
    try { setSettings(await apiRequest<ReminderSettings>("/api/me/reminders")) }
    catch (e) { if (!featureMissing(e)) notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }, [notify, t])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function save(doseReminders: boolean, appointmentReminders: boolean) {
    setBusy("save")
    try {
      setSettings(await apiRequest<ReminderSettings>("/api/me/reminders", { method: "PUT", body: JSON.stringify({ doseReminders, appointmentReminders }) }))
      notify({ tone: "success", text: t("Your reminder choices are saved.") })
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") })
    } finally {
      setBusy(null)
    }
  }
  async function answer(dose: DoseEntry, response: "TAKEN" | "MISSED") {
    setBusy(dose.id)
    try {
      const result = await apiRequest<{ recorded: boolean; message: string }>(`/api/me/doses/${dose.id}`, { method: "POST", body: JSON.stringify({ response }) })
      notify({ tone: "success", text: result.message })
      await load()
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") })
    } finally {
      setBusy(null)
    }
  }

  if (!settings) return null
  const waiting = settings.adherence.doses.filter((d) => d.response === null && recent(d)).reverse()
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="reminders-title">
      <SectionHeading eyebrow={t("Reminders")} title={t("Medicine and visit reminders")} />
      <div className={styles.panel}>
        <p className={styles.lead} id="reminders-title">{t("Khabar can remind you on Telegram when it is time for your medicines and the evening before a visit. The reminders only repeat your doctor's plan.")}</p>
        {!settings.telegramLinked && <p className={styles.muted}>{t("Link Telegram first to get reminders there. You can still answer them here.")}</p>}
        {!settings.hasPlan && <p className={styles.muted}>{t("Medicine reminders start once your doctor signs a plan with regular medicines.")}</p>}
        <div className={styles.row}>
          <label className={styles.switch}><input type="checkbox" checked={settings.doseReminders} disabled={busy === "save"}
            onChange={(e) => void save(e.target.checked, settings.appointmentReminders)} />{t("Remind me to take my medicines")}</label>
          <label className={styles.switch}><input type="checkbox" checked={settings.appointmentReminders} disabled={busy === "save"}
            onChange={(e) => void save(settings.doseReminders, e.target.checked)} />{t("Remind me the evening before a visit")}</label>
        </div>

        {waiting.length > 0 && <>
          <strong>{t("Did you take these?")}</strong>
          <ul className={styles.log}>
            {waiting.map((d) => <li key={d.id}>
              <div className={styles.rowBetween}>
                <span><BellRing size={14} aria-hidden="true" /> {t(d.slot)} · {new Intl.DateTimeFormat(locale, { weekday: "short", day: "numeric", month: "short" }).format(new Date(`${d.day}T12:00:00`))}</span>
                <span className={styles.row}>
                  <button className="button-secondary" disabled={busy === d.id} onClick={() => void answer(d, "TAKEN")}><Check size={14} aria-hidden="true" /> {t("Taken")}</button>
                  <button className="button-quiet" disabled={busy === d.id} onClick={() => void answer(d, "MISSED")}><X size={14} aria-hidden="true" /> {t("Missed")}</button>
                </span>
              </div>
              <pre>{d.medicines}</pre>
            </li>)}
          </ul>
        </>}

        {settings.adherence.sent > 0 && <AdherenceStrip adherence={settings.adherence} />}
      </div>
    </section>
  )
}

/** Fourteen days of dose answers: one square per reminder, taken, missed or not answered. */
function AdherenceStrip({ adherence }: { adherence: Adherence }) {
  const { t } = useUi()
  const level = (d: DoseEntry) => d.response === "TAKEN" ? "ok" : d.response === "MISSED" ? "red" : "review"
  return (
    <div className={styles.adherence}>
      <strong>{t("Last {days} days: {taken} taken, {missed} missed, {unanswered} not answered", {
        days: adherence.days, taken: adherence.taken, missed: adherence.missed, unanswered: adherence.unanswered })}</strong>
      <div className={styles.doseGrid} role="list">
        {adherence.doses.map((d) => <span key={d.id} role="listitem" className={styles[`dose_${level(d)}`]}
          title={`${d.day} · ${t(d.slot)} · ${d.response === "TAKEN" ? t("Taken") : d.response === "MISSED" ? t("Missed") : t("Not answered")}`} />)}
      </div>
    </div>
  )
}

/** For the clinic's patient record: dose answers and plan checks. Reading them is in the access history. */
export function PatientAdherencePanel({ patientId }: { patientId: string }) {
  const [adherence, setAdherence] = useState<Adherence | null>(null)
  const [checks, setChecks] = useState<TeachBackAttempt[]>([])
  useEffect(() => {
    let live = true
    void apiRequest<Adherence>(`/api/patients/${patientId}/adherence`).then((a) => { if (live) setAdherence(a) }).catch(() => undefined)
    void apiRequest<TeachBackAttempt[]>(`/api/patients/${patientId}/teach-back`).then((c) => { if (live) setChecks(c) }).catch(() => undefined)
    return () => { live = false }
  }, [patientId])
  if (!adherence && checks.length === 0) return null
  const QUESTION: Record<string, string> = { times: "times a day", food: "before or after food", next_visit: "next visit" }
  return (
    <section className={homeStyles.peopleCard}>
      <SectionHeading eyebrow="Between visits" title="Doses and plan checks" />
      <div className={styles.panel}>
        {adherence && (adherence.sent === 0
          ? <p className={styles.muted}>No dose reminders in the last {adherence.days} days. The patient switches reminders on themselves.</p>
          : <>
            <p className={styles.lead}>Last {adherence.days} days: {adherence.taken} taken, {adherence.missed} missed, {adherence.unanswered} not answered, of {adherence.sent} reminders. Answers are the patient&apos;s own taps.</p>
            <div className={styles.doseGrid} role="list">
              {adherence.doses.map((d) => <span key={d.id} role="listitem" className={styles[`dose_${d.response === "TAKEN" ? "ok" : d.response === "MISSED" ? "red" : "review"}`]}
                title={`${d.day} · ${d.slot} · ${d.response?.toLowerCase() ?? "not answered"}`} />)}
            </div>
          </>)}
        {checks.length > 0 && <>
          <strong>Plan checks</strong>
          <ul className={styles.log}>
            {checks.slice(0, 5).map((c) => <li key={c.id}>
              <div className={styles.rowBetween}>
                <span>{c.correct} of {c.total} right{c.missed.length ? ` · explain again: ${c.missed.map((m) => QUESTION[m.split(":")[0]] ?? m).join(", ")}` : ""}</span>
                <span className={styles.row}><StatusBadge level={c.correct === c.total ? "ok" : "watch"}>{c.correct === c.total ? "understood" : "explain again"}</StatusBadge>
                  <span className={styles.muted}>{new Date(c.takenAt).toLocaleDateString()}</span></span>
              </div>
            </li>)}
          </ul>
          <p className={styles.muted}>A study aid for the patient, not a test result.</p>
        </>}
      </div>
    </section>
  )
}

export const REMINDER_SLOTS = SLOTS
