package ink.erro.backend.knowledge;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static ink.erro.backend.knowledge.KnowledgeModels.*;

@Repository
public class KnowledgeRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public KnowledgeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }
    public JdbcTemplate jdbc() { return jdbc; }
    public static final String TABLE = "erro_knowledge.knowledge_document";
    public static final String CHUNKS = "erro_knowledge.knowledge_chunk";
    static final String SELECT = "SELECT c.id AS chunk_id, c.content AS chunk_content, c.section_path, c.section_title, d.* ";
    static final String JOIN = " FROM " + CHUNKS + " c JOIN " + TABLE + " d ON d.id=c.document_id ";
    static final String FILTER = " d.enabled AND d.status='READY' AND d.copyright_status <> 'RESTRICTED' "
            + "AND (d.effective_date IS NULL OR d.effective_date <= CURRENT_DATE) AND d.jurisdiction IN (:jurisdictions) ";

    public List<Map<String, Object>> list(int offset) {
        return jdbc.queryForList("SELECT id,title,source_type,publisher,source_url,jurisdiction,language,document_version,"
                + "edition,amendment,publication_date::text,effective_date::text,standard_number,standard_family,manufacturer,"
                + "product_family,copyright_status,license_notes,enabled,status,error,source_hash,updated_at::text,"
                + "(SELECT count(*) FROM " + CHUNKS + " c WHERE c.document_id=d.id) AS chunk_count FROM " + TABLE
                + " d ORDER BY created_at DESC,id LIMIT 50 OFFSET ?", offset);
    }
    public Map<String, Object> document(UUID id) {
        var rows = jdbc.queryForList("SELECT id,title,content,status,source_type,copyright_status,standard_number FROM " + TABLE + " WHERE id=?", id);
        if (rows.isEmpty()) throw new NoSuchElementException("Source not found.");
        return rows.getFirst();
    }
    public List<Map<String,Object>> chunks(UUID id) {
        document(id);
        return jdbc.queryForList("SELECT id,section_title,section_path,chunk_index,content,token_count,embedding_model FROM "
                + CHUNKS + " WHERE document_id=? ORDER BY chunk_index", id);
    }
    public List<Hit> keyword(String terms, List<String> standards, List<String> jurisdictions, int limit) {
        var args = new MapSqlParameterSource().addValue("terms", terms).addValue("standards", standards.isEmpty() ? List.of("") : standards)
                .addValue("jurisdictions", jurisdictions).addValue("limit", limit);
        String exact = "(d.standard_number <> '' AND regexp_replace(upper(d.standard_number), '[^A-Z0-9-]', '', 'g') IN (:standards))";
        return named.query(SELECT + ", ts_rank_cd(c.search_vector, to_tsquery('simple', :terms)) AS lexical, "
                + "CASE WHEN " + exact + " THEN 1.0 ELSE 0.0 END AS exact_match " + JOIN + " WHERE " + FILTER
                + " AND (c.search_vector @@ to_tsquery('simple', :terms) OR " + exact + ")"
                + " ORDER BY exact_match DESC,lexical DESC,d.id,c.chunk_index LIMIT :limit", args,
                (rs, row) -> hit(rs, Map.of("keyword", rs.getDouble("lexical"), "exactStandard", rs.getDouble("exact_match"))));
    }
    public List<Hit> documents(List<UUID> ids,List<String> jurisdictions) {
        if(ids.isEmpty()) return List.of();
        return named.query(SELECT + JOIN + " WHERE " + FILTER
                + " AND d.id IN (:ids) AND c.chunk_index=0 ORDER BY d.id LIMIT 6",
                new MapSqlParameterSource().addValue("ids",ids).addValue("jurisdictions",jurisdictions),
                (rs,row)->hit(rs,Map.of("catalogDocument",1.0)));
    }
    public List<Hit> semantic(double[] vector, String model, List<String> jurisdictions, int limit) {
        // Model and dimension must both match; incompatible embedding spaces never mix.
        boolean pgvector = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_extension e "
                + "JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='vector' AND n.nspname='public')", Boolean.class));
        String cosine = pgvector
                ? "1 - (c.embedding::public.vector OPERATOR(public.<=>) CAST(:vector AS public.vector))"
                : "(SELECT sum(a*b)/NULLIF(sqrt(sum(a*a))*sqrt(sum(b*b)),0) FROM unnest(c.embedding, CAST(:array AS double precision[])) AS v(a,b))";
        String values = Arrays.toString(vector);
        var args = new MapSqlParameterSource().addValue("vector", values)
                .addValue("array", "{" + values.substring(1, values.length()-1) + "}")
                .addValue("model", model).addValue("dimensions", vector.length).addValue("jurisdictions", jurisdictions).addValue("limit", limit);
        return named.query(SELECT + ", " + cosine + " AS semantic " + JOIN + " WHERE " + FILTER
                + " AND c.embedding_model=:model AND cardinality(c.embedding)=:dimensions "
                + "ORDER BY semantic DESC NULLS LAST,d.id,c.chunk_index LIMIT :limit", args,
                (rs, row) -> hit(rs, Map.of("semantic", rs.getDouble("semantic"))));
    }
    static Hit hit(ResultSet rs, Map<String, Double> scores) throws SQLException {
        var type = SourceType.valueOf(rs.getString("source_type"));
        UUID document = rs.getObject("id", UUID.class), chunk = rs.getObject("chunk_id", UUID.class);
        return new Hit(new Source("K-" + chunk, document, chunk, rs.getString("title"), rs.getString("publisher"),
                rs.getString("source_url"), rs.getString("section_path").isBlank() ? rs.getString("section_title") : rs.getString("section_path"),
                rs.getString("standard_number"), rs.getString("document_version"), rs.getString("edition"), rs.getString("amendment"),
                rs.getObject("publication_date", java.time.LocalDate.class), rs.getObject("effective_date", java.time.LocalDate.class),
                rs.getString("jurisdiction"), type.name(), rs.getString("copyright_status"), rs.getString("license_notes"), type.authority(), 0),
                rs.getString("chunk_content"), scores);
    }
}
