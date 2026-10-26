package ink.erro.backend.guest;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ink.erro.backend.document.DocumentProperties;
import static ink.erro.backend.guest.GuestModels.*;

@RestController
public class GuestController {
    private final GuestAuth auth;
    private final GuestRepository repository;
    private final DocumentProperties documents;
    public GuestController(GuestAuth auth,GuestRepository repository,DocumentProperties documents) { this.auth=auth;this.repository=repository;this.documents=documents; }
    @GetMapping("/api/guest") public ResponseEntity<Session> session(HttpServletRequest req,HttpServletResponse res) {
        var guest=auth.resolve(req,res,true,false);var list=repository.chats(guest.id(),0);
        if(list.chats().isEmpty()) { repository.createChat(guest.id());list=repository.chats(guest.id(),0); }
        return response(HttpStatus.OK,new Session(guest.csrfToken(),list.chats(),list.hasMore()));
    }
    @DeleteMapping("/api/guest") public ResponseEntity<Session> erase(HttpServletRequest req,HttpServletResponse res) {
        var guest=auth.erase(req,res);var list=repository.chats(guest.id(),0);
        return response(HttpStatus.OK,new Session(guest.csrfToken(),list.chats(),list.hasMore()));
    }
    @GetMapping("/api/chats") public ResponseEntity<ChatList> chats(@RequestParam(defaultValue="0") int offset,HttpServletRequest req,HttpServletResponse res) {
        return response(HttpStatus.OK,repository.chats(auth.resolve(req,res,false,false).id(),offset));
    }
    @PostMapping("/api/chats") public ResponseEntity<Chat> create(HttpServletRequest req,HttpServletResponse res) {
        return response(HttpStatus.CREATED,repository.createChat(auth.resolve(req,res,false,true).id()));
    }
    @GetMapping("/api/chats/{id}") public ResponseEntity<Detail> detail(@PathVariable UUID id,@RequestParam(required=false) Long before,HttpServletRequest req,HttpServletResponse res) {
        if(before!=null && before<1) throw new IllegalArgumentException("Invalid message page.");
        return response(HttpStatus.OK,repository.detail(auth.resolve(req,res,false,false).id(),id,before));
    }
    @DeleteMapping("/api/chats/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,HttpServletRequest req,HttpServletResponse res) {
        repository.deleteChat(auth.resolve(req,res,false,true).id(),id);return response(HttpStatus.NO_CONTENT,null);
    }
    public record Send(@NotNull UUID requestId,@NotBlank @Size(max=8000) String message,@Size(max=8) List<@NotNull UUID> attachmentIds) {}
    @PostMapping("/api/chats/{id}/messages") public ResponseEntity<Request> send(@PathVariable UUID id,@Valid @RequestBody Send input,HttpServletRequest req,HttpServletResponse res) {
        return response(HttpStatus.ACCEPTED,repository.enqueue(auth.resolve(req,res,false,true).id(),id,input.requestId(),input.message(),input.attachmentIds()));
    }
    @GetMapping("/api/chats/{id}/requests/{requestId}") public ResponseEntity<Request> status(@PathVariable UUID id,@PathVariable UUID requestId,HttpServletRequest req,HttpServletResponse res) {
        return response(HttpStatus.OK,repository.request(auth.resolve(req,res,false,false).id(),id,requestId));
    }
    @DeleteMapping("/api/chats/{id}/requests/{requestId}") public ResponseEntity<Void> cancel(@PathVariable UUID id,@PathVariable UUID requestId,HttpServletRequest req,HttpServletResponse res) {
        repository.cancel(auth.resolve(req,res,false,true).id(),id,requestId);return response(HttpStatus.NO_CONTENT,null);
    }
    @PostMapping(value="/api/chats/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Attachment> upload(@PathVariable UUID id,@RequestParam("file") MultipartFile file,HttpServletRequest req,HttpServletResponse res) throws IOException {
        var guest=auth.resolve(req,res,false,true);
        if(!documents.enabled()) throw new GuestException(HttpStatus.SERVICE_UNAVAILABLE,"File uploads are disabled.");
        return response(HttpStatus.ACCEPTED,repository.upload(guest.id(),id,file));
    }
    @GetMapping("/api/chats/{id}/attachments") public ResponseEntity<List<Attachment>> files(@PathVariable UUID id,HttpServletRequest req,HttpServletResponse res) {
        repository.owned(auth.resolve(req,res,false,false).id(),id,false);return response(HttpStatus.OK,repository.attachments(id));
    }
    @DeleteMapping("/api/chats/{id}/attachments/{file}") public ResponseEntity<Void> remove(@PathVariable UUID id,@PathVariable UUID file,HttpServletRequest req,HttpServletResponse res) {
        repository.removeAttachment(auth.resolve(req,res,false,true).id(),id,file);return response(HttpStatus.NO_CONTENT,null);
    }
    private <T> ResponseEntity<T> response(HttpStatus status,T body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
