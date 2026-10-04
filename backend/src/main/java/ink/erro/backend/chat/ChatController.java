package ink.erro.backend.chat;

import ink.erro.backend.ai.AiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    private final AiService aiService;

    public ChatController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatReply> chat(@Valid @RequestBody ChatMessage request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ChatReply(aiService.reply(request.message().strip())));
    }

    public record ChatMessage(@NotBlank @Size(max = 8000) String message) {}
    public record ChatReply(String reply) {}
}
