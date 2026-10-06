package ink.erro.backend.knowledge;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ink.erro.backend.ai.AiService;
import ink.erro.backend.electrical.*;
import ink.erro.backend.product.*;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {
    private final KnowledgeRepository repository;
    private final KnowledgeIngestionService ingestion;
    private final ElectricalContextService electrical;
    private final ProblemContextService problems;
    private final ProductService products;
    public KnowledgeAdminController(KnowledgeRepository repository,KnowledgeIngestionService ingestion,ElectricalContextService electrical,ProblemContextService problems,ProductService products) {
        this.repository=repository; this.ingestion=ingestion; this.electrical=electrical; this.problems=problems; this.products=products;
    }
    @GetMapping("/documents") public List<Map<String,Object>> list(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int offset) { return repository.list(offset); }
    @GetMapping("/documents/{id}/chunks") public List<Map<String,Object>> chunks(@PathVariable UUID id) { return repository.chunks(id); }
    @PostMapping("/documents") public Map<String,UUID> ingest(@Valid @RequestBody SourceInput input) { return Map.of("id",ingestion.ingest(input)); }
    public record Enabled(@NotNull Boolean enabled) {}
    @PatchMapping("/documents/{id}") public void enabled(@PathVariable UUID id,@Valid @RequestBody Enabled value) {
        if(repository.jdbc().update("UPDATE "+KnowledgeRepository.TABLE+" SET enabled=?,updated_at=now() WHERE id=?",value.enabled(),id)==0) throw new NoSuchElementException();
    }
    @DeleteMapping("/documents/{id}") public void delete(@PathVariable UUID id) {
        if(repository.jdbc().update("DELETE FROM "+KnowledgeRepository.TABLE+" WHERE id=?",id)==0) throw new NoSuchElementException();
    }
    @PostMapping("/documents/{id}/reindex") public void reindex(@PathVariable UUID id) { ingestion.reindex(id); }
    public record DebugInput(@NotBlank @Size(max=8000) String question,@Size(max=8) List<@NotBlank @Size(max=8000) String> problemContext) {}
    @PostMapping("/debug") public Map<String,Object> debug(@Valid @RequestBody DebugInput input) {
        var problem=problems.resolve(input.question(),input.problemContext());
        var context=electrical.prepare(problem.question(),problem.assumptions());
        return Map.of("systemInstruction",AiService.systemInstruction(),"input",Map.of("userQuestion",problem.question(),"referenceData",context),"contextReset",problem.reset());
    }
    @GetMapping("/products") public List<Map<String,Object>> products(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int offset) { return products.list(offset); }
    @PostMapping("/products") public Map<String,UUID> product(@Valid @RequestBody ProductModels.ProductInput input) { return Map.of("id",products.save(input)); }
    @PatchMapping("/products/{id}") public void productEnabled(@PathVariable UUID id,@Valid @RequestBody Enabled value) {
        if(repository.jdbc().update("UPDATE erro_knowledge.product SET enabled=?,updated_at=now() WHERE id=?",value.enabled(),id)==0) throw new NoSuchElementException();
    }
    @PostMapping("/compatibility") public void compatibility(@Valid @RequestBody ProductModels.CompatibilityInput input) { products.compatibility(input); }
}
