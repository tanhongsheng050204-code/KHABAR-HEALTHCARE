"use client"

import { Fragment, useCallback, useEffect, useState } from "react"
import { EyeOff } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { AiReceipt } from "@/lib/types"
import { EmptyState, SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

const SPLIT = /(\[NAME\]|\[IC\]|\[PHONE\])/
const IS_TOKEN = /^\[(NAME|IC|PHONE)\]$/

/** Shows the text exactly as sent, with the replaced identifiers picked out. */
function Sent({ text }: { text: string }) {
  return <pre className={styles.sent}>{text.split(SPLIT).map((part, i) => IS_TOKEN.test(part)
    ? <span className={styles.token} key={i}>{part}</span>
    : <Fragment key={i}>{part}</Fragment>)}</pre>
}

export type ReceiptsCopy = {
  eyebrow: string; title: string; lead: string; empty: string; emptyCopy: string; removed: (n: number, ic: number, phone: number) => string; media: string; cut: string
  /** Translates the server's English purpose label. */
  purpose?: (label: string) => string
}

export const RECEIPTS_EN: ReceiptsCopy = {
  eyebrow: "Privacy",
  title: "What the AI saw",
  lead: "Every time this patient's words, a photo or a recording left the clinical system for the AI service. Names, IC and phone numbers are replaced before anything is sent.",
  empty: "Nothing sent yet",
  emptyCopy: "No text about this patient has gone to the AI service.",
  removed: (n, ic, phone) => `${n} name${n === 1 ? "" : "s"}, ${ic} IC, ${phone} phone removed`,
  media: "Photo or recording, not kept",
  cut: "Shortened here; the full text was sent.",
}

/** "What the AI saw" for one patient: for the patient themself and their clinic's doctors. */
export function AiReceiptsPanel({ patientId, copy = RECEIPTS_EN, locale = "en-MY" }: { patientId: string; copy?: ReceiptsCopy; locale?: string }) {
  const [items, setItems] = useState<AiReceipt[] | null>(null)
  const [error, setError] = useState("")
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setItems(await apiRequest<AiReceipt[]>(`/api/patients/${patientId}/ai-receipts`)) }
    catch (e) { if (featureMissing(e)) setMissing(true); else setError(e instanceof Error ? e.message : "The AI receipts could not be loaded.") }
  }, [patientId])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  if (missing) return null
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="ai-receipts-title">
      <SectionHeading eyebrow={copy.eyebrow} title={copy.title} />
      <div className={styles.panel}>
        <p className={styles.lead} id="ai-receipts-title">{copy.lead}</p>
        {error && <p role="alert">{error}</p>}
        {items && items.length === 0 && <EmptyState icon={<EyeOff size={20} />} title={copy.empty} copy={copy.emptyCopy} />}
        {items && items.length > 0 && <ul className={styles.receipts}>
          {items.map((r) => <li key={r.id}><details>
            <summary><strong>{copy.purpose ? copy.purpose(r.purposeLabel) : r.purposeLabel}</strong><span>{new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(r.at))}</span></summary>
            <div className={styles.counts}>
              {r.media
                ? <StatusBadge level="review">{copy.media}</StatusBadge>
                : <StatusBadge level="ok">{copy.removed(r.namesRemoved, r.icsRemoved, r.phonesRemoved)}</StatusBadge>}
              <StatusBadge level="review">{r.endpoint}</StatusBadge>
            </div>
            <Sent text={r.sent} />
            {r.truncated && <p className={`${styles.muted} ${styles.cut}`}>{copy.cut}</p>}
          </details></li>)}
        </ul>}
      </div>
    </section>
  )
}
