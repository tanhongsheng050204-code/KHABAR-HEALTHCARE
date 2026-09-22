package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.CheckInRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Sends every check-in that is due today or earlier and has not been sent yet. */
@Service
public class CheckInSender {

    private static final Logger log = LoggerFactory.getLogger(CheckInSender.class);

    private final CheckInRepository checkIns;
    private final PatientMessages messages;
    private final AdjustableClock clock;

    public CheckInSender(CheckInRepository checkIns, PatientMessages messages, AdjustableClock clock) {
        this.checkIns = checkIns;
        this.messages = messages;
        this.clock = clock;
    }

    /** Returns how many check-ins were delivered. */
    @Transactional
    public int sendDue() {
        List<CheckIn> due = checkIns.findByStatusAndDueDateLessThanEqual(CheckIn.Status.PENDING, LocalDate.now(clock));
        int delivered = 0;
        for (CheckIn checkIn : due) {
            String text = PatientMessages.checkInText(checkIn.getPatient().getPreferredLanguage(), checkIn.isFasting());
            Messenger.Result result = messages.send(checkIn.getPatient(), text, Messenger.Kind.CHECK_IN);
            if (result.delivered()) {
                checkIn.markSent(clock.instant());
                delivered++;
            } else {
                checkIn.markFailed();
                log.warn("Check-in {} (day {}) not delivered: {}", checkIn.getId(), checkIn.getDayNumber(), result.error());
            }
        }
        return delivered;
    }
}
