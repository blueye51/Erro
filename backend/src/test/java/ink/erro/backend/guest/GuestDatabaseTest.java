package ink.erro.backend.guest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.ai.*;
import ink.erro.backend.chat.*;
import ink.erro.backend.document.*;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.*;
import ink.erro.backend.product.ProductService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="RAG_TEST_DATABASE_URL",matches=".+")
class GuestDatabaseTest {
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    GuestRepository repository;
    GuestAuth auth;
    GuestJobs jobs;
    DocumentStore legacy;
    MockMvc mvc;
    AiService ai;
    final JsonMapper json=JsonMapper.builder().build();
    final AtomicReference<String> input=new AtomicReference<>();
    record Browser(Cookie cookie,String csrf,UUID chat) {}
    @BeforeAll static void database() {
        String url=System.getenv("RAG_TEST_DATABASE_URL");
        if(!url.matches("jdbc:postgresql://[^/]+/erro_knowledge_test(?:\\?.*)?")) throw new IllegalArgumentException("Use only the disposable erro_knowledge_test database.");
        var ds=new DriverManagerDataSource(url,System.getenv("RAG_TEST_DATABASE_USER"),System.getenv("RAG_TEST_DATABASE_PASSWORD"));
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        var migrations=Flyway.configure().dataSource(ds).defaultSchema("erro_knowledge").schemas("erro_knowledge").load();
        migrations.migrate();migrations.validate();
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE erro_chat.guest CASCADE");
        repository=new GuestRepository(jdbc,manager);
        auth=new GuestAuth(repository,new GuestProperties(false,Duration.ofDays(365)),List.of("http://localhost:5173"));
        ai=mock(AiService.class);
        when(ai.reply(anyString())).thenAnswer(call->{
            String prompt=call.getArgument(0);input.set(prompt);
            var excerpts=json.readTree(prompt).path("documentData").path("excerpts");
            return excerpts.isEmpty()?"Remembered answer; an assistant's 230 V estimate is unverified.":"Readable schedule ["+excerpts.get(0).path("id").asText()+"]";
        });
        var properties=new DocumentProperties(true,"mock",Duration.ofMinutes(30),8);
        var processor=new DocumentExtractor(new VisionExtractor(ai,properties));
        legacy=new DocumentStore(processor,properties);
        var calculations=new ElectricalCalculationService();var validations=new ElectricalValidationService();
        var products=mock(ProductService.class);when(products.retrieve(anyString(),anySet(),any())).thenReturn(List.of());
        var retrieval=mock(KnowledgeRetrievalService.class);
        when(retrieval.retrieve(anyString(),anyList(),any(),anyString())).thenReturn(new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"));
        var intents=new ElectricalIntentService();var parameters=new ElectricalParameterExtractor();
        var electrical=new ElectricalContextService(intents,parameters,calculations,validations,retrieval,products,new KnowledgeProperties());
        var assessment=new DocumentAssessmentService(parameters,intents,calculations,validations,products);
        var documents=new DocumentContextService(legacy,calculations,assessment);
        var chat=new ChatService(ai,electrical,new ProblemContextService(intents),new CitationMapper(),documents,legacy);
        jobs=new GuestJobs(repository,chat,processor);
        mvc=MockMvcBuilders.standaloneSetup(new GuestController(auth,repository,properties)).setControllerAdvice(new GuestErrorHandler()).build();
    }
    @AfterEach void close() { jobs.close();legacy.close(); }
    @Test void cookieIsPrivateAndChatOwnershipAndCsrfAreEnforced() throws Exception {
        Browser one=browser(),two=browser();
        assertThat(one.cookie().getValue()).hasSize(43).isNotEqualTo(two.cookie().getValue());
        assertThat(jdbc.queryForList("SELECT token_hash FROM erro_chat.guest",String.class)).doesNotContain(one.cookie().getValue());
        mvc.perform(get("/api/chats/"+one.chat()).cookie(two.cookie())).andExpect(status().isNotFound());
        mvc.perform(post("/api/chats").cookie(one.cookie())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/guest").cookie(one.cookie()).header("X-CSRF-Token",two.csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/guest").header("Origin","https://unrelated.example")).andExpect(status().isForbidden());
        mvc.perform(get("/api/chats/"+one.chat())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.guest",Integer.class)).isEqualTo(2);
    }
    @Test void durableRequestsAreIdempotentAndFollowupsUseSavedDialogueWithoutReusingAssistantRatings() throws Exception {
        Browser browser=browser();UUID id=UUID.randomUUID();
        send(browser,id,"The motor operates at 400 V",List.of());send(browser,id,"The motor operates at 400 V",List.of());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.chat_request",Integer.class)).isEqualTo(1);
        verifyNoInteractions(ai);
        jobs.runChat();
        assertThat(repository.request(guestId(browser),browser.chat(),id).status()).isEqualTo("COMPLETE");
        var restarted=new GuestRepository(jdbc,manager);
        assertThat(restarted.detail(guestId(browser),browser.chat(),null).messages()).hasSize(2);
        send(browser,UUID.randomUUID(),"a",List.of());jobs.runChat();
        assertThat(input.get()).contains("previousConversation","Remembered answer");
        var parameters=json.readTree(input.get()).path("referenceData").path("parameters").path("values");
        assertThat(parameters.path("voltage").path("value").asInt()).isEqualTo(400);
        assertThat(input.get().length()).isLessThanOrEqualTo(ChatPrompt.MAX_CHARS);
        send(browser,id,"The motor operates at 400 V",List.of());
        assertThat(repository.detail(guestId(browser),browser.chat(),null).messages()).hasSize(4);
        mvc.perform(get("/api/chats/"+browser.chat()).cookie(browser.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(4));
    }
    @Test void attachmentsSurviveRepositoryRestartAndOriginalBytesAreRemovedAfterExtraction() throws Exception {
        Browser one=browser(),two=browser();
        UUID file=upload(one);
        assertThat(upload(one)).isEqualTo(file);
        assertThat(jdbc.queryForObject("SELECT original IS NOT NULL FROM erro_chat.attachment WHERE id=?",Boolean.class,file)).isTrue();
        jobs.runFile();
        assertThat(jdbc.queryForObject("SELECT original IS NULL FROM erro_chat.attachment WHERE id=?",Boolean.class,file)).isTrue();
        var restarted=new GuestRepository(jdbc,manager);
        assertThat(restarted.detail(guestId(one),one.chat(),null).attachments()).singleElement().satisfies(a->assertThat(a.status()).isEqualTo("READY"));
        mvc.perform(post("/api/chats/"+two.chat()+"/messages").cookie(two.cookie()).header("X-CSRF-Token",two.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new GuestController.Send(UUID.randomUUID(),"Read",List.of(file))))).andExpect(status().isNotFound());
        send(one,UUID.randomUUID(),"How many C16 breakers are listed?",List.of(file));jobs.runChat();
        var saved=restarted.detail(guestId(one),one.chat(),null);
        assertThat(saved.messages().getLast().answer().documentSources()).singleElement().satisfies(s->assertThat(s.filename()).isEqualTo("materials.csv"));
        assertThat(input.get()).contains("materials.csv");
    }
    @Test void deletingEverythingRotatesTheGuestAndDiscardsLateWorkerResults() throws Exception {
        Browser old=browser();UUID file=upload(old);
        var fileWork=repository.claimFile();
        send(old,UUID.randomUUID(),"Hello",List.of());var work=repository.claimChat();
        var response=mvc.perform(delete("/api/guest").cookie(old.cookie()).header("X-CSRF-Token",old.csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(cookie(response.getHeader("Set-Cookie")).getValue()).isNotEqualTo(old.cookie().getValue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.conversation WHERE id=?",Integer.class,old.chat())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.attachment WHERE id=?",Integer.class,file)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.chat_request",Integer.class)).isZero();
        var answer=new ChatService.ChatReply("late",UUID.randomUUID().toString(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),false,List.of());
        assertThat(repository.complete(work,answer)).isFalse();
        repository.completeFile(fileWork,new DocumentModels.Extraction(List.of(),List.of()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.message",Integer.class)).isZero();
        mvc.perform(get("/api/chats/"+old.chat()).cookie(old.cookie())).andExpect(status().isUnauthorized());
    }
    @Test void cancellationAndExpiredLeasesCannotPermanentlyBlockNewQuestions() throws Exception {
        Browser browser=browser();UUID id=UUID.randomUUID();send(browser,id,"Hello",List.of());var work=repository.claimChat();
        mvc.perform(delete("/api/chats/"+browser.chat()+"/requests/"+id).cookie(browser.cookie()).header("X-CSRF-Token",browser.csrf())).andExpect(status().isNoContent());
        assertThat(repository.request(guestId(browser),browser.chat(),id).status()).isEqualTo("FAILED");
        send(browser,UUID.randomUUID(),"A new question",List.of());repository.claimChat();
        jdbc.update("UPDATE erro_chat.chat_request SET lease_until=now()-interval '1 second' WHERE status='PROCESSING'");repository.cleanup();
        send(browser,UUID.randomUUID(),"b",List.of());jobs.runChat();
        assertThat(repository.detail(guestId(browser),browser.chat(),null).messages()).hasSize(2);
        assertThat(repository.request(guestId(browser),browser.chat(),work.id()).status()).isEqualTo("FAILED");
    }
    @Test void messagePaginationKeepsAllStoredMessagesAndOtherGuestsAreUnaffectedByDeletion() throws Exception {
        Browser browser=browser(),other=browser();
        for(int i=0;i<105;i++) jdbc.update("INSERT INTO erro_chat.message(conversation_id,role,content) VALUES (?,'user',?)",browser.chat(),"message "+i);
        var last=repository.detail(guestId(browser),browser.chat(),null);
        assertThat(last.messages()).hasSize(100);assertThat(last.hasMore()).isTrue();
        var first=repository.detail(guestId(browser),browser.chat(),last.messages().getFirst().id());
        assertThat(first.messages()).hasSize(5);assertThat(first.hasMore()).isFalse();
        repository.deleteGuest(guestId(browser));
        assertThat(repository.detail(guestId(other),other.chat(),null).chat().id()).isEqualTo(other.chat());
    }
    @Test void concurrentDuplicateSubmissionsAndClaimsProduceOnlyOneWorkItem() throws Exception {
        Browser browser=browser();UUID id=UUID.randomUUID(),guest=guestId(browser);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->repository.enqueue(guest,browser.chat(),id,"Hello",List.of()));
            var second=pool.submit(()->repository.enqueue(guest,browser.chat(),id,"Hello",List.of()));
            assertThat(first.get().id()).isEqualTo(second.get().id());
            var a=pool.submit(repository::claimChat);var b=pool.submit(repository::claimChat);
            assertThat(Arrays.asList(a.get(),b.get()).stream().filter(Objects::nonNull)).hasSize(1);
        }
    }
    @Test void relevantUserFactsCanBeRetrievedBeyondTheRecentDialogueWindow() throws Exception {
        Browser browser=browser();
        jdbc.update("INSERT INTO erro_chat.message(conversation_id,role,content) VALUES (?,'user','My name is Eric')",browser.chat());
        for(int i=0;i<130;i++) jdbc.update("INSERT INTO erro_chat.message(conversation_id,role,content) VALUES (?,'user',?)",browser.chat(),"Unrelated filler "+i);
        send(browser,UUID.randomUUID(),"What is my name?",List.of());jobs.runChat();
        assertThat(input.get()).contains("My name is Eric");
        assertThat(input.get().length()).isLessThanOrEqualTo(ChatPrompt.MAX_CHARS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.message WHERE conversation_id=?",Integer.class,browser.chat())).isEqualTo(133);
    }
    @Test void secureCookiesAndGuestExpiryProtectAndRemoveSavedData() throws Exception {
        Browser one=browser(),two=browser();UUID file=upload(one);
        var secure=new GuestAuth(repository,new GuestProperties(true,Duration.ofDays(365)),List.of("http://localhost:5173"));
        var controller=new GuestController(secure,repository,new DocumentProperties(true,"mock",Duration.ofMinutes(30),8));
        var secureMvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GuestErrorHandler()).build();
        secureMvc.perform(get("/api/guest").cookie(one.cookie())).andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Secure")));
        jdbc.update("UPDATE erro_chat.guest SET expires_at=now()-interval '1 second' WHERE id=?",guestId(one));
        repository.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erro_chat.attachment WHERE id=?",Integer.class,file)).isZero();
        mvc.perform(get("/api/chats/"+one.chat()).cookie(one.cookie())).andExpect(status().isUnauthorized());
        assertThat(repository.detail(guestId(two),two.chat(),null).chat().id()).isEqualTo(two.chat());
    }
    private Browser browser() throws Exception {
        var res=mvc.perform(get("/api/guest")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        String header=res.getHeader("Set-Cookie");assertThat(header).contains("HttpOnly","SameSite=Strict","Path=/api");
        var data=json.readTree(res.getContentAsString());
        return new Browser(cookie(header),data.path("csrfToken").asText(),UUID.fromString(data.path("chats").get(0).path("id").asText()));
    }
    private Cookie cookie(String header) { return new Cookie(GuestAuth.COOKIE,header.substring(header.indexOf('=')+1,header.indexOf(';'))); }
    private UUID guestId(Browser browser) { return repository.findGuest(GuestAuth.digest(browser.cookie().getValue().getBytes(StandardCharsets.US_ASCII)),Duration.ofDays(365)); }
    private void send(Browser browser,UUID request,String text,List<UUID> files) throws Exception {
        mvc.perform(post("/api/chats/"+browser.chat()+"/messages").cookie(browser.cookie()).header("X-CSRF-Token",browser.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new GuestController.Send(request,text,files)))).andExpect(status().isAccepted());
    }
    private UUID upload(Browser browser) throws Exception {
        var file=new MockMultipartFile("file","materials.csv","text/csv","Description,Quantity,Unit\nC16 breaker,3,pcs".getBytes(StandardCharsets.UTF_8));
        var response=mvc.perform(multipart("/api/chats/"+browser.chat()+"/attachments").file(file).cookie(browser.cookie()).header("X-CSRF-Token",browser.csrf()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).path("id").asText());
    }
}
