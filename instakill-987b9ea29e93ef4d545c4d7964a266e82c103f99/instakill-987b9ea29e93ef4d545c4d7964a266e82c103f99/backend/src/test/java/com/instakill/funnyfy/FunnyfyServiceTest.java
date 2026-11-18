package com.instakill.funnyfy;

import com.instakill.funnyfy.LocalLlmClient.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunnyfyServiceTest {

    private final LocalLlmClient llm = mock(LocalLlmClient.class);
    private final FunnyfyService service = new FunnyfyService(llm);

    @Test
    void sendsSystemPromptExampleAndTrimmedText() {
        List<Message> expected = FunnyfyService.buildMessages("Going to the lab");
        when(llm.complete(eq(expected), anyDouble(), anyInt())).thenReturn("Marching bravely to the lab 🧪");

        assertThat(service.makeFunnier("  Going to the lab  ")).isEqualTo("Marching bravely to the lab 🧪");
        verify(llm).complete(eq(expected), anyDouble(), anyInt());
    }

    @Test
    void conversationHasSystemThenExampleThenUserPost() {
        List<Message> messages = FunnyfyService.buildMessages(" hello ");

        assertThat(messages).extracting(Message::role).containsExactly("system", "user", "assistant", "user");
        assertThat(messages.getLast().content()).isEqualTo("Rewrite this post: hello");
    }

    @Test
    void removesSurroundingQuotesAndWhitespace() {
        assertThat(FunnyfyService.clean("  \"Hey everyone! So much fun!\"  ")).isEqualTo("Hey everyone! So much fun!");
        assertThat(FunnyfyService.clean("“quoted”")).isEqualTo("quoted");
        assertThat(FunnyfyService.clean("keeps \"inner\" quotes")).isEqualTo("keeps \"inner\" quotes");
    }

    @Test
    void limitsLengthToPostMaximum() {
        assertThat(FunnyfyService.clean("a".repeat(1500))).hasSize(FunnyfyService.MAX_LENGTH);
    }

    @Test
    void emptyAnswerIsAnError() {
        when(llm.complete(anyList(), anyDouble(), anyInt())).thenReturn(" \"\" ");

        assertThatThrownBy(() -> service.makeFunnier("x"))
                .isInstanceOf(LlmException.class);
    }
}
