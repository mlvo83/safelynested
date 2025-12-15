package com.learning.learning.controller;




import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Debug Controller - Use this to test password validation
 *
 * USAGE:
 * 1. Start your application
 * 2. Visit: http://localhost:8080/debug/test-password?username=admin&password=admin123
 * 3. This will show you if the password matches
 *
 * REMOVE THIS CLASS IN PRODUCTION!
 */
@RestController
public class DebugController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Test if a password matches the one in database
     * URL: http://localhost:8080/debug/test-password?username=admin&password=admin123
     */
    @GetMapping("/debug/test-password")
    public String testPassword(
            @RequestParam String username,
            @RequestParam String password) {

        StringBuilder result = new StringBuilder();
        result.append("<html><body style='font-family: monospace;'>");
        result.append("<h2>Password Validation Debug</h2>");
        result.append("<hr>");

        // Check if user exists
        result.append("<h3>Step 1: Check if user exists</h3>");
        var userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            result.append("<p style='color: red;'>❌ User NOT found in database: " + username + "</p>");
            result.append("<p>Available users:</p><ul>");
            userRepository.findAll().forEach(u ->
                    result.append("<li>" + u.getUsername() + "</li>")
            );
            result.append("</ul>");
            result.append("</body></html>");
            return result.toString();
        }

        User user = userOptional.get();
        result.append("<p style='color: green;'>✅ User found: " + user.getUsername() + "</p>");

        // Show password hash
        result.append("<h3>Step 2: Password hash from database</h3>");
        result.append("<p>Hash: <code>" + user.getPassword() + "</code></p>");
        result.append("<p>Hash starts with: <code>" + user.getPassword().substring(0, 10) + "...</code></p>");

        if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
            result.append("<p style='color: red;'>⚠️ WARNING: This doesn't look like a BCrypt hash!</p>");
            result.append("<p>BCrypt hashes should start with $2a$ or $2b$</p>");
        }

        // Test password
        result.append("<h3>Step 3: Test password matching</h3>");
        result.append("<p>Testing password: <code>" + password + "</code></p>");

        boolean matches = passwordEncoder.matches(password, user.getPassword());

        if (matches) {
            result.append("<p style='color: green; font-size: 20px;'>✅ PASSWORD MATCHES!</p>");
            result.append("<p>The password is correct. Login should work with:</p>");
            result.append("<ul>");
            result.append("<li>Username: <b>" + username + "</b></li>");
            result.append("<li>Password: <b>" + password + "</b></li>");
            result.append("</ul>");
        } else {
            result.append("<p style='color: red; font-size: 20px;'>❌ PASSWORD DOES NOT MATCH!</p>");
            result.append("<p><b>Troubleshooting:</b></p>");
            result.append("<ol>");
            result.append("<li>Make sure you're using the correct password</li>");
            result.append("<li>Check if the hash in database is correct</li>");
            result.append("<li>Try updating the password in database:</li>");
            result.append("</ol>");
            result.append("<pre style='background: #f0f0f0; padding: 10px;'>");
            result.append("UPDATE users \n");
            result.append("SET password = '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a' \n");
            result.append("WHERE username = '" + username + "';");
            result.append("</pre>");
        }

        // Show user roles
        result.append("<h3>Step 4: User roles</h3>");
        result.append("<ul>");
        user.getRoles().forEach(role ->
                result.append("<li>" + role.getName() + "</li>")
        );
        result.append("</ul>");

        // Show enabled status
        result.append("<h3>Step 5: User status</h3>");
        result.append("<p>Enabled: " + (user.isEnabled() ? "✅ Yes" : "❌ No") + "</p>");

        // Generate new hash
        result.append("<h3>Step 6: Generate new hash (optional)</h3>");
        String newHash = passwordEncoder.encode(password);
        result.append("<p>New BCrypt hash for '" + password + "':</p>");
        result.append("<pre style='background: #f0f0f0; padding: 10px;'>" + newHash + "</pre>");
        result.append("<p>SQL to update:</p>");
        result.append("<pre style='background: #f0f0f0; padding: 10px;'>");
        result.append("UPDATE users SET password = '" + newHash + "' WHERE username = '" + username + "';");
        result.append("</pre>");

        result.append("<hr>");
        result.append("<p><a href='/debug/test-password?username=admin&password=admin123'>Test admin/admin123</a></p>");
        result.append("<p><a href='/'>Go to Home</a> | <a href='/login'>Go to Login</a></p>");
        result.append("</body></html>");

        return result.toString();
    }

    /**
     * Generate a BCrypt hash for any password
     * URL: http://localhost:8080/debug/generate-hash?password=mypassword
     */
    @GetMapping("/debug/generate-hash")
    public String generateHash(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);

        StringBuilder result = new StringBuilder();
        result.append("<html><body style='font-family: monospace;'>");
        result.append("<h2>BCrypt Hash Generator</h2>");
        result.append("<hr>");
        result.append("<p>Password: <b>" + password + "</b></p>");
        result.append("<p>BCrypt Hash:</p>");
        result.append("<pre style='background: #f0f0f0; padding: 10px;'>" + hash + "</pre>");
        result.append("<p>SQL to update admin user:</p>");
        result.append("<pre style='background: #f0f0f0; padding: 10px;'>");
        result.append("UPDATE users SET password = '" + hash + "' WHERE username = 'admin';");
        result.append("</pre>");
        result.append("<hr>");
        result.append("<p><a href='/debug/generate-hash?password=admin123'>Generate for 'admin123'</a></p>");
        result.append("<p><a href='/debug/test-password?username=admin&password=" + password + "'>Test this password</a></p>");
        result.append("</body></html>");

        return result.toString();
    }

    /**
     * List all users in database
     * URL: http://localhost:8080/debug/list-users
     */
    @GetMapping("/debug/list-users")
    public String listUsers() {
        StringBuilder result = new StringBuilder();
        result.append("<html><body style='font-family: monospace;'>");
        result.append("<h2>All Users in Database</h2>");
        result.append("<hr>");
        result.append("<table border='1' cellpadding='10'>");
        result.append("<tr><th>ID</th><th>Username</th><th>Password Hash</th><th>Enabled</th><th>Roles</th></tr>");

        userRepository.findAll().forEach(user -> {
            result.append("<tr>");
            result.append("<td>" + user.getId() + "</td>");
            result.append("<td><b>" + user.getUsername() + "</b></td>");
            result.append("<td><code>" + user.getPassword().substring(0, 30) + "...</code></td>");
            result.append("<td>" + (user.isEnabled() ? "✅" : "❌") + "</td>");
            result.append("<td>");
            user.getRoles().forEach(role -> result.append(role.getName() + " "));
            result.append("</td>");
            result.append("</tr>");
        });

        result.append("</table>");
        result.append("<hr>");
        result.append("<p><a href='/debug/test-password?username=admin&password=admin123'>Test admin login</a></p>");
        result.append("</body></html>");

        return result.toString();
    }
}