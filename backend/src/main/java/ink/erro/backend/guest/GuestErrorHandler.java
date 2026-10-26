package ink.erro.backend.guest;

import java.io.IOException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ink.erro.backend.document.DocumentException;

@RestControllerAdvice(assignableTypes=GuestController.class)
public class GuestErrorHandler {
    @ExceptionHandler(GuestException.class) public ResponseEntity<Map<String,String>> guest(GuestException e) { return error(e.status(),e.getMessage()); }
    @ExceptionHandler(DocumentException.class) public ResponseEntity<Map<String,String>> document(DocumentException e) { return error(e.status(),e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<Map<String,String>> size() { return error(HttpStatus.PAYLOAD_TOO_LARGE,"Upload exceeds 20 MiB per file."); }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MultipartException.class,IOException.class})
    public ResponseEntity<Map<String,String>> invalid() { return error(HttpStatus.BAD_REQUEST,"Check the message, selected files or chat identifier and retry."); }
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<Map<String,String>> database() { return error(HttpStatus.SERVICE_UNAVAILABLE,"Saved chats are temporarily unavailable. Please retry."); }
    private ResponseEntity<Map<String,String>> error(HttpStatus status,String message) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error",message)); }
}
