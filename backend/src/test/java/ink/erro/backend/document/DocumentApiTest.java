package ink.erro.backend.document;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.ai.*;
import ink.erro.backend.chat.*;
import ink.erro.backend.config.ChatWebConfiguration;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.*;
import ink.erro.backend.product.ProductService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DocumentController.class,ChatController.class})
@Import({DocumentStore.class,DocumentExtractor.class,VisionExtractor.class,DocumentContextService.class,DocumentAssessmentService.class,
    DocumentErrorHandler.class,ChatService.class,CitationMapper.class,ElectricalContextService.class,ElectricalIntentService.class,
    ElectricalParameterExtractor.class,ElectricalCalculationService.class,ElectricalValidationService.class,ProblemContextService.class,
    KnowledgeProperties.class,ChatErrorHandler.class,ChatWebConfiguration.class})
class DocumentApiTest {
    @Autowired MockMvc mvc;
    @Autowired DocumentStore store;
    @MockitoBean AiService ai;
    @MockitoBean KnowledgeRetrievalService retrieval;
    @MockitoBean ProductService products;
    final JsonMapper json=JsonMapper.builder().build();
    final AtomicReference<String> prompt=new AtomicReference<>();
    @BeforeEach void configure() {
        when(retrieval.retrieve(anyString(),anyList(),any(),anyString())).thenReturn(new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"));
        when(products.retrieve(anyString(),anySet(),any())).thenReturn(List.of());
        when(ai.reply(anyString())).thenAnswer(call->{
            String input=call.getArgument(0);prompt.set(input);
            var excerpts=json.readTree(input).path("documentData").path("excerpts");
            return excerpts.isEmpty()?"New problem ready.":"Project evidence ["+excerpts.get(0).path("id").asText()+"] [D-fabricated]";
        });
    }
    @Test void uploadMultipleFilesChatFollowupAndExplicitReset() throws Exception {
        String token=create();
        String a=upload(token,"spec.pdf",DocumentTest.pdf(true,false,false));
        String b=upload(token,"cables.csv","Description,Quantity,Unit\nCable 3x2.5,40,m".getBytes());
        DocumentTest.awaitReady(store,token);
        for(String question:List.of("List the project materials","How many C16 breakers are listed?")) {
            mvc.perform(post("/api/chat").header("X-Problem-Token",token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ChatController.ChatMessage(question,List.of("Project A materials"),List.of(a,b)))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.documentSources[0].filename").exists()).andExpect(jsonPath("$.warnings").isNotEmpty());
            assertThat(prompt.get()).contains("spec.pdf","cables.csv","documentData","referenceData");
        }
        verify(ai,times(2)).reply(anyString());
        verify(ai,never()).analyzeImage(anyString(),anyString(),anyString(),any());
        mvc.perform(post("/api/chat").header("X-Problem-Token",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new ChatController.ChatMessage("New problem: hello",List.of(),List.of(a,b)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contextReset").value(true));
        assertThat(prompt.get()).doesNotContain("spec.pdf","cables.csv");
        mvc.perform(get("/api/attachments/files").header("X-Problem-Token",token)).andExpect(status().isGone());
    }
    @Test void capabilityIsRequiredAndFilesCannotCrossProblems() throws Exception {
        String token=create(),other=create(); String id=upload(token,"hello.txt","Project A private details".getBytes());
        DocumentTest.awaitReady(store,token);
        mvc.perform(get("/api/attachments/files")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/attachments/files").header("X-Problem-Token","wrong")).andExpect(status().isGone());
        mvc.perform(post("/api/chat").header("X-Problem-Token",other).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new ChatController.ChatMessage("Read files",List.of(),List.of(id)))))
            .andExpect(status().isNotFound());
        verifyNoInteractions(ai);
        mvc.perform(delete("/api/attachments/problem").header("X-Problem-Token",token)).andExpect(status().isNoContent());
        assertThatThrownBy(()->store.read(token,List.of(id))).hasMessageContaining("expired");
    }
    @Test void providerFailureIsSafeVisibleAndBlocksUseUntilRemoved() throws Exception {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenThrow(new AiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Could not reach the AI service. Please try again."));
        String token=create(); String id=upload(token,"scan.png",DocumentTest.png(120,120));
        DocumentTest.awaitReady(store,token);
        mvc.perform(get("/api/attachments/files").header("X-Problem-Token",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("ERROR")).andExpect(jsonPath("$[0].error").value("Could not reach the AI service. Please try again."));
        mvc.perform(post("/api/chat").header("X-Problem-Token",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new ChatController.ChatMessage("Read",List.of(),List.of(id)))))
            .andExpect(status().isConflict());
        mvc.perform(delete("/api/attachments/files/"+id).header("X-Problem-Token",token)).andExpect(status().isNoContent());
        assertThat(store.list(token)).isEmpty();
    }
    @Test void unlocatedVisualReadingsReachChatWithWarningsAndWithoutTotals() throws Exception {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn("""
            {"coordinateSpace":"NORMALIZED_1000","items":[{"reference":"Q1","description":"C16 breaker label",
            "quantity":99,"unit":"pcs","basis":"LISTED","modulesEach":2,"x":1500,"y":500}],"notes":"Readable drawing"}
            """);
        String token=create(); String id=upload(token,"panel.png",DocumentTest.png(120,120));
        DocumentTest.awaitReady(store,token);
        mvc.perform(get("/api/attachments/files").header("X-Problem-Token",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].warnings").isNotEmpty());
        mvc.perform(post("/api/chat").header("X-Problem-Token",token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ChatController.ChatMessage("Read the labels and explain the DIN requirement",List.of(),List.of(id)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentSources[0].filename").value("panel.png"))
                .andExpect(jsonPath("$.calculations").isEmpty()).andExpect(jsonPath("$.warnings").isNotEmpty());
        var data=json.readTree(prompt.get()).path("documentData");
        assertThat(data.path("complete").asBoolean()).isFalse();
        assertThat(data.path("excerpts").get(0).path("incomplete").asBoolean()).isTrue();
        assertThat(prompt.get()).contains("VISION_UNLOCATED").doesNotContain("\"quantity\":99");
        verify(ai,times(1)).analyzeImage(anyString(),anyString(),anyString(),any());
    }
    @Test void rejectsOversizeUnsupportedTypesAndCrossOriginAccess() throws Exception {
        String token=create();
        mvc.perform(multipart("/api/attachments/files").file(new MockMultipartFile("file","too-big.txt","text/plain",new byte[(int)DocumentExtractor.MAX_FILE_BYTES+1]))
            .header("X-Problem-Token",token)).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/attachments/files").file(new MockMultipartFile("file","script.exe","application/octet-stream",new byte[]{1}))
            .header("X-Problem-Token",token)).andExpect(status().isUnprocessableEntity());
        mvc.perform(options("/api/attachments/files").header("Origin","https://unrelated.example")
            .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
        mvc.perform(options("/api/attachments/files").header("Origin","http://localhost:5173")
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","x-problem-token"))
            .andExpect(status().isOk());
        verifyNoInteractions(ai);
    }
    private String create() throws Exception {
        return json.readTree(mvc.perform(post("/api/attachments/problem")).andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
    private String upload(String token,String name,byte[] content) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/attachments/files").file(new MockMultipartFile("file",name,"application/octet-stream",content))
            .header("X-Problem-Token",token)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }
}
