package ink.erro.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ChatWebConfiguration implements WebMvcConfigurer {
    private final List<String> allowedOrigins;

    public ChatWebConfiguration(@Value("${chat.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/chat")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(false);
    }
}
