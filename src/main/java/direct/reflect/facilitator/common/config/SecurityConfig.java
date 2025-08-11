package direct.reflect.facilitator.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.net.URI;

/**
 * Simplified Spring Security configuration.
 * 
 * Key principles:
 * - GUEST and USER are just different roles, same authentication structure
 * - Home page allows choosing between guest login or user login
 * - Backend treats both identically, only difference is the role in token
 * - Uses @PreAuthorize annotations to control access based on roles
 * - Much simpler testing with standard @WithMockUser(roles = "GUEST") or @WithMockUser(roles = "USER")
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance(); // For development only
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Regular users with credentials
        UserDetails regularUser = User.builder()
            .username("michel")
            .password("t")
            .roles("USER")
            .build();
            
        UserDetails adminUser = User.builder()
            .username("admin")
            .password("admin")
            .roles("USER", "ADMIN")
            .build();
            
        return new InMemoryUserDetailsManager(regularUser, adminUser);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF Configuration
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            // Authorization rules
            .authorizeHttpRequests(requests -> requests
                // Public static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/static/**").permitAll()
                .requestMatchers("/favicon.ico", "/htmx.min.js", "/sse.js", "/json-enc.js", "/script.js").permitAll()
                
                // Public pages - login page where you choose guest vs user login
                .requestMatchers("/login").permitAll()
                
                // API endpoints require either USER or GUEST role
                // Individual endpoints can use @PreAuthorize to be more specific
                .requestMatchers("/api/**").hasAnyRole("USER", "GUEST")
                
                // Admin endpoints require ADMIN role
                .requestMatchers("/admin/**").hasRole("ADMIN")
                
                // Everything else requires authentication (USER or GUEST)
                .anyRequest().hasAnyRole("USER", "GUEST")
            )
            // Disable default form login since we handle it manually
            .formLogin(form -> form.disable())
            // Exception handling - return 401/403 for API, redirect for pages
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, ex) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(403);
                        response.getWriter().write("Access denied");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            )
            // Standard logout
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
            )
            .build();
    }
    
    private SimpleUrlLogoutSuccessHandler logoutSuccessHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/login");
        return handler;
    }
}