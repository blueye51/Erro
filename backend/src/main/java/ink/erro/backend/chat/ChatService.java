package ink.erro.backend.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.ai.*;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.KnowledgeModels.Source;
import ink.erro.backend.product.ProductModels.Candidate;

@Service
public class ChatService {
    public record ChatReply(String reply,String requestId,List<Source> sources,
        List<ElectricalCalculationService.Calculation> calculations,List<String> assumptions,List<String> warnings,
        List<String> missingInformation,List<ElectricalValidationService.Check> validations,List<Candidate> products,boolean contextReset) {}
    private final AiService ai;
    private final ElectricalContextService electrical;
    private final ProblemContextService problems;
    private final CitationMapper citations;
    private final JsonMapper json=JsonMapper.builder().build();
    public ChatService(AiService ai,ElectricalContextService electrical,ProblemContextService problems,CitationMapper citations) {
        this.ai=ai; this.electrical=electrical; this.problems=problems; this.citations=citations;
    }
    public ChatReply reply(String message,List<String> previous) {
        String id=UUID.randomUUID().toString();
        var problem=problems.resolve(message.strip(),previous);
        var context=electrical.prepare(problem.question(),problem.assumptions());
        LoggerFactory.getLogger(getClass()).info("chat request={} intents={} parameters={} queryHash={} retrievalMode={} evidence={} calculations={} validators={}",id,
                context.intents(),context.parameters().values().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().value())),
                fingerprint(context.retrievalQuery()),context.retrieval().mode(),context.retrieval().hits().stream().map(h->h.source().documentId()+":"+h.source().relevance()).toList(),
                context.calculations().stream().map(ElectricalCalculationService.Calculation::name).toList(),context.validations().stream().map(c->c.rule()+":"+c.status()).toList());
        String answer;
        var failures=context.validations().stream().filter(c->c.status()==ElectricalValidationService.Status.FAIL).toList();
        if(!failures.isEmpty()) {
            answer="The stated combination is unsuitable under the stated conditions.\n\n"+String.join("\n\n",failures.stream().map(ElectricalValidationService.Check::explanation).toList());
        } else {
            try { answer=ai.reply(json.writeValueAsString(Map.of("userQuestion",problem.question(),"referenceData",context))); }
            catch(AiException failure) {
                LoggerFactory.getLogger(getClass()).warn("chat request={} providerErrorStatus={}",id,failure.status().value());
                throw failure;
            }
        }
        if(failures.isEmpty() && !context.missingInformation().isEmpty()
                && context.validations().stream().anyMatch(c->c.rule().equals("protection-system")))
            answer="There is not enough verified information to select protection safely. Load current alone does not determine a breaker rating.\n\n"+answer;
        var cited=citations.map(answer,context.retrieval().hits());
        var warnings=new ArrayList<>(context.warnings()); warnings.addAll(cited.warnings());
        if(!context.retrieval().hits().isEmpty() && cited.sources().isEmpty() && failures.isEmpty()) warnings.add("The generated explanation did not cite retrieved evidence. Treat uncited claims as unverified.");
        return new ChatReply(cited.reply(),id,cited.sources(),context.calculations(),context.assumptions(),warnings,
                context.missingInformation(),context.validations(),context.products(),problem.reset());
    }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,16); }
        catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
