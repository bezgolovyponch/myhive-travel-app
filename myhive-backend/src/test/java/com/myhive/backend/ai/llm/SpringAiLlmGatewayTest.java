package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.Violation;
import com.myhive.backend.ai.plan.ViolationCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiLlmGatewayTest {

    private static final String EMPTY_PLAN_JSON = "{\"packages\":[]}";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiProperties props = new AiProperties();
    private final SpringAiLlmGateway gateway =
            new SpringAiLlmGateway(chatModel, new PromptRenderer(), new LlmOutputParser(), props);

    private static ChatResponse response(String text, Integer promptTokens, Integer completionTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("qwen-test")
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    private static ChatResponse response(String text) {
        return response(text, 1, 1);
    }

    private static PlanRequest planRequest() {
        return new PlanRequest("en", "Prague", new Brief(1, 4, List.of(), "x", null, null, null, null, null),
                List.of(), List.of());
    }

    private OpenAiChatOptions capturedOptions() {
        return (OpenAiChatOptions) capturedPrompt().getOptions();
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    @Test
    void chatTurn_usesChatModelJsonModeAndThinkingOff_andMapsUsage() {
        String expectedModel = "qwen3.7-plus";
        String expectedReply = "hey";
        int expectedDays = 2;
        int expectedPromptTokens = 12;
        int expectedCompletionTokens = 34;
        props.setChatModel(expectedModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"reply\":\"" + expectedReply + "\",\"brief\":{\"days\":" + expectedDays + "},\"missingFields\":[]}",
                expectedPromptTokens, expectedCompletionTokens));

        ChatTurnResult result = gateway.chatTurn(new ChatTurnRequest("en", "Prague", List.of(), Brief.empty(), List.of(
                new ChatMessage(ChatMessage.USER, "2 days", "t"))));

        assertThat(result.reply()).isEqualTo(expectedReply);
        assertThat(result.briefUpdate().days()).isEqualTo(expectedDays);
        assertThat(result.usage().model()).isEqualTo(expectedModel);
        assertThat(result.usage().promptTokens()).isEqualTo(expectedPromptTokens);
        assertThat(result.usage().completionTokens()).isEqualTo(expectedCompletionTokens);
        OpenAiChatOptions options = capturedOptions();
        assertThat(options.getModel()).isEqualTo(expectedModel);
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
        assertThat(options.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void chatTurn_sendsSystemPromptAndWrapsTheLatestUserMessage() {
        String expectedLatestUserMessage = "we are 8 lads";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"reply\":\"ok\",\"brief\":{},\"missingFields\":[]}"));

        gateway.chatTurn(new ChatTurnRequest("en", "Prague", List.of(), Brief.empty(), List.of(
                new ChatMessage(ChatMessage.USER, "hi", "t1"),
                new ChatMessage(ChatMessage.ASSISTANT, "hello", "t2"),
                new ChatMessage(ChatMessage.USER, expectedLatestUserMessage, "t3"))));

        List<Message> messages = capturedPrompt().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(0).getText()).contains("Prague");
        assertThat(messages.get(1).getText()).isEqualTo("hi");
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(3).getText()).isEqualTo("<user>" + expectedLatestUserMessage + "</user>");
    }

    @Test
    void composePlan_usesThePlannerModelAndItsOwnTemperature() {
        String expectedPlannerModel = "qwen3.8-max";
        props.setPlannerModel(expectedPlannerModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(EMPTY_PLAN_JSON));

        PlanDraftResult result = gateway.composePlan(planRequest());

        assertThat(result.draft().packages()).isEmpty();
        assertThat(result.usage().model()).isEqualTo(expectedPlannerModel);
        OpenAiChatOptions options = capturedOptions();
        assertThat(options.getModel()).isEqualTo(expectedPlannerModel);
        assertThat(options.getTemperature()).isEqualTo(0.4);
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
    }

    @Test
    void repairPlan_resendsThePlannerPromptPlusTheViolations() {
        String expectedViolationDetail = "day 1 has no evening";
        when(chatModel.call(any(Prompt.class))).thenReturn(response(EMPTY_PLAN_JSON));

        gateway.repairPlan(new RepairRequest(planRequest(), new PlanDraft(List.of()),
                List.of(new Violation(ViolationCode.EMPTY_DAY, null, 1, expectedViolationDetail))));

        List<Message> messages = capturedPrompt().getInstructions();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(2).getText()).contains(expectedViolationDetail);
    }

    @Test
    void composePlan_wrapsTransportFailures() {
        props.setPlannerTimeout(Duration.ofSeconds(1));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));

        assertThatThrownBy(() -> gateway.composePlan(planRequest()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("connection reset");
    }

    @Test
    void composePlan_wrapsTimeoutsAsLlmUnavailable() {
        Duration expectedTimeout = Duration.ofMillis(50);
        props.setPlannerTimeout(expectedTimeout);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(2000L);
            return response(EMPTY_PLAN_JSON);
        });

        assertThatThrownBy(() -> gateway.composePlan(planRequest()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining(expectedTimeout.toString());
    }

    @Test
    void composePlan_invalidJsonSurfacesAsLlmOutputException() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("not json"));

        assertThatThrownBy(() -> gateway.composePlan(planRequest()))
                .isInstanceOf(LlmOutputException.class);
    }
}
