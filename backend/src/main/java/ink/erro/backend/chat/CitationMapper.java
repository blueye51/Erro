package ink.erro.backend.chat;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Component
public class CitationMapper {
    public record CitedAnswer(String reply,List<Source> sources,List<String> warnings) {}
    public record DocumentSource(String id,String filename,String location,String method) {}
    public record DocumentAnswer(String reply,List<DocumentSource> sources,List<String> warnings) {}
    public DocumentAnswer documents(String answer,List<ink.erro.backend.document.DocumentModels.Evidence> evidence) {
        return documents(answer,evidence,false);
    }
    public DocumentAnswer documents(String answer,List<ink.erro.backend.document.DocumentModels.Evidence> evidence,boolean aliases) {
        var allowed=new HashMap<String,ink.erro.backend.document.DocumentModels.Evidence>();
        for(int i=0;i<evidence.size();i++) allowed.put(aliases?"D"+(i+1):evidence.get(i).id(),evidence.get(i));
        var used=new LinkedHashMap<String,DocumentSource>(); var warnings=new ArrayList<String>();
        var matcher=Pattern.compile("\\[(D(?:\\d|-)[^\\]]*)\\]").matcher(answer); var result=new StringBuilder();
        while(matcher.find()) {
            var replacement=new StringBuilder();
            for(String id:matcher.group(1).split("[,;\\s]+")) {
                var e=allowed.get(id);
                if(e==null) warnings.add("An unsupported document citation was removed.");
                else {
                    used.putIfAbsent(e.id(),new DocumentSource(e.id(),e.filename(),e.location(),e.method()));
                    replacement.append("[Document ").append(new ArrayList<>(used.keySet()).indexOf(e.id())+1).append("]");
                }
            }
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return new DocumentAnswer(result.toString(),List.copyOf(used.values()),warnings.stream().distinct().toList());
    }
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
