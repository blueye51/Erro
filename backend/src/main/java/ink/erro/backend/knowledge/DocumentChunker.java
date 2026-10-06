package ink.erro.backend.knowledge;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Component
public class DocumentChunker {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private final KnowledgeProperties properties;
    public DocumentChunker(KnowledgeProperties properties) { this.properties = properties; }

    public List<ChunkDraft> chunk(String content) {
        int max = properties.getMaxChunkChars();
        List<ChunkDraft> result = new ArrayList<>();
        List<String> path = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        for (String line : normalize(content).split("\n", -1)) {
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                flush(result, path, block, max);
                int level = heading.group(1).length();
                while (path.size() >= level) path.removeLast();
                path.add(heading.group(2).strip());
            } else if (line.isBlank()) {
                flush(result, path, block, max);
            } else {
                block.append(line).append('\n');
            }
        }
        flush(result, path, block, max);
        if (result.isEmpty()) throw new IllegalArgumentException("The source contains no indexable text.");
        if (result.size() > 128) throw new IllegalArgumentException("Too many sections; ingest smaller documents.");
        return List.copyOf(result);
    }

    private void flush(List<ChunkDraft> result, List<String> path, StringBuilder block, int max) {
        String text = block.toString().strip(); block.setLength(0);
        // Prefer whole paragraphs/tables, then line/sentence/word boundaries for oversized blocks.
        while (!text.isEmpty()) {
            int end = Math.min(max, text.length());
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end);
                if (boundary < max / 3) boundary = text.lastIndexOf(". ", end);
                if (boundary < max / 3) boundary = text.lastIndexOf(' ', end);
                if (boundary > 0) end = boundary + 1;
            }
            String part = text.substring(0, end).strip();
            String section = path.isEmpty() ? "Overview" : path.getLast();
            result.add(new ChunkDraft(section.substring(0, Math.min(300, section.length())),
                    String.join(" / ", path).substring(0, Math.min(2000, String.join(" / ", path).length())),
                    result.size(), part, (part.length() + 2) / 3));
            text = text.substring(end).strip();
        }
    }
    public static String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
    }
}
