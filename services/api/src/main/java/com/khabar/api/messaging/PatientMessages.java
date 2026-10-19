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

    /** One gentle re-ask, a day after a check-in went unanswered. zh and ta need fluent-reader review. */
    private static final Map<String, String> REMINDER = Map.of(
            "ms", "Kami belum dengar khabar anda sejak semalam. Semuanya baik? Balas mesej ini bila sempat, walaupun hanya \"baik\".",
            "en", "We haven't heard from you since yesterday. Is everything all right? Reply when you can, even just \"fine\".",
            "zh", "从昨天起还没有收到您的回复。一切都好吗？有空请回复，哪怕只是“好”。",
            "ta", "நேற்றிலிருந்து உங்களிடமிருந்து பதில் வரவில்லை. எல்லாம் சரியா? நேரம் கிடைக்கும்போது பதில் அனுப்புங்கள், \"நலம்\" என்று மட்டும் கூட.");

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

    /** Sent once a patient links Telegram. zh and ta need fluent-reader review like the rest of this file. */
    private static final Map<String, String> TELEGRAM_LINKED = Map.of(
            "ms", "Telegram anda kini disambungkan ke Khabar. Klinik anda akan menghantar pelan rawatan dan semakan susulan di sini.",
            "en", "Your Telegram is now connected to Khabar. Your clinic will send your care plan and check-ins here.",
            "zh", "您的 Telegram 已连接到 Khabar。诊所会在这里发送您的护理计划和随访问候。",
            "ta", "உங்கள் Telegram இப்போது Khabar உடன் இணைக்கப்பட்டுள்ளது. உங்கள் மருத்துவமனை பராமரிப்புத் திட்டத்தையும் பின்தொடர் செய்திகளையும் இங்கே அனுப்பும்.");

    /** After a voice note: what we heard, to confirm before it reaches the clinic. %s is the transcript. */
    private static final Map<String, String> VOICE_HEARD = Map.of(
            "ms", "Kami dengar: “%s”\n\nBetul? Tekan “✅ Hantar / Send” untuk menghantarnya ke klinik, atau taip semula. Jika kecemasan, hubungi 999.",
            "en", "We heard: “%s”\n\nIs that right? Tap “✅ Hantar / Send” to send it to the clinic, or type it instead. In an emergency, call 999.",
            "zh", "我们听到的是：“%s”\n\n对吗？点“✅ Hantar / Send”发送给诊所，或者直接打字。紧急情况请拨打999。",
            "ta", "நாங்கள் கேட்டது: “%s”\n\nசரியா? கிளினிக்கிற்கு அனுப்ப “✅ Hantar / Send” ஐ அழுத்தவும், அல்லது தட்டச்சு செய்யவும். அவசரநிலையில் 999 ஐ அழைக்கவும்.");

    private static final Map<String, String> VOICE_UNHEARD = Map.of(
            "ms", "Maaf, kami tidak dapat mendengar nota suara itu. Sila taip mesej anda. Jika kecemasan, hubungi 999.",
            "en", "Sorry, we couldn't make out that voice note. Please type your message. In an emergency, call 999.",
            "zh", "抱歉，我们听不清这条语音。请用文字回复。紧急情况请拨打999。",
            "ta", "மன்னிக்கவும், அந்தக் குரல் செய்தியைப் புரிந்துகொள்ள முடியவில்லை. தயவுசெய்து தட்டச்சு செய்யவும். அவசரநிலையில் 999 ஐ அழைக்கவும்.");

    /** Before a packet photo is read: the same consent the web app asks for. */
    private static final Map<String, String> PHOTO_CONSENT = Map.of(
            "ms", "Mahu kami baca label ubat dalam gambar ini? Gambar akan dihantar kepada AI untuk dibaca dan tidak disimpan. Tekan “📷 Baca label / Read label” untuk setuju. Jika ini gambar makanan, tekan “🍽️ Semak makanan / Check my meal” untuk nasihat pemakanan klinik anda.",
            "en", "Shall we read the medicine label in this photo? It is sent to an AI to read and is not kept. Tap “📷 Baca label / Read label” to agree. If it is a photo of a meal, tap “🍽️ Semak makanan / Check my meal” for your clinic's diet advice.",
            "zh", "要读取这张照片里的药品标签吗？照片会交给AI读取，不会保存。同意请点“📷 Baca label / Read label”。如果是餐食照片，请点“🍽️ Semak makanan / Check my meal”查看诊所的饮食建议。",
            "ta", "இந்தப் புகைப்படத்தில் உள்ள மருந்து லேபிளைப் படிக்கலாமா? இது படிக்க AI-க்கு அனுப்பப்படும், சேமிக்கப்படாது. ஒப்புக்கொள்ள “📷 Baca label / Read label” ஐ அழுத்தவும். இது உணவுப் படம் என்றால், உங்கள் கிளினிக்கின் உணவு ஆலோசனைக்கு “🍽️ Semak makanan / Check my meal” ஐ அழுத்தவும்.");

    /** After a packet photo. %s lists what was read. */
    private static final Map<String, String> PHOTO_READ = Map.of(
            "ms", "Terima kasih. Daripada gambar itu kami baca: %s. Ia ditambah ke senarai ubat anda sebagai “perlu disemak”; klinik anda akan menyemaknya.",
            "en", "Thank you. From the photo we read: %s. It has been added to your medicine list as “please verify”; your clinic will check it.",
            "zh", "谢谢。我们从照片中读到：%s。已加入您的药物清单并标记为“待核实”，诊所会核对。",
            "ta", "நன்றி. புகைப்படத்திலிருந்து நாங்கள் படித்தது: %s. இது உங்கள் மருந்துப் பட்டியலில் “சரிபார்க்க வேண்டும்” என்று சேர்க்கப்பட்டது; உங்கள் கிளினிக் சரிபார்க்கும்.");

    private static final Map<String, String> PHOTO_UNREAD = Map.of(
            "ms", "Maaf, kami tidak dapat membaca label ubat dalam gambar itu. Cuba gambar yang lebih dekat dan terang, atau taip nama ubat.",
            "en", "Sorry, we couldn't read a medicine label in that photo. Try a closer, brighter photo, or type the medicine's name.",
            "zh", "抱歉，无法从照片中读出药品标签。请拍得更近、更亮一些，或直接输入药名。",
            "ta", "மன்னிக்கவும், அந்தப் புகைப்படத்தில் மருந்து லேபிளைப் படிக்க முடியவில்லை. நெருக்கமான, வெளிச்சமான புகைப்படம் எடுக்கவும், அல்லது மருந்தின் பெயரைத் தட்டச்சு செய்யவும்.");

    private static final Map<String, String> TEXT_ONLY = Map.of(
            "ms", "Sila balas dalam bentuk teks.",
            "en", "Please reply in text.",
            "zh", "请用文字回复。",
            "ta", "தயவுசெய்து உரையாகப் பதிலளிக்கவும்.");

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

    public static String reminderText(String language) {
        return REMINDER.getOrDefault(language, REMINDER.get("en"));
    }

    public static String voiceHeardText(String language, String transcript) {
        return VOICE_HEARD.getOrDefault(language, VOICE_HEARD.get("en")).formatted(transcript);
    }

    public static String voiceUnheardText(String language) {
        return VOICE_UNHEARD.getOrDefault(language, VOICE_UNHEARD.get("en"));
    }

    public static String photoReadText(String language, String items) {
        return PHOTO_READ.getOrDefault(language, PHOTO_READ.get("en")).formatted(items);
    }

    public static String photoConsentText(String language) {
        return PHOTO_CONSENT.getOrDefault(language, PHOTO_CONSENT.get("en"));
    }

    public static String photoUnreadText(String language) {
        return PHOTO_UNREAD.getOrDefault(language, PHOTO_UNREAD.get("en"));
    }

    /** The clinic's fixed emergency sentence, also read out at the end of every follow-up call script. */
    public static String emergencyAdviceText(String language) {
        return EMERGENCY_ADVICE.getOrDefault(language, EMERGENCY_ADVICE.get("en"));
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

    /** In-app response for an idempotent retry; do not send this as a second outbound message. */
    public static String telegramLinkedText(String language) {
        return TELEGRAM_LINKED.getOrDefault(language, TELEGRAM_LINKED.get("en"));
    }

    public static String textOnlyText(String language) {
        return TEXT_ONLY.getOrDefault(language, TEXT_ONLY.get("en"));
    }

    public static String duplicateUpdateText(String language) {
        return Map.of(
                "ms", "Kemas kini ini telah diterima dan tidak dihantar semula.",
                "en", "This update was already received and was not sent again.",
                "zh", "这条更新已收到，没有重复发送。",
                "ta", "இந்தப் புதுப்பிப்பு ஏற்கனவே பெறப்பட்டது; மீண்டும் அனுப்பப்படவில்லை."
        ).getOrDefault(language, "This update was already received and was not sent again.");
    }

    public Messenger.Result send(Patient patient, String text, Messenger.Kind kind) {
        Messenger.Result result = messenger.send(patient.getTelegramChatId(), text, patient.getPreferredLanguage(), kind);
        outbox.save(new OutboundMessage(patient.getId(), kind, messenger.channel(), text, result, clock.instant()));
        return result;
    }
}
