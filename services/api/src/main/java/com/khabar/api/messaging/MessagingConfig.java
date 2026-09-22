package com.khabar.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfig.class);

    /** WhatsApp when a phone number id and access token are set; otherwise the local outbox. */
    @Bean
    public Messenger messenger(@Value("${khabar.whatsapp.phone-number-id:}") String phoneNumberId,
                               @Value("${khabar.whatsapp.access-token:}") String accessToken,
                               @Value("${khabar.whatsapp.api-version:v21.0}") String apiVersion,
                               @Value("${khabar.whatsapp.api-base-url:https://graph.facebook.com}") String baseUrl,
                               @Value("${khabar.whatsapp.checkin-template:}") String checkInTemplate) {
        if (phoneNumberId.isBlank() || accessToken.isBlank()) {
            log.info("WhatsApp not configured: messages go to the outbox table only.");
            return new OutboxMessenger();
        }
        return new WhatsAppCloudMessenger(baseUrl, apiVersion, phoneNumberId, accessToken, checkInTemplate);
    }
}
