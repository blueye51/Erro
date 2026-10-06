package ink.erro.backend.knowledge;

import java.time.LocalDate;
import java.util.*;
import jakarta.validation.constraints.*;

public final class KnowledgeModels {
    private KnowledgeModels() {}
    public enum SourceType {
        LAW(1), NATIONAL_REQUIREMENT(.98), STANDARD(.95), MANUFACTURER_DATASHEET(.9),
        MANUFACTURER_GUIDE(.8), DESIGN_GUIDE(.7), THEORY(.6), EDUCATIONAL(.5), INFORMATIONAL(.3);
        private final double authority;
        SourceType(double authority) { this.authority = authority; }
        public double authority() { return authority; }
    }
    public enum CopyrightStatus { PUBLIC, OPEN_LICENSE, USER_PROVIDED, LICENSED, METADATA_ONLY, RESTRICTED }
    public enum Jurisdiction { ESTONIA, EU, IEC, INTERNATIONAL, MANUFACTURER, US }
    public record SourceInput(
            @NotBlank @Size(max=300) String title,
            @Size(max=2000) String description,
            @NotNull SourceType sourceType,
            @NotBlank @Size(max=200) String publisher,
            @Size(max=2000) String sourceUrl,
            @NotNull Jurisdiction jurisdiction,
            @Pattern(regexp="[a-z]{2,3}(-[A-Z]{2})?") String language,
            @Size(max=100) String documentVersion,
            @Size(max=100) String edition, @Size(max=100) String amendment,
            LocalDate publicationDate, LocalDate effectiveDate,
            @Size(max=100) String standardNumber, @Size(max=100) String standardFamily,
            @Size(max=200) String manufacturer, @Size(max=200) String productFamily,
            @NotNull CopyrightStatus copyrightStatus,
            @NotBlank @Size(max=2000) String licenseNotes,
            @NotBlank @Size(max=50000) String content,
            boolean rightsConfirmed) {
        public SourceInput {
            description = empty(description); sourceUrl = empty(sourceUrl);
            language = language == null ? "en" : language;
            documentVersion = empty(documentVersion); edition = empty(edition); amendment = empty(amendment);
            standardNumber = empty(standardNumber).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
            standardFamily = empty(standardFamily); manufacturer = empty(manufacturer); productFamily = empty(productFamily);
        }
    }
    public record ChunkDraft(String sectionTitle, String sectionPath, int chunkIndex, String content, int tokenCount) {}
    public record Source(String id, UUID documentId, UUID chunkId, String title, String publisher,
                         String url, String section, String standardNumber, String documentVersion,
                         String edition, String amendment, LocalDate publicationDate, LocalDate effectiveDate,
                         String jurisdiction, String sourceType, String copyrightStatus, String licenseNotes, double authority,
                         double relevance) {}
    public record Hit(Source source, String content, Map<String, Double> scores) {}
    public record Retrieval(List<Hit> hits, List<String> warnings, String mode) {}
    public static String empty(String v) { return v == null ? "" : v; }
}
