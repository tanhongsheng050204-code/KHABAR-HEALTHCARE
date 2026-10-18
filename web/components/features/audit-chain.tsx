"use client"

import { useState } from "react"
import { Link2, ShieldCheck, ShieldX } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { AuditVerification } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

/** Recomputes every link of the record-access chain on demand and says whether it is intact. */
export function AuditChainPanel() {
  const [result, setResult] = useState<AuditVerification | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  async function verify() {
    setBusy(true)
    setError("")
    try { setResult(await apiRequest<AuditVerification>("/api/clinic/audit/verify")) }
    catch (e) { setError(e instanceof Error ? e.message : "The chain could not be checked.") }
    finally { setBusy(false) }
  }
  return (
    <section className={homeStyles.peopleCard} id="audit-chain">
      <SectionHeading eyebrow="Trust" title="Tamper-evident access log"
        action={<button className="button-secondary" disabled={busy} onClick={() => void verify()}><Link2 size={15} aria-hidden="true" /> {busy ? "Checking…" : "Verify the chain"}</button>} />
      <div className={styles.panel}>
        <p className={styles.lead}>Every look at a patient&apos;s record is sealed into a chain: each entry carries a keyed hash of the one before it, so changing or deleting any entry breaks every link after it. Once a day the newest link is copied outside the database, into the project&apos;s repository.</p>
        {error && <p role="alert">{error}</p>}
        {result && (result.intact
          ? <div className={`${styles.banner} ${styles.bannerCalm}`}><ShieldCheck size={20} aria-hidden="true" /><div className={styles.panelFlat}>
              <strong>{result.checked ? `Intact: all ${result.checked.toLocaleString()} sealed entries check out.` : "Intact. No record access has been sealed yet."}</strong>
              <span className={styles.muted}>{result.unsealed ? `${result.unsealed} new entr${result.unsealed === 1 ? "y waits" : "ies wait"} to be sealed (within a minute).` : "Nothing is waiting to be sealed."} Checked {new Date(result.checkedAt).toLocaleString()}.</span>
              <span className={styles.hash}>Newest link #{result.tailSeq}: {result.tailHash}</span>
            </div></div>
          : <div className={styles.banner}><ShieldX size={20} aria-hidden="true" /><div className={styles.panelFlat}>
              <strong>Broken at entry #{result.brokenAt}</strong>
              <span>{result.problem}</span>
              <StatusBadge level="red">investigate as a possible breach</StatusBadge>
            </div></div>)}
      </div>
    </section>
  )
}
