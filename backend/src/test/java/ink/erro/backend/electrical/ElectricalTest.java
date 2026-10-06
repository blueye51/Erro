package ink.erro.backend.electrical;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static ink.erro.backend.electrical.ElectricalValidationService.Status.*;

class ElectricalTest {
    final ElectricalParameterExtractor extractor=new ElectricalParameterExtractor();
    final ElectricalIntentService intents=new ElectricalIntentService();
    final ElectricalCalculationService calc=new ElectricalCalculationService();
    final ElectricalValidationService validation=new ElectricalValidationService();
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    @ParameterizedTest @ValueSource(strings={"400v","400 V","400VAC","0.4 kV"})
    void normalizesVoltageWithoutChangingUnits(String text) { assertThat(extractor.extract(text).values().get("voltage").value()).isEqualByComparingTo("400"); }
    @Test void extractsMotorValuesAndRatios() {
        var p=extractor.extract("7,5 kW 400 VAC 3-phase motor, PF 0.82, efficiency 90%, 30 meters, IEC 60947-4-1");
        assertThat(p.values().get("power").value()).isEqualByComparingTo("7500");
        assertThat(p.values().get("efficiency").value()).isEqualByComparingTo("0.90");
        assertThat(p.values().get("length").value()).isEqualByComparingTo("30");
        assertThat(p.standards()).containsExactly("IEC 60947-4-1");
        assertThat(p.currentType()).isEqualTo("AC");
    }
    @Test void ambiguousVoltagesAreNotSilentlyChosen() {
        var p=extractor.extract("230 V AC contactor to switch 24 V DC");
        assertThat(p.values()).doesNotContainKey("voltage"); assertThat(p.ambiguities()).isNotEmpty();
    }
    @Test void testsEveryCalculation() {
        assertThat(calc.ohmsVoltage(n("2"),n("10")).value()).isEqualByComparingTo("20");
        assertThat(calc.dcPower(n("24"),n("2")).value()).isEqualByComparingTo("48");
        assertThat(calc.singlePhasePower(n("230"),n("10"),n("0.8")).value()).isEqualByComparingTo("1840");
        assertThat(calc.threePhasePower(n("400"),n("10"),n("0.8")).value().doubleValue()).isCloseTo(5542.5626,within(.0001));
        assertThat(calc.motorInputPower(n("7500"),n("0.9")).value().doubleValue()).isCloseTo(8333.3333,within(.0001));
        var motor=calc.motorCurrent(n("7500"),n("400"),n("0.82"),n("0.9"));
        assertThat(motor.value().doubleValue()).isCloseTo(14.66845196,within(.00001));
        assertThat(motor.inputs()).containsKey("efficiency"); assertThat(motor.assumptions()).isNotEmpty();
        assertThatThrownBy(()->calc.motorCurrent(n("7500"),n("400"),n("0"),n("0.9"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->calc.singlePhasePower(n("230"),n("10"),n("1.2"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingEfficiencyDoesNotInventMotorCurrent() {
        var q="400 V 3-phase 7.5 kW motor protection"; var p=extractor.extract(q);
        assertThat(calc.applicable(p,intents.detect(q))).isEmpty();
        assertThat(validation.assess(q,p,intents.detect(q)).missingInformation()).anyMatch(x->x.contains("efficiency"));
    }
    @Test void doesNotSelectBreakerByNominalCurrent() {
        String q="My load is 14 A. Should I use a 16 A breaker?";
        var a=validation.assess(q,extractor.extract(q),intents.detect(q));
        assertThat(a.checks()).anyMatch(c->c.rule().equals("protection-system")&&c.status()==UNKNOWN);
        assertThat(a.missingInformation()).anyMatch(x->x.contains("Conductor"));
    }
    @Test void faultAboveCapacityFails() {
        String q="The prospective fault current is 9 kA and this MCB has 6 kA breaking capacity. Is that okay?";
        var p=extractor.extract(q);
        assertThat(p.values().get("shortCircuitCurrent").value()).isEqualByComparingTo("9000");
        assertThat(p.values().get("breakingCapacity").value()).isEqualByComparingTo("6000");
        assertThat(validation.assess(q,p,intents.detect(q)).failed()).isTrue();
    }
    @Test void contactorAcRatingDoesNotProveDcSwitching() {
        String q="Can this 230 V AC contactor switch 24 V DC?";
        assertThat(validation.assess(q,extractor.extract(q),intents.detect(q)).checks()).anyMatch(c->c.rule().equals("switching-current-type")&&c.status()==UNKNOWN);
    }
    @Test void incompatibleCoilSupplyFails() {
        String q="Can a 230 VAC relay coil run on 24 VDC?";
        assertThat(validation.assess(q,extractor.extract(q),intents.detect(q)).failed()).isTrue();
    }
    @Test void outdoorEnclosureNeedsEnvironmentalData() {
        String q="I need an outdoor enclosure";
        assertThat(validation.assess(q,extractor.extract(q),intents.detect(q)).missingInformation()).anyMatch(x->x.contains("UV"));
        assertThat(validation.ip("IP65","IP68").status()).isEqualTo(UNKNOWN);
        assertThat(validation.ip("IP65","IP44").status()).isEqualTo(FAIL);
        assertThat(validation.ip("IP65","IP66").status()).isEqualTo(WARNING);
        assertThat(validation.maximum("capacity",n("9000"),n("6000"),"").status()).isEqualTo(FAIL);
        assertThat(validation.maximum("capacity",n("6000"),n("6000"),"").status()).isEqualTo(PASS);
        assertThat(validation.maximum("capacity",null,n("6000"),"").status()).isEqualTo(UNKNOWN);
        assertThat(validation.maximum("voltage",n("-600"),n("300"),"").status()).isEqualTo(UNKNOWN);
    }
    @Test void signedValuesAndConflictingSupplyTypesAreNotSilentlyChanged() {
        assertThat(extractor.extract("-24 V DC").values().get("voltage").value()).isEqualByComparingTo("-24");
        assertThat(extractor.extract("part ABC230V").values()).doesNotContainKey("voltage");
        String q="400 V DC 3-phase 7.5 kW motor PF 0.8 efficiency 90%";
        assertThatThrownBy(()->calc.applicable(extractor.extract(q),intents.detect(q))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rolesAndJurisdictionAreNotInferredFromUnitsOrForeignReferences() {
        assertThat(extractor.extract("24 V DC 20 mA sensor load").values().get("current").value()).isEqualByComparingTo("0.020");
        assertThat(extractor.extract("30 mA RCD").values().get("residualCurrent").value()).isEqualByComparingTo("0.030");
        assertThat(extractor.extract("single-phase supply for a three-phase motor").values()).doesNotContainKey("phaseCount");
        assertThat(extractor.extract("Is NEC applicable in Estonia?").jurisdiction()).isEqualTo(ink.erro.backend.knowledge.KnowledgeModels.Jurisdiction.ESTONIA);
    }
    @Test void contextCombinesFragmentsAndResetsForNewEquipment() {
        var problems=new ProblemContextService(intents);
        assertThat(problems.resolve("400 V",List.of("7.5 kW three-phase motor")).question()).contains("motor","400 V");
        assertThat(problems.resolve("I need an outdoor enclosure",List.of("7.5 kW motor 400 V")).reset()).isTrue();
        assertThat(problems.resolve("New problem: another motor",List.of("400 V")).question()).doesNotContain("400 V");
    }
}
