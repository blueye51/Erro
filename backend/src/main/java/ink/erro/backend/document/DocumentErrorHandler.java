package ink.erro.backend.document;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import ink.erro.backend.chat.ChatController;

@RestControllerAdvice(assignableTypes={DocumentController.class,ChatController.class})
public class DocumentErrorHandler {
    @ExceptionHandler(DocumentException.class) public ResponseEntity<Map<String,String>> invalid(DocumentException failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore()).body(Map.of("error",failure.getMessage()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<Map<String,String>> tooLarge() {
        return ResponseEntity.status(413).cacheControl(CacheControl.noStore()).body(Map.of("error","Upload exceeds 20 MiB per file. Split or resize it."));
    }
    @ExceptionHandler({MultipartException.class,org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,java.io.IOException.class})
    public ResponseEntity<Map<String,String>> unreadable() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error","Upload could not be read. Attach a supported file in the current problem."));
    }
}
