package org.zanata.spring.security;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AccountRepository accountRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/health", "/actuator/info",
                            "/rest/**",
                            "/error/**",
                            "/resources/**", "/static/**",
                            "/messages/**",
                            "/line-awesome/**"
                    ).permitAll())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                            PathPatternRequestMatcher.withDefaults().matcher("/rest/**"),
                            PathPatternRequestMatcher.withDefaults().matcher("/actuator/**")))
            // CLI auth: X-Auth-User + X-Auth-Token headers populate the
            // SecurityContext before authorization runs, so the CLI bridge's
            // write endpoints can identify the caller.
            .addFilterBefore(new ApiKeyAuthenticationFilter(accountRepository),
                    UsernamePasswordAuthenticationFilter.class)
            .with(VaadinSecurityConfigurer.vaadin(), v -> v
                    .loginView(LoginView.class));
        return http.build();
    }
}
