package direct.reflect.facilitator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.ignoringRequestMatchers("/retrospective/*/events"))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/retrospective/*/events")
                                                .permitAll().anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                SessionCreationPolicy.IF_REQUIRED)
                                                .maximumSessions(1).expiredUrl("/login?expired"))
                                .anonymous(anonymous -> anonymous.disable());

                return http.build();
        }

        @Configuration
        @Profile("dev")
        public static class DevSecurityConfig {

                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http.authorizeHttpRequests((requests) -> requests
                                        .requestMatchers("/css/**", "/", "/retrospective",
                                                        "/retrospective/**", "/login")
                                        .permitAll().anyRequest().permitAll())
                                        .formLogin(Customizer.withDefaults())
                                        .logout((logout) -> logout.logoutUrl("/logout")
                                                        .logoutSuccessUrl("/login?logout")
                                                        .permitAll())
                                        .exceptionHandling((exceptions) -> exceptions
                                                        .accessDeniedPage("/login"));

                        return http.build();
                }

                @Bean
                public UserDetailsService userDetailsService() {
                        UserDetails user = User.withDefaultPasswordEncoder().username("michel")
                                        .password("t").roles("USER").build();

                        return new InMemoryUserDetailsManager(user);
                }
        }

        @Configuration
        @Profile("prod")
        public static class ProdSecurityConfig {

                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http.authorizeHttpRequests((requests) -> requests
                                        .requestMatchers("/css/**", "/retrospective/**").permitAll()
                                        .anyRequest().authenticated())
                                        .oauth2Login((oauth2) -> oauth2
                                                        .loginPage("/oauth2/authorization/keycloak")
                                                        .defaultSuccessUrl("/", true))
                                        .logout(Customizer.withDefaults());

                        return http.build();
                }
        }
}
