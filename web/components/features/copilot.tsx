"use client"

import { useCallback, useEffect, useState } from "react"
import { MessageSquareText, PhoneCall, Plus, Search, Sparkles, Trash2 } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { AskAnswer, CallScript, DietTip, PreBriefData } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

const EXAMPLES = ["Which of my diabetics are slipping?", "Who is missing doses this week?", "Who missed a visit?", "Whose HbA1c is above target?"]

/**
 * Ask Khabar: a doctor's plain question answered from the clinic's own follow-up records. The AI sees coded rows,
 * never names, and has to cite the rows it used; without a model, Khabar's rules answer.
 */
export function AskKhabarPanel() {
  const [question, setQuestion] = useState("")
  const [answer, setAnswer] = useState<AskAnswer | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  async function ask(q: string) {
    if (!q.trim()) return
    setBusy(true)
    setError("")
    try { setAnswer(await apiRequest<AskAnswer>("/api/clinic/ask", { method: "POST", body: JSON.stringify({ question: q }) })) }
    catch (e) { setError(e instanceof Error ? e.message : "No answer this time.") }
    finally { setBusy(false) }
  }
  return (
    <section className={homeStyles.peopleCard} aria-label="Ask Khabar">
      <SectionHeading eyebrow="Ask Khabar" title="Ask about your follow-up" />
      <div className={styles.panel}>
        <form className={styles.askRow} onSubmit={(e) => { e.preventDefault(); void ask(question) }}>
          <input className={styles.askField} aria-label="Question for Khabar" value={question} maxLength={500}
            placeholder="e.g. Which of my diabetics are slipping?" onChange={(e) => setQuestion(e.target.value)} />
          <button className="button-primary" disabled={busy || !question.trim()}><Search size={15} aria-hidden="true" /> {busy ? "Asking…" : "Ask"}</button>
        </form>
        <div className={styles.row}>
          {EXAMPLES.map((q) => <button key={q} className="button-quiet" onClick={() => { setQuestion(q); void ask(q) }}>{q}</button>)}
        </div>
        {error && <p role="alert">{error}</p>}
        {answer && <div className={styles.answer} aria-live="polite">
          <div className={styles.rowBetween}>
            <strong><Sparkles size={14} aria-hidden="true" /> Answer</strong>
            <StatusBadge level="review">{answer.source === "model" ? "AI, from your records" : "Khabar's rules"}</StatusBadge>
          </div>
          <p>{answer.answer}</p>
          {answer.patients.length > 0 && <ul className={styles.log}>
            {answer.patients.map((p) => <li key={p.patientId}>
              <a href={`/home/patients/${p.patientId}`}><strong>{p.fullName}</strong></a>
              <span className={styles.muted}>{p.why}</span>
            </li>)}
          </ul>}
          <p className={styles.muted}>From {answer.rowsConsidered} patients in follow-up. Read-only: nothing was changed. Each patient listed is in their access history.</p>
        </div>}
      </div>
    </section>
  )
}

/** A call script for one patient, opened from the call list: what to ask, in English and the patient's language. */
export function CallScriptButton({ patientId }: { patientId: string }) {
  const [script, setScript] = useState<CallScript | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  async function load() {
    if (script) { setOpen(!open); return }
    setBusy(true)
    try { setScript(await apiRequest<CallScript>(`/api/patients/${patientId}/call-script`, { method: "POST" })); setOpen(true) }
    catch { /* the call list works without it */ }
    finally { setBusy(false) }
  }
  return (
    <div className={styles.callScript}>
      <button className="button-quiet" disabled={busy} aria-expanded={open} onClick={() => void load()}>
        <PhoneCall size={14} aria-hidden="true" /> {busy ? "Preparing…" : open ? "Hide call script" : "Call script"}
      </button>
      {open && script && <ol className={styles.scriptLines}>
        {script.lines.map((l, i) => <li key={i}><strong lang={script.language}>{l.spoken}</strong>{l.spoken !== l.english && <span>{l.english}</span>}</li>)}
        <li className={styles.scriptSafety}><strong lang={script.language}>{script.safety.spoken}</strong>{script.safety.spoken !== script.safety.english && <span>{script.safety.english}</span>}</li>
        <li className={styles.muted}>{script.source === "model" ? "Drafted by AI from redacted messages; ask and listen, don't advise." : "Khabar's standard questions for what the record shows."}</li>
      </ol>}
    </div>
  )
}

const LEVEL: Record<string, string> = { RED: "red", WATCH: "watch", REVIEW: "review", OK: "ok" }

