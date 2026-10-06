package ink.erro.backend.chat;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Component
public class CitationMapper {
    public record CitedAnswer(String reply,List<Source> sources,List<String> warnings) {}
    private static final Pattern CITATION=Pattern.compile("\\[(K-[^\\]\\s]+|S\\d+)\\]");
    public CitedAnswer map(String answer,List<Hit> retrieved) {
        Map<String,Source> allowed=new HashMap<>(); retrieved.forEach(h->allowed.put(h.source().id(),h.source()));
        Map<String,Source> cited=new LinkedHashMap<>(); var warnings=new ArrayList<String>();
        var matcher=CITATION.matcher(answer); StringBuilder cleaned=new StringBuilder();
        while(matcher.find()) {
            String id=matcher.group(1);
            if(allowed.containsKey(id)) {
                cited.putIfAbsent(id,allowed.get(id));
                matcher.appendReplacement(cleaned,"["+(new ArrayList<>(cited.keySet()).indexOf(id)+1)+"]");
            } else {
                matcher.appendReplacement(cleaned,""); warnings.add("An unsupported source reference was removed from the generated answer.");
            }
        }
        matcher.appendTail(cleaned);
        return new CitedAnswer(cleaned.toString(),List.copyOf(cited.values()),warnings.stream().distinct().toList());
    }
}
