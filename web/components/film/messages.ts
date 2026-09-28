/**
 * Patient-facing words on the landing page. The check-in and answers are copied from
 * services/api/.../messaging/PatientMessages.java, so the page shows exactly what the product sends;
 * change both together. zh and ta await fluent-reader review, as in the app.
 */
export type Lang = "ms" | "en" | "zh" | "ta";

export const LANGS: { id: Lang; label: string }[] = [
  { id: "ms", label: "BM" },
  { id: "zh", label: "中文" },
  { id: "ta", label: "தமிழ்" },
  { id: "en", label: "EN" },
];

const HTML_LANG: Record<Lang, string> = { ms: "ms", en: "en", zh: "zh-Hans", ta: "ta" };
export const htmlLang = (lang: Lang) => HTML_LANG[lang];

/** The hero's short greeting (page copy, not a product message). */
export const GREETING: Record<Lang, string> = {
  ms: "Apa khabar, Mak Cik?",
  en: "How are you today?",
  zh: "阿姨，今天好吗？",
  ta: "நலமா, அம்மா?",
};

/** PatientMessages.CHECK_IN */
export const CHECK_IN: Record<Lang, string> = {
  ms: "Apa khabar hari ini? Dah makan ubat? Balas mesej ini untuk beritahu klinik.",
  en: "How are you today? Have you taken your medicine? Reply to this message to let the clinic know.",
  zh: "今天感觉怎么样？吃药了吗？回复这条信息告诉诊所。",
  ta: "இன்று எப்படி இருக்கிறீர்கள்? மருந்து சாப்பிட்டீர்களா? இந்த செய்திக்கு பதில் அனுப்பி கிளினிக்கிற்கு தெரியப்படுத்துங்கள்.",
};

export type Reply = "ok" | "dizzy" | "chest";

/** What Aminah types (fictional replies). */
export const REPLY_TEXT: Record<Reply, Record<Lang, string>> = {
  ok: { ms: "Okay, sihat.", en: "I'm okay.", zh: "我很好。", ta: "நான் நலமாக இருக்கிறேன்." },
  dizzy: { ms: "Pening sikit hari ini.", en: "A bit dizzy today.", zh: "今天有点头晕。", ta: "இன்று கொஞ்சம் தலைச்சுற்றல்." },
  chest: { ms: "Sakit dada.", en: "Chest pain.", zh: "胸口痛。", ta: "நெஞ்சு வலி." },
};

export const MEDICINE_TAKEN: Record<Lang, string> = {
  ms: "Dah makan ubat.", en: "I've taken my medicine.", zh: "药已经吃了。", ta: "மருந்து சாப்பிட்டேன்.",
};
export const FEELING_BETTER: Record<Lang, string> = {
  ms: "Dah okay, terima kasih.", en: "Better now, thank you.", zh: "好多了，谢谢。", ta: "இப்போது பரவாயில்லை, நன்றி.",
};
export const THANK_YOU: Record<Lang, string> = {
  ms: "Terima kasih, Khabar.", en: "Thank you, Khabar.", zh: "谢谢你，Khabar。", ta: "நன்றி, Khabar.",
};

/** PatientMessages.THANKS */
const THANKS: Record<Lang, string> = {
  ms: "Terima kasih kerana memberitahu. Jaga diri!",
  en: "Thanks for letting us know. Take care!",
  zh: "谢谢您告诉我们。请保重！",
  ta: "தெரிவித்ததற்கு நன்றி. உடல்நலத்தைக் கவனித்துக் கொள்ளுங்கள்!",
};
/** PatientMessages.WAITING_FOR_REVIEW */
const WAITING: Record<Lang, string> = {
  ms: "Terima kasih. Mesej anda ada dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya.",
  en: "Thank you. Your message is in the clinic's follow-up list, but the clinic may not have seen it yet.",
  zh: "谢谢。您的消息已加入诊所的随访列表，但诊所可能还没有看到。",
  ta: "நன்றி. உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம்.",
};
/** PatientMessages.EMERGENCY_ADVICE */
const EMERGENCY: Record<Lang, string> = {
  ms: "Kalau sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
  en: "If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
  zh: "如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
  ta: "நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.",
};
/** PatientMessages.URGENT */
const URGENT: Record<Lang, string> = {
  ms: "Mesej anda telah dimasukkan dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya. Jika sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
  en: "Your message has been added to the clinic's follow-up list, but the clinic may not have seen it yet. If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
  zh: "您的消息已加入诊所的随访列表，但诊所可能还没有看到。如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
  ta: "உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம். நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.",
};

/** Khabar's answer, as PatientMessages.acknowledgementText (OK, WATCH) and urgentText (RED) give it. */
export function answer(reply: Reply, lang: Lang): string {
  if (reply === "ok") return THANKS[lang];
  if (reply === "dizzy") return `${WAITING[lang]} ${EMERGENCY[lang]}`;
  return URGENT[lang];
}
