package ink.erro.backend.guest;

import org.springframework.http.HttpStatus;

public class GuestException extends RuntimeException {
    private final HttpStatus status;
    public GuestException(HttpStatus status, String message) { super(message); this.status=status; }
    public HttpStatus status() { return status; }
    static GuestException missing() { return new GuestException(HttpStatus.NOT_FOUND,"This chat or file is unavailable in your guest session."); }
    static GuestException busy(String message) { return new GuestException(HttpStatus.CONFLICT,message); }
}