/** The doctor's one-screen "what changed since the last visit", at the top of the record. */
export function PreBriefPanel({ patientId }: { patientId: string }) {
  const [brief, setBrief] = useState<PreBriefData | null>(null)
  useEffect(() => {
    let live = true
    apiRequest<PreBriefData>(`/api/patients/${patientId}/prebrief`).then((b) => { if (live) setBrief(b) }).catch(() => undefined)
    return () => { live = false }
  }, [patientId])
  if (!brief) return null
  return (
    <section className={`${homeStyles.peopleCard} ${styles.prebrief}`} aria-label="Pre-brief">
      <SectionHeading eyebrow="Pre-brief" title={brief.headline} />
      <ul className={styles.log}>
        {brief.lines.map((l, i) => <li key={i} className={styles.rowBetween}>
          <span>{l.text}</span>
          {l.level !== "OK" && <StatusBadge level={LEVEL[l.level] ?? "review"}>{l.level.toLowerCase()}</StatusBadge>}
        </li>)}
      </ul>
      <p className={styles.muted}>Built from the record; no AI involved.</p>
    </section>
  )
}

const CATEGORY_LABELS: Record<string, string> = {
  RICE_NOODLES: "Rice / noodles", BREAD: "Bread / roti", SWEET_DRINK: "Sweet drink", FRIED: "Fried food", SWEETS_DESSERT: "Sweets / dessert",
  FRUIT: "Fruit", VEGETABLES: "Vegetables", SALTY_SAUCES: "Salty food / sauces", MEAT_FISH_EGG: "Meat / fish / egg",
}

/** The clinic's diet tips: the only words a patient's meal photo can get back. Doctors write and retire them. */
export function DietTipsPanel({ notify, canEdit }: { notify: (n: Notice) => void; canEdit: boolean }) {
  const [tips, setTips] = useState<DietTip[] | null>(null)
  const [draft, setDraft] = useState({ category: "RICE_NOODLES", ms: "", en: "", zh: "", ta: "" })
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setTips(await apiRequest<DietTip[]>("/api/clinic/diet-tips")) } catch (e) { if (featureMissing(e)) setMissing(true) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function save(e: React.FormEvent) {
    e.preventDefault()
    try {
      await apiRequest("/api/clinic/diet-tips", { method: "POST", body: JSON.stringify(draft) })
      setDraft({ ...draft, ms: "", en: "", zh: "", ta: "" })
      notify({ tone: "success", text: "Diet tip saved. It replaces any earlier tip for that food group." })
      await load()
    } catch (err) { notify({ tone: "error", text: err instanceof Error ? err.message : "The tip could not be saved." }) }
  }
  if (missing || !tips) return null
  return (
    <section className={homeStyles.peopleCard} aria-label="Diet tips">
      <SectionHeading eyebrow="Meal photos" title="Diet tips your patients can get" />
      <div className={styles.panel}>
        <p className={styles.lead}>When a patient sends a photo of a meal, AI only names the food groups. The patient gets back these tips, word for word, for the groups found. No calorie counts and no advice of the AI&apos;s own.</p>
        <ul className={styles.log}>
          {tips.map((t) => <li key={t.id}>
            <div className={styles.rowBetween}><strong><MessageSquareText size={14} aria-hidden="true" /> {t.label}</strong>
              {canEdit && <button className="button-quiet" aria-label={`Retire the ${t.label} tip`} onClick={() => void apiRequest(`/api/clinic/diet-tips/${t.id}`, { method: "DELETE" }).then(load)}><Trash2 size={14} aria-hidden="true" /></button>}</div>
            <span>{t.en}</span>
            <span className={styles.muted} lang="ms">{t.ms}</span>
          </li>)}
          {tips.length === 0 && <li className={styles.muted}>No tips yet: meal photos get a polite &ldquo;ask at your next visit&rdquo;.</li>}
        </ul>
        {canEdit && <form className={styles.releaseForm} onSubmit={save}>
          <label className={styles.field}>Food group
            <select value={draft.category} onChange={(e) => setDraft({ ...draft, category: e.target.value })}>
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className={styles.field}>Bahasa Melayu<textarea rows={2} maxLength={1000} required value={draft.ms} onChange={(e) => setDraft({ ...draft, ms: e.target.value })} /></label>
          <label className={styles.field}>English<textarea rows={2} maxLength={1000} required value={draft.en} onChange={(e) => setDraft({ ...draft, en: e.target.value })} /></label>
          <label className={styles.field}>中文 (optional)<textarea rows={2} maxLength={1000} value={draft.zh} onChange={(e) => setDraft({ ...draft, zh: e.target.value })} /></label>
          <label className={styles.field}>தமிழ் (optional)<textarea rows={2} maxLength={1000} value={draft.ta} onChange={(e) => setDraft({ ...draft, ta: e.target.value })} /></label>
          <button className="button-secondary" disabled={!draft.ms.trim() || !draft.en.trim()}><Plus size={14} aria-hidden="true" /> Save tip</button>
        </form>}
      </div>
    </section>
  )
}
