package ink.erro.backend.knowledge;
import java.util.List;
public interface EmbeddingService {
    boolean enabled();
    String modelKey();
    List<double[]> embed(List<String> texts);
}
