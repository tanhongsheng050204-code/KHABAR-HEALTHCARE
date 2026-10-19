"use client"

import { useCallback, useEffect, useState } from "react"
import { Check, GraduationCap, X } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { useUi } from "@/lib/i18n"
import type { TeachBackOutcome, TeachBackQuestion, TeachBackQuiz } from "@/lib/types"
import { SectionHeading } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

/**
 * "Check you understood your plan": up to three questions from the doctor's own plan, with the right answer
 * straight after. A memory aid, not a test; the clinic sees which parts to explain again.
 */
export function TeachBackPanel({ notify }: { notify: (n: Notice) => void }) {
  const { t } = useUi()
  const [quiz, setQuiz] = useState<TeachBackQuiz | null>(null)
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [outcome, setOutcome] = useState<TeachBackOutcome | null>(null)
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    try { setQuiz(await apiRequest<TeachBackQuiz>("/api/me/teach-back")) }
    catch (e) { if (!featureMissing(e)) notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }, [notify, t])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  const question = (q: TeachBackQuestion) => {
    const medicine = q.medicine ?? ""
    if (q.kind === "TIMES_PER_DAY") return t("How many times a day do you take {medicine}?", { medicine })
    if (q.kind === "FOOD") return t("Do you take {medicine} before or after food?", { medicine })
    return t("When should your next visit be?")
  }
  const option = (q: TeachBackQuestion, value: string | null) => {
    if (value === null) return t("No answer")
    if (q.kind === "TIMES_PER_DAY") return t("{n} times a day", { n: value })
    if (q.kind === "FOOD") return value === "before_food" ? t("Before food") : value === "after_food" ? t("After food") : t("It does not matter")
    return t("In about {n} weeks", { n: value })
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!quiz) return
    setBusy(true)
    try {
      setOutcome(await apiRequest<TeachBackOutcome>("/api/me/teach-back", { method: "POST", body: JSON.stringify({ encounterId: quiz.encounterId, answers }) }))
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") })
    } finally {
      setBusy(false)
    }
  }

  if (!quiz || quiz.questions.length === 0) return null
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="teach-back-title">
      <SectionHeading eyebrow={t("Your plan")} title={t("Check you understood your plan")} />
      <div className={styles.panel}>
        <p className={styles.lead} id="teach-back-title">{t("This is to help you remember your plan. It is not a test. If anything is unclear, ask your clinic.")}</p>
        {outcome ? <>
          <strong>{t("{correct} of {total} match your plan", { correct: outcome.correct, total: outcome.total })}</strong>
          <ul className={styles.log}>
            {outcome.results.map((r) => {
              const q = quiz.questions.find((x) => x.id === r.id)
              if (!q) return null
              return <li key={r.id}>
                <span>{r.correct ? <Check size={14} aria-hidden="true" /> : <X size={14} aria-hidden="true" />} {question(q)}</span>
                <span>{r.correct ? t("Yes: {answer}", { answer: option(q, r.answer) }) : t("Your plan says: {answer}", { answer: option(q, r.answer) })}</span>
              </li>
            })}
          </ul>
          <button className="button-secondary" onClick={() => { setOutcome(null); setAnswers({}); void load() }}>{t("Try again")}</button>
        </> : <form className={styles.releaseForm} onSubmit={submit}>
          {quiz.questions.map((q) => <fieldset key={q.id} className={styles.question}>
            <legend>{question(q)}</legend>
            <div className={styles.row}>
              {q.options.map((value) => <label key={value} className={styles.choice}>
                <input type="radio" name={q.id} value={value} checked={answers[q.id] === value}
                  onChange={() => setAnswers({ ...answers, [q.id]: value })} />{option(q, value)}
              </label>)}
            </div>
          </fieldset>)}
          <button className="button-primary" disabled={busy || Object.keys(answers).length < quiz.questions.length}>
            <GraduationCap size={15} aria-hidden="true" /> {t("Check my answers")}
          </button>
        </form>}
      </div>
    </section>
  )
}
