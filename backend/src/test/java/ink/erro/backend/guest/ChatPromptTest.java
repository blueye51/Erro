package ink.erro.backend.guest;

import java.util.*;
import org.junit.jupiter.api.Test;
import ink.erro.backend.chat.*;
import ink.erro.backend.document.DocumentModels;
import ink.erro.backend.electrical.*;
import ink.erro.backend.knowledge.KnowledgeModels;
import static org.assertj.core.api.Assertions.*;

class ChatPromptTest {
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
        assertThat(json.readTree(result.json()).path("previousConversation").size()).isLessThan(64);
    }
}
