package ink.erro.backend.guest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import static ink.erro.backend.guest.GuestModels.*;

@Service
@EnableConfigurationProperties(GuestProperties.class)
public class GuestAuth {
    public static final String COOKIE="erro_guest";
    private final GuestRepository repository;
    private final GuestProperties properties;
    private final Set<String> origins;
    public GuestAuth(GuestRepository repository,GuestProperties properties,@Value("${chat.allowed-origins}") List<String> origins) {
        this.repository=repository;this.properties=properties;this.origins=Set.copyOf(origins);
    }
    public Identity resolve(HttpServletRequest request,HttpServletResponse response,boolean create,boolean mutation) {
        String origin=request.getHeader("Origin");
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site")) || (origin!=null && !origins.contains(origin)))
            throw new GuestException(HttpStatus.FORBIDDEN,"Open the chat on the E.R.R.O. website to continue.");
        String token=null;
        if(request.getCookies()!=null) for(var cookie:request.getCookies())
            if(cookie.getName().equals(COOKIE) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) token=cookie.getValue();
        UUID id=token==null?null:repository.findGuest(digest(token.getBytes(StandardCharsets.US_ASCII)),properties.ttl());
        if(id==null) {
            if(!create) throw new GuestException(HttpStatus.UNAUTHORIZED,"Your guest session is unavailable. Reload the page to start a new session.");
            byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);
            token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            id=repository.createGuest(digest(token.getBytes(StandardCharsets.US_ASCII)),properties.ttl());
        }
        String csrf=digest(("csrf:"+token).getBytes(StandardCharsets.US_ASCII));
        if(mutation && !MessageDigest.isEqual(csrf.getBytes(StandardCharsets.US_ASCII),Objects.toString(request.getHeader("X-CSRF-Token"),"").getBytes(StandardCharsets.US_ASCII)))
            throw new GuestException(HttpStatus.FORBIDDEN,"The session check failed. Reload the page and try again.");
        if(create) response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,token).httpOnly(true)
                .secure(properties.cookieSecure()).sameSite("Strict").path("/api").maxAge(properties.ttl()).build().toString());
        return new Identity(id,csrf);
    }
    public Identity erase(HttpServletRequest request,HttpServletResponse response) {
        var old=resolve(request,response,false,true);
        byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID id=repository.replaceGuest(old.id(),digest(token.getBytes(StandardCharsets.US_ASCII)),properties.ttl());
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,token).httpOnly(true)
                .secure(properties.cookieSecure()).sameSite("Strict").path("/api").maxAge(properties.ttl()).build().toString());
        return new Identity(id,digest(("csrf:"+token).getBytes(StandardCharsets.US_ASCII)));
    }
    public static String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
