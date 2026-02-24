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
                        .requestMatchers("/debug/**").permitAll()
                        .requestMatchers("/demo/**").permitAll()
                        .requestMatchers("/invite/**").permitAll()
                        .requestMatchers("/referral/invite/**").permitAll()
                        .requestMatchers("/stay-partner/**").permitAll()
                        // Role-based access control
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/facilitator/**").hasRole("FACILITATOR")
                        .requestMatchers("/charity-facilitator/**").hasRole("CHARITY_FACILITATOR")
                        .requestMatchers("/charity-partner/**").hasAnyRole("CHARITY_PARTNER", "CHARITY_FACILITATOR")
                        .requestMatchers("/donor/**").hasRole("DONOR")
                        .requestMatchers("/location-admin/**").hasAnyRole("ADMIN", "LOCATION_ADMIN")
                        .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN", "FACILITATOR", "CHARITY_PARTNER", "CHARITY_FACILITATOR", "LOCATION_ADMIN", "DONOR")
                        // Dashboard requires authentication
                        .requestMatchers("/home").authenticated()



                        // All other requests require authentication
                        .anyRequest().authenticated()
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
