package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffGrantRepository;
import com.khabar.api.identity.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Notes on a follow-up case for the clinic team, with @mentions. Writing "@Nurse Mei" mentions that colleague:
 * the note appears in their mentions until they open them. For doctors and nurses only.
 */
@RestController
public class CaseNotesController {

    static final int MAX_NOTE = 1000;

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicStaffGrantRepository grants;
    private final FollowUpCases cases;
    private final FollowUpCaseRepository caseRepository;
    private final CaseNoteRepository notes;
    private final MentionsSeenRepository seen;
    private final AdjustableClock clock;

    public CaseNotesController(CurrentUser currentUser, ClinicStaffAccess staffAccess, ClinicStaffGrantRepository grants,
                               FollowUpCases cases, FollowUpCaseRepository caseRepository, CaseNoteRepository notes,
                               MentionsSeenRepository seen, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.grants = grants;
        this.cases = cases;
        this.caseRepository = caseRepository;
        this.notes = notes;
        this.seen = seen;
        this.clock = clock;
    }

    public record NoteInput(String text) {
    }

    public record Mentioned(UUID userId, String displayName) {
    }

    public record NoteView(UUID id, UUID caseId, String by, String text, List<Mentioned> mentions, Instant at) {
    }

    public record MentionView(UUID noteId, UUID caseId, UUID patientId, String patientName, String by, String text, Instant at, boolean unread) {
    }

    public record Mentions(int unread, List<MentionView> items) {
    }

    @GetMapping("/api/clinic/cases/{id}/notes")
    @Transactional(readOnly = true)
    public List<NoteView> list(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        FollowUpCase c = cases.caseFor(actor, id);
        List<AppUser> staff = staff(actor);
        return notes.findByCaseIdOrderByAtAsc(c.getId()).stream().map(n -> view(n, staff)).toList();
    }

    @PostMapping("/api/clinic/cases/{id}/notes")
    @Transactional
    public NoteView add(@PathVariable UUID id, @RequestBody NoteInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        FollowUpCase c = cases.caseFor(actor, id);
        String text = input == null || input.text() == null ? "" : input.text().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the note first.");
        }
        if (text.length() > MAX_NOTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep a note under " + MAX_NOTE + " characters.");
        }
        List<AppUser> staff = staff(actor);
        List<UUID> mentioned = mentionsIn(text, staff);
        CaseNote note = notes.save(new CaseNote(c.getId(), actor.getClinic().getId(), actor.getId(), actor.getDisplayName(), text,
                mentioned, clock.instant()));
        return view(note, staff);
    }

    /** Notes that mention me, newest first, and how many arrived since I last looked. */
    @GetMapping("/api/clinic/mentions")
    @Transactional(readOnly = true)
    public Mentions mentions(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        Instant lastSeen = seen.findById(actor.getId()).map(MentionsSeen::getSeenAt).orElse(Instant.EPOCH);
        List<MentionView> items = notes.mentioning(actor.getClinic().getId(), actor.getId().toString(), PageRequest.of(0, 20)).stream()
                .filter(n -> n.mentioned().contains(actor.getId()))
                .map(n -> {
                    FollowUpCase c = caseRepository.findById(n.getCaseId()).orElse(null);
                    return new MentionView(n.getId(), n.getCaseId(), c == null ? null : c.getPatient().getId(),
                            c == null ? null : c.getPatient().getFullName(), n.getAuthorName(), n.getText(), n.getAt(),
                            n.getAt().isAfter(lastSeen));
                })
                .toList();
        return new Mentions((int) items.stream().filter(MentionView::unread).count(), items);
    }

    @PostMapping("/api/clinic/mentions/seen")
    @Transactional
    public Mentions markSeen(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        MentionsSeen row = seen.findById(actor.getId()).orElseGet(() -> new MentionsSeen(actor.getId(), Instant.EPOCH));
        row.seen(clock.instant());
        seen.save(row);
        return mentions(jwt);
    }

    /** "@Display Name" for each colleague named in the text; longer names first, so "@Dr Priya Nair" wins over "@Dr Priya". */
    static List<UUID> mentionsIn(String text, List<AppUser> staff) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<UUID> found = new java.util.ArrayList<>();
        StringBuilder remaining = new StringBuilder(lower);
        for (AppUser u : staff.stream().sorted(Comparator.comparingInt((AppUser u) -> -u.getDisplayName().length())).toList()) {
            String handle = "@" + u.getDisplayName().toLowerCase(Locale.ROOT);
            int at = remaining.indexOf(handle);
            if (at >= 0) {
                found.add(u.getId());
                remaining.replace(at, at + handle.length(), " ".repeat(handle.length()));
            }
        }
        return found;
    }

    private List<AppUser> staff(AppUser actor) {
        List<AppUser> people = new java.util.ArrayList<>(grants.findByClinicIdAndRevokedAtIsNullOrderByGrantedAt(actor.getClinic().getId()).stream()
                .map(g -> g.getAppUser())
                .distinct()
                .filter(staffAccess::canManageFollowUp)
                .toList());
        if (people.stream().noneMatch(u -> u.getId().equals(actor.getId()))) {
            people.add(actor);
        }
        return people;
    }

    private static NoteView view(CaseNote n, List<AppUser> staff) {
        List<Mentioned> mentioned = n.mentioned().stream()
                .map(id -> staff.stream().filter(u -> u.getId().equals(id)).findFirst()
                        .map(u -> new Mentioned(u.getId(), u.getDisplayName()))
                        .orElse(new Mentioned(id, "Former staff member")))
                .toList();
        return new NoteView(n.getId(), n.getCaseId(), n.getAuthorName(), n.getText(), mentioned, n.getAt());
    }

    private AppUser requireFollowUpStaff(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (!staffAccess.canManageFollowUp(user) || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Case notes are for clinic doctors and nurses.");
        }
        return user;
    }
}
