package com.khabar.api.dev;

import com.khabar.api.messaging.CheckInSender;
import com.khabar.api.messaging.OutboundMessage;
import com.khabar.api.messaging.OutboundMessageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** `local` profile only: send due check-ins now, and see what would have gone out on WhatsApp. */
@RestController
@RequestMapping("/dev")
@Profile("local")
public class DevMessagingController {

    private final CheckInSender sender;
    private final OutboundMessageRepository outbox;

    public DevMessagingController(CheckInSender sender, OutboundMessageRepository outbox) {
        this.sender = sender;
        this.outbox = outbox;
    }

    public record OutboxEntry(Instant at, UUID patientId, String kind, String channel, String text, boolean delivered, String error) {
    }

    @PostMapping("/check-ins/run")
    public Map<String, Integer> runCheckIns() {
        return Map.of("sent", sender.sendDue());
    }

    @GetMapping("/outbox")
    @Transactional(readOnly = true)
    public List<OutboxEntry> outbox() {
        return outbox.findTop50ByOrderByIdDesc().stream()
                .map((OutboundMessage m) -> new OutboxEntry(m.getCreatedAt(), m.getPatientId(), m.getKind(), m.getChannel(), m.getText(), m.isDelivered(), m.getError()))
                .toList();
    }
}
