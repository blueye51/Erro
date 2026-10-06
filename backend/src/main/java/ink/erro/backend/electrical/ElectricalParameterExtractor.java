package ink.erro.backend.electrical;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import ink.erro.backend.knowledge.KnowledgeModels.Jurisdiction;

@Service
public class ElectricalParameterExtractor {
    public record Quantity(BigDecimal value, String unit, String suppliedAs) {}
    public record Parameters(Map<String,Quantity> values, List<String> standards, List<String> ambiguities,
                             Jurisdiction jurisdiction, String manufacturer, String currentType, Map<String,String> attributes) {}
    private static final String NUMBER="(?<![\\p{L}\\p{N}.,])([+-]?\\d+(?:[.,]\\d+)?)";
    public Parameters extract(String query) {
        var values=new LinkedHashMap<String,Quantity>(); var ambiguities=new ArrayList<String>();
        capture(query, values, ambiguities, "voltage", NUMBER+"\\s*(kV|V)(?:\\s*(AC|DC))?\\b", "V", false);
        capture(query, values, ambiguities, "current", NUMBER+"\\s*(mA|kA|A)\\b", "A", true);
        capture(query, values, ambiguities, "power", NUMBER+"\\s*(kW|W)\\b", "W", false);
        capture(query, values, ambiguities, "frequency", NUMBER+"\\s*(Hz)\\b", "Hz", false);
        capture(query, values, ambiguities, "conductorSize", NUMBER+"\\s*(mm2|mm²)\\b", "mm²", false);
        capture(query, values, ambiguities, "length", NUMBER+"\\s*(km|m|meters|metres)\\b", "m", false);
        capture(query, values, ambiguities, "resistance", NUMBER+"\\s*(ohms?|Ω)\\b", "Ω", false);
        labelled(query, values, ambiguities, "inputVoltage", "(?:input|supply)\\s*(?:voltage)?\\s*(?:is|of|=)?\\s*"+NUMBER+"\\s*V(?:AC|DC)?\\b", "V");
        labelled(query, values, ambiguities, "outputVoltage", "(?:required\\s+)?output\\s*(?:voltage)?\\s*(?:is|of|=)?\\s*"+NUMBER+"\\s*V(?:AC|DC)?\\b", "V");
        labelled(query, values, ambiguities, "outputCurrent", "(?:output|load)\\s*(?:current)?\\s*(?:is|of|=)?\\s*"+NUMBER+"\\s*A\\b", "A");
        labelled(query, values, ambiguities, "coilVoltage", "coil\\s*(?:voltage)?\\s*(?:is|of|=)?\\s*"+NUMBER+"\\s*V", "V");
        labelled(query, values, ambiguities, "requiredPoles", NUMBER+"[ -]*poles?\\b", "poles");
        labelled(query, values, ambiguities, "ambientTemperature", "(?:ambient|temperature)\\s*(?:temperature)?\\s*(?:is|of|=)?\\s*(-?\\d+(?:[.,]\\d+)?)\\s*°?C\\b", "°C");
        ratio(query,values,ambiguities,"powerFactor","(?:\\bPF|power factor|cos\\s*phi)\\s*(?:=|is|of)?\\s*"+NUMBER+"\\s*(%)?");
        ratio(query,values,ambiguities,"efficiency","(?:efficiency|eta|η)\\s*(?:=|is|of)?\\s*"+NUMBER+"\\s*(%)?");
        boolean threePhase=Pattern.compile("\\b(?:3|three)[ -]?phase|kolmefaas",Pattern.CASE_INSENSITIVE).matcher(query).find();
        boolean singlePhase=Pattern.compile("\\b(?:1|single)[ -]?phase|ühefaas",Pattern.CASE_INSENSITIVE).matcher(query).find();
        if(threePhase && singlePhase) ambiguities.add("Both single-phase and three-phase are present; identify supply and load roles.");
        else if(threePhase) values.put("phaseCount",new Quantity(BigDecimal.valueOf(3),"phases","three-phase"));
        else if(singlePhase) values.put("phaseCount",new Quantity(BigDecimal.ONE,"phases","single-phase"));
        var standardMatcher=Pattern.compile("\\b(IEC|EN|HD|EVS(?:-EN)?)(?:\\s+IEC)?[ -]*(\\d{4,5}(?:-\\d{1,2}){0,3})",Pattern.CASE_INSENSITIVE).matcher(query);
        List<String> standards=new ArrayList<>();
        while(standardMatcher.find()) standards.add(standardMatcher.group(1).toUpperCase(Locale.ROOT)+" "+standardMatcher.group(2));
        String lower=query.toLowerCase(Locale.ROOT);
        Jurisdiction scope=lower.matches("(?s).*\\b(estonia|estonian|eesti)\\b.*") ? Jurisdiction.ESTONIA
                : lower.matches("(?s).*\\b(eu|european union)\\b.*") ? Jurisdiction.EU
                : lower.matches("(?s).*\\b(usa|united states|nec|nfpa)\\b.*") ? Jurisdiction.US : Jurisdiction.ESTONIA;
        String manufacturer=List.of("Schneider","ABB","Siemens","Eaton","Hager","Phoenix Contact","Omron").stream()
                .filter(m->lower.contains(m.toLowerCase(Locale.ROOT))).findFirst().orElse("");
        boolean ac=Pattern.compile("\\bac\\b|vac\\b",Pattern.CASE_INSENSITIVE).matcher(query).find();
        boolean dc=Pattern.compile("\\bdc\\b|vdc\\b",Pattern.CASE_INSENSITIVE).matcher(query).find();
        String type=ac&&dc ? "MIXED" : dc ? "DC" : ac ? "AC" : "UNKNOWN";
        if(type.equals("MIXED")) ambiguities.add("Both AC and DC appear: distinguish supply, switching/contact and coil/control ratings.");
        Map<String,String> attributes = new LinkedHashMap<>();
        attribute(query,attributes,"utilizationCategory","\\b((?:AC|DC)-\\d+[a-z]?)\\b");
        attribute(query,attributes,"ipRating","\\b(IP[0-6X][0-9X])\\b");
        attribute(query,attributes,"breakerCurve","(?:curve|characteristic)\\s*([BCD])\\b");
        attribute(query,attributes,"conductorMaterial","\\b(copper|aluminium|aluminum)\\b");
        attribute(query,attributes,"coilVoltageType","coil\\s*(?:voltage)?\\s*(?:is|of|=)?\\s*\\d+(?:[.,]\\d+)?\\s*V\\s*(AC|DC)\\b");
        attribute(query,attributes,"partNumber","(?:part(?: number)?|model|sku)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9._-]{2,99})\\b");
        return new Parameters(Collections.unmodifiableMap(values),standards.stream().distinct().toList(),List.copyOf(ambiguities),scope,manufacturer,type,Collections.unmodifiableMap(attributes));
    }
    private void capture(String text, Map<String,Quantity> values, List<String> ambiguities, String key, String expression,String unit,boolean amps) {
        var matcher=Pattern.compile(expression,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);
        List<Quantity> found=new ArrayList<>();
        while(matcher.find()) {
            String prefix=matcher.group(2).toLowerCase(Locale.ROOT);
            var value=new BigDecimal(matcher.group(1).replace(',','.'));
            if(prefix.startsWith("k")) value=value.multiply(BigDecimal.valueOf(1000));
            if(amps && prefix.equals("ma")) value=value.movePointLeft(3);
            var quantity=new Quantity(value,unit,matcher.group());
            if(amps && prefix.equals("ka")) {
                String nearby=text.substring(Math.max(0,matcher.start()-65),Math.min(text.length(),matcher.end()+35)).toLowerCase(Locale.ROOT);
                // Assign by nearest preceding label, not by treating all amperes as load current.
                String before=text.substring(Math.max(0,matcher.start()-65),matcher.start()).toLowerCase(Locale.ROOT);
                if(lastLabel(before,"fault|prospective|short.circuit") > lastLabel(before,"mcb|breaker|breaking|capacity|icn|icu|ics")) putUnique(values,ambiguities,"shortCircuitCurrent",quantity);
                else if(nearby.contains("breaking")||nearby.contains("mcb")||nearby.contains("capacity")) putUnique(values,ambiguities,"breakingCapacity",quantity);
                continue;
            }
            if(amps && prefix.equals("ma")) {
                String nearby=text.substring(Math.max(0,matcher.start()-40),Math.min(text.length(),matcher.end()+40)).toLowerCase(Locale.ROOT);
                if(nearby.matches("(?s).*(residual|rcd|rccb|rcbo|leakage|rikkevool).*")) {
                    putUnique(values,ambiguities,"residualCurrent",quantity); continue;
                }
            }
            found.add(quantity);
        }
        if(!found.isEmpty()) {
            var distinct=found.stream().map(q->q.value().stripTrailingZeros()).distinct().count();
            if(distinct==1) values.put(key,found.getFirst());
            else ambiguities.add("Multiple "+key+" values were supplied; identify which belongs to the load and which to the device.");
        }
    }
    private void putUnique(Map<String,Quantity> values,List<String> ambiguities,String key,Quantity value) {
        if(ambiguities.stream().anyMatch(a->a.startsWith("Conflicting "+key+" values"))) return;
        if(values.containsKey(key) && values.get(key).value().compareTo(value.value())!=0) {
            values.remove(key); ambiguities.add("Conflicting "+key+" values; clarify their roles.");
        } else values.put(key,value);
    }
    private void attribute(String query,Map<String,String> attributes,String key,String pattern) {
        var m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(query);
        if(m.find()) attributes.put(key,m.group(1).toUpperCase(Locale.ROOT));
    }
    private void labelled(String query,Map<String,Quantity> values,List<String> ambiguities,String key,String pattern,String unit) {
        var m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(query);
        while(m.find()) putUnique(values,ambiguities,key,new Quantity(new BigDecimal(m.group(1).replace(',','.')),unit,m.group()));
    }
    private int lastLabel(String text,String regex) {
        var matcher=Pattern.compile(regex).matcher(text); int index=-1;
        while(matcher.find()) index=matcher.start(); return index;
    }
    private void ratio(String text,Map<String,Quantity> values,List<String> ambiguities,String key,String regex) {
        var m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(text);
        while(m.find()) {
            var value=new BigDecimal(m.group(1).replace(',','.'));
            if(m.group(2)!=null) value=value.movePointLeft(2);
            putUnique(values,ambiguities,key,new Quantity(value,"ratio",m.group()));
        }
    }
}
