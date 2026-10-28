package ink.erro.backend.guest;

import java.util.*;
import org.junit.jupiter.api.Test;
import ink.erro.backend.chat.*;
import ink.erro.backend.document.DocumentModels;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.KnowledgeModels;
import static org.assertj.core.api.Assertions.*;

class ChatPromptTest {
    @Test void shortClarificationSurvivesDocumentPressureWithoutBecomingTechnicalInput() {
        var question="Before evaluating: 1. What is the motor nameplate current? 2. Is 230 V only an unverified possibility?";
        var original="Evaluate my 400 V motor installation.";
        var parameters=new ElectricalParameterExtractor().extract(original);
        var context=new ElectricalContextService.Context(original,Set.of(),parameters,
                new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var excerpts=new ArrayList<DocumentModels.Evidence>();
        for(int i=0;i<24;i++) excerpts.add(new DocumentModels.Evidence("D-"+UUID.randomUUID(),"project.pdf","Page "+i,"VISION","Drawing "+"a".repeat(2200),List.of()));
        var documents=new DocumentModels.Context(excerpts,List.of(),24,true,List.of(),List.of());
        var history=List.of(new ChatService.Turn("assistant","Old evaluation "+"x".repeat(6000)),
                new ChatService.Turn("user",original),new ChatService.Turn("assistant",question));

        var result=new ChatPrompt().build("14 A; no, keep the original supply.",List.of(original),history,context,documents);
        var envelope=tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.json());
        assertThat(result.json()).hasSizeLessThanOrEqualTo(ChatPrompt.MAX_CHARS).doesNotContain("Old evaluation");
        assertThat(envelope.path("previousConversation").size()).isEqualTo(1);
        assertThat(envelope.path("previousConversation").get(0).path("content").asText()).isEqualTo(question);
        assertThat(envelope.path("previousUserMessages").get(0).asText()).isEqualTo(original);
        assertThat(envelope.path("referenceData").path("parameters").path("values").path("voltage").path("value").asInt()).isEqualTo(400);
        assertThat(result.excerpts()).isNotEmpty().hasSizeLessThan(excerpts.size());
        assertThat(envelope.path("documentData").path("coverage").path("omittedExcerpts").asInt()).isPositive();
        assertThat(history).hasSize(3);
    }

    @Test void allThirtyNineSavedDrawingExcerptsFitWithoutRepeatingItemJson() {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var evidence=new ArrayList<DocumentModels.Evidence>();
        for(int i=0;i<39;i++) {
            var items=List.of(
                    new DocumentModels.Item("Q"+i,"C16 breaker; XPJ-HF D 5G6; matching schedule row",java.math.BigDecimal.ONE,"pcs","VISION_SYMBOL_COUNT",null,""),
                    new DocumentModels.Item("M"+i,"Meter, 3x230/400 V; exact model and mounting width not printed",java.math.BigDecimal.ONE,"pcs","VISION_SYMBOL_COUNT",null,""),
                    new DocumentModels.Item("R"+i,"Type A RCD, In 25A, residual current 30mA; one physical device",java.math.BigDecimal.ONE,"pcs","VISION_SYMBOL_COUNT",null,""),
                    new DocumentModels.Item("K"+i,"Contactor In 25A; circuit reference and pole notation unresolved",null,"pcs","VISION_UNLOCATED",null,""));
            evidence.add(new DocumentModels.Evidence("D-"+UUID.randomUUID(),"26004_PP_EL-7-01_v01_Peakilp.pdf","Page "+(i/13+1)+", region x=0..1654, y="+((i%13)/4*647)+".."+Math.min(2339,((i%13)/4+1)*647)+" (1654×2339 px), items "+(i*4+1)+"–"+(i*4+4)+", part 1","VISION",
                    "Check original drawing\n"+json.writeValueAsString(items),items,true));
        }
        var assessment=org.mockito.Mockito.mock(ink.erro.backend.document.DocumentAssessmentService.class);
        org.mockito.Mockito.when(assessment.assess(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        var documents=new ink.erro.backend.document.DocumentContextService(null,new ElectricalCalculationService(),assessment)
                .fromExtractions(List.of(new DocumentModels.Extraction(evidence,List.of())),"DIN module and cabinet estimate");
        assertThat(documents.excerpts()).hasSize(39);
        assertThat(documents.complete()).isFalse();
        assertThat(documents.uncertainExcerpts()).isEqualTo(39);
        var context=new ElectricalContextService.Context("query",Set.of(),new ElectricalParameterExtractor().extract(""),
                new KnowledgeModels.Retrieval(List.of(),List.of(),"KEYWORD"),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var result=new ChatPrompt().build("DIN module estimate",List.of(),List.of(new ChatService.Turn("assistant","older reply "+"x".repeat(8000))),context,documents);
        assertThat(result.json()).hasSizeLessThanOrEqualTo(ChatPrompt.MAX_CHARS);
        var data=json.readTree(result.json()).path("documentData");
        assertThat(data.path("excerpts").size()).isEqualTo(39);
        assertThat(data.path("coverage").path("allExtractedEvidenceSupplied").asBoolean()).isTrue();
        assertThat(data.path("complete").asBoolean()).isFalse();
        assertThat(data.path("coverage").path("uncertainExcerpts").asInt()).isEqualTo(39);
        assertThat(data.path("coverage").path("omittedExcerpts").asInt()).isZero();
        assertThat(data.path("excerpts").get(0).path("text").asText()).isEqualTo("Check original drawing");
        assertThat(data.path("excerpts").get(0).path("id").asText()).isEqualTo("D1");
        assertThat(data.path("itemColumns").get(5).asText()).isEqualTo("modulesEach");
        assertThat(data.path("excerpts").get(0).path("items").get(0).get(5).isNull()).isTrue();
        assertThat(data.path("excerpts").get(0).path("items").get(0).get(2).asInt()).isEqualTo(1);
        assertThat(result.excerpts().stream().flatMap(e->e.items().stream())).hasSize(156);
        assertThat(data.path("sources").size()).isEqualTo(12);
        for(int i=0;i<39;i++) {
            var excerpt=data.path("excerpts").get(i);
            var source=data.path("sources").path(excerpt.path("source").asText());
            assertThat(source.path("filename").asText()).isEqualTo(result.excerpts().get(i).filename());
            assertThat(source.path("location").asText()+excerpt.path("locationSuffix").asText()).isEqualTo(result.excerpts().get(i).location());
            assertThat(excerpt.path("items").get(3).get(2).isNull()).isTrue();
            assertThat(excerpt.path("items").get(3).get(4).asText()).isEqualTo("VISION_UNLOCATED");
        }
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
