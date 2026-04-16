package direct.reflect.facilitator.config;

import direct.reflect.facilitator.auth.AuthService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import direct.reflect.facilitator.common.config.SecurityConfig;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("test")
public class TestSecurityOverride {

    @Bean
    @Primary
    @Order(0)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
                                                       SecurityConfig.OidcSuccessHandler oidcSuccessHandler) throws Exception {
        return http
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oidcSuccessHandler)
            )
            .csrf(csrf -> csrf
                .spa()
                .ignoringRequestMatchers("/test/**", "/auth/guest")
            )
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/static/**", "/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/favicon.ico", "/favicon.svg", "/vite.svg").permitAll()
                .requestMatchers("/login").permitAll()
                .requestMatchers("/auth/guest", "/auth/local/**").permitAll()
                .requestMatchers("/test/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/", "/home").authenticated()
                .requestMatchers("/api/retro/*/join").authenticated()
                .requestMatchers("/api/retro/*/participants").authenticated()
                .requestMatchers("/api/retro/*/events").authenticated()
                .requestMatchers("/retro/**").authenticated()
                .requestMatchers("/api/user/**").authenticated()
                .requestMatchers("/profile/**").authenticated()
                .requestMatchers("/admin/**").authenticated()
                .anyRequest().authenticated()
            )
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
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(hybridLogoutHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .build();
    }

    @Bean
    public SecurityConfig.OidcSuccessHandler oidcSuccessHandler(AuthService authService) {
        return new SecurityConfig.OidcSuccessHandler(authService);
    }

    private SimpleUrlLogoutSuccessHandler hybridLogoutHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }
}
