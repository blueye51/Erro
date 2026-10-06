package ink.erro.backend.knowledge;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Service
public class HybridKnowledgeRetrievalService implements KnowledgeRetrievalService {
    private final KnowledgeRepository repository;
    private final EmbeddingService embeddings;
    private final KnowledgeProperties properties;
    private static final Set<String> STOP = Set.of("the","a","an","i","have","is","it","this","that","what","which","should","can","use","my","for","of","to","and","with","need","please","about","from","on","me","in","be","do","you","or","at");
    public HybridKnowledgeRetrievalService(KnowledgeRepository repository, EmbeddingService embeddings, KnowledgeProperties properties) {
        this.repository=repository; this.embeddings=embeddings; this.properties=properties;
    }
    public List<Hit> documents(List<UUID> ids,Jurisdiction jurisdiction) {
        return repository.documents(ids,jurisdictions(jurisdiction));
    }
    public Retrieval retrieve(String query, List<String> standards, Jurisdiction jurisdiction, String manufacturer) {
        List<String> warnings = new ArrayList<>();
        Map<UUID,Hit> candidates = new LinkedHashMap<>();
        var scopes = jurisdictions(jurisdiction);
        String terms = terms(query);
        try {
            repository.keyword(terms, standards.stream().map(s -> s.replaceAll("[^A-Z0-9-]", "")).toList(), scopes,
                    properties.getKeywordTopK()).forEach(h -> candidates.put(h.source().chunkId(),h));
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(getClass()).warn("knowledge keyword retrieval failed type={}", failure.getClass().getSimpleName());
            warnings.add("Knowledge keyword retrieval failed; supporting evidence may be incomplete.");
        }
        String mode = "KEYWORD";
        if (embeddings.enabled()) {
            try {
                var vector = embeddings.embed(List.of(query)).getFirst();
                for (var hit : repository.semantic(vector, embeddings.modelKey(), scopes, properties.getVectorTopK())) {
                    candidates.merge(hit.source().chunkId(),hit,(a,b) -> {
                        var scores = new LinkedHashMap<>(a.scores()); scores.putAll(b.scores());
                        return new Hit(a.source(),a.content(),scores);
                    });
                }
                mode = "HYBRID";
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger(getClass()).warn("knowledge semantic retrieval failed type={}", failure.getClass().getSimpleName());
                warnings.add("Semantic retrieval unavailable; using keyword evidence only.");
            }
        }
        var ranked = candidates.values().stream().map(h -> rank(h, jurisdiction, manufacturer))
                .filter(h -> h.source().relevance() >= properties.getMinRelevance())
                .sorted(Comparator.comparingDouble((Hit h)->h.source().relevance()).reversed().thenComparing(h->h.source().id())).toList();
        List<Hit> selected = new ArrayList<>(); Map<UUID,Integer> perDocument=new HashMap<>();
        int chars = 0;
        for (var h : ranked) {
            if (selected.size() >= properties.getFinalChunks()) break;
            if (perDocument.getOrDefault(h.source().documentId(),0) >= 2 || chars+serializedSize(h) > properties.getMaxContextChars()) continue;
            selected.add(h); chars += serializedSize(h); perDocument.merge(h.source().documentId(),1,Integer::sum);
        }
        if (selected.isEmpty()) warnings.add("No supporting knowledge source was retrieved. Do not invent citations or clause requirements.");
        else if (selected.stream().noneMatch(h->h.source().authority() >= .8 && !h.source().copyrightStatus().equals("METADATA_ONLY")))
            warnings.add("No authoritative full-text source was retrieved. Scope metadata and general guidance cannot establish compliance.");
        if (selected.stream().filter(h->!h.source().standardNumber().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(h->h.source().standardNumber(),java.util.stream.Collectors.mapping(h->h.source().documentVersion(),java.util.stream.Collectors.toSet())))
                .values().stream().anyMatch(versions->versions.size()>1))
            warnings.add("Retrieved versions may differ. Compare edition, adoption and effective dates; do not silently resolve conflicts.");
        return new Retrieval(List.copyOf(selected),List.copyOf(warnings),mode);
    }
    private int serializedSize(Hit h) { return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(h).length(); }
    static Hit rank(Hit hit, Jurisdiction jurisdiction, String manufacturer) {
        var s=hit.source(); var scores=new LinkedHashMap<>(hit.scores());
        double keyword = scores.getOrDefault("keyword",0.0), semantic=scores.getOrDefault("semantic",0.0);
        double authority=s.authority() * (s.copyrightStatus().equals("METADATA_ONLY") ? .35 : 1);
        double score=.48*keyword/(keyword+.1)+.38*Math.max(0,semantic)+.4*scores.getOrDefault("exactStandard",0.0);
        // Metadata may boost relevant evidence, never make unrelated text relevant by itself.
        if (score > .02) {
            scores.put("authority",.08*authority); score+=.08*authority;
            if (s.jurisdiction().equals(jurisdiction.name())) { scores.put("jurisdiction",.03); score+=.03; }
            if (manufacturer != null && !manufacturer.isBlank() && s.publisher().toLowerCase(Locale.ROOT).contains(manufacturer.toLowerCase(Locale.ROOT))) { scores.put("manufacturer",.04); score+=.04; }
            if (s.publicationDate()!=null && s.publicationDate().isAfter(LocalDate.now().minusYears(5))) { scores.put("recency",.01); score+=.01; }
        }
        score=Math.min(1,score);
        return new Hit(new Source(s.id(),s.documentId(),s.chunkId(),s.title(),s.publisher(),s.url(),s.section(),s.standardNumber(),
                s.documentVersion(),s.edition(),s.amendment(),s.publicationDate(),s.effectiveDate(),s.jurisdiction(),s.sourceType(),s.copyrightStatus(),s.licenseNotes(),s.authority(),score),hit.content(),scores);
    }
    public static List<String> jurisdictions(Jurisdiction scope) {
        return scope == Jurisdiction.US ? List.of("US","INTERNATIONAL","MANUFACTURER")
                : scope == Jurisdiction.ESTONIA ? List.of("ESTONIA","EU","IEC","INTERNATIONAL","MANUFACTURER")
                : List.of(scope.name(),"IEC","INTERNATIONAL","MANUFACTURER").stream().distinct().toList();
    }
    public static String terms(String query) {
        return Pattern.compile("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*").matcher(query.toLowerCase(Locale.ROOT)).results()
                .map(m->m.group()).filter(t->t.length()>1 && !STOP.contains(t)).distinct().limit(60)
                .map(t->"'"+t+"'").reduce((a,b)->a+" | "+b).orElse("'zzemptyqueryzz'");
    }
}
