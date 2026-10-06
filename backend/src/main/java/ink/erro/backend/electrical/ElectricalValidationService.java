package ink.erro.backend.electrical;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import static ink.erro.backend.electrical.ElectricalIntentService.Category.*;

@Service
public class ElectricalValidationService {
    public enum Status { PASS,FAIL,UNKNOWN,WARNING }
    public record Check(String rule,Status status,String explanation) {}
    public record Assessment(List<Check> checks,List<String> missingInformation,List<String> warnings) {
        public boolean failed() { return checks.stream().anyMatch(c->c.status()==Status.FAIL); }
    }
    public Assessment assess(String question,ElectricalParameterExtractor.Parameters p,Set<ElectricalIntentService.Category> categories) {
        var checks=new ArrayList<Check>(); var missing=new LinkedHashSet<String>(); var warnings=new ArrayList<>(p.ambiguities());
        if(categories.contains(CIRCUIT_BREAKER)||categories.contains(MOTOR_PROTECTION)||categories.contains(FUSE)) {
            missing.addAll(List.of("Motor/load nameplate current and duty","Startup/inrush characteristics and starting method",
                    "Conductor size, material, installation method, loaded conductors and ambient temperature",
                    "Prospective short-circuit current at the installation point",
                    "Protective-device voltage, AC/DC rating, poles, trip characteristics and applicable breaking-capacity rating",
                    "Overload and short-circuit protection arrangement; manufacturer coordination tables"));
            if(p.values().containsKey("shortCircuitCurrent")) missing.remove("Prospective short-circuit current at the installation point");
            checks.add(new Check("protection-system",Status.UNKNOWN,"Nominal load current alone cannot select a breaker. Overload, short-circuit, residual-current and conductor protection have distinct purposes."));
        }
        if(categories.contains(MOTOR)) {
            if(!p.values().containsKey("powerFactor")) missing.add("Motor power factor at the operating point (needed for a current estimate)");
            if(!p.values().containsKey("efficiency")) missing.add("Motor efficiency and confirmation that kW is shaft output (needed for a current estimate)");
            if(!p.values().containsKey("phaseCount")) missing.add("Phase count");
            if(!p.values().containsKey("voltage")) missing.add("Supply voltage and whether it is line-to-line");
        }
        var fault=p.values().get("shortCircuitCurrent"); var capacity=p.values().get("breakingCapacity");
        if(fault!=null && capacity!=null) checks.add(maximum("breaking-capacity",fault.value(),capacity.value(),
                "Stated prospective fault current versus stated device breaking capacity, assuming the same operating voltage, AC/DC conditions and applicable rating standard. No tested backup combination has been supplied."));
        String lower=question.toLowerCase(Locale.ROOT);
        if(lower.contains("coil") && p.currentType().equals("MIXED") && !lower.contains("universal") && !lower.contains("ac/dc"))
            checks.add(new Check("coil-supply",Status.FAIL,"The stated AC coil rating does not permit operation from the stated DC supply. Use the manufacturer's exact coil voltage and AC/DC specification; an explicitly rated universal coil needs its documented input range."));
        if(categories.contains(CONTACTOR) && p.currentType().equals("MIXED")) {
            checks.add(new Check("switching-current-type",Status.UNKNOWN,"An AC switching rating does not establish a DC rating. Retrieve the manufacturer's DC utilization category, voltage, current and pole-connection requirements."));
            missing.add("Manufacturer part number and application-specific AC/DC contact and coil ratings");
        }
        if(categories.contains(ENCLOSURE)) {
            missing.addAll(List.of("Required IP protection against dust and water in the actual assembled installation",
                    "UV, corrosion, impact, temperature, condensation and cable-entry conditions"));
            checks.add(new Check("enclosure-environment",Status.UNKNOWN,"Dimensions or a bare enclosure IP label alone do not establish outdoor suitability."));
        }
        if(categories.contains(VOLTAGE_DROP)) missing.add("Validated conductor resistance/reactance at operating temperature, length, current, phase arrangement and calculation model");
        if(categories.contains(SHORT_CIRCUIT) && fault==null) missing.add("Source impedance/fault level, transformer and conductor impedances for a short-circuit calculation");
        if(!categories.isEmpty()) warnings.add("Final installation/design decisions require competent electrical assessment and applicable local requirements. These checks do not establish complete suitability or compliance.");
        return new Assessment(List.copyOf(checks),List.copyOf(missing),List.copyOf(warnings));
    }
    public Check maximum(String rule,BigDecimal required,BigDecimal rating,String description) {
        if(required==null||rating==null) return new Check(rule,Status.UNKNOWN,description+" Missing requirement or documented rating.");
        if(required.signum()<=0 || rating.signum()<=0)
            return new Check(rule,Status.UNKNOWN,description+" A positive magnitude and positive documented rating are required; signed or zero values need clarification.");
        return new Check(rule,required.compareTo(rating)>0 ? Status.FAIL : Status.PASS,description+" Required "+required.toPlainString()+"; rated "+rating.toPlainString()+". PASS covers only this comparison.");
    }
    public Check match(String rule,String required,String rating) {
        if(required==null||rating==null||required.isBlank()||rating.isBlank()||required.equals("UNKNOWN")||required.equals("MIXED")) return new Check(rule,Status.UNKNOWN,"Missing or ambiguous requirement or documented rating.");
        return new Check(rule,required.equalsIgnoreCase(rating)?Status.PASS:Status.FAIL,"Required "+required+"; documented "+rating+".");
    }
    public Check ip(String required,String actual) {
        if(required==null||actual==null||!required.matches("(?i)IP[0-6][0-8]")||!actual.matches("(?i)IP[0-6][0-8]"))
            return new Check("ip-rating",Status.UNKNOWN,"Compare both IP digits using documented test conditions; X and water-jet/immersion ratings need explicit interpretation.");
        int rd=required.charAt(2)-'0',rw=required.charAt(3)-'0',ad=actual.charAt(2)-'0',aw=actual.charAt(3)-'0';
        if(ad<rd || (rw<=6 && aw<=6 && aw<rw)) return new Check("ip-rating",Status.FAIL,"Documented enclosure protection is below a required IP digit.");
        if(aw>=7 && rw>=5 && rw<=6 || rw>=7 && aw!=rw) return new Check("ip-rating",Status.UNKNOWN,"Immersion and water-jet ratings are not interchangeable; obtain the specific test rating.");
        return new Check("ip-rating",Status.WARNING,"Basic IP digits meet the stated requirement; confirm assembled cable entries, accessories and environmental conditions.");
    }
}
