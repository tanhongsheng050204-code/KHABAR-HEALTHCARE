"use client";

import { useEffect, useState } from "react";
import { Square, Volume2 } from "lucide-react";
import type { SummaryLanguage } from "@/lib/types";

// Preferred regional voices first; any voice of the same language is the fallback.
const PREFERRED: Record<SummaryLanguage, string[]> = {
  en: ["en-MY", "en-SG", "en-GB", "en-US"],
  ms: ["ms-MY", "ms-SG"],
  zh: ["zh-CN", "zh-SG", "zh-MY"],
  ta: ["ta-MY", "ta-SG", "ta-IN"],
};

const LANGUAGE_NAME: Record<SummaryLanguage, string> = {
  en: "English",
  ms: "Bahasa Melayu",
  zh: "Chinese",
  ta: "Tamil",
};

function voiceFor(
  voices: SpeechSynthesisVoice[],
  language: SummaryLanguage,
): SpeechSynthesisVoice | null {
  const tag = (voice: SpeechSynthesisVoice) => voice.lang.replace("_", "-");
  for (const wanted of PREFERRED[language]) {
    const exact = voices.find((v) => tag(v).toLowerCase() === wanted.toLowerCase());
    if (exact) return exact;
  }
  return voices.find((v) => tag(v).toLowerCase().split("-")[0] === language) ?? null;
}

/**
 * Reads the approved summary aloud with the device's own speech engine, for patients who find
 * reading hard. It speaks the approved text only, and only with a voice in the summary's own
 * language: another language's voice would mispronounce medicine instructions, so none is used.
 */
export function ListenButton({ text, language }: { text: string; language: SummaryLanguage }) {
  // undefined while the device is still listing its voices. Rendered only after the summary has
  // loaded in the browser, so reading `window` here is safe.
  const [voice, setVoice] = useState<SpeechSynthesisVoice | null | undefined>(() =>
    "speechSynthesis" in window ? undefined : null,
  );
  const [speaking, setSpeaking] = useState(false);

  useEffect(() => {
    if (!("speechSynthesis" in window)) return;
    const synth = window.speechSynthesis;
    const pick = () => {
      const voices = synth.getVoices();
      if (voices.length) setVoice(voiceFor(voices, language));
    };
    const firstLook = window.setTimeout(pick, 0);
    synth.addEventListener("voiceschanged", pick);
    // Some browsers list no voices and never fire voiceschanged.
    const giveUp = window.setTimeout(() => setVoice((v) => (v === undefined ? null : v)), 2000);
    return () => {
      synth.removeEventListener("voiceschanged", pick);
      window.clearTimeout(firstLook);
      window.clearTimeout(giveUp);
      synth.cancel();
    };
  }, [language]);

  if (voice === undefined) return null;
  if (voice === null)
    return (
      <small style={{ color: "var(--ink-soft)" }}>
        Read aloud needs a {LANGUAGE_NAME[language]} voice on this device.
      </small>
    );

  function toggle() {
    const synth = window.speechSynthesis;
    synth.cancel();
    if (speaking || !voice) {
      setSpeaking(false);
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.voice = voice;
    utterance.lang = voice.lang;
    utterance.rate = 0.9;
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    synth.speak(utterance);
  }

  return (
    <button className="button-quiet" type="button" onClick={toggle} aria-pressed={speaking}>
      {speaking ? <Square size={15} /> : <Volume2 size={15} />}
      {speaking ? "Stop reading" : "Listen to this plan"}
    </button>
  );
}
