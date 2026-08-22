package com.learning.learning.controller;

import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class ChangePasswordController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/change-password")
    public String showForm(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        model.addAttribute("forced", user != null && user.isMustChangePassword());
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam(required = false) String newPassword,
                                 @RequestParam(required = false) String confirmPassword,
                                 Principal principal,
                                 Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return reject(model, user, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return reject(model, user, "Passwords do not match.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Flag cleared — the interceptor now lets them through to a normal authenticated page.
        return "redirect:/home";
    }

    private String reject(Model model, User user, String error) {
        model.addAttribute("error", error);
        model.addAttribute("forced", user.isMustChangePassword());
        return "change-password";
    }
}
