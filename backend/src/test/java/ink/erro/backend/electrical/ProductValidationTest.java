package ink.erro.backend.electrical;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import ink.erro.backend.product.ProductService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static ink.erro.backend.electrical.ElectricalValidationService.Status.*;

class ProductValidationTest {
    final ProductService products=new ProductService(mock(JdbcTemplate.class),new ElectricalValidationService());
    final ElectricalParameterExtractor extractor=new ElectricalParameterExtractor();
    @Test void voltageSpecificBreakingRatingCannotBeExtrapolated() {
        var product=new HashMap<String,Object>(); product.put("product_type","CIRCUIT_BREAKER"); product.put("rated_voltage_ac",new BigDecimal("400"));
        Map<String,Object> rating=Map.of("voltage",new BigDecimal("230"),"current_type","AC","icn",new BigDecimal("10000"));
        String q="400 V AC breaker, prospective fault current 9 kA, Icn";
        var checks=products.evaluate(q,product,List.of(rating),extractor.extract(q));
        assertThat(checks).anyMatch(c->c.rule().equals("application-rating")&&c.status()==UNKNOWN);
        assertThat(checks).noneMatch(c->c.rule().equals("product-breaking-capacity")&&c.status()==PASS);
    }
    @Test void productFaultCapacityFailsAtMatchedVoltage() {
        Map<String,Object> product=Map.of("product_type","CIRCUIT_BREAKER","rated_voltage_ac",new BigDecimal("400"));
        Map<String,Object> rating=Map.of("voltage",new BigDecimal("400"),"current_type","AC","icn",new BigDecimal("6000"));
        String q="400 V AC breaker, prospective fault current 9 kA, Icn";
        assertThat(products.evaluate(q,product,List.of(rating),extractor.extract(q)))
                .anyMatch(c->c.rule().equals("product-breaking-capacity")&&c.status()==FAIL);
    }
    @Test void powerSupplyOutputMismatchAndCurrentOverloadFail() {
        Map<String,Object> product=Map.of("product_type","POWER_SUPPLY","input_voltage_min",new BigDecimal("100"),"input_voltage_max",new BigDecimal("240"),"output_voltage",new BigDecimal("12"),"output_current",new BigDecimal("2"));
        String q="Power supply input 230 V AC, output 24 V DC, load current 3 A";
        var checks=products.evaluate(q,product,List.of(),extractor.extract(q));
        assertThat(checks).anyMatch(c->c.rule().equals("output-voltage")&&c.status()==FAIL)
                .anyMatch(c->c.rule().equals("output-current-before-derating")&&c.status()==FAIL);
    }
    @Test void coilMismatchIsComparedSeparately() {
        Map<String,Object> product=Map.of("product_type","CONTACTOR","coil_voltage",new BigDecimal("230"),"coil_voltage_type","AC");
        String q="Contactor coil 24 V DC";
        assertThat(products.evaluate(q,product,List.of(),extractor.extract(q)))
                .anyMatch(c->c.rule().equals("coil-voltage")&&c.status()==FAIL)
                .anyMatch(c->c.rule().equals("coil-current-type")&&c.status()==FAIL);
    }
}
