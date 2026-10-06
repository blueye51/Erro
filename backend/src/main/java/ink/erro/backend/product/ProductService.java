package ink.erro.backend.product;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.knowledge.*;
import ink.erro.backend.electrical.*;
import static ink.erro.backend.product.ProductModels.*;
import static ink.erro.backend.electrical.ElectricalValidationService.*;

@Service
public class ProductService {
    private final JdbcTemplate jdbc;
    private final ElectricalValidationService validators;
    private static final String TABLE="erro_knowledge.product";
    public ProductService(JdbcTemplate jdbc,ElectricalValidationService validators) { this.jdbc=jdbc; this.validators=validators; }
    public List<Map<String,Object>> list(int offset) {
        return jdbc.queryForList("SELECT id,manufacturer,part_number,name,product_type,enabled,source_document_id FROM "+TABLE+" ORDER BY manufacturer,part_number LIMIT 50 OFFSET ?",offset);
    }
    @Transactional
    public UUID save(ProductInput input) {
        requireDatasheet(input.sourceDocumentId());
        DocumentIngestionService.safeUrl(input.datasheetUrl()); DocumentIngestionService.safeUrl(input.manualUrl()); DocumentIngestionService.safeUrl(input.manufacturerProductUrl());
        if(input.inputVoltageMin()!=null && input.inputVoltageMax()!=null && input.inputVoltageMin().compareTo(input.inputVoltageMax())>0)
            throw new IllegalArgumentException("Input voltage minimum exceeds maximum.");
        UUID id=UUID.randomUUID();
        var params=new MapSqlParameterSource().addValue("id",id);
        List<String> columns=new ArrayList<>(List.of("id")), placeholders=new ArrayList<>(List.of(":id")), updates=new ArrayList<>();
        // DTO fields map explicitly to relational snake_case columns; only additionalAttributes is JSONB.
        for(var field:ProductInput.class.getRecordComponents()) {
            String name=field.getName();
            if(name.equals("ratings")||name.equals("standards")) continue;
            String column=name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            Object value;
            try { value=field.getAccessor().invoke(input); } catch(ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
            if(value==null && Set.of("series","description").contains(name)) value="";
            if(name.equals("additionalAttributes")) value=JsonMapper.builder().build().writeValueAsString(value==null?Map.of():value);
            params.addValue(name,value); columns.add(column); placeholders.add(name.equals("additionalAttributes")?"CAST(:additionalAttributes AS jsonb)":":"+name);
            if(!Set.of("manufacturer","partNumber").contains(name)) updates.add(column+"=EXCLUDED."+column);
        }
        UUID saved=new NamedParameterJdbcTemplate(jdbc).queryForObject("INSERT INTO "+TABLE+" ("+String.join(",",columns)+") VALUES ("+String.join(",",placeholders)+") ON CONFLICT (manufacturer,part_number) DO UPDATE SET "+String.join(",",updates)+",updated_at=now() RETURNING id",params,UUID.class);
        jdbc.update("DELETE FROM erro_knowledge.product_rating WHERE product_id=?",saved);
        jdbc.update("DELETE FROM erro_knowledge.product_standard WHERE product_id=?",saved);
        for(var r:input.ratings()) jdbc.update("INSERT INTO erro_knowledge.product_rating (id,product_id,voltage,current_type,utilization_category,rated_current,motor_power,icu,ics,icn,standard_number,conditions) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),saved,r.voltage(),r.currentType(),r.utilizationCategory(),r.ratedCurrent(),r.motorPower(),r.icu(),r.ics(),r.icn(),r.standardNumber(),r.conditions()==null?"":r.conditions());
        for(var s:input.standards()) jdbc.update("INSERT INTO erro_knowledge.product_standard (product_id,standard_number,edition,certification) VALUES (?,?,?,?)",
                saved,s.standardNumber(),s.edition()==null?"":s.edition(),s.certification()==null?"":s.certification());
        return saved;
    }
    @Transactional
    public void compatibility(CompatibilityInput input) {
        requireDatasheet(input.sourceDocumentId());
        if(input.productId().equals(input.relatedProductId())) throw new IllegalArgumentException("Compatibility requires two distinct products.");
        jdbc.update("INSERT INTO erro_knowledge.product_compatibility (id,product_id,related_product_id,compatibility_type,conditions,source_document_id,verified,notes) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (product_id,related_product_id,compatibility_type) DO UPDATE SET conditions=EXCLUDED.conditions,source_document_id=EXCLUDED.source_document_id,verified=EXCLUDED.verified,notes=EXCLUDED.notes",
                UUID.randomUUID(),input.productId(),input.relatedProductId(),input.compatibilityType(),input.conditions(),input.sourceDocumentId(),input.verified(),input.notes()==null?"":input.notes());
    }
    private void requireDatasheet(UUID id) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM erro_knowledge.knowledge_document WHERE id=? AND enabled AND status='READY' AND source_type IN ('MANUFACTURER_DATASHEET','MANUFACTURER_GUIDE') AND copyright_status NOT IN ('METADATA_ONLY','RESTRICTED'))",Boolean.class,id)))
            throw new IllegalArgumentException("Provide an enabled, indexed manufacturer datasheet/guide with indexing rights.");
    }
    public List<Candidate> retrieve(String question,Set<ElectricalIntentService.Category> categories,ElectricalParameterExtractor.Parameters parameters) {
        var types=categories.stream().map(Enum::name).toList();
        if(types.isEmpty()) return List.of();
        var args=new MapSqlParameterSource().addValue("types",types).addValue("manufacturer",parameters.manufacturer())
                .addValue("jurisdictions",HybridKnowledgeRetrievalService.jurisdictions(parameters.jurisdiction()))
                .addValue("part",parameters.attributes().getOrDefault("partNumber",""));
        var rows=new NamedParameterJdbcTemplate(jdbc).queryForList("SELECT p.*,p.additional_attributes::text AS additional_attributes,p.updated_at::text AS updated_at FROM "+TABLE+" p JOIN erro_knowledge.knowledge_document d ON d.id=p.source_document_id WHERE p.enabled AND d.enabled AND d.status='READY' AND d.copyright_status NOT IN ('METADATA_ONLY','RESTRICTED') AND (d.effective_date IS NULL OR d.effective_date<=CURRENT_DATE) AND d.jurisdiction IN (:jurisdictions) AND p.product_type IN (:types) AND (:manufacturer='' OR lower(p.manufacturer)=lower(:manufacturer)) AND (:part='' OR upper(p.part_number)=upper(:part)) ORDER BY p.manufacturer,p.part_number LIMIT 3",args);
        List<Candidate> result=new ArrayList<>();
        for(var row:rows) {
            UUID id=(UUID)row.get("id");
            var ratings=jdbc.queryForList("SELECT voltage,current_type,utilization_category,rated_current,motor_power,icu,ics,icn,standard_number,conditions FROM erro_knowledge.product_rating WHERE product_id=?",id);
            var standards=jdbc.queryForList("SELECT standard_number,edition,certification FROM erro_knowledge.product_standard WHERE product_id=?",id);
            var compatibility=jdbc.queryForList("SELECT c.related_product_id,c.compatibility_type,c.conditions,c.source_document_id,c.notes FROM erro_knowledge.product_compatibility c JOIN erro_knowledge.knowledge_document d ON d.id=c.source_document_id JOIN "+TABLE+" p ON p.id=c.related_product_id WHERE c.product_id=? AND c.verified AND d.enabled AND d.status='READY' AND d.copyright_status NOT IN ('METADATA_ONLY','RESTRICTED') AND p.enabled AND (d.effective_date IS NULL OR d.effective_date<=CURRENT_DATE) LIMIT 3",id);
            result.add(new Candidate(id,row,ratings,standards,compatibility,evaluate(question,row,ratings,parameters)));
        }
        return result;
    }
    public List<Check> evaluate(String question,Map<String,Object> product,List<Map<String,Object>> ratings,ElectricalParameterExtractor.Parameters parameters) {
        List<Check> checks=new ArrayList<>(); var v=parameters.values(); String type=parameters.currentType();
        BigDecimal voltage=v.containsKey("voltage")?v.get("voltage").value():null;
        BigDecimal current=v.containsKey("current")?v.get("current").value():null;
        checks.add(validators.maximum("voltage",voltage,type.equals("AC")?decimal(product,"rated_voltage_ac"):type.equals("DC")?decimal(product,"rated_voltage_dc"):null,"Operating voltage and documented "+type+" maximum voltage."));
        // Select a rating at the exact operating voltage/type; do not extrapolate interrupting capacity.
        var applicable=ratings.stream().filter(r->voltage!=null && decimal(r,"voltage").compareTo(voltage)==0 && type.equals(r.get("current_type"))
                && (!parameters.attributes().containsKey("utilizationCategory") || parameters.attributes().get("utilizationCategory").equalsIgnoreCase(String.valueOf(r.get("utilization_category"))))).toList();
        if(applicable.size()==1) {
            var r=applicable.getFirst();
            checks.add(validators.maximum("operating-current",current,decimal(r,"rated_current"),"Application-specific current rating; utilization category and derating still require confirmation."));
            if(v.containsKey("shortCircuitCurrent")) {
                // The applicable standard/rating must be identified; Icu/Ics/Icn are not interchangeable.
                String lower=question.toLowerCase(Locale.ROOT); String capacity=lower.contains("icn")?"icn":lower.contains("icu")?"icu":lower.contains("ics")?"ics":null;
                checks.add(validators.maximum("product-breaking-capacity",v.get("shortCircuitCurrent").value(),capacity==null?null:decimal(r,capacity),"Capacity at the specified voltage and AC/DC type; identify Icn, Icu or Ics and confirm listed conditions."));
            }
        } else checks.add(new Check("application-rating",Status.UNKNOWN,"No unique documented voltage/AC-DC rating matches this application."));
        String productType=(String)product.get("product_type");
        if(v.containsKey("requiredPoles")) checks.add(validators.match("pole-count",v.get("requiredPoles").value().stripTrailingZeros().toPlainString(),product.get("number_of_poles")==null?null:product.get("number_of_poles").toString()));
        if(v.containsKey("coilVoltage")) {
            var coil=decimal(product,"coil_voltage");
            checks.add(validators.match("coil-voltage",v.get("coilVoltage").value().stripTrailingZeros().toPlainString(),coil==null?null:coil.stripTrailingZeros().toPlainString()));
            checks.add(validators.match("coil-current-type",parameters.attributes().get("coilVoltageType"),(String)product.get("coil_voltage_type")));
        }
        if(parameters.attributes().containsKey("utilizationCategory")) checks.add(validators.match("utilization-category",parameters.attributes().get("utilizationCategory"),applicable.size()==1?(String)applicable.getFirst().get("utilization_category"):null));
        if(productType.equals("POWER_SUPPLY")) {
            if(v.containsKey("inputVoltage")) {
                checks.add(validators.maximum("input-maximum",v.get("inputVoltage").value(),decimal(product,"input_voltage_max"),"Input upper voltage limit; also verify input AC/DC type and frequency."));
                checks.add(validators.maximum("input-minimum",decimal(product,"input_voltage_min"),v.get("inputVoltage").value(),"Input must be at least the documented minimum."));
            }
            if(v.containsKey("outputVoltage")) {
                var output=decimal(product,"output_voltage");
                checks.add(validators.match("output-voltage",v.get("outputVoltage").value().stripTrailingZeros().toPlainString(),output==null?null:output.stripTrailingZeros().toPlainString()));
            }
            if(v.containsKey("outputCurrent")) checks.add(validators.maximum("output-current-before-derating",v.get("outputCurrent").value(),decimal(product,"output_current"),"Nameplate output current only; confirm temperature and all applicable derating before selection."));
        }
        if(productType.equals("CONTACTOR")||productType.equals("RELAY")) checks.add(new Check("coil-and-utilization",Status.UNKNOWN,"Confirm separate coil supply, utilization category, operating duty and manufacturer coordination; main-circuit voltage is not coil voltage."));
        if(productType.equals("CIRCUIT_BREAKER")) checks.add(new Check("poles-and-protection",Status.UNKNOWN,"Confirm pole configuration, disconnection requirements, cable protection, trip curve, starting behavior and coordination."));
        if(productType.equals("POWER_SUPPLY")) checks.add(new Check("power-supply",Status.UNKNOWN,"Identify input supply separately from required output voltage/current; verify documented input range, output rating and temperature derating."));
        if(productType.equals("ENCLOSURE")) {
            var m=java.util.regex.Pattern.compile("(?i)IP[0-6X][0-9X]").matcher(question);
            checks.add(validators.ip(m.find()?m.group():null,(String)product.get("ip_rating")));
        }
        checks.add(new Check("complete-suitability",Status.WARNING,"Candidate data is not a purchase approval. Unchecked specifications, derating, installation conditions and manufacturer compatibility must be resolved."));
        return checks;
    }
    private BigDecimal decimal(Map<String,Object> map,String key) { return map.get(key) instanceof BigDecimal n?n:null; }
}
