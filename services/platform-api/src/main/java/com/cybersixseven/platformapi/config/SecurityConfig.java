package com.cybersixseven.platformapi.config;

import com.cybersixseven.platformapi.service.OauthLoginSuccessHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String[] allowedOrigins;
    private final String internalApiKey;
    private final boolean cookieSecure;
    private final boolean productEnabled;
    private final OauthLoginSuccessHandler oauthLoginSuccessHandler;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(
            @Value("${app.cors.allowed-origins}") String[] allowedOrigins,
            @Value("${app.internal-api-key}") String internalApiKey,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure,
            @Value("${app.product-enabled}") boolean productEnabled,
            OauthLoginSuccessHandler oauthLoginSuccessHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.allowedOrigins = allowedOrigins;
        this.internalApiKey = internalApiKey;
        this.cookieSecure = cookieSecure;
        this.productEnabled = productEnabled;
        this.oauthLoginSuccessHandler = oauthLoginSuccessHandler;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(internalApiKey);
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain oauthSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth.successHandler(oauthLoginSuccessHandler));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieName("XSRF-TOKEN");
        csrfTokens.setHeaderName("X-XSRF-TOKEN");
        csrfTokens.setCookieCustomizer(cookie -> cookie.httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/"));
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/questions/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/submissions/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/robots/**")))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterAfter(new CsrfCookieRequestHandlerFilter(), CsrfFilter.class)
                .addFilterAfter(new ProductAvailabilityFilter(productEnabled), CsrfFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            String code = ex instanceof CsrfException ? "CSRF_DENIED" : "FORBIDDEN";
                            String message = ex instanceof CsrfException
                                    ? "csrf token required"
                                    : "forbidden";
                            response.getWriter()
                                    .write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
                        }))
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/csrf")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/{id}")
                        .permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/oauth/exchange",
                                "/api/auth/oauth/fixture",
                                "/api/auth/oauth/fixture/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/oauth2/**", config);
        source.registerCorsConfiguration("/login/oauth2/**", config);
        return source;
    }

    static final class CsrfCookieRequestHandlerFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrf != null) {
                csrf.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
