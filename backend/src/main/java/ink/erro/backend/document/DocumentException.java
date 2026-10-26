package ink.erro.backend.document;

import org.springframework.http.HttpStatus;

public class DocumentException extends RuntimeException {
    private final HttpStatus status;
    public DocumentException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static DocumentException invalid(String message) { return new DocumentException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
