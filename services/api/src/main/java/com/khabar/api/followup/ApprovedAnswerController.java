package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The clinic's library of doctor-approved answers to common follow-up questions. */
@RestController
@RequestMapping("/api/clinic/answers")
public class ApprovedAnswerController {

    private static final Set<String> LANGUAGES = Set.of("ms", "en", "zh", "ta");
    private static final int MAX_TEXT = 1000;

    private final CurrentUser currentUser;
    private final ApprovedAnswerRepository answers;
    private final AdjustableClock clock;

    public ApprovedAnswerController(CurrentUser currentUser, ApprovedAnswerRepository answers, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.answers = answers;
        this.clock = clock;
    }

    public record AnswerRequest(String title, List<String> triggers, Map<String, String> texts) {
    }

    public record AnswerView(UUID id, String title, List<String> triggers, Map<String, String> texts, String approvedBy, Instant approvedAt) {
        static AnswerView of(ApprovedAnswer a) {
            return new AnswerView(a.getId(), a.getTitle(), List.copyOf(a.getTriggers()), new LinkedHashMap<>(a.getTexts()),
                    a.getApprovedBy().getDisplayName(), a.getApprovedAt());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AnswerView> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        return answers.findByClinicIdAndRetiredAtIsNullOrderByApprovedAt(doctor.getClinic().getId()).stream().map(AnswerView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AnswerView approve(@RequestBody AnswerRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        if (request.title() == null || request.title().isBlank()) {
            throw bad("Give the question a short title.");
        }
        List<String> triggers = request.triggers() == null ? List.of() : request.triggers().stream()
                .filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toList();
        if (triggers.isEmpty()) {
            throw bad("Add at least one phrase a patient might use to ask this.");
        }
        Map<String, String> texts = new LinkedHashMap<>();
        if (request.texts() != null) {
            request.texts().forEach((language, text) -> {
                if (LANGUAGES.contains(language) && text != null && !text.isBlank()) {
                    if (text.length() > MAX_TEXT) {
                        throw bad("Keep each answer under " + MAX_TEXT + " characters.");
                    }
                    texts.put(language, text.trim());
                }
            });
        }
        if (texts.isEmpty()) {
            throw bad("Write the answer in at least one of ms, en, zh or ta.");
        }
        String title = request.title().trim();
        return AnswerView.of(answers.save(new ApprovedAnswer(doctor.getClinic(), title.length() > 200 ? title.substring(0, 200) : title,
                triggers, texts, doctor, clock.instant())));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public AnswerView retire(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        ApprovedAnswer answer = answers.findById(id)
                .filter(a -> a.getClinic().getId().equals(doctor.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        answer.retire(clock.instant());
        return AnswerView.of(answer);
    }

    private AppUser requireDoctor(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.DOCTOR || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approved answers are written by clinic doctors.");
        }
        return user;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
