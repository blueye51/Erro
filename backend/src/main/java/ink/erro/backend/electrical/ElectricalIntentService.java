package ink.erro.backend.electrical;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ElectricalIntentService {
    public enum Category { GENERAL_THEORY,CABLE_SELECTION,CIRCUIT_BREAKER,FUSE,RCD,RCBO,CONTACTOR,MOTOR,MOTOR_PROTECTION,
        TRANSFORMER,POWER_SUPPLY,RELAY,PLC,INDUSTRIAL_CONTROL,SURGE_PROTECTION,EARTHING,SHORT_CIRCUIT,VOLTAGE_DROP,
        POWER_FACTOR,THREE_PHASE,SINGLE_PHASE,DC_SYSTEM,MACHINERY,LIGHTING,ENCLOSURE,IP_RATING,PRODUCT_SELECTION,
        PRODUCT_COMPATIBILITY,REGULATORY,CE_CONFORMITY }
    private static final Map<Category,String> PATTERNS = Map.ofEntries(
        Map.entry(Category.GENERAL_THEORY,"ohm|kirchhoff|resistan|capacit|induct|theory"),
        Map.entry(Category.CABLE_SELECTION,"cable|conductor|kaabel|juhe|mm²|mm2"),
        Map.entry(Category.CIRCUIT_BREAKER,"breaker|mcb|mpcb|kaitselüliti|60947-2|60898"),
        Map.entry(Category.FUSE,"fuse|sulavkaitse"), Map.entry(Category.RCD,"\\brcd\\b|rccb|residual|earth leakage|rikkevool|61008"),
        Map.entry(Category.RCBO,"rcbo|61009"), Map.entry(Category.CONTACTOR,"contactor|kontaktor|60947-4-1"),
        Map.entry(Category.MOTOR,"motor|mootor"), Map.entry(Category.TRANSFORMER,"transformer|trafo"),
        Map.entry(Category.POWER_SUPPLY,"power supply|toiteplokk"), Map.entry(Category.RELAY,"relay|relee|coil"),
        Map.entry(Category.PLC,"\\bplc\\b|i/o module"), Map.entry(Category.INDUSTRIAL_CONTROL,"industrial control|automation"),
        Map.entry(Category.SURGE_PROTECTION,"\\bspd\\b|surge|61643"), Map.entry(Category.EARTHING,"earth|ground|\\bpe\\b|tn-s|tn-c|tn-c-s|\\btt\\b"),
        Map.entry(Category.SHORT_CIRCUIT,"short.circuit|fault current|breaking capacity|interrupting capacity|\\bicu\\b|\\bics\\b|\\bicn\\b"),
        Map.entry(Category.VOLTAGE_DROP,"voltage drop|pingelang"), Map.entry(Category.POWER_FACTOR,"power factor|cos.?phi|\\bpf\\b"),
        Map.entry(Category.THREE_PHASE,"three.phase|3.phase|kolmefaas"), Map.entry(Category.SINGLE_PHASE,"single.phase|1.phase|ühefaas"),
        Map.entry(Category.DC_SYSTEM,"\\bdc\\b|vdc|direct current"), Map.entry(Category.MACHINERY,"machine|machinery|60204"),
        Map.entry(Category.LIGHTING,"lighting|led|valgust"), Map.entry(Category.ENCLOSURE,"enclosure|outdoor|korpus|kilp"),
        Map.entry(Category.IP_RATING,"ip[0-6x][0-9x]|ingress|60529"), Map.entry(Category.PRODUCT_SELECTION,"select|recommend|buy|purchase|need|choose|which|find|should i use"),
        Map.entry(Category.PRODUCT_COMPATIBILITY,"compatib|can this|can a |fit|switch|work with|run on"),
        Map.entry(Category.REGULATORY,"standard|regulat|legislat|directive|\\biec\\b|\\bevs\\b|\\bnec\\b|\\bnfpa\\b|60364"),
        Map.entry(Category.CE_CONFORMITY,"\\bce\\b|conform|rohs|emc|2014/35|2014/30"));
    public Set<Category> detect(String query) {
        var result=EnumSet.noneOf(Category.class);
        PATTERNS.forEach((key,value)-> { if(Pattern.compile(value,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(query).find()) result.add(key); });
        if (result.contains(Category.MOTOR)) {
            result.add(Category.MOTOR_PROTECTION); result.add(Category.CONTACTOR);
            if (query.matches("(?is).*protect|.*breaker.*|.*kaitse.*")) result.add(Category.CIRCUIT_BREAKER);
        }
        return Collections.unmodifiableSet(result);
    }
    public String expand(String question, Set<Category> categories) {
        var query=new StringBuilder(question);
        if(categories.contains(Category.MOTOR)) query.append(" motor overload short-circuit startup inrush coordination IEC 60947-4-1");
        if(categories.contains(Category.CIRCUIT_BREAKER)) query.append(" circuit breaker MCB MPCB breaking capacity Icu Ics Icn");
        if(categories.contains(Category.RCD)) query.append(" residual current RCD RCCB");
        if(categories.contains(Category.RCBO)) query.append(" RCBO residual overcurrent");
        if(categories.contains(Category.SURGE_PROTECTION)) query.append(" SPD surge protective device");
        if(categories.contains(Category.EARTHING)) query.append(" PE protective earth earthing");
        if(categories.contains(Category.THREE_PHASE)) query.append(" three phase 3-phase");
        if(categories.contains(Category.ENCLOSURE)) query.append(" IP ingress protection environment enclosure");
        return query.toString();
    }
}
