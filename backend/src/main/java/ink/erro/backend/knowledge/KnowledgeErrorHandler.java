package ink.erro.backend.knowledge;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=KnowledgeAdminController.class)
public class KnowledgeErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,String>> invalid() { return error(400,"Invalid source, metadata or request. Check field formats and size limits."); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex) { return error(400,ex.getMessage()); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String,String>> absent() { return error(404,"Source or product not found."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> conflict() { return error(409,"This change conflicts with catalog references or existing data. Disable referenced sources instead of deleting them."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> failed(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("knowledge administration failed type={}",ex.getClass().getSimpleName());
        return error(503,"Knowledge operation failed. Inspect source status and server configuration, then retry.");
    }
    private ResponseEntity<Map<String,String>> error(int status,String message) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error",message)); }
}
