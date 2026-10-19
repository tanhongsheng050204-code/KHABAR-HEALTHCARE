package com.khabar.api.meals;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The patient's meal-photo check in the app, and the clinic's diet tips that it quotes. */
@RestController
public class MealController {

    static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    static final int MAX_TIP = 1000;

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final ClinicStaffAccess staffAccess;
    private final MealAdvice advice;
    private final DietTipRepository tips;
    private final AdjustableClock clock;

    public MealController(CurrentUser currentUser, PatientRepository patients, ClinicStaffAccess staffAccess, MealAdvice advice,
                          DietTipRepository tips, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.staffAccess = staffAccess;
        this.advice = advice;
        this.tips = tips;
        this.clock = clock;
    }

    public record TipView(UUID id, String category, String label, String ms, String en, String zh, String ta) {
    }

    public record TipInput(String category, String ms, String en, String zh, String ta) {
    }

    @PostMapping(path = "/api/me/meal-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public MealAdvice.Reply mealPhoto(@RequestParam("image") MultipartFile image, @RequestParam("consentConfirmed") boolean consent,
                                      @AuthenticationPrincipal Jwt jwt) throws IOException {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The meal check is for patients.");
        }
        Patient patient = patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
        if (!consent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm before sending the photo.");
        }
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image no larger than 8 MB.");
        }
        return advice.read(patient, image.getBytes(), "in the app");
    }

    @GetMapping("/api/meal-categories")
    public List<Map<String, String>> categories() {
        return MealAdvice.CATEGORIES.stream().map(c -> Map.of("key", c, "label", MealAdvice.label(c, "en"))).toList();
    }

    @GetMapping("/api/clinic/diet-tips")
    @Transactional(readOnly = true)
    public List<TipView> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser staff = currentUser.from(jwt);
        if (staff.getClinic() == null || !(staffAccess.canManageFollowUp(staff) || staffAccess.canManageStaff(staff))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return tips.findByClinicIdAndRetiredAtIsNullOrderByCategory(staff.getClinic().getId()).stream().map(MealController::view).toList();
    }

    @PostMapping("/api/clinic/diet-tips")
    @Transactional
    public TipView add(@RequestBody TipInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = doctor(jwt);
        if (input.category() == null || !MealAdvice.CATEGORIES.contains(input.category())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the food groups.");
        }
        String ms = clean(input.ms());
        String en = clean(input.en());
        if (ms == null || en == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the tip in Bahasa Melayu and English at least.");
        }
        // One live tip per food group: a new one replaces the old.
        UUID clinicId = doctor.getClinic().getId();
        tips.findByClinicIdAndRetiredAtIsNullOrderByCategory(clinicId).stream()
                .filter(t -> t.getCategory().equals(input.category()))
                .forEach(t -> t.retire(clock.instant()));
        return view(tips.save(new DietTip(clinicId, input.category(), ms, en, clean(input.zh()), clean(input.ta()), doctor.getId(), clock.instant())));
    }

    @DeleteMapping("/api/clinic/diet-tips/{id}")
    @Transactional
    public void retire(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = doctor(jwt);
        DietTip tip = tips.findById(id).filter(t -> t.getClinicId().equals(doctor.getClinic().getId()) && t.getRetiredAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        tip.retire(clock.instant());
    }

    private AppUser doctor(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getClinic() == null || !staffAccess.hasRole(user, ClinicStaffRole.DOCTOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors approve diet tips.");
        }
        return user;
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip();
        return t.length() > MAX_TIP ? t.substring(0, MAX_TIP) : t;
    }

    private static TipView view(DietTip t) {
        return new TipView(t.getId(), t.getCategory(), MealAdvice.label(t.getCategory(), "en"), t.getTextMs(), t.getTextEn(), t.getTextZh(), t.getTextTa());
    }
}
