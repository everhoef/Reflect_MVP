package direct.reflect.facilitator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import java.net.URI;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Bean
    public MapReactiveUserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username("michel")
            .password("{noop}t")  // {noop} means no password encoding
            .roles("USER")
            .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http       
            // Enable CSRF protection with a cookie-based token repository
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
            )
            // Store security context in session
            .securityContextRepository(new WebSessionServerSecurityContextRepository())
            // Configure authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Public resources
                .pathMatchers("/", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Login page is public
                .pathMatchers("/login", "/register", "/error").permitAll()
                // API endpoints require authenticated users except creation/joining
                .pathMatchers("/api/retro/create", "/api/retro/join").permitAll()
                .pathMatchers("/api/retro/**").authenticated()
                // Retrospective pages require user role
                .pathMatchers("/retro/**").permitAll()
                // Everything else requires authentication
                .anyExchange().authenticated()
            )
            // Use default form login
            .formLogin(withDefaults())
            // Configure logout
            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler())
            )
            .build();
    }
   
    private RedirectServerLogoutSuccessHandler logoutSuccessHandler() {
        RedirectServerLogoutSuccessHandler logoutSuccessHandler = new RedirectServerLogoutSuccessHandler();
        logoutSuccessHandler.setLogoutSuccessUrl(URI.create("/"));
        return logoutSuccessHandler;
    }
}
