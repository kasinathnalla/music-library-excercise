package com.kasi.musiclibrary.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The authorization matrix, in one place.
 *
 * <p>Rules live here rather than as method security on the domain services for two reasons.
 * They are URL-and-method rules, so the whole boundary can be read and diffed in one screen.
 * And {@code SeedRunner} ingests the bundled tracks at boot with no authentication present --
 * an annotation on {@code IngestService} would refuse it, and would push an HTTP concern into
 * a package that AGENTS.md says knows nothing about HTTP.
 *
 * <p>The cost of that choice: a new controller that forgets to add a matcher here is reachable
 * by any signed-in user. It is never reachable anonymously, because {@code anyRequest()} is
 * the last rule.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Strength 10 matches the hashes seeded in V3__users.sql. Raising it means
        // regenerating them.
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contexts)
            throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        return http
                .authorizeHttpRequests(auth -> auth
                        // Open on purpose: the reviewer can still read the API without an
                        // account (UC-7), and the health endpoint is infrastructure.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // The SPA shell has to be servable to someone who has no credentials
                        // yet, or the login screen itself would 401.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                                "/*.js", "/*.css", "/assets/**", "/media/**").permitAll()

                        // Self-registration is deliberately open: signing up is how an account
                        // is meant to come into being for anyone but an admin. It still goes
                        // through CSRF like any other POST -- see the filter chain below.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()

                        // Curating the library is an admin act. Editing is included because a
                        // hand edit is recorded in field_edit and thereby becomes off limits to
                        // future automated enrichment (D10) -- a library-wide consequence that
                        // a listener should not be able to cause.
                        .requestMatchers(HttpMethod.POST, "/api/tracks").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/tracks/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tracks/*").hasRole("ADMIN")

                        // Everything else -- browsing, searching, streaming, stats -- needs an
                        // account but not a particular one.
                        .anyRequest().authenticated())

                // Basic credentials authenticate, and the resulting context is written to the
                // session. That is what makes playback work: the browser's <audio> element
                // issues its own Range requests and there is no way to put an Authorization
                // header on them, so the session cookie is what authenticates them.
                //
                // Since Spring Security 6 the Basic filter defaults to a request-scoped
                // context repository, so this is not the default and has to be said.
                .securityContext(sc -> sc.securityContextRepository(contexts))
                .httpBasic(basic -> basic.securityContextRepository(contexts))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // A 401 carrying WWW-Authenticate makes the browser pop its own credential
                // dialog over the SPA, which cannot then be dismissed or signed out of. A bare
                // 401 lets the SPA own the login experience. curl -u still works, because it
                // sends the header preemptively.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // A session cookie is an ambient credential, so CSRF protection is required.
                // withHttpOnlyFalse lets Angular read the XSRF-TOKEN cookie and echo it as
                // X-XSRF-TOKEN, which is exactly what Spring expects -- do not rename either
                // side. The plain request handler (rather than the XOR default) is what makes
                // the value Angular sends back acceptable.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    /**
     * Forces the CSRF token to be rendered, and therefore the cookie to be written.
     *
     * <p>Without this the token is deferred until something reads it, so the first mutating
     * request after signing in fails with 403 and the second succeeds -- a maddening bug to
     * chase, and the first thing to suspect if an upload starts returning 403.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
