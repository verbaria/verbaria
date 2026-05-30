package org.zanata.spring.security;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.LoginView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import javax.sql.DataSource;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${VERBARIA_REMEMBER_ME_KEY:verbaria-insecure-default-change-me}")
    private String rememberMeKey;

    @Value("${VERBARIA_REMEMBER_ME_DAYS:30}")
    private int rememberMeDays;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        // Table is created by Hibernate via the HPersistentLogin entity, so
        // we don't ask the repo to create it on startup.
        return repo;
    }

    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService uds,
                                                 PersistentTokenRepository tokenRepository) {
        PersistentTokenBasedRememberMeServices svc =
                new PersistentTokenBasedRememberMeServices(rememberMeKey, uds, tokenRepository);
        svc.setTokenValiditySeconds(rememberMeDays * 24 * 60 * 60);
        svc.setParameter("remember-me");
        svc.setCookieName("VERBARIA_REMEMBER_ME");
        return svc;
    }

    /**
     * Expose the AuthenticationManager so {@link org.zanata.spring.vaadin.LoginDialogService}
     * can authenticate users programmatically (popover sign-in flow). Without
     * this bean, Spring Security keeps the manager internal and Vaadin can't
     * call into it from a {@code LoginListener}.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds,
                                                       PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AccountRepository accountRepository,
                                                   RememberMeServices rememberMeServices) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    // Metrics may expose JVM/HTTP internals — admins only.
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                            .hasRole("ADMIN")
                    .requestMatchers(
                            "/actuator/health", "/actuator/info",
                            "/rest/**",
                            "/error/**",
                            "/resources/**", "/static/**",
                            "/messages/**",
                            "/line-awesome/**",
                            "/ace-builds/**"
                    ).permitAll())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                            PathPatternRequestMatcher.withDefaults().matcher("/rest/**"),
                            PathPatternRequestMatcher.withDefaults().matcher("/actuator/**")))
            .rememberMe(rm -> rm
                    .rememberMeServices(rememberMeServices)
                    .key(rememberMeKey))
            // Default Spring Security logout filter only matches POST /logout.
            // Our user menu navigates to /logout via GET (LoginDialogService
            // uses Page.setLocation), so we widen the matcher to accept GET
            // too. CSRF protection is still active on POST forms.
            .logout(logout -> logout
                    .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/logout"))
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "VERBARIA_REMEMBER_ME"))
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
