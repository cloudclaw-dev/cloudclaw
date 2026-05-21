package run.cloudclaw.auth.config;

import run.cloudclaw.auth.token.TokenProperties;
import run.cloudclaw.auth.security.AuthUserArgumentResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration for the CloudClaw authentication module.
 *
 * <p>Imports:</p>
 * <ul>
 *   <li>{@link run.cloudclaw.auth.security.SecurityConfig} - Spring Security filter chain configuration</li>
 * </ul>
 *
 * <p>Declares beans for:</p>
 * <ul>
 *   <li>{@link PasswordEncoder} - BCrypt-based password encoder</li>
 *   <li>{@link AuthUserArgumentResolver} - resolves {@code @AuthUser} annotated parameters</li>
 * </ul>
 */
@AutoConfiguration
@Import(run.cloudclaw.auth.security.SecurityConfig.class)
@EnableConfigurationProperties(TokenProperties.class)
public class AuthAutoConfiguration implements WebMvcConfigurer {

    /**
     * BCrypt password encoder bean for hashing and verifying passwords.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Register the {@link AuthUserArgumentResolver} to resolve
     * {@code @AuthUser} annotated String parameters in controller methods.
     *
     * @param resolvers the list of argument resolvers to add to
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }
}
