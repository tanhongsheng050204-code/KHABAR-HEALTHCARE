"use client"

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react"
import { UI_TEXT } from "./ui-text"

/**
 * The patient and caregiver screens in Bahasa Melayu, Chinese, Tamil or English. Each English phrase is its own
 * key (see ui-text.ts); a phrase without a translation falls back to English rather than showing a key. The
 * patient's registered language is the default; the choice made with the switcher is remembered on this device.
 *
 * The Chinese and Tamil wording was drafted for this prototype and needs a fluent reader's review, like the rest
 * of Khabar's patient-facing text.
 */
export type UiLang = "en" | "ms" | "zh" | "ta"

export const UI_LANGS: { code: UiLang; label: string }[] = [
  { code: "ms", label: "BM" },
  { code: "en", label: "EN" },
  { code: "zh", label: "中文" },
  { code: "ta", label: "தமிழ்" },
]

const LOCALE: Record<UiLang, string> = { en: "en-MY", ms: "ms-MY", zh: "zh-MY", ta: "ta-MY" }
const STORAGE_KEY = "khabar.uiLang"

export type Translate = (phrase: string, vars?: Record<string, string | number>) => string

export function translate(lang: UiLang, phrase: string, vars?: Record<string, string | number>): string {
  const row = UI_TEXT[phrase]
  const translated = !row ? undefined : lang === "ms" ? row[0] : lang === "zh" ? row[1] : lang === "ta" ? row[2] : undefined
  let text = translated || phrase
  if (vars) {
    for (const [key, value] of Object.entries(vars)) text = text.replaceAll(`{${key}}`, String(value))
  }
  return text
}

function asLang(value: string | null | undefined): UiLang | null {
  return value === "en" || value === "ms" || value === "zh" || value === "ta" ? value : null
}

type Ui = { lang: UiLang; locale: string; t: Translate; setLang: (lang: UiLang) => void }

const english: Ui = { lang: "en", locale: LOCALE.en, t: (p, v) => translate("en", p, v), setLang: () => {} }
const UiContext = createContext<Ui>(english)

export function UiLanguageProvider({ preferred, children }: { preferred?: string | null; children: ReactNode }) {
  const [lang, setLangState] = useState<UiLang>(asLang(preferred) ?? "en")
  useEffect(() => {
    let stored: UiLang | null = null
    try { stored = asLang(localStorage.getItem(STORAGE_KEY)) } catch { /* storage may be blocked; keep the default */ }
    if (stored) {
      const timer = window.setTimeout(() => setLangState(stored), 0)
      return () => window.clearTimeout(timer)
    }
  }, [])
  useEffect(() => {
    document.documentElement.lang = lang
    return () => { document.documentElement.lang = "en" }
  }, [lang])
  const setLang = useCallback((next: UiLang) => {
    setLangState(next)
    try { localStorage.setItem(STORAGE_KEY, next) } catch { /* the choice still holds for this visit */ }
  }, [])
  const value = useMemo<Ui>(() => ({ lang, locale: LOCALE[lang], t: (p, v) => translate(lang, p, v), setLang }), [lang, setLang])
  return <UiContext.Provider value={value}>{children}</UiContext.Provider>
}

export function useUi(): Ui {
  return useContext(UiContext)
}

/** Four buttons, one per language, each labelled in its own script. */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { lang, setLang, t } = useUi()
  return (
    <div className={className} role="group" aria-label={t("Language")}>
      {UI_LANGS.map((l) => (
        <button key={l.code} type="button" lang={l.code} aria-pressed={lang === l.code} onClick={() => setLang(l.code)}>{l.label}</button>
      ))}
    </div>
  )
}
