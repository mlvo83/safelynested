package com.learning.learning.config;


import com.learning.learning.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Public resources
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/static/**").permitAll()
                        .requestMatchers("/*.jpeg", "/*.png", "/*.jpg", "/*.ico").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/terms").permitAll()
                        .requestMatchers("/debug/**").permitAll()
                        .requestMatchers("/demo/**").permitAll()
                        .requestMatchers("/invite/**").permitAll()
                        .requestMatchers("/referral/invite/**").permitAll()
                        .requestMatchers("/stay-partner/**").permitAll()
                        .requestMatchers("/charity-application/**").permitAll()
                        .requestMatchers("/location-partner/register/**").permitAll()
                        .requestMatchers("/help", "/help/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Public donate page disabled — donations are recorded by charity partners
                        // .requestMatchers("/donate/**").permitAll()
                        .requestMatchers("/api/stripe/**").permitAll()
                        // Role-based access control
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/facilitator/**").hasRole("FACILITATOR")
                        .requestMatchers("/charity-facilitator/**").hasAnyRole("CHARITY_FACILITATOR", "MULTI_FACILITATOR")
                        .requestMatchers("/multi-facilitator/**").hasRole("MULTI_FACILITATOR")
                        // Charity-scoped partner paths (numeric charity ID in URL): all three
                        // partner/facilitator roles allowed at the security layer; per-request
                        // authorization is enforced in the controller via canFacilitateForCharity.
                        .requestMatchers("/charity-partner/{cid:[0-9]+}/**").hasAnyRole("CHARITY_PARTNER", "CHARITY_FACILITATOR", "MULTI_FACILITATOR")
                        // Legacy /charity-partner/dashboard/{cid} kept for backward compat
                        // with the Phase 5 dashboard view; subsumed by the matcher above
                        // when {cid} is numeric, but still useful for clarity.
                        .requestMatchers("/charity-partner/dashboard/*").hasAnyRole("CHARITY_PARTNER", "CHARITY_FACILITATOR", "MULTI_FACILITATOR")
                        // Legacy un-scoped paths (single-charity users only)
                        .requestMatchers("/charity-partner/**").hasAnyRole("CHARITY_PARTNER", "CHARITY_FACILITATOR")
                        .requestMatchers("/donor/**").hasRole("DONOR")
                        .requestMatchers("/location-admin/**").hasAnyRole("ADMIN", "LOCATION_ADMIN")
                        .requestMatchers("/location-partner/**").hasRole("LOCATION_PARTNER")
                        .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN", "FACILITATOR", "CHARITY_PARTNER", "CHARITY_FACILITATOR", "MULTI_FACILITATOR", "LOCATION_ADMIN", "LOCATION_PARTNER", "DONOR")
                        // Dashboard requires authentication
                        .requestMatchers("/home").authenticated()



                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/stripe/webhook")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)  // ← Use custom success handler for role-based routing
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }
}
