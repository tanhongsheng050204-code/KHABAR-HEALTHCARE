"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { CalendarDays, Check, Copy, Mic, Send, Square, Users, X } from "lucide-react"
import { apiRequest, apiRoot } from "@/lib/api"
import { getToken } from "@/lib/session"
import { useUi } from "@/lib/i18n"
import type { Appointment, DoseEntry, FamilyGroupStatus, Preferences, ReminderSettings, Slot, Summary } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { ListenButton } from "@/components/home/listen-button"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

/**
 * Reply to the clinic by speaking: record, see the words, fix them if needed, then send. Nothing goes to the clinic
 * until the patient taps Send; the recording itself is not kept.
 */
export function VoiceReplyPanel({ notify, big = false }: { notify: (n: Notice) => void; big?: boolean }) {
  const { t } = useUi()
  const [recording, setRecording] = useState(false)
  const [busy, setBusy] = useState(false)
  const [text, setText] = useState("")
  const [answer, setAnswer] = useState<string | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])

  async function start() {
    setAnswer(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const r = new MediaRecorder(stream)
      chunks.current = []
      r.ondataavailable = (e) => { if (e.data.size) chunks.current.push(e.data) }
      r.onstop = () => { stream.getTracks().forEach((track) => track.stop()); void upload(new Blob(chunks.current, { type: r.mimeType || "audio/webm" })) }
      recorder.current = r
      r.start()
      setRecording(true)
    } catch {
      notify({ tone: "error", text: t("The microphone could not be used. You can type instead.") })
    }
  }
  function stop() {
    recorder.current?.stop()
    setRecording(false)
  }
  async function upload(blob: Blob) {
    setBusy(true)
    try {
      const body = new FormData()
      body.append("audio", blob, blob.type.includes("ogg") ? "reply.ogg" : "reply.webm")
      const response = await fetch(`${apiRoot}/api/me/voice-reply`, { method: "POST", body, headers: { Authorization: `Bearer ${getToken() ?? ""}` } })
      if (!response.ok) throw new Error(String(response.status))
      setText((await response.json()).transcript)
    } catch {
      notify({ tone: "error", text: t("Nothing could be heard. Try again or type your reply.") })
    } finally {
      setBusy(false)
    }
  }
  async function send() {
    setBusy(true)
    try {
      const outcome = await apiRequest<{ message: string }>("/api/followup/replies", { method: "POST", body: JSON.stringify({ text: text.trim() }) })
      setAnswer(outcome.message)
      setText("")
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") })
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className={big ? styles.simpleCard : homeStyles.peopleCard} aria-labelledby="voice-title">
      {!big && <SectionHeading eyebrow={t("Voice")} title={t("Tell the clinic by speaking")} />}
      <div className={styles.panel}>
        <p className={styles.lead} id="voice-title">{t("Press the button and say how you are. You will see the words before anything is sent.")}</p>
        {recording
          ? <button className={`button-danger ${big ? styles.bigButton : ""}`} onClick={stop}><Square size={big ? 28 : 16} aria-hidden="true" /> {t("Stop")}</button>
          : <button className={`button-primary ${big ? styles.bigButton : ""}`} disabled={busy} onClick={() => void start()}><Mic size={big ? 28 : 16} aria-hidden="true" /> {busy ? t("Listening…") : t("Speak")}</button>}
        {text && <div className={styles.releaseForm}>
          <label className={styles.field}>{t("Check the words, then send")}
            <textarea rows={3} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <button className={`button-primary ${big ? styles.bigButton : ""}`} disabled={busy || !text.trim()} onClick={() => void send()}><Send size={big ? 24 : 15} aria-hidden="true" /> {t("Send to my clinic")}</button>
        </div>}
        {answer && <p className={styles.sent} aria-live="polite">{answer}</p>}
      </div>
    </section>
  )
}

