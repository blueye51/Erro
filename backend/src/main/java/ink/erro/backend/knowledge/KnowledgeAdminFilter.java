package ink.erro.backend.knowledge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class KnowledgeAdminFilter extends OncePerRequestFilter {
    private final KnowledgeProperties properties;
    public KnowledgeAdminFilter(KnowledgeProperties properties) { this.properties=properties; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith("/api/admin")) { chain.doFilter(request,response); return; }
        response.setHeader("Cache-Control","no-store");
        String expected=properties.getAdminToken();
        if(expected==null||expected.length()<32) { reject(response,404,"Knowledge administration is disabled."); return; }
        String supplied=request.getHeader("Authorization");
        if(supplied==null||!supplied.startsWith("Bearer ")||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.substring(7).getBytes(StandardCharsets.UTF_8))) {
            reject(response,401,"A valid knowledge administrator token is required."); return;
        }
        // Bound actual bytes too: Content-Length alone does not constrain chunked requests.
        if(request.getMethod().equals("POST")||request.getMethod().equals("PUT")||request.getMethod().equals("PATCH")) {
            if(request.getContentType()==null||!request.getContentType().toLowerCase().startsWith("application/json")) { reject(response,415,"Send application/json."); return; }
            byte[] body=request.getInputStream().readNBytes(262145);
            if(body.length>262144) { reject(response,413,"Request exceeds 256 KiB."); return; }
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    var stream=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return stream.read(); }
                        public boolean isFinished() { return stream.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous API"); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
            },response);
        } else chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String error) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"error\":\""+error+"\"}");
    }
}
