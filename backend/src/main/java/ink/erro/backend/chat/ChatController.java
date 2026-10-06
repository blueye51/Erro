package ink.erro.backend.chat;

import java.util.List;
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
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatService.ChatReply> chat(@Valid @RequestBody ChatMessage request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(chatService.reply(request.message(), request.problemContext()));
    }

    public record ChatMessage(@NotBlank @Size(max = 8000) String message,
                              @Size(max = 8) List<@NotBlank @Size(max = 8000) String> problemContext) {
        public ChatMessage(String message) { this(message, List.of()); }
    }
}
