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

import java.util.Collections;
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

        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setEnabled(true);
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhone(userDto.getPhone());

        // Set role
        Role role = roleRepository.findByName(userDto.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found: " + userDto.getRole()));
        user.setRoles(new HashSet<>(Collections.singletonList(role)));

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

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
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

        // Update role
        if (userDto.getRole() != null && !userDto.getRole().isEmpty()) {
            Role role = roleRepository.findByName(userDto.getRole())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + userDto.getRole()));
            user.setRoles(new HashSet<>(Collections.singletonList(role)));
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
