package ink.erro.backend.knowledge;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.electrical.*;
import ink.erro.backend.product.*;
import static ink.erro.backend.knowledge.KnowledgeModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="RAG_TEST_DATABASE_URL", matches=".+")
class KnowledgeDatabaseTest {
    static JdbcTemplate jdbc;
    static KnowledgeRepository repository;
    static DocumentIngestionService ingestion;
    static KnowledgeProperties properties;
    static EmbeddingService embedding;
    static HybridKnowledgeRetrievalService retrieval;
    static MockMvc mvc;
    static final String TOKEN="test-only-knowledge-admin-token-32-characters";
    @BeforeAll static void connect() {
        String url=System.getenv("RAG_TEST_DATABASE_URL");
        if(!url.matches("jdbc:postgresql://[^/]+/erro_knowledge_test(?:\\?.*)?")) throw new IllegalArgumentException("Integration tests require a disposable erro_knowledge_test database.");
        var ds=new DriverManagerDataSource(url,System.getenv("RAG_TEST_DATABASE_USER"),System.getenv("RAG_TEST_DATABASE_PASSWORD"));
        // Existing unrelated data survives first migration and repeat startup.
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS public.unrelated_existing_data (id integer PRIMARY KEY)");
        jdbc.update("INSERT INTO public.unrelated_existing_data VALUES (42) ON CONFLICT DO NOTHING");
        var flyway=Flyway.configure().dataSource(ds).defaultSchema("erro_knowledge").schemas("erro_knowledge").load();
        flyway.migrate(); flyway.validate(); assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT id FROM public.unrelated_existing_data",Integer.class)).isEqualTo(42);
        properties=new KnowledgeProperties(); properties.setAdminToken(TOKEN); properties.setEmbeddingDimensions(3);
        embedding=mock(EmbeddingService.class); when(embedding.enabled()).thenReturn(false); when(embedding.embed(anyList())).thenReturn(List.of());
        repository=new KnowledgeRepository(jdbc);
        ingestion=new DocumentIngestionService(repository,new DocumentChunker(properties),embedding,new DataSourceTransactionManager(ds));
        retrieval=new HybridKnowledgeRetrievalService(repository,embedding,properties);
        var intents=new ElectricalIntentService(); var validators=new ElectricalValidationService();
        var products=new ProductService(jdbc,validators);
        var context=new ElectricalContextService(intents,new ElectricalParameterExtractor(),new ElectricalCalculationService(),validators,retrieval,products,properties);
        var controller=new KnowledgeAdminController(repository,ingestion,context,new ProblemContextService(intents),products);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new KnowledgeErrorHandler()).addFilters(new KnowledgeAdminFilter(properties)).build();
    }
    @BeforeEach void clear() {
        jdbc.execute("TRUNCATE erro_knowledge.knowledge_document,erro_knowledge.product CASCADE");
        reset(embedding); when(embedding.enabled()).thenReturn(false); when(embedding.embed(anyList())).thenReturn(List.of());
    }
    @Test void duplicateEnableDisableDeleteAndReindex() {
        var input=source("IEC 60947-2",Jurisdiction.IEC,"# Breaking capacity\nIcu and Ics apply to circuit breaker ratings.");
        UUID id=ingestion.ingest(input);
        assertThat(ingestion.ingest(input)).isEqualTo(id);
        assertThat(repository.chunks(id)).hasSize(1);
        assertThat(retrieval.retrieve("IEC 60947-2 breaking capacity",List.of("IEC 60947-2"),Jurisdiction.ESTONIA,"").hits()).hasSize(1);
        jdbc.update("UPDATE erro_knowledge.knowledge_document SET enabled=false WHERE id=?",id);
        assertThat(retrieval.retrieve("breaking capacity",List.of(),Jurisdiction.ESTONIA,"").hits()).isEmpty();
        ingestion.reindex(id);
        assertThat(repository.chunks(id)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT enabled FROM erro_knowledge.knowledge_document WHERE id=?",Boolean.class,id)).isFalse();
        jdbc.update("UPDATE erro_knowledge.knowledge_document SET enabled=true WHERE id=?",id);
        assertThat(retrieval.retrieve("breaking capacity",List.of(),Jurisdiction.ESTONIA,"").hits()).hasSize(1);
        jdbc.update("DELETE FROM erro_knowledge.knowledge_document WHERE id=?",id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_knowledge.knowledge_chunk",Integer.class)).isZero();
    }
    @Test void exactStandardAndJurisdictionFilters() {
        UUID exact=ingestion.ingest(source("IEC 60947-2",Jurisdiction.IEC,"Circuit breaker breaking capacity Icu Ics"));
        ingestion.ingest(source("IEC 60898-1",Jurisdiction.IEC,"Circuit breaker capacity capacity capacity"));
        ingestion.ingest(source("NEC 240",Jurisdiction.US,"Circuit breaker IEC 60947-2 Icu Ics breaking capacity"));
        var result=retrieval.retrieve("IEC 60947-2 breaking capacity",List.of("IEC 60947-2"),Jurisdiction.ESTONIA,"");
        assertThat(result.hits().getFirst().source().documentId()).isEqualTo(exact);
        assertThat(result.hits()).noneMatch(h->h.source().jurisdiction().equals("US"));
    }
    @Test void semanticFallbackAndModelIsolation() {
        when(embedding.enabled()).thenReturn(true); when(embedding.modelKey()).thenReturn("fixture:3");
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{1,0,0}));
        UUID id=ingestion.ingest(source("",Jurisdiction.IEC,"# Motor\nInduction motor coordination"));
        var result=retrieval.retrieve("rotating machine",List.of(),Jurisdiction.ESTONIA,"");
        assertThat(result.mode()).isEqualTo("HYBRID"); assertThat(result.hits().getFirst().source().documentId()).isEqualTo(id);
        when(embedding.modelKey()).thenReturn("other-model:3");
        assertThat(retrieval.retrieve("rotating machine",List.of(),Jurisdiction.ESTONIA,"").hits()).isEmpty();
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("fixture failure"));
        assertThat(retrieval.retrieve("motor",List.of(),Jurisdiction.ESTONIA,"").warnings()).anyMatch(w->w.contains("Semantic retrieval unavailable"));
    }
    @Test void failedReindexDoesNotExposePartialEvidence() {
        UUID id=ingestion.ingest(source("",Jurisdiction.IEC,"Motor protection guidance"));
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("private secret provider body"));
        assertThatThrownBy(()->ingestion.reindex(id)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.list(0).getFirst().get("error").toString()).doesNotContain("secret");
        assertThat(retrieval.retrieve("motor",List.of(),Jurisdiction.ESTONIA,"").hits()).isEmpty();
    }
    @Test void adminProtectionValidationAndDebug() throws Exception {
        mvc.perform(get("/api/admin/knowledge/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get(java.net.URI.create("/api/%61dmin/knowledge/documents"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/knowledge/documents").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/knowledge/documents").header("Authorization","Bearer "+TOKEN)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/admin/knowledge/documents").header("Authorization","Bearer "+TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(source("IEC 60947-2",Jurisdiction.IEC,"Circuit breaker scope"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isString());
        mvc.perform(post("/api/admin/knowledge/debug").header("Authorization","Bearer "+TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"IEC 60947-2 breaking capacity\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.input.referenceData.retrieval.hits[0].source.standardNumber").value("IEC 60947-2"));
        mvc.perform(post("/api/admin/knowledge/documents").header("Authorization","Bearer "+TOKEN).contentType(MediaType.APPLICATION_JSON).content("x".repeat(262145)))
                .andExpect(status().isPayloadTooLarge());
    }
    @Test void rejectsUnlicensedStandardText() {
        var src=source("IEC 60947-2",Jurisdiction.IEC,"Not authorized");
        var denied=new SourceInput(src.title(),"",SourceType.STANDARD,"IEC","",Jurisdiction.IEC,"en","","","",null,null,"IEC 60947-2","","","",CopyrightStatus.PUBLIC,"public is not licensed",src.content(),true);
        assertThatThrownBy(()->ingestion.ingest(denied)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void catalogImportAndDisabledSourceExclusion() throws Exception {
        var source=new SourceInput("Test official fixture datasheet","",SourceType.MANUFACTURER_DATASHEET,"Test manufacturer","https://example.com/datasheet",Jurisdiction.EU,"en","1","","",null,null,"","","Test manufacturer","",CopyrightStatus.LICENSED,"Test data only, not a real product", "# Ratings\nFixture ratings used only in tests.",true);
        UUID id=ingestion.ingest(source);
        String body="""
                {"manufacturer":"Test manufacturer","partNumber":"TEST-MCB","productType":"CIRCUIT_BREAKER",
                 "name":"Test-only breaker","sourceDocumentId":"%s","ratedVoltageAc":400,"ratedCurrent":16,
                 "ratings":[{"voltage":400,"currentType":"AC","icn":6000,"standardNumber":"IEC 60898-1"}],
                 "standards":[{"standardNumber":"IEC 60898-1"}],"additionalAttributes":{"test":"fixture"}}
                """.formatted(id);
        for(int i=0;i<2;i++) mvc.perform(post("/api/admin/knowledge/products").header("Authorization","Bearer "+TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_knowledge.product",Integer.class)).isEqualTo(1);
        var service=new ProductService(jdbc,new ElectricalValidationService());
        String q="Need a 400 V AC breaker, prospective fault current 9 kA, Icn";
        var candidates=service.retrieve(q,new ElectricalIntentService().detect(q),new ElectricalParameterExtractor().extract(q));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().checks()).anyMatch(c->c.rule().equals("product-breaking-capacity")&&c.status()==ElectricalValidationService.Status.FAIL);
        jdbc.update("UPDATE erro_knowledge.knowledge_document SET enabled=false WHERE id=?",id);
        assertThat(service.retrieve(q,new ElectricalIntentService().detect(q),new ElectricalParameterExtractor().extract(q))).isEmpty();
    }

    static SourceInput source(String standard,Jurisdiction jurisdiction,String content) {
        return new SourceInput("Test "+standard,"",SourceType.STANDARD,"Test publisher","https://example.com/"+standard.replace(' ','_'),jurisdiction,"en","1","1","",null,null,standard,"","","",CopyrightStatus.METADATA_ONLY,"Original test fixture scope only",content,true);
    }
}
