package direct.reflect.facilitator.web.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;

import java.util.List;

/**
 * SPA fallback configuration: serves {@code index.html} for all unknown frontend routes.
 *
 * <p>Implemented as a {@link RouterFunction} registered via an explicit {@link RouterFunctionMapping}
 * at order {@code 2} — AFTER {@code RequestMappingHandlerMapping} (order 0) and any other
 * standard handler mappings. This ensures {@code @Controller} and {@code @RestController}
 * endpoints always win before the SPA catch-all is considered.
 *
 * <p>The bean is named {@code spaRouterFunctionMapping} (not {@code routerFunctionMapping}) so it
 * does NOT replace Spring Boot's auto-configured {@link RouterFunctionMapping} at order {@code -1}.
 * Both mappings coexist: Spring Boot's at {@code -1} (empty — no routes are registered there),
 * ours at {@code 2}.
 *
 * <p>Matching rules:
 * <ul>
 *   <li>HTTP method: GET only</li>
 *   <li>Accept: must include {@code text/html}</li>
 *   <li>Path: all paths EXCEPT the excluded prefixes below</li>
 * </ul>
 *
 * <p>Excluded paths — the router does NOT match these; other handlers win.
 * Entries ending with {@code /} are matched as prefixes ({@code startsWith});
 * all other entries are matched exactly.
 * <ul>
 *   <li>Group A — REST/SSE: {@code /api/}</li>
 *   <li>Group B — Auth/Security: {@code /auth/}, {@code /oauth2/}, {@code /login/oauth2/},
 *       {@code /logout} (exact)</li>
 *   <li>Group C — Error: {@code /error} (exact)</li>
 *   <li>Group D — Monitoring/Docs: {@code /actuator/}, {@code /v3/api-docs/}, {@code /swagger-ui/},
 *       {@code /swagger-ui.html} (exact)</li>
 *   <li>Group E — Static assets: {@code /assets/}, {@code /css/}, {@code /js/}, {@code /images/},
 *       {@code /img/}, {@code /static/}, {@code /webjars/},
 *       {@code /favicon.ico} (exact), {@code /favicon.svg} (exact),
 *       {@code /vite.svg} (exact), {@code /index.html} (exact)</li>
 *   <li>Group F — Test helpers: {@code /test/} (prevents SPA from intercepting {@code TestAuthController})</li>
 * </ul>
 *
 * <p>When the router does NOT match (excluded path or non-HTML Accept), the request falls through
 * to the next handler mapping — allowing {@code ResourceHttpRequestHandler} to serve static assets
 * and {@code RequestMappingHandlerMapping} to serve API controllers.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig {

    static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/",
            "/auth/",
            "/oauth2/",
            "/login/oauth2/",
            "/logout",
            "/error",
            "/actuator/",
            "/v3/api-docs/",
            "/swagger-ui/",
            "/swagger-ui.html",
            "/assets/",
            "/css/",
            "/js/",
            "/images/",
            "/img/",
            "/static/",
            "/webjars/",
            "/favicon.ico",
            "/favicon.svg",
            "/vite.svg",
            "/index.html",
            "/test/"
    );

    private static final Resource INDEX_HTML = new ClassPathResource("static/index.html");

    /**
     * RouterFunction that serves {@code index.html} for all frontend routes.
     *
     * <p>The predicate matches GET requests with an HTML Accept header whose path is
     * NOT in the excluded list. When the predicate returns {@code false}, the router
     * returns an empty {@code Optional} and Spring tries the next handler mapping.
     */
    @Bean
    public RouterFunction<ServerResponse> spaFallback() {
        RequestPredicate spaPredicate = RequestPredicates.GET("/**")
                .and(RequestPredicates.accept(MediaType.TEXT_HTML))
                .and(request -> isNotExcluded(request.path()));

        return RouterFunctions.route()
                .resource(spaPredicate, INDEX_HTML)
                .build();
    }

    /**
     * Explicit {@link RouterFunctionMapping} at order {@code 2}, ensuring it runs AFTER
     * {@code RequestMappingHandlerMapping} (order 0). This prevents the SPA fallback from
     * intercepting {@code @Controller}/{@code @RestController} endpoints that browsers
     * navigate to with an {@code Accept: text/html} header.
     *
     * <p>Named {@code spaRouterFunctionMapping} (not {@code routerFunctionMapping}) so it coexists
     * with Spring Boot's auto-configured {@code RouterFunctionMapping} at order {@code -1} rather
     * than replacing it. See class-level Javadoc for the full mapping-order explanation.
     */
    @Bean
    public RouterFunctionMapping spaRouterFunctionMapping(RouterFunction<ServerResponse> spaFallback) {
        RouterFunctionMapping mapping = new RouterFunctionMapping(spaFallback);
        mapping.setOrder(2);
        return mapping;
    }

    private static boolean isNotExcluded(String path) {
        for (String entry : EXCLUDED_PREFIXES) {
            if (entry.endsWith("/")) {
                // Prefix match: /api/ excludes /api/foo but not /apiary
                if (path.startsWith(entry) || path.equals(entry.substring(0, entry.length() - 1))) {
                    return false;
                }
            } else {
                // Exact match: /logout excludes /logout but not /logout-success
                if (path.equals(entry)) {
                    return false;
                }
            }
        }
        return true;
    }
}
