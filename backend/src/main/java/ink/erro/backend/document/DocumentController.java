package ink.erro.backend.document;

import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static ink.erro.backend.document.DocumentModels.*;

@RestController
@RequestMapping("/api/attachments")
public class DocumentController {
    private final DocumentStore store;
    public DocumentController(DocumentStore store) { this.store = store; }
    @PostMapping("/problem") public ResponseEntity<Problem> create() { return response(HttpStatus.CREATED,store.create()); }
    @DeleteMapping("/problem") public ResponseEntity<Void> clear(@RequestHeader("X-Problem-Token") String token) {
        store.delete(token); return response(HttpStatus.NO_CONTENT,null);
    }
    @PostMapping(value="/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Status> upload(@RequestHeader("X-Problem-Token") String token, @RequestParam("file") MultipartFile file) throws IOException {
        return response(HttpStatus.ACCEPTED,store.upload(token,file));
    }
    @GetMapping("/files") public ResponseEntity<List<Status>> list(@RequestHeader("X-Problem-Token") String token) {
        return response(HttpStatus.OK,store.list(token));
    }
    @DeleteMapping("/files/{id}") public ResponseEntity<Void> remove(@RequestHeader("X-Problem-Token") String token,@PathVariable String id) {
        store.remove(token,id); return response(HttpStatus.NO_CONTENT,null);
    }
    private <T> ResponseEntity<T> response(HttpStatus status,T body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
