package com.learning.learning.config;

import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Redirects any authenticated user whose {@code mustChangePassword} flag is set to
 * {@code /change-password} and blocks everything else until they change it. Registered in
 * {@link WebConfig} with an allowlist (the change-password page, logout, static assets, and
 * the public/unauthenticated routes) so it can't cause a redirect loop.
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user != null && user.isMustChangePassword()) {
            response.sendRedirect(request.getContextPath() + "/change-password");
            return false;
        }
        return true;
    }
}
