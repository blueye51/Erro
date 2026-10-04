package ink.erro.backend.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.model() == null || properties.model().isBlank()) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is not configured yet.");
        }

        var request = new LinkedHashMap<String, Object>();
        request.put("model", properties.model());
        request.put("input", message);
        request.put("store", false);
        request.put("max_output_tokens", properties.maxOutputTokens());
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
            throw new AiException(HttpStatus.BAD_GATEWAY,
                    "The AI could not finish the reply. Please try a shorter message.");
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
