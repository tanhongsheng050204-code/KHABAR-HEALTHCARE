package com.khabar.api.dev;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Makes up Malaysian-style patients for demos and tests: Malay, Chinese, Indian and East Malaysian
 * names, the four languages, common long-term conditions, medicines from more than one clinic, some
 * herbal use and some allergies. Nobody here is real. Phone numbers use 03-0000 xxxx, which no real
 * line can have, so a misconfigured WhatsApp can never message a stranger. The same seed always gives
 * the same people.
 */
public class FakePatientGenerator {

    public record Item(String name, String source) {
    }

    public record FakePatient(String group, String fullName, boolean male, String icNumber, String phone, String language,
                              List<String> allergies, boolean pregnant, List<Item> medicines, List<Item> herbs) {
    }

    private static final String[] GROUPS = {"malay", "chinese", "indian", "east_malaysian"};

    private static final String[] MALAY_MALE = {"Ahmad", "Muhammad", "Ismail", "Hafiz", "Azman", "Rahim", "Zulkifli", "Hassan", "Kamarul", "Roslan"};
    private static final String[] MALAY_FEMALE = {"Siti", "Noraini", "Aishah", "Zainab", "Faridah", "Rohani", "Salmah", "Mariam", "Halimah", "Rokiah"};
    private static final String[] MALAY_FATHER = {"Abdullah", "Ibrahim", "Osman", "Yusof", "Hamid", "Razak", "Salleh", "Omar", "Daud", "Jaafar"};
    private static final String[] CHINESE_SURNAME = {"Tan", "Lim", "Lee", "Wong", "Ng", "Chong", "Teoh", "Goh", "Ooi", "Chin"};
    private static final String[] CHINESE_MALE = {"Kok Wai", "Chee Keong", "Wei Ming", "Ah Kow", "Boon Hock", "Kah Seng"};
    private static final String[] CHINESE_FEMALE = {"Mei Ling", "Siew Lan", "Pei Shan", "Bee Hoon", "Lay Kuan", "Swee Lian"};
    private static final String[] INDIAN_MALE = {"Muthu", "Ravi", "Suresh", "Ganesan", "Kumar", "Rajendran", "Selvam"};
    private static final String[] INDIAN_FEMALE = {"Lakshmi", "Kavitha", "Devi", "Saraswathy", "Meena", "Thilaga"};
    private static final String[] INDIAN_FATHER = {"Rajan", "Subramaniam", "Krishnan", "Arumugam", "Perumal", "Maniam"};
    private static final String[] EAST_MALE = {"Jimbun", "Awang", "Rayner", "Joseph", "Gerald", "Ambrose"};
    private static final String[] EAST_FEMALE = {"Linda", "Juliana", "Christina", "Dayang", "Mary", "Veronica"};
    private static final String[] EAST_FATHER = {"Ngau", "Unting", "Masing", "Jelani", "Sagan"};
    private static final String[] EAST_SURNAME = {"Gimbang", "Majinal", "Lojuki", "Gumis", "Tingkalor"};

    private static final String[] SOURCES = {"Klinik Kesihatan", "GP clinic", "Hospital specialist clinic", "Pharmacy"};

    /** Long-term conditions and a medicine list for each; the chance a patient has it, in percent. */
    private record Condition(int chance, String... medicines) {
    }

    private static final Condition[] CONDITIONS = {
            new Condition(60, "Metformin 500mg", "Gliclazide 80mg"),
            new Condition(60, "Amlodipine 5mg", "Perindopril 4mg"),
            new Condition(25, "Aspirin 100mg", "Simvastatin 20mg", "Clopidogrel 75mg"),
            new Condition(20, "Omeprazole 20mg"),
            new Condition(20, "Paracetamol 500mg", "Ibuprofen 400mg"),
    };

    private static final String[] HERBS = {"Jus peria (bitter gourd)", "Ginkgo capsules", "Garlic pills", "Jamu", "Chinese herbal tea"};
    private static final String[] HERB_SOURCES = {"Family", "Traditional medicine shop", "Online shop"};
    private static final String[] ALLERGIES = {"penicillin", "aspirin", "nsaid"};

    private final Random random;

    public FakePatientGenerator(long seed) {
        this.random = new Random(seed);
    }

