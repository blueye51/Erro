package ink.erro.backend.document;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import ink.erro.backend.electrical.ElectricalCalculationService;
import static ink.erro.backend.document.DocumentModels.*;

@Service
public class DocumentContextService {
    private final DocumentStore store;
    private final ElectricalCalculationService arithmetic;
    private final DocumentAssessmentService assessment;
    private static final int MAX_CONTEXT = 24000, MAX_EXCERPTS = 24;
    private static final Pattern BREAKER = Pattern.compile("(?i)(?<![\\p{L}\\d])([BCD])\\s?(\\d{1,3})(?!\\d)");
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    public DocumentContextService(DocumentStore store, ElectricalCalculationService arithmetic,DocumentAssessmentService assessment) { this.store = store; this.arithmetic = arithmetic; this.assessment=assessment; }
    public Context retrieve(String token, List<String> ids, String question) {
        if (ids == null || ids.isEmpty()) return Context.empty();
        var files = store.read(token,ids);
        var all = files.stream().flatMap(f -> f.evidence().stream()).toList();
        var warnings = new LinkedHashSet<String>(); files.forEach(f -> warnings.addAll(f.warnings()));
        var terms = terms(question);
        int followUp=question.lastIndexOf("\nFollow-up:");
        var latestTerms=terms(followUp<0 ? question : question.substring(followUp+11));
        var ranked = new ArrayList<>(all);
        ranked.sort(Comparator.comparingInt((Evidence e) -> score(e,terms)+3*score(e,latestTerms)).reversed());
        // At least one excerpt per selected file precedes the rest; comparisons cannot silently omit a file.
        var selected = new LinkedHashMap<String,Evidence>();
        for (var file : files) ranked.stream().filter(e -> file.evidence().contains(e)).findFirst().ifPresent(e -> selected.put(e.id(),e));
        for (var e : ranked) selected.putIfAbsent(e.id(),e);
        int size = 0; var bounded = new ArrayList<Evidence>();
        for (var e : selected.values()) {
            int length = json.writeValueAsString(e).length();
            if (bounded.size() < MAX_EXCERPTS && size + length <= MAX_CONTEXT) { bounded.add(e); size += length; }
        }
        boolean complete = bounded.size() == all.size();
        var assessments=assessment.assess(bounded);
        var catalogWidths=new HashMap<String,BigDecimal>();
        assessments.forEach(a->{ if(a.catalogModulesEach()!=null) catalogWidths.put(a.evidenceId(),a.catalogModulesEach()); });
        if (!complete) warnings.add("Only " + bounded.size() + " of " + all.size() + " document excerpts fit this question. This is partial retrieval, not an exhaustive project takeoff. Ask about a specific file, sheet or component.");
        warnings.add("Selected files are evidence for this problem, not verified manufacturer specifications. Do not combine projects or sum specifications with drawings; reconcile references and revisions first.");
        var computed = new ArrayList<ElectricalCalculationService.Calculation>();
        // Aggregate complete structured tables per file only. Vision-derived numbers never enter exact arithmetic.
        for (var file : files) {
            var entries = file.evidence().stream().filter(e -> e.method().equals("TABLE"))
                    .flatMap(e -> e.items().stream().map(item -> Map.entry(e,item))).toList();
            if (entries.isEmpty()) continue;
            String filename = file.evidence().getFirst().filename();
            var references = new HashSet<String>(); var rows = new HashSet<Item>(); boolean ambiguous = false;
            for (var entry : entries) {
                var item = entry.getValue();
                if (!rows.add(item) || (!item.reference().isBlank() && !references.add(item.reference()))) ambiguous = true;
                if (item.description().toLowerCase(Locale.ROOT).matches("(?s).*\\b(total|subtotal|kokku|summa)\\b.*")) ambiguous = true;
            }
            if (ambiguous) {
                warnings.add(filename + ": repeated references/rows or totals need reconciliation. Exact quantity and DIN totals were withheld to prevent double-counting.");
                continue;
            }
            if (entries.stream().anyMatch(entry -> entry.getValue().quantity() == null || !entry.getValue().basis().equals("LISTED"))) {
                warnings.add(filename + ": missing or unreadable table quantities need review. Exact quantity and DIN totals were withheld rather than omitting those rows.");
                continue;
            }
            var counts = new TreeMap<String,BigDecimal>();
            var quantities = new ArrayList<BigDecimal>(); var modules = new ArrayList<BigDecimal>();
            for (var entry : entries) {
                var item = entry.getValue();
                if (!item.basis().equals("LISTED") || item.quantity() == null) continue;
                // Cable lengths and package counts must not become breaker piece counts.
                boolean pieces = item.unit().strip().toLowerCase(Locale.ROOT).matches("tk|pcs|pc|piece|pieces|ea");
                var matcher = BREAKER.matcher(item.description());
                if (pieces && matcher.find()) {
                    String key = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2);
                    if (!matcher.find()) counts.merge(key,item.quantity(),BigDecimal::add);
                }
                BigDecimal width=catalogWidths.getOrDefault(entry.getKey().id(),item.modulesEach());
                if(width!=null && item.modulesEach()!=null && width.compareTo(item.modulesEach())!=0)
                    warnings.add(filename+": the catalog DIN width differs from the project row. The sourced catalog width was used; resolve the discrepancy.");
                if (pieces && width != null) { quantities.add(item.quantity()); modules.add(width); }
            }
            counts.forEach((type,count) -> computed.add(new ElectricalCalculationService.Calculation(
                    filename + ": listed " + type + " quantity",count,"pcs","Σ explicit table quantities within this file",Map.of("listed_pieces",count),
                    List.of("Printed schedule quantities only; not an independently verified project total. Do not add this to quantities in another file."))));
            if (quantities.size() == entries.size()) computed.add(arithmetic.dinModules(filename,quantities,modules));
            else if (question.toLowerCase(Locale.ROOT).matches("(?s).*(din|modul|moodul).*"))
                warnings.add(filename + ": DIN total requires explicit modules per device and piece units for every row. No width was inferred from breaker rating or pole count.");
        }
        return new Context(List.copyOf(bounded),List.copyOf(warnings),all.size(),complete,List.copyOf(computed),assessments);
    }
    public static String searchHints(Context context) {
        // Hints drive retrieval, not scalar parameter extraction across unrelated circuits.
        return context.excerpts().stream().map(e -> e.text().substring(0,Math.min(400,e.text().length())))
                .limit(8).collect(java.util.stream.Collectors.joining("\n"));
    }
    private static Set<String> terms(String text) {
        var result = new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
        result.removeIf(t -> t.length() < 2);
        if (text.toLowerCase(Locale.ROOT).matches("(?s).*(breaker|kaitse).*")) result.addAll(List.of("mcb","b16","c16","c25","kaitselüliti"));
        if (text.toLowerCase(Locale.ROOT).matches("(?s).*(socket|pistik).*")) result.addAll(List.of("pistikupesa","socket"));
        if (text.toLowerCase(Locale.ROOT).matches("(?s).*(cable|kaabel).*")) result.addAll(List.of("cable","kaabel"));
        return result;
    }
    private static int score(Evidence evidence, Set<String> terms) {
        String text = (evidence.filename() + " " + evidence.location() + " " + evidence.text()).toLowerCase(Locale.ROOT);
        return terms.stream().mapToInt(t -> text.contains(t) ? 1 : 0).sum();
    }
}
