package ink.erro.backend.knowledge;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties("knowledge")
public class KnowledgeProperties {
    private String adminToken = "", embeddingEndpoint = "", embeddingApiKey = "", embeddingModel = "";
    private int keywordTopK = 24, vectorTopK = 16, finalChunks = 6, maxChunkChars = 1600,
            maxContextChars = 12000, embeddingDimensions = 1536;
    private double minRelevance = .15;
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String v) { adminToken = v; }
    @Min(1) @Max(100) public int getKeywordTopK() { return keywordTopK; }
    public void setKeywordTopK(int v) { keywordTopK = v; }
    @Min(1) @Max(100) public int getVectorTopK() { return vectorTopK; }
    public void setVectorTopK(int v) { vectorTopK = v; }
    @Min(1) @Max(12) public int getFinalChunks() { return finalChunks; }
    public void setFinalChunks(int v) { finalChunks = v; }
    @Min(400) @Max(3000) public int getMaxChunkChars() { return maxChunkChars; }
    public void setMaxChunkChars(int v) { maxChunkChars = v; }
    @Min(2000) @Max(24000) public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int v) { maxContextChars = v; }
    @DecimalMin("0.01") @DecimalMax("1") public double getMinRelevance() { return minRelevance; }
    public void setMinRelevance(double v) { minRelevance = v; }
    public String getEmbeddingEndpoint() { return embeddingEndpoint; }
    public void setEmbeddingEndpoint(String v) { embeddingEndpoint = v; }
    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String v) { embeddingApiKey = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { embeddingModel = v; }
    @Min(1) @Max(4096) public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int v) { embeddingDimensions = v; }
}
