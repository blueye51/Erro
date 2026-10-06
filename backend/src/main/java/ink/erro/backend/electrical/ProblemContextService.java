package ink.erro.backend.electrical;

import java.util.*;
import org.springframework.stereotype.Service;
import static ink.erro.backend.electrical.ElectricalIntentService.Category.*;

@Service
public class ProblemContextService {
    public record Problem(String question,boolean reset,List<String> assumptions) {}
    private final ElectricalIntentService intents;
    public ProblemContextService(ElectricalIntentService intents) { this.intents=intents; }
    public Problem resolve(String message,List<String> previous) {
        if(previous==null || previous.isEmpty()) return new Problem(message,false,List.of());
        if(previous.stream().mapToInt(String::length).sum()>24000) throw new IllegalArgumentException("Problem context is too long; start a new problem.");
        String last=String.join("\n",previous);
        Set<ElectricalIntentService.Category> old = equipment(intents.detect(last)), current=equipment(intents.detect(message));
        boolean reset=message.matches("(?is).*\\b(new problem|different problem|another motor|different motor|start over)\\b.*")
                || (!current.isEmpty() && !old.isEmpty() && Collections.disjoint(old,current));
        // Long self-contained requests are safer as a new problem than silently inheriting stale parameters.
        if(message.length()>500) reset=true;
        if(reset) return new Problem(message,true,List.of("Earlier problem parameters were cleared because the equipment/topic changed."));
        return new Problem(last+"\nFollow-up: "+message,false,List.of("Using the preceding user-supplied details for this active problem. Start a new problem when equipment or conditions change."));
    }
    private Set<ElectricalIntentService.Category> equipment(Set<ElectricalIntentService.Category> categories) {
        var copy=EnumSet.noneOf(ElectricalIntentService.Category.class); copy.addAll(categories);
        copy.retainAll(Set.of(MOTOR,TRANSFORMER,POWER_SUPPLY,RELAY,PLC,ENCLOSURE,LIGHTING)); return copy;
    }
}