/** Voice-first, the simple screen and the family group: settings the patient chooses for themselves. */
export function AccessSettingsPanel({ notify, onChange }: { notify: (n: Notice) => void; onChange?: (p: Preferences) => void }) {
  const { t } = useUi()
  const [prefs, setPrefs] = useState<Preferences | null>(null)
  const [family, setFamily] = useState<FamilyGroupStatus | null>(null)
  const [command, setCommand] = useState<string | null>(null)
  const load = useCallback(async () => {
    try {
      setPrefs(await apiRequest<Preferences>("/api/me/preferences"))
      setFamily(await apiRequest<FamilyGroupStatus>("/api/me/family-group"))
    } catch (e) { if (!featureMissing(e)) notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }, [notify, t])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function save(next: Preferences) {
    try {
      const saved = await apiRequest<Preferences>("/api/me/preferences", { method: "PUT", body: JSON.stringify(next) })
      setPrefs(saved)
      onChange?.(saved)
    } catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }
  if (!prefs) return null
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="access-title">
      <SectionHeading eyebrow={t("Easier to use")} title={t("Make Khabar easier for me")} />
      <div className={styles.panel}>
        <p className={styles.lead} id="access-title">{t("Choose what helps. You can change these at any time.")}</p>
        <label className={styles.switch}><input type="checkbox" checked={prefs.simpleMode} onChange={(e) => void save({ ...prefs, simpleMode: e.target.checked })} />{t("Simple screen with big buttons")}</label>
        <label className={styles.switch}><input type="checkbox" checked={prefs.voiceFirst} onChange={(e) => void save({ ...prefs, voiceFirst: e.target.checked })} />{t("I prefer to speak rather than type")}</label>
        {family && <>
          <div className={styles.rowBetween}>
            <strong><Users size={15} aria-hidden="true" /> {t("Family group on Telegram")}</strong>
            <StatusBadge level={family.linked ? "ok" : "review"}>{family.linked ? t("Linked") : t("Not linked")}</StatusBadge>
          </div>
          <p className={styles.muted}>{t("Your family group gets copies of your medicine and visit reminders, and anyone in it can tap Taken for you. Messages typed in the group do not go to the clinic.")}</p>
          <div className={styles.row}>
            <button className="button-secondary" onClick={() => void apiRequest<{ command: string }>("/api/me/family-group/link-code", { method: "POST" }).then((c) => setCommand(c.command))}>
              {family.linked ? t("Link a different group") : t("Link my family group")}
            </button>
            {family.linked && <button className="button-quiet" onClick={() => void apiRequest<FamilyGroupStatus>("/api/me/family-group", { method: "DELETE" }).then(setFamily)}>{t("Unlink")}</button>}
          </div>
          {command && <div className={styles.code}>
            <span>{t("Add the Khabar bot to your family group, then send this in the group:")}</span>
            <code>{command}</code>
            <button className="button-quiet" onClick={() => void navigator.clipboard.writeText(command)}><Copy size={14} aria-hidden="true" /> {t("Copy")}</button>
            <span className={styles.muted}>{t("The code works for 30 minutes.")}</span>
          </div>}
        </>}
      </div>
    </section>
  )
}

/**
 * The simple screen: four big buttons and large type, for patients who find the full app hard. Everything here
 * also exists in the full app; the switch back is always on screen.
 */
