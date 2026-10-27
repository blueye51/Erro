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
import ink.erro.backend.document.*;

@Service
public class ChatService {
    public record Turn(String role,String content) {}
    public record ChatReply(String reply,String requestId,List<Source> sources,
        List<ElectricalCalculationService.Calculation> calculations,List<String> assumptions,List<String> warnings,
        List<String> missingInformation,List<ElectricalValidationService.Check> validations,List<Candidate> products,boolean contextReset,
        List<CitationMapper.DocumentSource> documentSources) {}
    private final AiService ai;
    private final ElectricalContextService electrical;
    private final ProblemContextService problems;
    private final CitationMapper citations;
    private final DocumentContextService documents;
    private final DocumentStore documentStore;
    private final JsonMapper json=JsonMapper.builder().build();
    public ChatService(AiService ai,ElectricalContextService electrical,ProblemContextService problems,CitationMapper citations,
            DocumentContextService documents,DocumentStore documentStore) {
        this.ai=ai; this.electrical=electrical; this.problems=problems; this.citations=citations;
        this.documents=documents; this.documentStore=documentStore;
    }
    public ChatReply reply(String message,List<String> previous) {
        return reply(message,previous,null,List.of());
    }
    public ChatReply reply(String message,List<String> previous,String token,List<String> attachmentIds) {
        var problem=problems.resolve(message.strip(),previous,token!=null);
        if(problem.reset() && token!=null) documentStore.delete(token);
        var documentContext=problem.reset() || attachmentIds==null || attachmentIds.isEmpty() ? DocumentModels.Context.empty() :
                documents.retrieve(token,attachmentIds,problem.question());
        return respond(message,problem,documentContext,List.of(),false);
    }
    public ChatReply replySaved(String message,List<String> previous,List<Turn> dialogue,List<DocumentModels.Extraction> files) {
        var problem=problems.resolve(message.strip(),previous,!files.isEmpty());
        var documentContext=problem.reset()?DocumentModels.Context.empty():documents.fromExtractions(files,problem.question());
        return respond(message,problem,documentContext,problem.reset()?List.of():dialogue,true);
    }
    private ChatReply respond(String message,ProblemContextService.Problem problem,DocumentModels.Context documentContext,List<Turn> dialogue,boolean saved) {
        String id=UUID.randomUUID().toString();
        var context=electrical.prepare(problem.question(),problem.assumptions(),DocumentContextService.searchHints(documentContext));
        var input=saved?new ChatPrompt().build(message.strip(),problem.previousMessages(),dialogue,context,documentContext):null;
        var allowedHits=input==null?context.retrieval().hits():input.hits();
        var allowedDocuments=input==null?documentContext.excerpts():input.excerpts();
        LoggerFactory.getLogger(getClass()).info("chat request={} intents={} parameters={} queryHash={} retrievalMode={} evidence={} calculations={} validators={}",id,
                context.intents(),context.parameters().values().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().value())),
                fingerprint(context.retrievalQuery()),context.retrieval().mode(),context.retrieval().hits().stream().map(h->h.source().documentId()+":"+h.source().relevance()).toList(),
                context.calculations().stream().map(ElectricalCalculationService.Calculation::name).toList(),context.validations().stream().map(c->c.rule()+":"+c.status()).toList());
        String answer;
        var failures=context.validations().stream().filter(c->c.status()==ElectricalValidationService.Status.FAIL).toList();
        if(!failures.isEmpty()) {
            answer="The stated combination is unsuitable under the stated conditions.\n\n"+String.join("\n\n",failures.stream().map(ElectricalValidationService.Check::explanation).toList());
        } else {
            try { answer=ai.reply(input==null?json.writeValueAsString(Map.of("userQuestion",message.strip(),
                    "previousUserMessages",problem.previousMessages(),"referenceData",context,"documentData",documentContext)):input.json()); }
            catch(AiException failure) {
                LoggerFactory.getLogger(getClass()).warn("chat request={} providerErrorStatus={}",id,failure.status().value());
                throw failure;
            }
        }
        if(failures.isEmpty() && !context.missingInformation().isEmpty()
                && context.validations().stream().anyMatch(c->c.rule().equals("protection-system")))
            answer="There is not enough verified information to select protection safely. Load current alone does not determine a breaker rating.\n\n"+answer;
        var cited=citations.map(answer,allowedHits);
        var documentCited=citations.documents(cited.reply(),allowedDocuments);
        var warnings=new ArrayList<>(context.warnings()); warnings.addAll(cited.warnings());
        documentContext.warnings().stream().filter(w->input==null || !w.startsWith("Only ")).forEach(warnings::add);
        if(input!=null && allowedDocuments.size()<documentContext.totalExcerpts()) warnings.add("This answer received "+allowedDocuments.size()+" of "+documentContext.totalExcerpts()+" saved document excerpts. Omitted evidence remains saved; this does not establish that the original is unreadable.");
        warnings.addAll(documentCited.warnings());
        if(input!=null && input.trimmed()) warnings.add("Some conversation/reference context was omitted for this answer. Your full chat remains saved.");
        var validations=new ArrayList<>(context.validations());
        var candidates=new ArrayList<>(context.products());
        documentContext.assessments().forEach(a->{
            warnings.addAll(a.warnings().stream().map(w->a.filename()+", "+a.location()+": "+w).toList());
            a.checks().forEach(c->validations.add(new ElectricalValidationService.Check("document:"+c.rule(),c.status(),a.filename()+", "+a.location()+": "+c.explanation())));
            a.products().forEach(p->{if(candidates.stream().noneMatch(existing->existing.id().equals(p.id()))) candidates.add(p);});
        });
        if(!documentContext.excerpts().isEmpty() && documentCited.sources().isEmpty() && failures.isEmpty())
            warnings.add("The generated explanation did not cite document evidence. Verify its claims against the originals.");
        if(!context.retrieval().hits().isEmpty() && cited.sources().isEmpty() && failures.isEmpty()) warnings.add("The generated explanation did not cite retrieved evidence. Treat uncited claims as unverified.");
        var computed=new ArrayList<>(context.calculations()); computed.addAll(documentContext.calculations());
        return new ChatReply(documentCited.reply(),id,cited.sources(),computed,context.assumptions(),warnings,
                context.missingInformation(),validations,candidates,problem.reset(),documentCited.sources());
    }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,16); }
        catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
