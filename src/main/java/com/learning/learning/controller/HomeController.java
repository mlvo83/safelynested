package com.learning.learning.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String landingPage() {
        return "index";
    }

    @GetMapping("/home")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("username", username);
        model.addAttribute("isAdmin", isAdmin);

        return "home";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}