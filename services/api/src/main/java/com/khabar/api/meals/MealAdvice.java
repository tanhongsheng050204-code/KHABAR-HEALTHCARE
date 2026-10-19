package com.khabar.api.meals;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.medications.MedicationPacketController;
import com.khabar.api.patients.Patient;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A meal photo the patient chose to send: the AI names the food groups on the plate from a fixed list, and the
 * patient gets back their clinic's own approved tip for each group found. No calorie or carbohydrate estimates, no
 * advice of the AI's own. The photo is not kept.
 */
@Service
public class MealAdvice {

    private static final Logger log = LoggerFactory.getLogger(MealAdvice.class);

    public static final List<String> CATEGORIES = List.of("RICE_NOODLES", "BREAD", "SWEET_DRINK", "FRIED", "SWEETS_DESSERT", "FRUIT",
            "VEGETABLES", "SALTY_SAUCES", "MEAT_FISH_EGG");

    static final Map<String, Map<String, String>> LABELS = labels();

    private static Map<String, Map<String, String>> labels() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        all.put("RICE_NOODLES", Map.of("ms", "Nasi / mi", "en", "Rice / noodles", "zh", "米饭 / 面", "ta", "சாதம் / நூடுல்ஸ்"));
        all.put("BREAD", Map.of("ms", "Roti", "en", "Bread / roti", "zh", "面包 / 煎饼", "ta", "ரொட்டி"));
        all.put("SWEET_DRINK", Map.of("ms", "Minuman manis", "en", "Sweet drink", "zh", "甜饮料", "ta", "இனிப்பு பானம்"));
        all.put("FRIED", Map.of("ms", "Makanan bergoreng", "en", "Fried food", "zh", "油炸食物", "ta", "பொரித்த உணவு"));
        all.put("SWEETS_DESSERT", Map.of("ms", "Kuih / pencuci mulut", "en", "Sweets / dessert", "zh", "甜点", "ta", "இனிப்புகள்"));
        all.put("FRUIT", Map.of("ms", "Buah", "en", "Fruit", "zh", "水果", "ta", "பழம்"));
        all.put("VEGETABLES", Map.of("ms", "Sayur", "en", "Vegetables", "zh", "蔬菜", "ta", "காய்கறி"));
        all.put("SALTY_SAUCES", Map.of("ms", "Sos / makanan masin", "en", "Salty food / sauces", "zh", "咸食 / 酱料", "ta", "உப்பு உணவு / சாஸ்"));
        all.put("MEAT_FISH_EGG", Map.of("ms", "Daging / ikan / telur", "en", "Meat / fish / egg", "zh", "肉 / 鱼 / 蛋", "ta", "இறைச்சி / மீன் / முட்டை"));
        return all;
    }

    private static final Map<String, String> HEADER = Map.of(
            "ms", "🍽️ Nasihat klinik anda untuk makanan ini:",
            "en", "🍽️ Your clinic's advice for this meal:",
            "zh", "🍽️ 诊所对这餐的建议：",
            "ta", "🍽️ இந்த உணவுக்கு உங்கள் கிளினிக்கின் ஆலோசனை:");

    private static final Map<String, String> NO_TIP = Map.of(
            "ms", "Terima kasih. Klinik anda belum menyimpan nasihat untuk makanan ini. Tanya pada lawatan seterusnya.",
            "en", "Thank you. Your clinic has no advice saved for this meal yet. Ask at your next visit.",
            "zh", "谢谢。诊所还没有为这类食物保存建议，请在下次复诊时询问。",
            "ta", "நன்றி. இந்த உணவுக்கு உங்கள் கிளினிக் இன்னும் ஆலோசனை சேமிக்கவில்லை. அடுத்த வருகையில் கேளுங்கள்.");

    private static final Map<String, String> UNREAD = Map.of(
            "ms", "Maaf, gambar makanan ini tidak dapat dibaca. Cuba ambil gambar dari atas dengan cahaya yang cukup.",
            "en", "Sorry, this meal photo could not be read. Try a photo from above in good light.",
            "zh", "抱歉，无法识别这张餐食照片。请在光线充足处从上方拍摄。",
            "ta", "மன்னிக்கவும், இந்த உணவுப் படத்தைப் படிக்க முடியவில்லை. நல்ல வெளிச்சத்தில் மேலிருந்து படம் எடுக்கவும்.");

    /** What the patient is told, and which food groups were seen. */
    public record Reply(List<String> categories, List<String> tips, String message) {
    }

    private final AgentClientService agents;
    private final AiReceipts aiReceipts;
    private final DietTipRepository tips;
    private final AdjustableClock clock;

    public MealAdvice(AgentClientService agents, AiReceipts aiReceipts, DietTipRepository tips, AdjustableClock clock) {
        this.agents = agents;
        this.aiReceipts = aiReceipts;
        this.tips = tips;
        this.clock = clock;
    }

    public static String label(String category, String language) {
        Map<String, String> l = LABELS.get(category);
        return l == null ? category : l.getOrDefault(language, l.get("en"));
    }

    /** image: the bytes the patient chose to send. Returns the reply to show or send. */
    public Reply read(Patient patient, byte[] image, String channel) {
        String language = patient.getPreferredLanguage();
        String mimeType = image == null ? null : MedicationPacketController.detectedImageType(image);
        List<String> categories = new ArrayList<>();
        if (mimeType != null) {
            aiReceipts.media(patient, AiReceipts.Purpose.MEAL_PHOTO, "/agents/meal/read",
                    "A meal photo sent " + channel + " (" + Math.max(1, image.length / 1024) + " KB). The AI names the food groups; the photo is not kept.");
            try {
                Map<String, Object> result = agents.readMeal(image, mimeType);
                if (result != null && result.get("categories") instanceof List<?> found) {
                    found.stream().map(String::valueOf).filter(CATEGORIES::contains).distinct().forEach(categories::add);
                }
            } catch (RuntimeException e) {
                log.warn("Meal photo not read: {}", e.getClass().getSimpleName());
            }
        }
        if (categories.isEmpty()) {
            return new Reply(List.of(), List.of(), UNREAD.getOrDefault(language, UNREAD.get("en")));
        }
        return advise(patient, categories);
    }

    Reply advise(Patient patient, List<String> categories) {
        String language = patient.getPreferredLanguage();
        Map<String, DietTip> byCategory = new LinkedHashMap<>();
        tips.findByClinicIdAndRetiredAtIsNullOrderByCategory(patient.getClinic().getId()).forEach(t -> byCategory.putIfAbsent(t.getCategory(), t));
        List<String> lines = new ArrayList<>();
        for (String category : categories) {
            DietTip tip = byCategory.get(category);
            if (tip != null) {
                lines.add("• " + label(category, language) + ": " + tip.textFor(language));
            }
        }
        if (lines.isEmpty()) {
            return new Reply(categories, List.of(), NO_TIP.getOrDefault(language, NO_TIP.get("en")));
        }
        return new Reply(categories, lines, HEADER.getOrDefault(language, HEADER.get("en")) + "\n" + String.join("\n", lines));
    }
}
