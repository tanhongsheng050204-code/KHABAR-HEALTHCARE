"use client"

import { useRef, useState } from "react"
import { AlertTriangle, Clock, History, UserRound } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { Assignee, CallItem, CaseEvent, ClosureReason, Coverage, FollowUpCase, Me } from "@/lib/types"
import styles from "./case-controls.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

export const STATUS_WORDS: Record<string, string> = {
  NEW: "New",
  ASSIGNED: "Assigned",
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
  ESCALATED: "Escalated",
  UNABLE_TO_CONTACT: "Unable to contact",
  RESOLVED: "Resolved",
}

const CLOSURE_WORDS: Record<Exclude<ClosureReason, "CONTACT_RECORDED">, string> = {
  CONTACTED_NO_FURTHER_ACTION: "Contacted, no further action",
  ADVICE_GIVEN_PER_PROTOCOL: "Advice given per clinic protocol",
  APPOINTMENT_ARRANGED: "Appointment arranged",
  DOCTOR_REVIEWED: "Doctor reviewed",
  REFERRED_TO_EMERGENCY_CARE: "Referred to emergency care",
  UNABLE_TO_CONTACT_AFTER_ATTEMPTS: "Unable to contact after attempts",
  DUPLICATE_OR_FALSE_ALARM: "Duplicate or false alarm",
}

export type QueueFilter = "ALL" | "MINE" | "UNASSIGNED" | "OVERDUE" | "URGENT"

const FILTER_WORDS: Record<QueueFilter, string> = {
  ALL: "All",
  MINE: "Assigned to me",
  UNASSIGNED: "Unassigned",
  OVERDUE: "Overdue",
  URGENT: "Urgent",
}

export function filterItems(items: CallItem[], filter: QueueFilter, me: Me): CallItem[] {
  return items.filter((item) => {
    const c = item.followUpCase
    switch (filter) {
      case "MINE":
        return c?.ownerId === me.id
      case "UNASSIGNED":
        return !c?.ownerId
      case "OVERDUE":
        return !!c?.overdue
      case "URGENT":
        return item.level === "RED"
      default:
        return true
    }
  })
}

export function QueueFilters({ value, onChange, items, me }: { value: QueueFilter; onChange: (f: QueueFilter) => void; items: CallItem[]; me: Me }) {
  return (
    <div className={styles.filters} role="group" aria-label="Filter the call list">
      {(Object.keys(FILTER_WORDS) as QueueFilter[]).map((f) => (
        <button key={f} type="button" aria-pressed={value === f} onClick={() => onChange(f)}>
          {FILTER_WORDS[f]} ({filterItems(items, f, me).length})
        </button>
      ))}
    </div>
  )
}

/** Who owns follow-up today. A missing rota is said plainly, never hidden. */
export function CoverageBanner({ coverage }: { coverage?: Coverage }) {
  if (!coverage) return null
  return (
    <p className={`${styles.coverage} ${coverage.warning ? styles.coverageWarning : ""}`} role="note">
      {coverage.onDuty ? (
        <span>
          <strong>On duty today:</strong> {coverage.onDuty}
          {coverage.backup ? ` · backup ${coverage.backup}` : ""}
        </span>
      ) : null}
      {coverage.escalationContact && (
        <span>
          <strong>Escalation:</strong> {coverage.escalationContact}
        </span>
      )}
      {coverage.warning && (
        <span>
          <AlertTriangle size={14} aria-hidden="true" /> {coverage.warning}
        </span>
      )}
    </p>
  )
}

