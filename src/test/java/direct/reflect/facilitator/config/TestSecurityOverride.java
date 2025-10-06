package direct.reflect.facilitator.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import direct.reflect.facilitator.common.config.SecurityConfig;

/**
 * Complete test security configuration that replaces the main SecurityConfig for tests.
 * Includes the /test/** endpoints for test authentication.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("test")
public class TestSecurityOverride {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            // OIDC authentication for registered users
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oidcSuccessHandler())
            )
            // CSRF Configuration - disable for test endpoints
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/test/**")
            )
            // Authorization rules - same as main config PLUS test endpoints
            .authorizeHttpRequests(requests -> requests
                // Public static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/static/**").permitAll()
                .requestMatchers("/favicon.ico", "/htmx.min.js", "/sse.js", "/json-enc.js", "/script.js").permitAll()
                
                // Public pages
                .requestMatchers("/login").permitAll()
                
                // Auth endpoints
                .requestMatchers("/auth/guest").permitAll()
                
                // TEST ENDPOINTS - This is the only addition for testing
                .requestMatchers("/test/**").permitAll()
                
                // Health checks for monitoring
                .requestMatchers("/actuator/health/**").permitAll()
                
                // Continue with the rest of the main config rules - authenticated endpoints
                .requestMatchers("/", "/home").authenticated()
                .requestMatchers("/api/retro/*/join").authenticated()
                .requestMatchers("/api/retro/*/participants").authenticated()
                .requestMatchers("/api/retro/*/events").authenticated()
                .requestMatchers("/retro/**").authenticated()
                .requestMatchers("/api/user/**").authenticated()
                .requestMatchers("/profile/**").authenticated()
                .requestMatchers("/admin/**").authenticated()
                
                // Default: allow access, enforce business rules in service layer
                .anyRequest().permitAll()
            )
            // Exception handling (same as main config)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, ex) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Authentication required\",\"loginUrl\":\"/login\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            )
            // Logout handling (same as main config)
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(hybridLogoutHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .build();
    }
    
    @Bean
    public SecurityConfig.OidcSuccessHandler oidcSuccessHandler() {
        return new SecurityConfig.OidcSuccessHandler();
    }
    
    private SimpleUrlLogoutSuccessHandler hybridLogoutHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }
}