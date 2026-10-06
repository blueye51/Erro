package ink.erro.backend.knowledge;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpEmbeddingService implements EmbeddingService {
    private final KnowledgeProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();
    public HttpEmbeddingService(KnowledgeProperties properties) { this.properties = properties; }
    public boolean enabled() { return !properties.getEmbeddingEndpoint().isBlank() && !properties.getEmbeddingModel().isBlank(); }
    public String modelKey() { return properties.getEmbeddingModel() + ":" + properties.getEmbeddingDimensions() + ":" + DocumentIngestionService.hash(properties.getEmbeddingEndpoint()).substring(0,16); }
    public List<double[]> embed(List<String> texts) {
        if (!enabled()) return List.of();
        try {
            var body = json.writeValueAsString(Map.of("model", properties.getEmbeddingModel(), "input", texts,
                    "dimensions", properties.getEmbeddingDimensions()));
            var request = HttpRequest.newBuilder(URI.create(properties.getEmbeddingEndpoint()))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json");
            if (!properties.getEmbeddingApiKey().isBlank()) request.header("Authorization", "Bearer " + properties.getEmbeddingApiKey());
            var response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (response.statusCode() != 200 || bytes.length > 8_000_000) throw new IllegalStateException();
            var data = json.readTree(bytes).path("data");
            if (!data.isArray() || data.size() != texts.size()) throw new IllegalStateException();
            var vectors = new double[texts.size()][];
            for (var item : data) {
                int index = item.path("index").asInt(-1);
                var vector = item.path("embedding");
                if (index < 0 || index >= vectors.length || vectors[index] != null
                        || vector.size() != properties.getEmbeddingDimensions()) throw new IllegalStateException();
                double[] values = new double[vector.size()];
                double norm = 0;
                for (int i = 0; i < values.length; i++) {
                    if (!vector.get(i).isNumber()) throw new IllegalStateException();
                    values[i] = vector.get(i).asDouble();
                    if (!Double.isFinite(values[i])) throw new IllegalStateException();
                    norm += values[i] * values[i];
                }
                if (norm == 0 || !Double.isFinite(norm)) throw new IllegalStateException();
                vectors[index] = values;
            }
            return Arrays.asList(vectors);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted.");
        } catch (Exception ex) {
            // Never return provider bodies, URLs with credentials, or keys to the client/logs.
            throw new IllegalStateException("Embedding provider failed or returned an invalid vector.");
        }
    }
}
