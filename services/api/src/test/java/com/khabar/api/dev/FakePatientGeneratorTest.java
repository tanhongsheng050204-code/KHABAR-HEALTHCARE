package com.khabar.api.dev;

import com.khabar.api.dev.FakePatientGenerator.FakePatient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FakePatientGeneratorTest {

    final List<FakePatient> thirty = new FakePatientGenerator(42).generate(30);

    @Test
    void generatesThirtyDifferentPeople() {
        assertThat(thirty).hasSize(30);
        assertThat(thirty.stream().map(FakePatient::fullName).collect(Collectors.toSet())).hasSize(30);
        assertThat(thirty.stream().map(FakePatient::icNumber).collect(Collectors.toSet())).hasSize(30);
    }

    @Test
    void theSameSeedGivesTheSamePatients() {
        assertThat(new FakePatientGenerator(42).generate(30)).isEqualTo(thirty);
    }

    @Test
    void namesComeFromMalayChineseIndianAndEastMalaysianFamilies() {
        Set<String> groups = thirty.stream().map(FakePatient::group).collect(Collectors.toSet());
        assertThat(groups).containsExactlyInAnyOrder("malay", "chinese", "indian", "east_malaysian");
        assertThat(thirty).anyMatch(p -> p.fullName().contains(" binti ") || p.fullName().contains(" bin "));
        assertThat(thirty).anyMatch(p -> p.fullName().contains(" a/l ") || p.fullName().contains(" a/p "));
    }

    @Test
    void icNumbersHaveAValidBirthDateAndMatchTheSex() {
        for (FakePatient p : thirty) {
            assertThat(p.icNumber()).matches("\\d{6}-\\d{2}-\\d{4}");
            LocalDate born = LocalDate.parse("19" + p.icNumber().substring(0, 6), DateTimeFormatter.ofPattern("yyyyMMdd"));
            assertThat(born).isBetween(LocalDate.of(1940, 1, 1), LocalDate.of(1995, 12, 31));
            int last = p.icNumber().charAt(p.icNumber().length() - 1) - '0';
            assertThat(last % 2 == 1).isEqualTo(p.male());
        }
    }

    @Test
    void phoneNumbersCanNeverReachARealPerson() {
        // 03-0000 xxxx is not a routable Malaysian number, so a misconfigured WhatsApp can't message a stranger
        assertThat(thirty).allMatch(p -> p.phone().startsWith("03-0000 "));
    }

    @Test
    void everyoneTakesSomethingAndSomeHaveHerbsAllergiesOrTwoClinics() {
        assertThat(thirty).allMatch(p -> !p.medicines().isEmpty());
        assertThat(thirty).anyMatch(p -> !p.herbs().isEmpty());
        assertThat(thirty).anyMatch(p -> !p.allergies().isEmpty());
        assertThat(thirty).anyMatch(p -> p.medicines().stream().map(FakePatientGenerator.Item::source).distinct().count() >= 2);
        assertThat(thirty).allMatch(p -> Set.of("ms", "en", "zh", "ta").contains(p.language()));
    }

    @Test
    void onlyWomenOfChildbearingAgeCanBePregnant() {
        assertThat(thirty).filteredOn(FakePatient::pregnant).allMatch(p -> !p.male()
                && Integer.parseInt(p.icNumber().substring(0, 2)) >= 80);
    }
}
