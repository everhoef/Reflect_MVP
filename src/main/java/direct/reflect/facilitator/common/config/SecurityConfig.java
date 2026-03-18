package direct.reflect.facilitator.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Hybrid Security Configuration: OIDC + Anonymous Guests
 * 
 * Architecture:
 * - OIDC authentication for registered users (extracts username only)
 * - Anonymous access for guest users (no external authentication)
 * - Application-level role management (FACILITATOR/PARTICIPANT)
 * - Session-based state tracking for both user types
 * 
 * This approach cleanly separates:
 * - Authentication (WHO): External OIDC provider handles user identity
 * - Authorization (WHAT): Application handles business roles and permissions
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(name = "spring.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // OIDC authentication for registered users
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oidcSuccessHandler())
            )
            .csrf(csrf -> csrf.spa())
            // Authorization rules - permissive approach with service-level enforcement
            .authorizeHttpRequests(requests -> requests
                // Public static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/static/**", "/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/favicon.ico", "/favicon.svg", "/vite.svg").permitAll()
                
                // Public pages
                .requestMatchers("/login").permitAll()
                
                // Auth endpoints
                .requestMatchers("/auth/guest").permitAll()
                
                // Home page requires authentication - Spring Security will redirect to /login
                .requestMatchers("/", "/home").authenticated()
                
                // Health checks for monitoring
                .requestMatchers("/actuator/health/**").permitAll()
                
                // OpenAPI / Swagger UI - no auth required
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                
                // Mixed endpoints - require authentication (either OIDC or guest session)
                // Service layer handles business logic authorization  
                .requestMatchers("/api/retro/*/join").authenticated()
                .requestMatchers("/api/retro/*/participants").authenticated()
                .requestMatchers("/api/retro/*/events").authenticated()
                .requestMatchers("/retro/**").authenticated()
                
                // User-only endpoints - require OIDC authentication
                .requestMatchers("/api/user/**").authenticated()
                .requestMatchers("/profile/**").authenticated()
                
                // Admin endpoints - require authentication + application-level admin check
                .requestMatchers("/admin/**").authenticated()
                
                .anyRequest().authenticated()
            )
            // Exception handling
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
            // Logout handling
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(hybridLogoutHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .build();
    }
    
    @Bean
    public OidcSuccessHandler oidcSuccessHandler() {
        return new OidcSuccessHandler();
    }
    
    /**
     * Handles logout for both OIDC users and guest users
     */
    private SimpleUrlLogoutSuccessHandler hybridLogoutHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }
    
    @Component
    public static class OidcSuccessHandler implements AuthenticationSuccessHandler {
        
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, 
                                          HttpServletResponse response, 
                                          Authentication authentication) throws IOException, ServletException {
            
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                // Extract basic user identity from OIDC claims
                String username = extractUsername(oauth2Token);
                String email = oauth2Token.getPrincipal().getAttribute("email");
                String displayName = oauth2Token.getPrincipal().getAttribute("name");
                
                // Store in session for application use
                HttpSession session = request.getSession(true);
                session.setAttribute("authenticatedUser", username);
                session.setAttribute("userEmail", email);
                session.setAttribute("userDisplayName", displayName != null ? displayName : username);
                session.setAttribute("authType", "OIDC");
                
                // Clear any guest session data
                session.removeAttribute("guestDisplayName");
                session.removeAttribute("guestId");
                
                // Create new authentication token with ROLE_USER
                OAuth2User userPrincipal = oauth2Token.getPrincipal();
                
                Set<GrantedAuthority> authorities = new HashSet<>(userPrincipal.getAuthorities());
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                
                OAuth2User userWithRole = new DefaultOAuth2User(
                    authorities,
                    userPrincipal.getAttributes(),
                    oauth2Token.getPrincipal().getAttribute("login") != null ? "login" : "sub"
                );
                
                OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(
                    userWithRole,
                    authorities,
                    oauth2Token.getAuthorizedClientRegistrationId()
                );
                
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
            
            response.sendRedirect("/");
        }
        
        /**
         * Extract username from OIDC token with fallback strategies
         */
        private String extractUsername(OAuth2AuthenticationToken token) {
            // Try different common claim names for username
            // GitHub uses 'login' as the username field
            String username = token.getPrincipal().getAttribute("login");
            if (username == null) {
                username = token.getPrincipal().getAttribute("preferred_username");
            }
            if (username == null) {
                username = token.getPrincipal().getAttribute("username");
            }
            if (username == null) {
                username = token.getPrincipal().getAttribute("sub");
            }
            if (username == null) {
                username = token.getPrincipal().getAttribute("email");
            }
            
            if (username == null) {
                throw new IllegalStateException("No username claim found in OIDC token");
            }
            
            return username;
        }
    }
}
