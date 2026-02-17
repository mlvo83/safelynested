package com.learning.learning.dto;




import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserDto {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    // No annotation - validated manually in service (required for create, optional for edit)
    private String password;

    @NotEmpty(message = "Please select at least one role")
    private List<String> roles = new ArrayList<>();

    // Additional fields for user profile
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;
    private String firstName;
    private String lastName;
    private String phone;

    // Charity assignment
    private Long charityId;
}
