package com.khabar.api.messaging;

/** Sends patient messages through the Telegram bot, to the chat the patient linked by sharing their number. */
public class TelegramMessenger implements Messenger {

    private final TelegramBotClient bot;

    public TelegramMessenger(TelegramBotClient bot) {
        this.bot = bot;
    }

    @Override
    public Result send(String recipient, String text, String language, Kind kind) {
        if (recipient == null || recipient.isBlank()) {
            return Result.failed("Telegram not linked");
        }
        long chatId;
        try {
            chatId = Long.parseLong(recipient.trim());
        } catch (NumberFormatException e) {
            return Result.failed("Telegram chat ID is not a number");
        }
        return bot.sendMessage(chatId, text);
    }

    @Override
    public String channel() {
        return "telegram";
    }
}
