package ink.erro.backend.knowledge;

import java.util.*;
import org.junit.jupiter.api.Test;
import ink.erro.backend.chat.CitationMapper;
import static ink.erro.backend.knowledge.KnowledgeModels.*;
import static org.assertj.core.api.Assertions.*;

class KnowledgeUnitTest {
    @Test void chunksRespectSectionsParagraphsAndSize() {
        var props=new KnowledgeProperties(); props.setMaxChunkChars(400);
        var chunks=new DocumentChunker(props).chunk("# Motor\n## Protection\nOverload is distinct.\n\n| item | rating |\n| motor | unknown |\n\n"+"A long paragraph. ".repeat(80));
        assertThat(chunks.getFirst().sectionPath()).isEqualTo("Motor / Protection");
        assertThat(chunks.get(1).content()).contains("| item | rating |","| motor | unknown |");
        assertThat(chunks).allMatch(c->c.content().length()<=400);
        assertThat(chunks.stream().map(ChunkDraft::chunkIndex)).containsExactlyElementsOf(java.util.stream.IntStream.range(0,chunks.size()).boxed().toList());
        assertThatThrownBy(()->new DocumentChunker(props).chunk("# Heading only")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exactStandardBoostBeatsSemanticNearMatch() {
        var exact=hit("IEC 60947-2",Map.of("keyword",.2,"exactStandard",1.0));
        var semantic=hit("IEC 60898-1",Map.of("semantic",.99));
        assertThat(HybridKnowledgeRetrievalService.rank(exact,Jurisdiction.ESTONIA,"").source().relevance())
                .isGreaterThan(HybridKnowledgeRetrievalService.rank(semantic,Jurisdiction.ESTONIA,"").source().relevance());
    }
    @Test void citationsOnlyMapRetrievedIdsAndDedupe() {
        var hit=hit("IEC 60947-2",Map.of()); String id=hit.source().id();
        var answer=new CitationMapper().map("Evidence ["+id+"] again ["+id+"] invented [K-no-such-source]",List.of(hit));
        assertThat(answer.sources()).hasSize(1); assertThat(answer.reply()).contains("[1]").doesNotContain("K-no-such-source"); assertThat(answer.warnings()).hasSize(1);
    }
    @Test void linkMetadataCannotContainExecutableSchemes() {
        assertThatThrownBy(()->DocumentIngestionService.safeUrl("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DocumentIngestionService.safeUrl("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->DocumentIngestionService.safeUrl("https://user:pass@example.com")).isInstanceOf(IllegalArgumentException.class);
    }
    private Hit hit(String standard,Map<String,Double> scores) {
        UUID id=UUID.randomUUID();
        return new Hit(new Source("K-"+id,UUID.randomUUID(),id,"Title","Publisher","https://example.com","Scope",standard,"1","1","",null,null,"IEC","STANDARD","METADATA_ONLY","Metadata only",.95,0),"Scope",scores);
    }
}
