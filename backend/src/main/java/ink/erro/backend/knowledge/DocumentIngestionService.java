package ink.erro.backend.knowledge;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Service
public class DocumentIngestionService implements KnowledgeIngestionService {
    private final KnowledgeRepository repository;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddings;
    private final TransactionTemplate transaction;
    public DocumentIngestionService(KnowledgeRepository repository, DocumentChunker chunker,
                                     EmbeddingService embeddings, PlatformTransactionManager manager) {
        this.repository = repository; this.chunker = chunker; this.embeddings = embeddings;
        this.transaction = new TransactionTemplate(manager);
    }
    public UUID ingest(SourceInput input) {
        validate(input);
        String content = DocumentChunker.normalize(input.content());
        chunker.chunk(content); // Reject invalid input before creating a row.
        UUID id = UUID.randomUUID();
        var args = new MapSqlParameterSource().addValue("id", id).addValue("title", input.title())
                .addValue("description", input.description()).addValue("type", input.sourceType().name())
                .addValue("publisher", input.publisher()).addValue("url", input.sourceUrl()).addValue("jurisdiction", input.jurisdiction().name())
                .addValue("language", input.language()).addValue("version", input.documentVersion()).addValue("edition", input.edition())
                .addValue("amendment", input.amendment()).addValue("publication", input.publicationDate()).addValue("effective", input.effectiveDate())
                .addValue("standard", input.standardNumber()).addValue("family", input.standardFamily()).addValue("manufacturer", input.manufacturer())
                .addValue("product", input.productFamily()).addValue("copyright", input.copyrightStatus().name()).addValue("license", input.licenseNotes())
                .addValue("content", content).addValue("hash", hash(content));
        var named = new NamedParameterJdbcTemplate(repository.jdbc());
        UUID saved = named.queryForObject("INSERT INTO " + KnowledgeRepository.TABLE + " (id,title,description,source_type,publisher,source_url,"
                + "jurisdiction,language,document_version,edition,amendment,publication_date,effective_date,standard_number,standard_family,"
                + "manufacturer,product_family,copyright_status,license_notes,content,source_hash) VALUES "
                + "(:id,:title,:description,:type,:publisher,:url,:jurisdiction,:language,:version,:edition,:amendment,:publication,:effective,"
                + ":standard,:family,:manufacturer,:product,:copyright,:license,:content,:hash) "
                + "ON CONFLICT (source_url,document_version,source_hash) DO UPDATE SET source_hash=EXCLUDED.source_hash RETURNING id", args, UUID.class);
        if (id.equals(saved)) reindex(saved);
        return saved;
    }
    public void reindex(UUID id) {
        var document = repository.document(id);
        int locked = repository.jdbc().update("UPDATE " + KnowledgeRepository.TABLE + " SET status='INDEXING', error=NULL,updated_at=now() "
                + "WHERE id=? AND (status<>'INDEXING' OR updated_at < now()-interval '5 minutes')", id);
        if (locked == 0) throw new IllegalArgumentException("This source is already being indexed. Retry after it finishes.");
        try {
            var chunks = chunker.chunk((String) document.get("content"));
            var vectors = embeddings.embed(chunks.stream().map(c -> document.get("title") + "\n" + c.sectionPath() + "\n" + c.content()).toList());
            transaction.executeWithoutResult(status -> {
                // Serialize with disable/delete and ensure no partial index is ever visible.
                repository.jdbc().queryForObject("SELECT id FROM " + KnowledgeRepository.TABLE + " WHERE id=? FOR UPDATE", UUID.class, id);
                repository.jdbc().update("DELETE FROM " + KnowledgeRepository.CHUNKS + " WHERE document_id=?", id);
                for (int i=0; i<chunks.size(); i++) {
                    var chunk = chunks.get(i);
                    String vector = vectors.isEmpty() ? null : Arrays.toString(vectors.get(i)).replace('[','{').replace(']','}');
                    repository.jdbc().update("INSERT INTO " + KnowledgeRepository.CHUNKS + " (id,document_id,section_title,section_path,chunk_index,"
                            + "content,token_count,embedding,embedding_model,search_vector) VALUES (?,?,?,?,?,?,?,CAST(? AS double precision[]),?,"
                            + "setweight(to_tsvector('simple',?), 'A') || to_tsvector('simple',?))", UUID.randomUUID(), id, chunk.sectionTitle(),
                            chunk.sectionPath(), chunk.chunkIndex(), chunk.content(), chunk.tokenCount(), vector,
                            vectors.isEmpty() ? null : embeddings.modelKey(), document.get("title") + " " + document.get("standard_number") + " " + chunk.sectionPath(), chunk.content());
                }
                repository.jdbc().update("UPDATE " + KnowledgeRepository.TABLE + " SET status='READY',error=NULL,updated_at=now() WHERE id=?", id);
            });
        } catch (RuntimeException failure) {
            repository.jdbc().update("UPDATE " + KnowledgeRepository.TABLE + " SET status='FAILED', error=?,updated_at=now() WHERE id=?",
                    "Indexing failed. Check embedding configuration and retry; previous chunks are excluded until successful re-indexing.", id);
            throw new IllegalStateException("Source saved but indexing failed. Check source status and retry.");
        }
    }
    public static void validate(SourceInput input) {
        safeUrl(input.sourceUrl());
        if (!input.rightsConfirmed()) throw new IllegalArgumentException("Confirm permission to index this content and send excerpts to the AI provider.");
        if (input.copyrightStatus() == CopyrightStatus.RESTRICTED) throw new IllegalArgumentException("Restricted content cannot be indexed.");
        if (input.sourceType() == SourceType.STANDARD && input.copyrightStatus() != CopyrightStatus.METADATA_ONLY
                && input.copyrightStatus() != CopyrightStatus.LICENSED) throw new IllegalArgumentException("Standards require LICENSED or METADATA_ONLY status.");
        if (input.copyrightStatus() == CopyrightStatus.METADATA_ONLY && input.content().length() > 2000)
            throw new IllegalArgumentException("Metadata-only entries must be short scope summaries, not standard text.");
    }
    public static void safeUrl(String url) {
        if (url == null || url.isBlank()) return;
        URI uri;
        try { uri = URI.create(url); } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid source URL."); }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null
                || url.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Source URLs must be public HTTP(S) links without credentials.");
        // URLs are citation metadata only. No network fetch is ever made from this user input.
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
