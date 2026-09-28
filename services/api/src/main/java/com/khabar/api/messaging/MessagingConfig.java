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

    @Bean
    public TelegramBotClient telegramBotClient(@Value("${khabar.telegram.bot-token:}") String botToken,
                                               @Value("${khabar.telegram.api-base-url:https://api.telegram.org}") String baseUrl) {
        return new TelegramBotClient(baseUrl, botToken);
    }

    /** Telegram when a bot token is set; otherwise the local outbox. */
    @Bean
    public Messenger messenger(TelegramBotClient bot) {
        if (!bot.configured()) {
            log.info("Telegram not configured: messages go to the outbox table only.");
            return new OutboxMessenger();
        }
        return new TelegramMessenger(bot);
    }
}
