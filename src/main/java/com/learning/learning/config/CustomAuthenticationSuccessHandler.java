package com.learning.learning.config;



import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Custom Authentication Success Handler
 *
 * Routes users to different pages based on their role after successful login:
 * - ROLE_ADMIN              → /home
 * - ROLE_FACILITATOR        → /facilitator/dashboard
 * - ROLE_CHARITY_FACILITATOR → /charity-partner/dashboard (with link to facilitator functions)
 * - ROLE_CHARITY_PARTNER    → /charity-partner/dashboard
 * - ROLE_DONOR              → /donor/dashboard
 * - ROLE_LOCATION_ADMIN     → /location-admin/dashboard
 * - ROLE_USER               → /user/dashboard
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // Get the roles of the logged-in user
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        // Route based on role (priority order: admin > facilitator > charity-facilitator > charity-partner > donor > location-admin > user)
        String redirectUrl = "/home"; // Default fallback

        if (roles.contains("ROLE_ADMIN")) {
            redirectUrl = "/home";
        } else if (roles.contains("ROLE_FACILITATOR")) {
            redirectUrl = "/facilitator/dashboard";
        } else if (roles.contains("ROLE_CHARITY_FACILITATOR")) {
            redirectUrl = "/charity-partner/dashboard";
        } else if (roles.contains("ROLE_CHARITY_PARTNER")) {
            redirectUrl = "/charity-partner/dashboard";
        } else if (roles.contains("ROLE_DONOR")) {
            redirectUrl = "/donor/dashboard";
        } else if (roles.contains("ROLE_LOCATION_ADMIN")) {
            redirectUrl = "/location-admin/dashboard";
        } else if (roles.contains("ROLE_USER")) {
            redirectUrl = "/user/dashboard";
        }

        // Redirect to the appropriate page
        response.sendRedirect(redirectUrl);
    }
}
