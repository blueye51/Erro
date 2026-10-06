package ink.erro.backend.knowledge;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class EmbeddingTest {
    HttpServer server;
    HttpEmbeddingService service;
    volatile String response;
    volatile String authorization;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/embeddings",exchange->{
            authorization=exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().readAllBytes();
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            try(exchange) { exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); }
        });
        server.start();
        var properties=new KnowledgeProperties(); properties.setEmbeddingEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/embeddings");
        properties.setEmbeddingApiKey("fixture-embedding-key"); properties.setEmbeddingModel("fixture"); properties.setEmbeddingDimensions(3);
        service=new HttpEmbeddingService(properties);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void preservesProviderIndicesAndUsesSeparateCredential() {
        response="{\"data\":[{\"index\":1,\"embedding\":[0,1,0]},{\"index\":0,\"embedding\":[1,0,0]}]}";
        var vectors=service.embed(List.of("first","second"));
        assertThat(vectors.getFirst()).containsExactly(1,0,0);
        assertThat(authorization).isEqualTo("Bearer fixture-embedding-key");
    }
    @Test void invalidDimensionsAndZeroVectorsFailSafely() {
        for(String data:List.of("[1,0]","[0,0,0]","[1,\"secret\",0]")) {
            response="{\"data\":[{\"index\":0,\"embedding\":"+data+"}]}";
            assertThatThrownBy(()->service.embed(List.of("source"))).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Embedding provider failed or returned an invalid vector.");
        }
    }
}
