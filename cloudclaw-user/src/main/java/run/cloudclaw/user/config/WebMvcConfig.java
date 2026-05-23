package run.cloudclaw.user.config;

import run.cloudclaw.auth.security.AuthUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Assets served at root / (SPA references /assets/xxx)
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/chat/assets/", "file:static/chat/assets/");
        registry.addResourceHandler("/favicon-*.png")
                .addResourceLocations("classpath:/static/chat/", "file:static/chat/");
        // Keep /chat/** for any legacy references
        registry.addResourceHandler("/chat/**")
                .addResourceLocations("classpath:/static/chat/", "file:static/chat/");
    }
}
