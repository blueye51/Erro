package ink.erro.backend.electrical;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import static ink.erro.backend.electrical.ElectricalIntentService.Category.*;

@Service
public class ProblemContextService {
    public record Problem(String question,boolean reset,List<String> assumptions,List<String> previousMessages) {}
    private static final Pattern NEW_PROBLEM = Pattern.compile(
            "^(?:new problem|different problem|another motor|different motor|start over|let['’]s start over|uus probleem|alustame uuesti)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CONTINUATION = Pattern.compile(
            "\\b(?:it|its|(?:same|this|that) (?:project|setup|system|installation|equipment))\\b|^(?:and|also|what about|how about)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final ElectricalIntentService intents;
    public ProblemContextService(ElectricalIntentService intents) { this.intents=intents; }
    public Problem resolve(String message,List<String> previous) {
        return resolve(message,previous,false);
    }
    public Problem resolve(String message,List<String> previous,boolean hasDocuments) {
        if(hasDocuments && NEW_PROBLEM.matcher(message.strip()).find()) return new Problem(message,true,List.of(),List.of());
        if(previous==null || previous.isEmpty()) return new Problem(message,false,List.of(),List.of());
        if(previous.stream().mapToInt(String::length).sum()>24000) throw new IllegalArgumentException("Problem context is too long; start a new problem.");
        String last=String.join("\n",previous);
        Set<ElectricalIntentService.Category> old = equipment(intents.detect(last)), current=equipment(intents.detect(message));
        boolean reset=NEW_PROBLEM.matcher(message.strip()).find()
                || (!hasDocuments && !CONTINUATION.matcher(message.strip()).find() && !current.isEmpty()
                    && !old.isEmpty() && Collections.disjoint(old,current));
        if(reset) return new Problem(message,true,List.of("Earlier problem parameters were cleared because the equipment/topic changed."),List.of());
        return new Problem(last+"\nFollow-up: "+message,false,
                List.of("Using the preceding user-supplied details for this active problem. Start a new problem when equipment or conditions change."),List.copyOf(previous));
    }
    private Set<ElectricalIntentService.Category> equipment(Set<ElectricalIntentService.Category> categories) {
        var copy=EnumSet.noneOf(ElectricalIntentService.Category.class); copy.addAll(categories);
        copy.retainAll(Set.of(MOTOR,TRANSFORMER,POWER_SUPPLY,RELAY,PLC,ENCLOSURE,LIGHTING)); return copy;
    }
}
