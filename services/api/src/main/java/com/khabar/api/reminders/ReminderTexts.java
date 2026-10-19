package com.khabar.api.reminders;

import com.khabar.api.encounters.PrescriptionLine;

import java.util.List;
import java.util.Map;

/**
 * Reminder wording in BM, English, Chinese and Tamil. Fixed phrases filled with the doctor's own prescription;
 * nothing here gives advice. The Chinese and Tamil need a fluent reader's review like the rest of Khabar.
 */
final class ReminderTexts {

    private ReminderTexts() {
    }

    static final String TAKEN_BUTTON = "✅ Sudah / Taken";
    static final String MISSED_BUTTON = "❌ Terlepas / Missed";
    static final String COMING_BUTTON = "✅ Saya datang / I'll come";
    static final String CANT_COME_BUTTON = "↻ Tak dapat datang / Can't come";

    private static final Map<String, Map<String, String>> SLOT = Map.of(
            "ms", Map.of("morning", "pagi", "afternoon", "tengah hari", "evening", "petang", "night", "malam", "sahur", "sahur", "berbuka", "berbuka"),
            "en", Map.of("morning", "morning", "afternoon", "afternoon", "evening", "evening", "night", "night", "sahur", "sahur", "berbuka", "berbuka"),
            "zh", Map.of("morning", "早上", "afternoon", "中午", "evening", "傍晚", "night", "晚上", "sahur", "封斋前", "berbuka", "开斋时"),
            "ta", Map.of("morning", "காலை", "afternoon", "மதியம்", "evening", "மாலை", "night", "இரவு", "sahur", "சஹர்", "berbuka", "நோன்பு திறப்பு"));

    private static final Map<String, String> HEADER = Map.of(
            "ms", "⏰ Masa makan ubat (%s):",
            "en", "⏰ Medicine time (%s):",
            "zh", "⏰ 吃药时间（%s）：",
            "ta", "⏰ மருந்து நேரம் (%s):");

    private static final Map<String, String> FOOTER = Map.of(
            "ms", "Tekan butang di bawah selepas anda makan ubat.",
            "en", "Tap a button below once you have taken them.",
            "zh", "吃药后请点下面的按钮。",
            "ta", "மருந்து எடுத்த பிறகு கீழே உள்ள பொத்தானை அழுத்தவும்.");

    private static final Map<String, String> UNIT = Map.of("ms", "biji", "en", "tablet(s)", "zh", "粒", "ta", "மாத்திரை");

    private static final Map<String, Map<String, String>> TIMING = Map.of(
            "ms", Map.of("after_food", "selepas makan", "before_food", "sebelum makan"),
            "en", Map.of("after_food", "after food", "before_food", "before food"),
            "zh", Map.of("after_food", "饭后", "before_food", "饭前"),
            "ta", Map.of("after_food", "சாப்பிட்ட பிறகு", "before_food", "சாப்பிடுவதற்கு முன்"));

    private static final Map<String, String> TAKEN = Map.of(
            "ms", "Terima kasih, direkodkan.",
            "en", "Thanks, recorded.",
            "zh", "谢谢，已记录。",
            "ta", "நன்றி, பதிவு செய்யப்பட்டது.");

    private static final Map<String, String> MISSED = Map.of(
            "ms", "Terima kasih kerana memberitahu. Klinik anda boleh melihat ini. Jika tidak pasti apa perlu dibuat, tanya klinik atau ahli farmasi.",
            "en", "Thanks for telling us. Your clinic can see this. If you are unsure what to do, ask your clinic or pharmacist.",
            "zh", "谢谢告诉我们，诊所可以看到这条记录。如果不确定该怎么做，请问诊所或药剂师。",
            "ta", "தெரிவித்ததற்கு நன்றி. உங்கள் கிளினிக் இதைப் பார்க்கும். என்ன செய்வது என்று தெரியாவிட்டால், கிளினிக் அல்லது மருந்தாளரிடம் கேளுங்கள்.");

