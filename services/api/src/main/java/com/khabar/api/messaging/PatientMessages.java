package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
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

    public Messenger.Result send(Patient patient, String text, Messenger.Kind kind) {
        Messenger.Result result = messenger.send(patient.getPhone(), text, patient.getPreferredLanguage(), kind);
        outbox.save(new OutboundMessage(patient.getId(), kind, messenger.channel(), text, result, clock.instant()));
        return result;
    }
}
