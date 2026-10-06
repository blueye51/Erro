package ink.erro.backend.knowledge;
import java.util.List;
import static ink.erro.backend.knowledge.KnowledgeModels.*;
public interface KnowledgeRetrievalService {
    default java.util.List<Hit> documents(java.util.List<java.util.UUID> ids, Jurisdiction jurisdiction) { return java.util.List.of(); }
    Retrieval retrieve(String query, List<String> standards, Jurisdiction jurisdiction, String manufacturer);
}