export function SimpleHome({ name, summary, appointment, notify, onExit }: {
  name: string; summary: Summary | null; appointment: Appointment | null; notify: (n: Notice) => void; onExit: () => void
}) {
  const { t, locale } = useUi()
  const [waiting, setWaiting] = useState<DoseEntry[]>([])
  const [speaking, setSpeaking] = useState(false)
  const loadDoses = useCallback(async () => {
    try {
      const s = await apiRequest<ReminderSettings>("/api/me/reminders")
      const since = Date.now() - 2.5 * 24 * 3600 * 1000
      setWaiting(s.adherence.doses.filter((d) => d.response === null && new Date(`${d.day}T12:00:00`).getTime() > since))
    } catch { setWaiting([]) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void loadDoses(), 0)
    return () => window.clearTimeout(timer)
  }, [loadDoses])
  async function tookAll() {
    for (const d of waiting) {
      try { await apiRequest(`/api/me/doses/${d.id}`, { method: "POST", body: JSON.stringify({ response: "TAKEN" }) }) } catch { /* already answered */ }
    }
    notify({ tone: "success", text: t("Thank you. Recorded.") })
    await loadDoses()
  }
  return (
    <main className={styles.simple} id="overview" tabIndex={-1}>
      <h1>{t("Hello, {name}.", { name })}</h1>
      <div className={styles.simpleGrid}>
        <button className={styles.simpleTile} disabled={waiting.length === 0} onClick={() => void tookAll()}>
          <Check size={44} aria-hidden="true" />
          <strong>{t("I took my medicine")}</strong>
          <span>{waiting.length ? t("{n} reminders waiting", { n: waiting.length }) : t("Nothing waiting")}</span>
        </button>
        <button className={styles.simpleTile} onClick={() => setSpeaking(!speaking)} aria-expanded={speaking}>
          <Mic size={44} aria-hidden="true" />
          <strong>{t("Tell the clinic how I feel")}</strong>
          <span>{t("Speak, check, send")}</span>
        </button>
        <div className={styles.simpleTile}>
          <CalendarDays size={44} aria-hidden="true" />
          <strong>{t("My next visit")}</strong>
          <span>{appointment ? new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long", hour: "numeric", minute: "2-digit" }).format(new Date(appointment.startsAt)) : t("No visit booked")}</span>
        </div>
        <div className={styles.simpleTile}>
          <strong>{t("My plan")}</strong>
          {summary ? <ListenButton text={summary.text} language={summary.language} /> : <span>{t("No plan yet")}</span>}
        </div>
      </div>
      {speaking && <VoiceReplyPanel notify={notify} big />}
      {summary && <p className={styles.simplePlan} lang={summary.language}>{summary.text}</p>}
      <button className="button-quiet" onClick={onExit}><X size={16} aria-hidden="true" /> {t("Show the full app")}</button>
    </main>
  )
}

/** A caregiver with alert consent: answer dose reminders, write a check-in, or book a visit for the patient. */
export function CaregiverActPanel({ patientId, name, notify }: { patientId: string; name: string; notify: (n: Notice) => void }) {
  const { t, locale } = useUi()
  const [doses, setDoses] = useState<DoseEntry[] | null>(null)
  const [text, setText] = useState("")
  const [slots, setSlots] = useState<Slot[]>([])
  const [slot, setSlot] = useState("")
  const [answer, setAnswer] = useState<string | null>(null)
  const load = useCallback(async () => {
    try {
      setDoses(await apiRequest<DoseEntry[]>(`/api/patients/${patientId}/doses-waiting`))
      setSlots(await apiRequest<Slot[]>(`/api/patients/${patientId}/slots`))
    } catch { setDoses(null) }
  }, [patientId])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function act(work: () => Promise<unknown>, done: string) {
    try { await work(); notify({ tone: "success", text: done }); await load() }
    catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }
  if (!doses) return null
  return (
    <section className={homeStyles.peopleCard} aria-label={t("Help {name}", { name })}>
      <SectionHeading eyebrow={t("Act for them")} title={t("Help {name}", { name })} />
      <div className={styles.panel}>
        <p className={styles.muted}>{t("Everything you do here is recorded as done by you, and the clinic can see it.")}</p>
        <strong>{t("Medicine reminders waiting")}</strong>
        {doses.length === 0 ? <p className={styles.muted}>{t("Nothing waiting")}</p> : <ul className={styles.log}>
          {doses.map((d) => <li key={d.id}>
            <div className={styles.rowBetween}><span>{t(d.slot)} · {d.day}</span>
              <span className={styles.row}>
                <button className="button-secondary" onClick={() => void act(() => apiRequest(`/api/patients/${patientId}/doses/${d.id}`, { method: "POST", body: JSON.stringify({ response: "TAKEN" }) }), t("Thank you. Recorded."))}>{t("Taken")}</button>
                <button className="button-quiet" onClick={() => void act(() => apiRequest(`/api/patients/${patientId}/doses/${d.id}`, { method: "POST", body: JSON.stringify({ response: "MISSED" }) }), t("Thank you. Recorded."))}>{t("Missed")}</button>
              </span></div>
            <pre>{d.medicines}</pre>
          </li>)}
        </ul>}
        <form className={styles.releaseForm} onSubmit={(e) => {
          e.preventDefault()
          void act(async () => { const o = await apiRequest<{ message: string }>(`/api/patients/${patientId}/replies-on-behalf`, { method: "POST", body: JSON.stringify({ text }) }); setAnswer(o.message); setText("") }, t("Sent to the clinic."))
        }}>
          <label className={styles.field}>{t("Tell the clinic how {name} is", { name })}
            <textarea rows={2} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <button className="button-primary" disabled={!text.trim()}><Send size={15} aria-hidden="true" /> {t("Send to the clinic")}</button>
        </form>
        {answer && <p className={styles.sent}>{answer}</p>}
        {slots.length > 0 && <form className={styles.row} onSubmit={(e) => {
          e.preventDefault()
          void act(() => apiRequest(`/api/patients/${patientId}/appointments`, { method: "POST", body: JSON.stringify({ startsAt: slot }) }), t("The visit is booked."))
        }}>
          <label className={styles.field}>{t("Book a visit for {name}", { name })}
            <select value={slot} onChange={(e) => setSlot(e.target.value)}>
              <option value="">{t("Choose a time")}</option>
              {slots.slice(0, 40).map((s) => <option key={s.startsAt} value={s.startsAt}>{new Intl.DateTimeFormat(locale, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(new Date(s.startsAt))}</option>)}
            </select>
          </label>
          <button className="button-secondary" disabled={!slot}>{t("Book")}</button>
        </form>}
      </div>
    </section>
  )
}
