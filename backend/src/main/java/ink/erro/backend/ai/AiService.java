package ink.erro.backend.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiService {
    private static final String SYSTEM_INSTRUCTION = loadInstruction();
    private static String loadInstruction() {
        try (var stream = AiService.class.getResourceAsStream("/knowledge/electrical-system.txt")) {
            if (stream == null) throw new IllegalStateException("Missing electrical system instruction");
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot load electrical system instruction", ex); }
    }
    public static String systemInstruction() { return SYSTEM_INSTRUCTION; }
    private final AiProperties properties;
    private final RestClient client;

    public AiService(AiProperties properties) {
        this.properties = properties;
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String reply(String message) {
        return generate(properties.model(), message, SYSTEM_INSTRUCTION, properties.maxOutputTokens(), false);
    }

    /** Inline images only: never upload provider files, fetch URLs or create conversations. */
    public String analyzeImage(String model, String instructions, String prompt, byte[] png) {
        var content = List.of(Map.of("type", "input_text", "text", prompt),
                Map.of("type", "input_image", "image_url", "data:image/png;base64," +
                        java.util.Base64.getEncoder().encodeToString(png), "detail", "original"));
        return generate(model, List.of(Map.of("role", "user", "content", content)), instructions, 8192, true);
    }

    private String generate(String model, Object input, String instructions, int outputTokens, boolean json) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || model == null || model.isBlank()) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is not configured yet.");
        }

        var request = new LinkedHashMap<String, Object>();
        request.put("model", model);
        request.put("input", input);
        request.put("instructions", instructions);
        request.put("store", false);
        request.put("max_output_tokens", outputTokens);
        if (json) request.put("text", Map.of("format", Map.of("type", "json_object")));
        if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
            request.put("reasoning", Map.of("effort", properties.reasoningEffort().strip()));
        }

        try {
            var response = client.post().uri(properties.endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(request)
                    .retrieve().body(JsonNode.class);
            return extractReply(response);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status == 429) {
                throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI chat is busy right now. Please try again shortly.");
            }
            if (status == 401 || status == 403) {
                throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI chat is not configured correctly.");
            }
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI service could not reply. Please try again.");
        } catch (ResourceAccessException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                    throw new AiException(HttpStatus.GATEWAY_TIMEOUT, "The reply took too long. Please try again.");
                }
            }
            throw new AiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service. Please try again.");
        } catch (RestClientException failure) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI service returned an invalid reply. Please try again.");
        }
    }

    private String extractReply(JsonNode response) {
        if (response == null || !"completed".equals(response.path("status").asText())) {
            if(response!=null && "max_output_tokens".equals(response.path("incomplete_details").path("reason").asText()))
                throw new AiException(HttpStatus.BAD_GATEWAY,
                        "The answer reached the response length limit. Ask for a concise answer or one section at a time.");
            throw new AiException(HttpStatus.BAD_GATEWAY,
                    "The AI did not complete the reply. Please try again.");
        }
        var parts = new ArrayList<String>();
        for (var item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())
                    || !"assistant".equals(item.path("role").asText())) continue;
            for (var content : item.path("content")) {
                String type = content.path("type").asText();
                String text = switch (type) {
                    case "output_text" -> content.path("text").asText("");
                    case "refusal" -> content.path("refusal").asText("");
                    default -> "";
                };
                if (!text.isBlank()) parts.add(text.strip());
            }
        }
        if (parts.isEmpty()) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI service returned an empty reply. Please try again.");
        }
        return String.join("\n\n", parts).replace("\r\n", "\n").strip();
    }
}
