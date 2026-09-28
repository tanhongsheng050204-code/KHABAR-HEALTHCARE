package com.khabar.api.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TelegramMessengerTest {

    final TelegramBotClient bot = mock(TelegramBotClient.class);
    final TelegramMessenger messenger = new TelegramMessenger(bot);

    @Test
    void aPatientWithoutALinkedChatIsNotSentAnything() {
        Messenger.Result result = messenger.send(null, "Your care plan", "ms", Messenger.Kind.SUMMARY);
        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).isEqualTo("Telegram not linked");
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void aLinkedChatIsSentTheText() {
        when(bot.sendMessage(987654321L, "Your care plan")).thenReturn(Messenger.Result.ok("42"));
        assertThat(messenger.send("987654321", "Your care plan", "ms", Messenger.Kind.SUMMARY).delivered()).isTrue();
    }

    @Test
    void theChannelIsTelegram() {
        assertThat(messenger.channel()).isEqualTo("telegram");
    }
}
