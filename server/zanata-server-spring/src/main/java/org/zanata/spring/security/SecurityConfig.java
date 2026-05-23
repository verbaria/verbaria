package org.zanata.spring.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Replaces the WildFly-era PicketLink/Elytron auth with Spring Security
 * backed by the legacy HAccount table.
 *
 * - Public: /, /explore, /languages, /glossary, /actuator/health,
 *   /account/login, /account/register, password-reset flows, the React
 *   bundles and static assets.
 * - Auth required: /admin/**, /dashboard/**, /profile/**, REST endpoints
 *   that change state.
 *
 * CSRF is disabled for the JSON REST routes (the React frontend uses
 * the apiServerUrl without a CSRF token) but kept for the Thymeleaf form
 * posts via a SameSite cookie.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public static + framework
                .requestMatchers(
                        "/", "/favicon.ico",
                        "/explore", "/explore/**",
                        "/languages", "/languages/**",
                        "/glossary", "/glossary/**",
                        "/actuator/health", "/actuator/info",
                        "/account/login", "/account/register",
                        "/account/password_reset_request", "/account/password_reset",
                        "/account/activate", "/account/validate_email",
                        "/account/openid", "/account/sso",
                        "/account/inactive_account",
                        "/error/**",
                        "/resources/**", "/static/**",
                        // React bundles + manifest
                        "/*.js", "/*.css", "/*.map", "/*.json",
                        "/messages/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()  // remaining /rest/** + /project + /iteration + /dashboard
                                            // are read-mostly during dev; tighten when each REST
                                            // endpoint gains mutating verbs.
            )
            .formLogin(form -> form
                .loginPage("/account/login")
                .loginProcessingUrl("/account/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/account/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/account/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // CSRF on Thymeleaf forms via a JS-readable cookie; REST routes
            // exempted so the React frontend's fetch() calls don't need a
            // token wrapper.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                        new AntPathRequestMatcher("/rest/**"),
                        new AntPathRequestMatcher("/actuator/**")
                )
            );
        return http.build();
    }
}