    public List<FakePatient> generate(int count) {
        List<FakePatient> patients = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> ics = new HashSet<>();
        Set<String> phones = new HashSet<>();
        while (patients.size() < count) {
            FakePatient p = one(GROUPS[patients.size() % GROUPS.length]);
            if (names.contains(p.fullName()) || ics.contains(p.icNumber()) || phones.contains(p.phone())) {
                continue;
            }
            names.add(p.fullName());
            ics.add(p.icNumber());
            phones.add(p.phone());
            patients.add(p);
        }
        return patients;
    }

    private FakePatient one(String group) {
        boolean male = random.nextBoolean();
        String name = switch (group) {
            case "malay" -> pick(male ? MALAY_MALE : MALAY_FEMALE) + (male ? " bin " : " binti ") + pick(MALAY_FATHER);
            case "chinese" -> pick(CHINESE_SURNAME) + " " + pick(male ? CHINESE_MALE : CHINESE_FEMALE);
            case "indian" -> pick(male ? INDIAN_MALE : INDIAN_FEMALE) + (male ? " a/l " : " a/p ") + pick(INDIAN_FATHER);
            default -> random.nextBoolean()
                    ? pick(male ? EAST_MALE : EAST_FEMALE) + " anak " + pick(EAST_FATHER)
                    : pick(male ? EAST_MALE : EAST_FEMALE) + " " + pick(EAST_SURNAME);
        };
        String language = switch (group) {
            case "malay" -> chance(85) ? "ms" : "en";
            case "chinese" -> chance(70) ? "zh" : "en";
            case "indian" -> chance(60) ? "ta" : chance(60) ? "en" : "ms";
            default -> chance(60) ? "ms" : "en";
        };
        LocalDate born = LocalDate.of(1940, 1, 1).plusDays(random.nextInt((int) (LocalDate.of(1995, 12, 31).toEpochDay() - LocalDate.of(1940, 1, 1).toEpochDay())));
        String state = switch (group) {
            case "malay" -> pick(new String[]{"01", "02", "03", "04", "05", "06", "08", "09", "10", "11", "14"});
            case "chinese" -> pick(new String[]{"01", "07", "08", "10", "14"});
            case "indian" -> pick(new String[]{"07", "08", "10", "14"});
            default -> pick(new String[]{"12", "13"});
        };
        int last = random.nextInt(5) * 2 + (male ? 1 : 0);
        String ic = String.format("%02d%02d%02d-%s-%03d%d", born.getYear() % 100, born.getMonthValue(), born.getDayOfMonth(), state, random.nextInt(1000), last);
        String phone = String.format("03-0000 %04d", random.nextInt(10000));

        List<String> allergies = chance(20) ? List.of(pick(ALLERGIES)) : List.of();
        boolean pregnant = !male && born.getYear() >= 1980 && chance(15);
        List<Item> medicines = medicines(allergies);
        List<Item> herbs = chance(35) ? List.of(new Item(pick(HERBS), pick(HERB_SOURCES))) : List.of();
        return new FakePatient(group, name, male, ic, phone, language, allergies, pregnant, medicines, herbs);
    }

    private List<Item> medicines(List<String> allergies) {
        List<Item> items = new ArrayList<>();
        for (Condition condition : CONDITIONS) {
            if (chance(condition.chance())) {
                String medicine = pick(condition.medicines());
                if (allergic(allergies, medicine)) {
                    continue;
                }
                String source = pick(SOURCES);
                items.add(new Item(medicine, source));
                // The duplicate Khabar exists to catch: the same drug from a second clinic under a brand name
                if (medicine.startsWith("Metformin") && chance(35)) {
                    items.add(new Item("Brand A 500mg", source.equals("GP clinic") ? "Klinik Kesihatan" : "GP clinic"));
                }
            }
        }
        if (items.isEmpty()) {
            items.add(new Item("Amlodipine 5mg", pick(SOURCES)));
        }
        return items;
    }

    private static boolean allergic(List<String> allergies, String medicine) {
        String m = medicine.toLowerCase();
        return allergies.contains("aspirin") && m.startsWith("aspirin")
                || allergies.contains("nsaid") && (m.startsWith("aspirin") || m.startsWith("ibuprofen"));
    }

    private boolean chance(int percent) {
        return random.nextInt(100) < percent;
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }
}
