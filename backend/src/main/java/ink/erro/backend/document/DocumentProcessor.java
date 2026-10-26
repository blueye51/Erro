package ink.erro.backend.document;

import java.io.IOException;
import java.util.function.Consumer;

public interface DocumentProcessor {
    DocumentModels.Extraction extract(String filename,byte[] bytes,Consumer<String> progress) throws IOException;
}
