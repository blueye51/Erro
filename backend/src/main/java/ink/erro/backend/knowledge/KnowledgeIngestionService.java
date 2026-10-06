package ink.erro.backend.knowledge;
import java.util.UUID;
import static ink.erro.backend.knowledge.KnowledgeModels.*;
public interface KnowledgeIngestionService {
    UUID ingest(SourceInput input);
    void reindex(UUID id);
}
