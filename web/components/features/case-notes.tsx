"use client"

import { Fragment, useCallback, useEffect, useState } from "react"
import { AtSign } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { Assignee, CaseNote, Mentions } from "@/lib/types"
import { SectionHeading } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

function Highlighted({ text, names }: { text: string; names: string[] }) {
  if (!names.length) return <>{text}</>
  const escaped = names.map((n) => `@${n}`.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
  const parts = text.split(new RegExp(`(${escaped.join("|")})`, "i"))
  return <>{parts.map((p, i) => names.some((n) => `@${n}`.toLowerCase() === p.toLowerCase())
    ? <span className={styles.mention} key={i}>{p}</span> : <Fragment key={i}>{p}</Fragment>)}</>
}

/** Team notes on one follow-up case. Type @ and a colleague's name to mention them. */
export function CaseNotes({ caseId, assignees }: { caseId: string; assignees: Assignee[] }) {
  const [notes, setNotes] = useState<CaseNote[]>([])
  const [text, setText] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setNotes(await apiRequest<CaseNote[]>(`/api/clinic/cases/${caseId}/notes`)) }
    catch (e) { if (featureMissing(e)) setMissing(true); else setError(e instanceof Error ? e.message : "Notes could not be loaded.") }
  }, [caseId])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function add(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError("")
    try {
      await apiRequest(`/api/clinic/cases/${caseId}/notes`, { method: "POST", body: JSON.stringify({ text: text.trim() }) })
      setText("")
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : "The note could not be saved.")
    } finally {
      setBusy(false)
    }
  }
  if (missing) return null
  return (
    <div className={styles.panelFlat}>
      <strong>Team notes</strong>
      {notes.length === 0 ? <p className={styles.muted}>No notes yet.</p> : <ul className={styles.notes}>
        {notes.map((n) => <li key={n.id}><small>{n.by} · {new Date(n.at).toLocaleString()}</small><Highlighted text={n.text} names={n.mentions.map((m) => m.displayName)} /></li>)}
      </ul>}
      <form className={styles.releaseForm} onSubmit={(e) => void add(e)}>
        <label className={styles.field}>Add a note for the team
          <textarea rows={2} maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} placeholder="e.g. @Nurse Mei she works mornings, call after 3pm." />
        </label>
        {assignees.length > 0 && <div className={styles.row}>
          <span className={styles.muted}>Mention:</span>
          {assignees.map((a) => <button type="button" className="button-quiet" key={a.userId} onClick={() => setText((t) => `${t}${t && !t.endsWith(" ") ? " " : ""}@${a.displayName} `)}>@{a.displayName}</button>)}
        </div>}
        {error && <p role="alert">{error}</p>}
        <button className="button-secondary" disabled={busy || !text.trim()}>{busy ? "Saving…" : "Add note"}</button>
      </form>
    </div>
  )
}

/** Notes that mention me, newest first. Opening the panel marks them read. */
export function MentionsPanel() {
  const [mentions, setMentions] = useState<Mentions | null>(null)
  const load = useCallback(async () => {
    try { setMentions(await apiRequest<Mentions>("/api/clinic/mentions")) } catch { setMentions(null) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  if (!mentions || mentions.items.length === 0) return null
  return (
    <section className={homeStyles.peopleCard} id="mentions" aria-label="Mentions">
      <SectionHeading eyebrow={mentions.unread ? `${mentions.unread} new` : "Team notes"} title="Mentions"
        action={mentions.unread ? <button className="button-quiet" onClick={() => void apiRequest<Mentions>("/api/clinic/mentions/seen", { method: "POST" }).then(setMentions)}>Mark all read</button> : undefined} />
      <div className={styles.panel}>
        <ul className={styles.notes}>
          {mentions.items.map((m) => <li key={m.noteId}>
            <small>{m.unread && <span className={styles.unreadDot} aria-label="New" />}<AtSign size={12} aria-hidden="true" /> {m.by} on {m.patientName ?? "a case"} · {new Date(m.at).toLocaleString()}</small>
            {m.text}
          </li>)}
        </ul>
      </div>
    </section>
  )
}
