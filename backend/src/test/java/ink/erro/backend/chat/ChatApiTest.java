package ink.erro.backend.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import ink.erro.backend.ai.AiException;
import ink.erro.backend.ai.AiProperties;
import ink.erro.backend.ai.AiService;
import ink.erro.backend.config.ChatWebConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import({AiService.class, ChatErrorHandler.class, ChatWebConfiguration.class})
class ChatApiTest {
    private static final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> authHeaders = new ConcurrentLinkedQueue<>();
    private static final HttpServer provider = startProvider();
    private static volatile int providerStatus = 200;
    private static volatile String providerBody;
    private static volatile boolean slow;
    private static final String REPLY = """
            {"status":"completed","output":[
              {"type":"reasoning","content":[{"type":"reasoning_text","text":"Do not show this"}]},
              {"type":"message","role":"assistant","content":[
                {"type":"output_text","text":"  **Hello**  "},
                {"type":"output_text","text":"Second paragraph."}]}]}
            """;

    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("ai.endpoint", () -> "http://localhost:" + provider.getAddress().getPort() + "/responses");
        registry.add("ai.api-key", () -> "test-server-only-key");
        registry.add("ai.model", () -> "deepseek-flash");
        registry.add("ai.reasoning-effort", () -> "none");
        registry.add("ai.timeout", () -> "5s");
        registry.add("chat.allowed-origins", () -> "http://localhost:5173");
    }

    @BeforeEach
    void resetProvider() {
        requests.clear();
        authHeaders.clear();
        providerStatus = 200;
        providerBody = REPLY;
        slow = false;
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
    }

    @Test
    void forwardsOnlyEachMessageAndReturnsNormalizedAssistantText() throws Exception {
        for (String input : new String[]{"  First message  ", "Second message"}) {
            mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                            .content(JsonMapper.builder().build().writeValueAsString(new ChatController.ChatMessage(input))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.reply").value("**Hello**\n\nSecond paragraph."));
        }
        assertThat(requests).hasSize(2);
        var mapper = JsonMapper.builder().build();
        var first = mapper.readTree(requests.remove());
        var second = mapper.readTree(requests.remove());
        assertThat(first.path("input").asText()).isEqualTo("First message");
        assertThat(second.path("input").asText()).isEqualTo("Second message");
        assertThat(second.path("store").asBoolean()).isFalse();
        assertThat(second.path("model").asText()).isEqualTo("deepseek-flash");
        assertThat(second.path("max_output_tokens").asInt()).isEqualTo(2048);
        assertThat(second.path("reasoning").path("effort").asText()).isEqualTo("none");
        assertThat(second.size()).isEqualTo(5);
        assertThat(authHeaders).containsOnly("Bearer test-server-only-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"   \"}", "not JSON"})
    void rejectsInvalidInputWithoutCallingProvider(String body) throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsOversizedMessages() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(8001) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"401,503", "403,503", "429,503", "500,502"})
    void hidesProviderErrorsAndCredentials(int upstreamStatus, int expectedStatus) throws Exception {
        providerStatus = upstreamStatus;
        providerBody = "{\"error\":\"private provider detail and test-server-only-key\"}";
        var response = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.error").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private provider detail", "test-server-only-key");
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{\"status\":\"completed\",\"output\":[]}", "{\"status\":\"incomplete\"}"})
    void handlesUnusableProviderReplies(String body) throws Exception {
        providerBody = body;
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").isString());
    }

    @Test
    void returnsRefusalAsAnAssistantReply() throws Exception {
        providerBody = """
                {"status":"completed","output":[{"type":"message","role":"assistant",
                "content":[{"type":"refusal","refusal":"I cannot help with that request."}]}]}
                """;
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("I cannot help with that request."));
    }

    @Test
    void timesOutWithoutRetrying() throws Exception {
        slow = true;
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isGatewayTimeout());
        assertThat(requests).hasSize(1);
    }

    @Test
    void missingKeyFailsWithoutContactingProvider() {
        var service = new AiService(new AiProperties(java.net.URI.create("http://localhost:" + provider.getAddress().getPort()),
                "", "deepseek-flash", "none", 2048, Duration.ofSeconds(1)));
        assertThatThrownBy(() -> service.reply("Hello"))
                .isInstanceOf(AiException.class).hasMessage("AI chat is not configured yet.");
        assertThat(requests).isEmpty();
    }

    @Test
    void emptyReasoningSettingLeavesProviderDefaultUnchanged() throws Exception {
        var service = new AiService(new AiProperties(java.net.URI.create("http://localhost:"
                + provider.getAddress().getPort() + "/responses"),
                "test-server-only-key", "test-model", "", 2048, Duration.ofSeconds(5)));
        assertThat(service.reply("Hello")).isEqualTo("**Hello**\n\nSecond paragraph.");
        assertThat(requests).hasSize(1);
        var request = JsonMapper.builder().build().readTree(requests.remove());
        assertThat(request.has("reasoning")).isFalse();
        assertThat(request.path("input").asText()).isEqualTo("Hello");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.size()).isEqualTo(4);
    }

    @Test
    void onlyAllowsConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/chat").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/chat").header("Origin", "https://unrelated.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        assertThat(requests).isEmpty();
    }

    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/responses", exchange -> {
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
                var response = providerBody.getBytes(StandardCharsets.UTF_8);
                int status = providerStatus;
                if (slow) {
                    try { Thread.sleep(8000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, response.length);
                    exchange.getResponseBody().write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