/** Status, owner and every lifecycle action for one follow-up case. */
export function CaseControls({ item, snapshotAt, me, assignees, onChanged, notify }: {
  item: CallItem
  snapshotAt: string
  me: Me
  assignees: Assignee[]
  onChanged: () => Promise<void> | void
  notify: (n: Notice | null) => void
}) {
  const c = item.followUpCase
  const [busy, setBusy] = useState(false)
  const [history, setHistory] = useState<CaseEvent[] | null>(null)
  const [dialog, setDialog] = useState<"close" | "escalate" | null>(null)
  const [reason, setReason] = useState<Exclude<ClosureReason, "CONTACT_RECORDED">>("CONTACTED_NO_FURTHER_ACTION")
  const [note, setNote] = useState("")
  const [escalateTo, setEscalateTo] = useState("")
  const [dialogError, setDialogError] = useState("")
  const dialogRef = useRef<HTMLDialogElement>(null)
  const openerRef = useRef<HTMLButtonElement | null>(null)

  if (!c) return null
  const followUpCase: FollowUpCase = c
  const doctors = assignees.filter((a) => a.roles.includes("DOCTOR"))
  const urgent = followUpCase.level === "RED"

  async function run(path: string, body: object | null, done: string) {
    setBusy(true)
    try {
      await apiRequest(`/api/clinic/cases/${followUpCase.id}/${path}`, { method: "POST", body: JSON.stringify(body ?? {}) })
      notify({ tone: "success", text: done })
      setHistory(null)
      await onChanged()
      return true
    } catch (error) {
      const text = error instanceof Error ? error.message : "The case could not be updated."
      if (dialog) setDialogError(text)
      else notify({ tone: "error", text })
      return false
    } finally {
      setBusy(false)
    }
  }

  function open(kind: "close" | "escalate", opener: HTMLButtonElement) {
    openerRef.current = opener
    setDialog(kind)
    setNote("")
    setDialogError("")
    setReason("CONTACTED_NO_FURTHER_ACTION")
    setEscalateTo(doctors[0]?.userId ?? "")
    requestAnimationFrame(() => dialogRef.current?.showModal())
  }

  function shut() {
    dialogRef.current?.close()
    setDialog(null)
    openerRef.current?.focus()
  }

  async function toggleHistory() {
    if (history) return setHistory(null)
    try {
      const result = await apiRequest<{ events: CaseEvent[] }>(`/api/clinic/cases/${followUpCase.id}`)
      setHistory(result.events)
    } catch (error) {
      notify({ tone: "error", text: error instanceof Error ? error.message : "History could not be loaded." })
    }
  }

  const mine = followUpCase.ownerId === me.id

  return (
    <div>
      <div className={styles.caseBar}>
        <span className={styles.status}>{STATUS_WORDS[followUpCase.status] ?? followUpCase.status}</span>
        <span>
          <UserRound size={13} aria-hidden="true" /> {followUpCase.ownerName ? (mine ? "You" : followUpCase.ownerName) : "Unassigned"}
        </span>
        {followUpCase.overdue ? (
          <span className={styles.overdue}>
            <Clock size={13} aria-hidden="true" /> Overdue: acknowledge was due{" "}
            {new Date(followUpCase.acknowledgeBy).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
          </span>
        ) : !followUpCase.acknowledgedAt ? (
          <span>
            Acknowledge by {new Date(followUpCase.acknowledgeBy).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
          </span>
        ) : null}
        {followUpCase.contactAttempts > 0 && <span>{followUpCase.contactAttempts} call attempt{followUpCase.contactAttempts === 1 ? "" : "s"}</span>}
        {followUpCase.escalatedTo && <span>Escalated to {followUpCase.escalatedTo}</span>}
      </div>
      <div className={styles.actions}>
        {!mine && (
          <button className="button-secondary" disabled={busy} onClick={() => void run("assign", {}, "The case is assigned to you.")}>
            Assign to me
          </button>
        )}
        {!followUpCase.acknowledgedAt && (
          <button className="button-secondary" disabled={busy} onClick={() => void run("acknowledge", null, "Case acknowledged.")}>
            Acknowledge
          </button>
        )}
        <button className="button-secondary" disabled={busy} onClick={() => void run("contact", { outcome: "REACHED" }, "Call recorded: reached.")}>
          Reached
        </button>
        <button className="button-secondary" disabled={busy} onClick={() => void run("contact", { outcome: "NO_ANSWER" }, "Call recorded: no answer.")}>
          No answer
        </button>
        <button className="button-secondary" disabled={busy} onClick={(e) => open("escalate", e.currentTarget)}>
          Escalate
        </button>
        <button className="button-primary" disabled={busy} onClick={(e) => open("close", e.currentTarget)}>
          Close case
        </button>
        <button className="button-quiet" aria-expanded={!!history} onClick={() => void toggleHistory()}>
          <History size={14} aria-hidden="true" /> History
        </button>
      </div>
      {history && (
        <ol className={styles.history} aria-label={`History of ${item.fullName}'s case`}>
          {history.map((e, i) => (
            <li key={i}>
              <strong>{new Date(e.at).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}</strong> · {e.by} ·{" "}
              {e.action.replaceAll("_", " ").toLowerCase()}
              {e.note ? ` — ${e.note}` : ""}
            </li>
          ))}
        </ol>
      )}

      <dialog ref={dialogRef} className={styles.dialog} aria-labelledby={`case-dialog-${followUpCase.id}`} onClose={() => setDialog(null)} onCancel={shut}>
        {dialog === "close" && (
          <form
            onSubmit={async (e) => {
              e.preventDefault()
              if (await run("close", { reason, note, observedThrough: snapshotAt }, "Case closed. Anything newer stays open.")) shut()
            }}
          >
            <h2 id={`case-dialog-${followUpCase.id}`}>Close {item.fullName}&apos;s case</h2>
            <p>This resolves the replies, readings and check-ins shown in the last refresh. Anything newer stays open.</p>
            {dialogError && <p className={styles.dialogError} role="alert">{dialogError}</p>}
            <label>
              Why is it being closed?
              <select value={reason} onChange={(e) => setReason(e.target.value as typeof reason)}>
                {Object.entries(CLOSURE_WORDS).map(([value, words]) => (
                  <option key={value} value={value}>{words}</option>
                ))}
              </select>
            </label>
            <label>
              Note{urgent ? " (required for an urgent case, at least 10 characters)" : " (optional)"}
              <textarea rows={3} maxLength={1000} required={urgent} minLength={urgent ? 10 : undefined} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <div className={styles.dialogActions}>
              <button type="button" className="button-secondary" onClick={shut}>Cancel</button>
              <button className="button-primary" disabled={busy}>{busy ? "Closing…" : "Close case"}</button>
            </div>
          </form>
        )}
        {dialog === "escalate" && (
          <form
            onSubmit={async (e) => {
              e.preventDefault()
              if (await run("escalate", { note, toUserId: escalateTo || null }, "Case escalated.")) shut()
            }}
          >
            <h2 id={`case-dialog-${followUpCase.id}`}>Escalate {item.fullName}&apos;s case</h2>
            <p>Follow your clinic&apos;s escalation route. Urgent symptoms still need the patient to call 999.</p>
            {dialogError && <p className={styles.dialogError} role="alert">{dialogError}</p>}
            <label>
              Escalate to
              <select value={escalateTo} onChange={(e) => setEscalateTo(e.target.value)}>
                <option value="">The clinic&apos;s escalation contact</option>
                {doctors.map((d) => (
                  <option key={d.userId} value={d.userId}>{d.displayName}</option>
                ))}
              </select>
            </label>
            <label>
              Why?
              <textarea rows={3} maxLength={1000} required value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <div className={styles.dialogActions}>
              <button type="button" className="button-secondary" onClick={shut}>Cancel</button>
              <button className="button-primary" disabled={busy}>{busy ? "Escalating…" : "Escalate"}</button>
            </div>
          </form>
        )}
      </dialog>
    </div>
  )
}
