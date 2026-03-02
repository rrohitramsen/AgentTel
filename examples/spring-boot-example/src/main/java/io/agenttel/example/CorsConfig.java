package io.agenttel.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for cross-stack trace correlation.
 *
 * <p>The browser's AgentTel Web SDK sends {@code traceparent} headers with API requests
 * and reads {@code traceparent} from responses. For this to work across origins,
 * the backend must:
 * <ul>
 *   <li>Allow the {@code traceparent} and {@code tracestate} request headers</li>
 *   <li>Expose the {@code traceparent} and {@code tracestate} response headers</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "traceparent", "tracestate")
                        .exposedHeaders("traceparent", "tracestate")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
