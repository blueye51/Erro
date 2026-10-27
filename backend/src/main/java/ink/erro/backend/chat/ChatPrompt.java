package ink.erro.backend.chat;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.document.DocumentModels;
import ink.erro.backend.document.DocumentEvidence;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.KnowledgeModels.Hit;
import ink.erro.backend.product.ProductModels.Candidate;

/** Storage is unlimited by the model window; each generation receives a bounded evidence selection. */
public final class ChatPrompt {
    public static final int MAX_CHARS=32000;
    public record Input(String json,List<Hit> hits,List<DocumentModels.Evidence> excerpts,boolean trimmed) {}
    private final JsonMapper json=JsonMapper.builder().build();
    public Input build(String question,List<String> users,List<ChatService.Turn> history,ElectricalContextService.Context context,DocumentModels.Context documents) {
        var hits=new ArrayList<>(context.retrieval().hits());
        var excerpts=new ArrayList<>(documents.excerpts());
        var dialogue=new ArrayList<>(history);
        var prior=new ArrayList<>(users);
        var products=new ArrayList<>(context.products().stream().map(this::product).toList());
        var assessments=new ArrayList<>(documents.assessments().stream().map(a->Map.<String,Object>of(
                "evidenceId",a.evidenceId(),"filename",a.filename(),"location",a.location(),"checks",a.checks(),
                "calculations",a.calculations().stream().map(this::calculation).toList(),
                "catalogModulesEach",a.catalogModulesEach()==null?"unknown":a.catalogModulesEach(),"warnings",notes(a.warnings()))).toList());
        var reference=new LinkedHashMap<String,Object>();
        reference.put("intents",context.intents());
        var p=context.parameters();
        reference.put("parameters",Map.of("values",p.values(),"standards",p.standards().stream().limit(12).toList(),
                "ambiguities",notes(p.ambiguities()),"jurisdiction",p.jurisdiction(),"manufacturer",p.manufacturer(),"currentType",p.currentType(),"attributes",p.attributes()));
        reference.put("calculations",context.calculations().stream().map(this::calculation).toList());
        reference.put("validations",context.validations());reference.put("assumptions",notes(context.assumptions()));
        reference.put("warnings",notes(context.warnings()));reference.put("missingInformation",notes(context.missingInformation()));
        var doc=new LinkedHashMap<String,Object>();
        doc.put("totalExcerpts",documents.totalExcerpts());
        doc.put("calculations",documents.calculations().stream().map(this::calculation).toList());
        boolean trimmed=false;
        for(int iteration=0;iteration<256;iteration++) {
            reference.put("retrieval",Map.of("hits",hits,"mode",context.retrieval().mode()));reference.put("products",products);
            doc.put("excerpts",excerpts.stream().map(DocumentEvidence::prompt).toList());doc.put("assessments",assessments.stream().filter(a->excerpts.stream().anyMatch(e->e.id().equals(a.get("evidenceId")))).toList());
            doc.put("complete",documents.complete() && excerpts.size()==documents.excerpts().size());
            doc.put("coverage",Map.of("extractedExcerpts",documents.totalExcerpts(),"suppliedExcerpts",excerpts.size(),
                    "omittedExcerpts",documents.totalExcerpts()-excerpts.size(),"uncertainExcerpts",documents.uncertainExcerpts(),
                    "allExtractedEvidenceSupplied",excerpts.size()==documents.totalExcerpts()));
            var warnings=new ArrayList<String>();
            if(excerpts.size()<documents.totalExcerpts()) warnings.add("This request includes "+excerpts.size()+" of "+documents.totalExcerpts()+" saved excerpts. The rest remain saved; this is a prompt coverage limit, not proof of source illegibility.");
            documents.warnings().stream().filter(w->!w.startsWith("Only ")).forEach(warnings::add);
            doc.put("warnings",notes(warnings));
            String input=json.writeValueAsString(Map.of("userQuestion",question,"previousUserMessages",prior,
                    "previousConversation",dialogue,"referenceData",reference,"documentData",doc,
                    "contextSelection",trimmed?"Some context was omitted to fit this request. Stored history is unchanged; do not claim an exhaustive result.":"Selected recent conversation and retrieved evidence."));
            if(input.length()<=MAX_CHARS) return new Input(input,List.copyOf(hits),List.copyOf(excerpts),trimmed);
            trimmed=true;
            if(!dialogue.isEmpty()) dialogue.removeFirst();
            else if(hits.size()>1) hits.removeLast();
            else if(!products.isEmpty()) products.removeLast();
            else if(!assessments.isEmpty()) assessments.removeLast();
            else if(prior.size()>1) prior.removeFirst();
            else if(excerpts.size()>1) excerpts.removeLast();
            else if(!prior.isEmpty()) prior.removeFirst();
            else if(!hits.isEmpty()) hits.removeLast();
            else if(!excerpts.isEmpty()) excerpts.removeLast();
            else {
                reference.clear();doc.clear();
                reference.put("warnings",List.of("Detailed reference metadata exceeded the request budget. Treat unsupported specifications as unknown."));
            }
        }
        throw new IllegalStateException("Unable to bound the generation context");
    }
    private List<String> notes(List<String> values) { return values.stream().distinct().limit(8).map(s->s.substring(0,Math.min(500,s.length()))).toList(); }
    private Map<String,Object> calculation(ElectricalCalculationService.Calculation c) {
        return Map.of("name",c.name(),"value",c.value(),"unit",c.unit(),"formula",c.formula(),"assumptions",notes(c.assumptions()));
    }
    private Map<String,Object> product(Candidate c) {
        var specs=new LinkedHashMap<String,Object>();
        c.specifications().forEach((k,v)->{ if(v!=null && !k.equals("additional_attributes")) specs.put(k,v instanceof String s?s.substring(0,Math.min(600,s.length())):v); });
        return Map.of("id",c.id(),"specifications",specs,"ratings",c.ratings().stream().limit(6).toList(),"checks",c.checks());
    }
}
