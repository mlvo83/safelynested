package com.learning.learning.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private PasswordChangeInterceptor passwordChangeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // the change-password page itself and the way out
                        "/change-password",
                        "/logout",
                        "/login",
                        // static assets + framework error pages
                        "/css/**", "/js/**", "/images/**", "/static/**",
                        "/error", "/access-denied",
                        "/*.png", "/*.jpg", "/*.jpeg", "/*.ico",
                        // API / webhooks and public (unauthenticated) routes — never gated
                        "/api/**",
                        "/charity-application/**", "/stay-partner/**",
                        "/referral/**", "/invite/**",
                        "/location-partner/register/**", "/help/**"
                );
    }
}
