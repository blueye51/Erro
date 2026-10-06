package ink.erro.backend.electrical;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static ink.erro.backend.electrical.ElectricalParameterExtractor.*;

@Service
public class ElectricalCalculationService {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal SQRT_THREE = BigDecimal.valueOf(3).sqrt(PRECISION);
    public record Calculation(String name, BigDecimal value, String unit, String formula,
                              Map<String,BigDecimal> inputs, List<String> assumptions) {}
    public Calculation ohmsVoltage(BigDecimal current, BigDecimal resistance) {
        positive(current,resistance);
        return result("Ohm's law",current.multiply(resistance,PRECISION),"V","V = I × R",Map.of("I_A",current,"R_ohm",resistance),List.of("Ohmic resistance at the stated operating conditions."));
    }
    public Calculation dcPower(BigDecimal voltage, BigDecimal current) {
        positive(voltage,current);
        return result("DC power",voltage.multiply(current,PRECISION),"W","P = V × I",Map.of("V_V",voltage,"I_A",current),List.of("Steady DC."));
    }
    public Calculation singlePhasePower(BigDecimal voltage, BigDecimal current, BigDecimal pf) {
        positive(voltage,current); ratio(pf);
        return result("Single-phase real power",voltage.multiply(current,PRECISION).multiply(pf,PRECISION),"W","P = V × I × PF",
                Map.of("V_V",voltage,"I_A",current,"PF",pf),List.of("RMS voltage/current and applicable true power factor."));
    }
    public Calculation threePhasePower(BigDecimal voltage, BigDecimal current, BigDecimal pf) {
        positive(voltage,current); ratio(pf);
        return result("Three-phase real power",SQRT_THREE.multiply(voltage,PRECISION).multiply(current,PRECISION).multiply(pf,PRECISION),"W","P = √3 × VLL × I × PF",
                Map.of("VLL_V",voltage,"I_A",current,"PF",pf),List.of("Balanced three-phase load; line-to-line RMS voltage and line current."));
    }
    public Calculation motorInputPower(BigDecimal output,BigDecimal efficiency) {
        positive(output); ratio(efficiency);
        return result("Motor electrical input power",output.divide(efficiency,PRECISION),"W","Pinput = Poutput / η",
                Map.of("Poutput_W",output,"efficiency",efficiency),List.of("Supplied power is mechanical shaft output; efficiency applies at this operating point."));
    }
    public Calculation motorCurrent(BigDecimal output,BigDecimal voltage,BigDecimal pf,BigDecimal efficiency) {
        positive(output,voltage); ratio(pf); ratio(efficiency);
        return result("Estimated motor current",output.divide(SQRT_THREE.multiply(voltage,PRECISION).multiply(pf,PRECISION).multiply(efficiency,PRECISION),PRECISION),"A",
                "I ≈ Poutput / (√3 × VLL × PF × η)",Map.of("Poutput_W",output,"VLL_V",voltage,"PF",pf,"efficiency",efficiency),
                List.of("Balanced three-phase load; supplied motor power interpreted as shaft output and voltage as line-to-line.",
                        "Estimate at the supplied PF and efficiency, not starting current or a protective device rating. Confirm motor nameplate."));
    }
    public List<Calculation> applicable(Parameters p,Set<ElectricalIntentService.Category> categories) {
        var v=p.values(); var calculations=new ArrayList<Calculation>();
        if(p.currentType().equals("MIXED")) return List.of();
        if(p.currentType().equals("DC") && has(v,"phaseCount") && n(v,"phaseCount").intValue()==3)
            throw new IllegalArgumentException("Three-phase AC calculations cannot be applied to a stated DC system; clarify supply and load roles.");
        if (v.containsKey("powerFactor")) ratio(v.get("powerFactor").value());
        if (v.containsKey("efficiency")) ratio(v.get("efficiency").value());
        boolean motor=categories.contains(ElectricalIntentService.Category.MOTOR);
        if(motor && has(v,"power","efficiency")) calculations.add(motorInputPower(n(v,"power"),n(v,"efficiency")));
        if(motor && has(v,"power","voltage","powerFactor","efficiency","phaseCount") && n(v,"phaseCount").intValue()==3)
            calculations.add(motorCurrent(n(v,"power"),n(v,"voltage"),n(v,"powerFactor"),n(v,"efficiency")));
        if(!motor && has(v,"voltage","current")) {
            if(p.currentType().equals("DC")) calculations.add(dcPower(n(v,"voltage"),n(v,"current")));
            else if(has(v,"powerFactor","phaseCount")) {
                if(n(v,"phaseCount").intValue()==3) calculations.add(threePhasePower(n(v,"voltage"),n(v,"current"),n(v,"powerFactor")));
                if(n(v,"phaseCount").intValue()==1) calculations.add(singlePhasePower(n(v,"voltage"),n(v,"current"),n(v,"powerFactor")));
            }
        }
        if(has(v,"current","resistance") && categories.contains(ElectricalIntentService.Category.GENERAL_THEORY)) calculations.add(ohmsVoltage(n(v,"current"),n(v,"resistance")));
        return List.copyOf(calculations);
    }
    private static boolean has(Map<String,Quantity> values,String...keys) { return Arrays.stream(keys).allMatch(values::containsKey); }
    private static BigDecimal n(Map<String,Quantity> values,String key) { return values.get(key).value(); }
    private static Calculation result(String name,BigDecimal value,String unit,String formula,Map<String,BigDecimal> inputs,List<String> assumptions) {
        return new Calculation(name,value.round(new MathContext(8,RoundingMode.HALF_UP)),unit,formula,inputs,assumptions);
    }
    private static void positive(BigDecimal...values) { for(var v:values) if(v==null || v.signum()<=0) throw new IllegalArgumentException("Calculation inputs must be positive."); }
    private static void ratio(BigDecimal v) { positive(v); if(v.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("Power factor and efficiency must be greater than zero and at most 1 (or expressed as a percentage)."); }
}
