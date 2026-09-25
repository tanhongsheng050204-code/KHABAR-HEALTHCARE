package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.patients.Patient;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Sends a message to a patient and records it, whichever channel is configured. */
@Service
public class PatientMessages {

    private static final Map<String, String> CHECK_IN = Map.of(
            "ms", "Apa khabar hari ini? Dah makan ubat? Balas mesej ini untuk beritahu klinik.",
            "en", "How are you today? Have you taken your medicine? Reply to this message to let the clinic know.",
            "zh", "今天感觉怎么样？吃药了吗？回复这条信息告诉诊所。",
            "ta", "இன்று எப்படி இருக்கிறீர்கள்? மருந்து சாப்பிட்டீர்களா? இந்த செய்திக்கு பதில் அனுப்பி கிளினிக்கிற்கு தெரியப்படுத்துங்கள்.");

    private static final Map<String, String> CHECK_IN_FASTING = Map.of(
            "ms", "Apa khabar? Ada rasa pening, berpeluh atau menggeletar sebelum berbuka? Balas mesej ini untuk beritahu klinik.",
            "en", "How are you? Any dizziness, sweating or shakiness before berbuka? Reply to this message to let the clinic know.",
            "zh", "身体怎么样？开斋前有没有头晕、冒汗或发抖？回复这条信息告诉诊所。",
            "ta", "எப்படி இருக்கிறீர்கள்? நோன்பு திறக்கும் முன் தலைச்சுற்றல், வியர்வை அல்லது நடுக்கம் உள்ளதா? இந்த செய்திக்கு பதில் அனுப்பி கிளினிக்கிற்கு தெரியப்படுத்துங்கள்.");

    /** Sent after a reply is routed to the clinic queue. Do not imply a staff notification or callback. */
    private static final Map<String, String> URGENT = Map.of(
            "ms", "Mesej anda telah dimasukkan dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya. Jika sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
            "en", "Your message has been added to the clinic's follow-up list, but the clinic may not have seen it yet. If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
            "zh", "您的消息已加入诊所的随访列表，但诊所可能还没有看到。如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
            "ta", "உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம். நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.");

    /** The emergency sentence of the urgent text on its own, for a reply nobody could check automatically. */
    private static final Map<String, String> EMERGENCY_ADVICE = Map.of(
            "ms", "Kalau sakit dada, sesak nafas atau pengsan, hubungi 999 atau pergi ke Jabatan Kecemasan yang terdekat sekarang.",
            "en", "If you have chest pain, trouble breathing or have fainted, call 999 or go to the nearest emergency department now.",
            "zh", "如果胸痛、呼吸困难或昏倒，请立即拨打999或前往最近的急诊部。",
            "ta", "நெஞ்சு வலி, மூச்சுத் திணறல் அல்லது மயக்கம் இருந்தால், உடனே 999 ஐ அழைக்கவும் அல்லது அருகிலுள்ள அவசர சிகிச்சைப் பிரிவுக்குச் செல்லவும்.");

    /** A reply in the clinic queue; no staff notification or review time is promised. */
    private static final Map<String, String> WAITING_FOR_REVIEW = Map.of(
            "ms", "Terima kasih. Mesej anda ada dalam senarai susulan klinik, tetapi klinik mungkin belum membacanya.",
            "en", "Thank you. Your message is in the clinic's follow-up list, but the clinic may not have seen it yet.",
            "zh", "谢谢。您的消息已加入诊所的随访列表，但诊所可能还没有看到。",
            "ta", "நன்றி. உங்கள் செய்தி கிளினிக்கின் பின்தொடர் பட்டியலில் சேர்க்கப்பட்டுள்ளது; ஆனால் கிளினிக் அதை இன்னும் பார்க்காமல் இருக்கலாம்.");

    /** A reassuring reply that needs no one. */
    private static final Map<String, String> THANKS = Map.of(
            "ms", "Terima kasih kerana memberitahu. Jaga diri!",
            "en", "Thanks for letting us know. Take care!",
            "zh", "谢谢您告诉我们。请保重！",
            "ta", "தெரிவித்ததற்கு நன்றி. உடல்நலத்தைக் கவனித்துக் கொள்ளுங்கள்!");

    private final Messenger messenger;
    private final OutboundMessageRepository outbox;
    private final AdjustableClock clock;

    public PatientMessages(Messenger messenger, OutboundMessageRepository outbox, AdjustableClock clock) {
        this.messenger = messenger;
        this.outbox = outbox;
        this.clock = clock;
    }

    public static String checkInText(String language, boolean fasting) {
        Map<String, String> texts = fasting ? CHECK_IN_FASTING : CHECK_IN;
        return texts.getOrDefault(language, texts.get("en"));
    }

    public static String urgentText(String language) {
        return URGENT.getOrDefault(language, URGENT.get("en"));
    }

    /**
     * When triage could not run, nobody knows whether the reply is urgent. It remains in the clinic
     * queue; the patient is told it may not have been seen yet and gets precautionary emergency advice.
     */
    public static String uncheckedText(String language) {
        return WAITING_FOR_REVIEW.getOrDefault(language, WAITING_FOR_REVIEW.get("en")) + " " + EMERGENCY_ADVICE.getOrDefault(language, EMERGENCY_ADVICE.get("en"));
    }

    /**
     * Only a reply the word lists classify as OK gets a plain thank-you. Anything routed for review also
     * carries the emergency sentence: the word lists miss many ways of describing an emergency (see
     * docs/evals), so a reply they could not place may still be one.
     */
    public static String acknowledgementText(String language, TriageLevel level) {
        if (level == TriageLevel.OK) {
            return THANKS.getOrDefault(language, THANKS.get("en"));
        }
        return uncheckedText(language);
    }

    public Messenger.Result send(Patient patient, String text, Messenger.Kind kind) {
        Messenger.Result result = messenger.send(patient.getPhone(), text, patient.getPreferredLanguage(), kind);
        outbox.save(new OutboundMessage(patient.getId(), kind, messenger.channel(), text, result, clock.instant()));
        return result;
    }
}
