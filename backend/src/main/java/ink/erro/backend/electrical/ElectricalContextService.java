package ink.erro.backend.electrical;

import java.util.*;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;
import ink.erro.backend.knowledge.*;
import ink.erro.backend.product.*;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Service
public class ElectricalContextService {
    public record Context(String retrievalQuery, Set<ElectricalIntentService.Category> intents,
                          ElectricalParameterExtractor.Parameters parameters,
                          Retrieval retrieval, List<ElectricalCalculationService.Calculation> calculations,
                          List<ElectricalValidationService.Check> validations, List<ProductModels.Candidate> products,
                          List<String> assumptions,List<String> warnings,List<String> missingInformation) {}
    private final ElectricalIntentService intents;
    private final ElectricalParameterExtractor extractor;
    private final ElectricalCalculationService calculations;
    private final ElectricalValidationService validation;
    private final KnowledgeRetrievalService retrieval;
    private final ProductService products;
    private final KnowledgeProperties properties;
    public ElectricalContextService(ElectricalIntentService intents,ElectricalParameterExtractor extractor,
            ElectricalCalculationService calculations,ElectricalValidationService validation,KnowledgeRetrievalService retrieval,ProductService products,KnowledgeProperties properties) {
        this.intents=intents; this.extractor=extractor; this.calculations=calculations; this.validation=validation; this.retrieval=retrieval; this.products=products; this.properties=properties;
    }
    public Context prepare(String question,List<String> contextAssumptions) {
        var categories=intents.detect(question); var parameters=extractor.extract(question);
        String query=intents.expand(question,categories);
        var evidence=categories.isEmpty()?new Retrieval(List.of(),List.of(),"NOT_NEEDED"):
                retrieval.retrieve(query,parameters.standards(),parameters.jurisdiction(),parameters.manufacturer());
        var assessment=validation.assess(question,parameters,categories);
        var warnings=new ArrayList<>(assessment.warnings()); warnings.addAll(evidence.warnings());
        List<ElectricalCalculationService.Calculation> computed;
        try { computed=calculations.applicable(parameters,categories); }
        catch(IllegalArgumentException invalid) { computed=List.of(); warnings.add(invalid.getMessage()); }
        var assumptions=new ArrayList<>(contextAssumptions);
        if(!categories.isEmpty()) assumptions.add("Jurisdiction: "+parameters.jurisdiction()+"; confirm the actual installation location and applicable national adoption.");
        List<ProductModels.Candidate> candidates=List.of();
        if(categories.contains(ElectricalIntentService.Category.PRODUCT_SELECTION)||categories.contains(ElectricalIntentService.Category.PRODUCT_COMPATIBILITY)) {
            try { candidates=products.retrieve(question,categories,parameters); }
            catch(RuntimeException ex) {
                LoggerFactory.getLogger(getClass()).warn("product retrieval failed type={}",ex.getClass().getSimpleName());
                warnings.add("Product catalog retrieval failed. No product evidence is available.");
            }
            if(candidates.isEmpty()) warnings.add("No documented catalog candidate was found. Do not invent product specifications or compatibility.");
        }
        if(!candidates.isEmpty()) {
            var ids=candidates.stream().map(c->(UUID)c.specifications().get("source_document_id")).distinct().toList();
            try {
                var hits=new ArrayList<>(retrieval.documents(ids,parameters.jurisdiction()));
                for(var hit:evidence.hits()) if(hits.stream().noneMatch(h->h.source().id().equals(hit.source().id()))) hits.add(hit);
                var bounded=new ArrayList<Hit>(); int remaining=properties.getMaxContextChars();
                var json=tools.jackson.databind.json.JsonMapper.builder().build();
                for(var hit:hits) {
                    int size=json.writeValueAsString(hit).length();
                    if(bounded.size()>=properties.getFinalChunks()) break;
                    if(size<=remaining) { bounded.add(hit); remaining-=size; }
                }
                evidence=new Retrieval(List.copyOf(bounded),evidence.warnings(),evidence.mode());
            } catch(RuntimeException ex) { warnings.add("Catalog source excerpts could not be retrieved. Product source-document links still require inspection."); }
        }
        computed.forEach(c->assumptions.addAll(c.assumptions()));
        return new Context(query,categories,parameters,evidence,computed,assessment.checks(),candidates,
                assumptions.stream().distinct().toList(),warnings.stream().distinct().toList(),assessment.missingInformation());
    }
}