    private static final Map<String, String> REFILL = Map.of(
            "ms", "💊 Ubat dari lawatan anda dijangka habis sekitar %s. Sila dapatkan bekalan baharu atau tempah temu janji sebelum itu.",
            "en", "💊 The medicine from your visit should last until about %s. Please get a new supply or book a visit before then.",
            "zh", "💊 这次就诊开的药大约用到 %s。请在那之前补药或预约复诊。",
            "ta", "💊 உங்கள் வருகையின் மருந்துகள் சுமார் %s வரை போதுமானதாக இருக்கும். அதற்கு முன் புதிய மருந்து பெறவும் அல்லது சந்திப்பைப் பதிவு செய்யவும்.");

    private static final Map<String, String> APPOINTMENT = Map.of(
            "ms", "📅 Peringatan: anda ada temu janji di %s esok, jam %s.",
            "en", "📅 Reminder: you have an appointment at %s tomorrow at %s.",
            "zh", "📅 提醒：您明天 %2$s 在 %1$s 有预约。",
            "ta", "📅 நினைவூட்டல்: நாளை %2$s மணிக்கு %1$s இல் உங்களுக்கு சந்திப்பு உள்ளது.");

    private static final Map<String, String> COMING = Map.of(
            "ms", "Terima kasih, jumpa esok.",
            "en", "Thank you, see you tomorrow.",
            "zh", "谢谢，明天见。",
            "ta", "நன்றி, நாளை சந்திப்போம்.");

    private static final Map<String, String> CANCELLED = Map.of(
            "ms", "Temu janji anda telah dibatalkan dan masa itu dibuka semula. Pilih masa baharu dalam Khabar.",
            "en", "Your appointment was cancelled and the time is free again. Choose a new time in Khabar.",
            "zh", "您的预约已取消，该时段已重新开放。请在 Khabar 选择新的时间。",
            "ta", "உங்கள் சந்திப்பு ரத்து செய்யப்பட்டது; அந்த நேரம் மீண்டும் கிடைக்கிறது. Khabar இல் புதிய நேரத்தைத் தேர்ந்தெடுக்கவும்.");

    static String lang(String language) {
        return HEADER.containsKey(language) ? language : "en";
    }

    static String slotWord(String language, String slot) {
        return SLOT.get(lang(language)).getOrDefault(slot, slot);
    }

    /** "Metformin 500 mg — 1 biji, selepas makan" for each line. */
    static String medicineLine(String language, PrescriptionLine rx) {
        String l = lang(language);
        StringBuilder out = new StringBuilder("• ").append(rx.getName() == null ? rx.getRaw() : rx.getName());
        if (rx.getStrengthMg() != null) {
            out.append(' ').append(number(rx.getStrengthMg())).append(" mg");
        }
        double units = rx.getUnitsPerDose() == null ? 1 : rx.getUnitsPerDose();
        out.append(" — ").append(number(units)).append(' ').append(UNIT.get(l));
        if (rx.getTiming() != null && TIMING.get(l).containsKey(rx.getTiming())) {
            out.append(", ").append(TIMING.get(l).get(rx.getTiming()));
        }
        return out.toString();
    }

    static String dose(String language, String slot, List<String> lines) {
        String l = lang(language);
        return HEADER.get(l).formatted(slotWord(l, slot)) + "\n" + String.join("\n", lines) + "\n\n" + FOOTER.get(l);
    }

    static String taken(String language) { return TAKEN.get(lang(language)); }
    static String missed(String language) { return MISSED.get(lang(language)); }
    static String refill(String language, String date) { return REFILL.get(lang(language)).formatted(date); }
    static String appointment(String language, String clinic, String time) { return APPOINTMENT.get(lang(language)).formatted(clinic, time); }
    static String coming(String language) { return COMING.get(lang(language)); }
    static String cancelled(String language) { return CANCELLED.get(lang(language)); }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
