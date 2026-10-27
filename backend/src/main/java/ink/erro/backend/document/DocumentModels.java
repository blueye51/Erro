package ink.erro.backend.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DocumentModels {
    private DocumentModels() {}
    public record Evidence(String id, String filename, String location, String method, String text,
                           List<Item> items, boolean incomplete) {
        public Evidence(String id, String filename, String location, String method, String text, List<Item> items) {
            this(id, filename, location, method, text, items, false);
        }
    }
    // An item describes one schedule row or one physical symbol, never a crop-wide total.
    public record Item(String reference, String description, BigDecimal quantity, String unit,
                       String basis, BigDecimal modulesEach, String partNumber) {}
    public record Extraction(List<Evidence> evidence, List<String> warnings) {}
    public record Status(String id, String filename, long bytes, String status, String progress,
                         String error, List<String> warnings, int excerpts, Instant expiresAt) {}
    public record Problem(String token, Instant expiresAt, int maxFiles, long maxFileBytes) {}
    public record Context(List<Evidence> excerpts, List<String> warnings, int totalExcerpts,
                          boolean complete, List<ink.erro.backend.electrical.ElectricalCalculationService.Calculation> calculations,
                          List<DocumentAssessmentService.Assessment> assessments, int uncertainExcerpts) {
        public Context(List<Evidence> excerpts,List<String> warnings,int totalExcerpts,boolean complete,
                       List<ink.erro.backend.electrical.ElectricalCalculationService.Calculation> calculations,
                       List<DocumentAssessmentService.Assessment> assessments) {
            this(excerpts,warnings,totalExcerpts,complete,calculations,assessments,(int)excerpts.stream().filter(Evidence::incomplete).count());
        }
        public static Context empty() { return new Context(List.of(), List.of(), 0, true, List.of(),List.of()); }
    }
}
