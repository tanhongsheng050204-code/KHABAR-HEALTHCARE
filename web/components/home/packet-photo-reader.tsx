"use client"

import { useState } from "react"
import { Camera, LoaderCircle, Plus, ScanLine } from "lucide-react"
import { apiRequest } from "@/lib/api"
import styles from "./packet-photo-reader.module.css"

type Candidate = {
  kind: "MEDICINE" | "HERB" | "UNSURE"
  brand: string
  ingredient_text: string
  strength: string
  form: string
  confidence: "high" | "medium" | "low"
  evidence: string
  generic_candidate: string | null
  review_warning: string
}
type ReadResult = { candidates: Candidate[]; unreadable: boolean; message: string }

const MAX_BYTES = 8 * 1024 * 1024
const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/webp"]

export function PacketPhotoReader({ patientId, onAdded }: { patientId: string; onAdded: () => Promise<void> }) {
  const [file, setFile] = useState<File | null>(null)
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ReadResult | null>(null)
  const [error, setError] = useState("")
  const [adding, setAdding] = useState<string | null>(null)

  function choose(next: File | null) {
    setResult(null)
    setError("")
    setFile(next)
    setConsent(false)
    if (next && (!ALLOWED_TYPES.includes(next.type) || next.size > MAX_BYTES)) {
      setError("Choose a JPEG, PNG, or WebP image no larger than 8 MB.")
      setFile(null)
    }
  }

  async function scan(event: React.FormEvent) {
    event.preventDefault()
    if (!file || !consent) return
    setBusy(true)
    setError("")
    setResult(null)
    try {
      const body = new FormData()
      body.set("image", file)
      body.set("consentConfirmed", "true")
      const read = await apiRequest<ReadResult>(`/api/patients/${patientId}/medications/packet-photo`, { method: "POST", body })
      setResult(read)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The packet could not be read. You can type the medicine instead.")
    } finally {
      setBusy(false)
    }
  }

  async function add(candidate: Candidate, candidateKey: string, selectedKind: "MEDICINE" | "HERB") {
    const name = candidate.generic_candidate || candidate.ingredient_text || candidate.brand
    if (!name) return
    setAdding(candidateKey)
    setError("")
    try {
      await apiRequest(`/api/patients/${patientId}/medications`, {
        method: "POST",
        body: JSON.stringify({ name: [name, candidate.strength].filter(Boolean).join(" "), kind: selectedKind, source: "Packet photo — please verify" }),
      })
      await onAdded()
      setResult((current) => current ? { ...current, candidates: current.candidates.filter((item) => item !== candidate) } : current)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The medicine could not be added.")
    } finally {
      setAdding(null)
    }
  }

  return <section className={styles.card} aria-labelledby="packet-reader-title">
    <div className={styles.heading}><span><ScanLine size={18}/></span><div><h3 id="packet-reader-title">Read a medicine packet</h3><p>Extract visible label text, then review it before adding anything.</p></div></div>
    <form onSubmit={scan} className={styles.form}>
      <label className={styles.fileLabel}><Camera size={17}/><span>{file ? file.name : "Choose a packet photo"}</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => choose(event.target.files?.[0] ?? null)}/></label>
      <label className={styles.consent}><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)}/><span>I agree to send this image to the clinic’s configured Google Gemini service for temporary label reading. Khabar does not save the image. Use fictional/demo packets unless your clinic has approved real-patient use.</span></label>
      <button className="button-secondary" type="submit" disabled={!file || !consent || busy}>{busy ? <LoaderCircle className={styles.spin} size={16}/> : <ScanLine size={16}/>} Read label</button>
    </form>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {result && <div className={styles.results} aria-live="polite">
      {result.candidates.length ? result.candidates.map((item, index) => {
        const key = `${item.brand}-${index}`
        return <article className={styles.candidate} key={key}>
          <div><strong>{item.brand || item.ingredient_text || "Unidentified packet"}</strong><span>{[item.generic_candidate, item.strength, item.form].filter(Boolean).join(" · ") || "No generic match"}</span></div>
          <p>Read from label: “{item.evidence || "No clear text evidence"}”</p>
          <small>Reading confidence: {item.confidence}. Confirm the packet and medicine with your clinic; this is not a safety check.</small>
          <label className={styles.kindChoice}>Review item type<select value={item.kind === "UNSURE" ? "MEDICINE" : item.kind} onChange={(event) => setResult((current) => current ? { ...current, candidates: current.candidates.map((candidate) => candidate === item ? { ...candidate, kind: event.target.value as Candidate["kind"] } : candidate) } : current)}><option value="MEDICINE">Medicine</option><option value="HERB">Herb, supplement, or remedy</option></select></label>
          <button className="button-secondary" type="button" disabled={adding !== null} onClick={() => void add(item, key, item.kind === "HERB" ? "HERB" : "MEDICINE")}>{adding === key ? <LoaderCircle className={styles.spin} size={15}/> : <Plus size={15}/>} Add after review</button>
        </article>
      }) : <p className={styles.empty}>{result.message || "No clear medicine label found. Try a sharper photo or type the name instead."}</p>}
    </div>}
  </section>
}
