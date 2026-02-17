package com.learning.learning.service;


import com.learning.learning.dto.UserDto;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Role;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.RoleRepository;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Add this field injection
    @Autowired
     private CharityRepository charityRepository;

    @Transactional
    public User createUser(UserDto userDto) {
        // Check if user already exists
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        // Password is required for new users
        if (userDto.getPassword() == null || userDto.getPassword().length() < 6) {
            throw new RuntimeException("Password is required and must be at least 6 characters");
        }

        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setEnabled(true);
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhone(userDto.getPhone());

        // Set roles
        HashSet<Role> userRoles = new HashSet<>();
        for (String roleName : userDto.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
            userRoles.add(role);
        }
        user.setRoles(userRoles);

        // Assign charity if provided
        if (userDto.getCharityId() != null) {
            Charity charity = charityRepository.findById(userDto.getCharityId())
                    .orElseThrow(() -> new RuntimeException("Charity not found"));
            user.setCharity(charity);
        }

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Get only active (non-deleted) users
     */
    public List<User> getActiveUsers() {
        return userRepository.findByEnabledTrue();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Soft delete a user by disabling their account.
     * This preserves referential integrity with related records (bookings, referrals, etc.)
     * while preventing the user from logging in.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Soft delete: disable the account instead of deleting
        user.setEnabled(false);
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Permanently delete a user. Use with caution - will fail if user has
     * related records in other tables (bookings, referrals, etc.)
     * For a clean database reset, use the DATABASE_RESET.sql script instead.
     */
    @Transactional
    public void hardDeleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Reactivate a previously disabled user account.
     */
    @Transactional
    public void reactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEnabled(true);
        user.setIsActive(true);
        userRepository.save(user);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

// ============================================================
// ADD this new method for updating users:
// ============================================================

    public User updateUser(Long userId, UserDto userDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update password only if provided
        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }

        // Update other fields
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhone(userDto.getPhone());

        // Update roles
        if (userDto.getRoles() != null && !userDto.getRoles().isEmpty()) {
            HashSet<Role> userRoles = new HashSet<>();
            for (String roleName : userDto.getRoles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
                userRoles.add(role);
            }
            user.setRoles(userRoles);
        }

        // Update charity assignment
        if (userDto.getCharityId() != null) {
            Charity charity = charityRepository.findById(userDto.getCharityId())
                    .orElseThrow(() -> new RuntimeException("Charity not found"));
            user.setCharity(charity);
        } else {
            user.setCharity(null); // Remove charity assignment
        }

        return userRepository.save(user);
    }


}
