package ink.erro.backend.document;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import ink.erro.backend.electrical.*;
import ink.erro.backend.product.*;
import static ink.erro.backend.document.DocumentModels.*;

/** Reuse existing rules per excerpt; never collapse multiple circuits into global scalar inputs. */
@Service
public class DocumentAssessmentService {
    public record Assessment(String evidenceId,String filename,String location,
        List<ElectricalCalculationService.Calculation> calculations,List<ElectricalValidationService.Check> checks,
        List<ProductModels.Candidate> products,BigDecimal catalogModulesEach,List<String> warnings) {}
    private final ElectricalParameterExtractor parameters;
    private final ElectricalIntentService intents;
    private final ElectricalCalculationService calculations;
    private final ElectricalValidationService validation;
    private final ProductService products;
    public DocumentAssessmentService(ElectricalParameterExtractor parameters,ElectricalIntentService intents,
        ElectricalCalculationService calculations,ElectricalValidationService validation,ProductService products) {
        this.parameters=parameters; this.intents=intents; this.calculations=calculations; this.validation=validation; this.products=products;
    }
    public List<Assessment> assess(List<Evidence> excerpts) {
        var result=new ArrayList<Assessment>();
        for(var evidence:excerpts.stream().filter(e->!e.method().equals("VISION")).limit(6).toList()) {
            String text=evidence.text();
            if(evidence.items().size()==1 && !evidence.items().getFirst().partNumber().isBlank())
                text+="\npart number: "+evidence.items().getFirst().partNumber();
            var p=parameters.extract(text); var categories=intents.detect(text);
            var assessed=validation.assess(text,p,categories);
            var warnings=new ArrayList<>(assessed.warnings()); warnings.addAll(p.ambiguities());
            List<ElectricalCalculationService.Calculation> computed=List.of();
            try { computed=calculations.applicable(p,categories); } catch(IllegalArgumentException ex) { warnings.add(ex.getMessage()); }
            List<ProductModels.Candidate> candidates=List.of(); BigDecimal width=null;
            // Exact reference is essential: never use a similar device's DIN width.
            if(p.attributes().containsKey("partNumber")) {
                try {
                    candidates=products.retrieve(text,EnumSet.allOf(ElectricalIntentService.Category.class),p);
                    if(candidates.size()==1) {
                        var attributes=candidates.getFirst().specifications().get("additional_attributes");
                        if(attributes!=null) {
                            var node=tools.jackson.databind.json.JsonMapper.builder().build().readTree(attributes.toString()).path("dinModules");
                            String value=node.asText("");
                            if(value.matches("\\d{1,3}(?:\\.\\d{1,3})?")) width=new BigDecimal(value);
                        }
                    }
                } catch(RuntimeException ex) { warnings.add("Catalog lookup for this document reference failed; its specifications remain unknown."); }
            }
            result.add(new Assessment(evidence.id(),evidence.filename(),evidence.location(),computed,assessed.checks(),candidates,width,warnings));
        }
        return List.copyOf(result);
    }
}
