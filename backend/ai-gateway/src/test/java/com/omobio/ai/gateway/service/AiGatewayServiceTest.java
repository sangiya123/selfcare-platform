package com.omobio.ai.gateway.service;

import com.omobio.ai.gateway.domain.AiConversation;
import com.omobio.ai.gateway.domain.AiMessage;
import com.omobio.ai.gateway.repository.AiConversationRepository;
import com.omobio.ai.gateway.client.LlmClient;
import com.omobio.ai.gateway.client.LlmResponse;
import com.omobio.platform.common.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock private AiConversationRepository conversationRepository;
    @Mock private LlmClient llmClient;

    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        service = new AiGatewayService(conversationRepository, llmClient);
    }

    @Test
    @DisplayName("chat sends message to LLM and stores conversation")
    void chat_sendsToLlmAndStores() {
        when(llmClient.complete(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(new LlmResponse("Here is your balance: Rs. 250.00",
                        "gpt-4o-mini", 150));

        when(conversationRepository.save(any(AiConversation.class)))
                .thenAnswer(inv -> {
                    AiConversation c = inv.getArgument(0);
                    c.setId("conv-1");
                    return c;
                });

        String response = service.chat("dialog-lk", "acc-1",
                "What is my balance?", "gpt-4o-mini");

        assertThat(response).contains("balance");
        verify(llmClient).complete(anyString(), anyList(), eq("dialog-lk"), eq("gpt-4o-mini"));
        verify(conversationRepository).save(any(AiConversation.class));
    }

    @Test
    @DisplayName("chat throws BadRequestException for empty message")
    void chat_emptyMessage() {
        assertThatThrownBy(() -> service.chat("dialog-lk", "acc-1", "", "gpt-4o-mini"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Message cannot be empty");
    }

    @Test
    @DisplayName("chat throws BadRequestException for message exceeding max length")
    void chat_messageTooLong() {
        String longMessage = "x".repeat(5001);

        assertThatThrownBy(() -> service.chat("dialog-lk", "acc-1", longMessage, "gpt-4o-mini"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    @DisplayName("getConversation returns full history for tenant and account")
    void getConversation_history() {
        AiConversation conversation = AiConversation.builder()
                .id("conv-1")
                .tenantId("dialog-lk")
                .accountId("acc-1")
                .messages(List.of(
                        AiMessage.builder()
                                .role("user")
                                .content("What is my balance?")
                                .build(),
                        AiMessage.builder()
                                .role("assistant")
                                .content("Your balance is Rs. 250")
                                .build()
                ))
                .build();

        when(conversationRepository.findFirstByTenantIdAndAccountIdOrderByCreatedAtDesc(
                "dialog-lk", "acc-1"))
                .thenReturn(Optional.of(conversation));

        AiConversation result = service.getConversation("dialog-lk", "acc-1");

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
    }

    @Test
    @DisplayName("listTools returns available tools for tenant")
    void listTools() {
        var tools = service.listTools("dialog-lk");

        assertThat(tools).isNotEmpty();
        // All tools are tenant-isolated; each tool must include tenantId in its scope
        tools.forEach(tool ->
                assertThat(tool.getRequiredScopes()).contains("tenant:" + "dialog-lk"));
    }
}
