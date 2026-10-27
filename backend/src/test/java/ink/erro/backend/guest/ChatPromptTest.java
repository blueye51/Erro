package ink.erro.backend.guest;

import java.util.*;
import org.junit.jupiter.api.Test;
import ink.erro.backend.chat.*;
import ink.erro.backend.document.DocumentModels;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.KnowledgeModels;
import static org.assertj.core.api.Assertions.*;

class ChatPromptTest {
    @Test void allThirtyNineSavedDrawingExcerptsFitWithoutRepeatingItemJson() {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var evidence=new ArrayList<DocumentModels.Evidence>();
        for(int i=0;i<39;i++) {
            var items=List.of(
                    new DocumentModels.Item("Q"+i,"C16 breaker; XPJ-HF D 5G6; readable diagram and matching schedule row",java.math.BigDecimal.ONE,"pcs","VISION_SYMBOL_COUNT",null,""),
                    new DocumentModels.Item("M"+i,"Meter, 3x230/400 V; exact model and mounting width not printed",java.math.BigDecimal.ONE,"pcs","VISION_SYMBOL_COUNT",null,""));
            evidence.add(new DocumentModels.Evidence("D-"+UUID.randomUUID(),"panel.pdf","Page "+(i/13+1)+", group "+i,"VISION",
                    "Check original drawing\n"+json.writeValueAsString(items),items));
        }
        var assessment=org.mockito.Mockito.mock(ink.erro.backend.document.DocumentAssessmentService.class);
        org.mockito.Mockito.when(assessment.assess(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        var documents=new ink.erro.backend.document.DocumentContextService(null,new ElectricalCalculationService(),assessment)
                .fromExtractions(List.of(new DocumentModels.Extraction(evidence,List.of())),"DIN module and cabinet estimate");
        assertThat(documents.excerpts()).hasSize(39);
        assertThat(documents.complete()).isTrue();
        var context=new ElectricalContextService.Context("query",Set.of(),new ElectricalParameterExtractor().extract(""),
                new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var result=new ChatPrompt().build("DIN module estimate",List.of(),List.of(new ChatService.Turn("assistant","older reply "+"x".repeat(8000))),context,documents);
        assertThat(result.json()).hasSizeLessThanOrEqualTo(ChatPrompt.MAX_CHARS);
        var data=json.readTree(result.json()).path("documentData");
        assertThat(data.path("excerpts").size()).isEqualTo(39);
        assertThat(data.path("coverage").path("allExtractedEvidenceSupplied").asBoolean()).isTrue();
        assertThat(data.path("coverage").path("omittedExcerpts").asInt()).isZero();
        assertThat(data.path("excerpts").get(0).path("text").asText()).isEqualTo("Check original drawing");
        assertThat(data.path("excerpts").get(0).path("id").asText()).isEqualTo("D1");
        assertThat(data.path("itemColumns").get(5).asText()).isEqualTo("modulesEach");
        assertThat(data.path("excerpts").get(0).path("items").get(0).get(5).isNull()).isTrue();
        assertThat(data.path("excerpts").get(0).path("items").get(0).get(2).asInt()).isEqualTo(1);
        assertThat(result.excerpts().stream().flatMap(e->e.items().stream())).hasSize(78);
    }
    @Test void savedHistoryAndLargeDocumentsCannotFillTheGenerationRequest() {
        var parameters=new ElectricalParameterExtractor().extract("400 V");
        var context=new ElectricalContextService.Context("Duplicated query "+"x".repeat(24000),Set.of(),parameters,
                new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var excerpts=new ArrayList<DocumentModels.Evidence>();
        for(int i=0;i<24;i++) excerpts.add(new DocumentModels.Evidence("D-"+UUID.randomUUID(),"project.pdf","Page "+i,"VISION","Drawing "+"a".repeat(2200),List.of()));
        var documents=new DocumentModels.Context(excerpts,List.of(),24,true,List.of(),List.of());
        var history=new ArrayList<ChatService.Turn>();
        for(int i=0;i<64;i++) history.add(new ChatService.Turn(i%2==0?"user":"assistant","Saved message "+i+" "+"z".repeat(4000)));
        var result=new ChatPrompt().build("a",List.of("400 V"),history,context,documents);
        assertThat(result.json()).hasSizeLessThanOrEqualTo(ChatPrompt.MAX_CHARS).contains("\"userQuestion\":\"a\"").doesNotContain("Duplicated query");
        assertThat(result.trimmed()).isTrue();
        assertThat(result.excerpts()).isNotEmpty().hasSizeLessThan(excerpts.size());
        assertThat(excerpts).hasSize(24);assertThat(history).hasSize(64);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(json.readTree(result.json()).path("documentData").path("complete").asBoolean()).isFalse();
        assertThat(json.readTree(result.json()).path("documentData").path("coverage").path("omittedExcerpts").asInt()).isPositive();
        assertThat(json.readTree(result.json()).path("previousConversation").size()).isLessThan(64);
    }
}
