package ink.erro.backend.guest;

import java.time.Instant;
import java.util.*;
import ink.erro.backend.chat.ChatService;

public final class GuestModels {
    private GuestModels() {}
    public record Identity(UUID id, String csrfToken) {}
    public record Chat(UUID id, String title, Instant createdAt, Instant updatedAt) {}
    public record ChatList(List<Chat> chats, boolean hasMore) {}
    public record Session(String csrfToken, List<Chat> chats, boolean hasMore) {}
    public record Message(long id, String role, String text, ChatService.ChatReply answer, List<String> filenames, Instant createdAt) {}
    public record Attachment(UUID id, String filename, long bytes, String status, String progress, String error,
                             List<String> warnings, int excerpts) {}
    public record Request(UUID id, String status, String message, List<UUID> attachmentIds, String error, Instant createdAt) {}
    public record Detail(Chat chat, List<Message> messages, boolean hasMore, List<Attachment> attachments, Request pending) {}
    public record Work(UUID chatId, UUID id, UUID lease, String message, List<UUID> attachmentIds) {}
    public record FileWork(UUID chatId, UUID id, UUID lease, String filename, byte[] bytes) {}
}
