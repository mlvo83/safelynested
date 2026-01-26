package com.learning.learning.controller;

import com.learning.learning.dto.UserDto;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private CharityRepository charityRepository;

    // ========================================
    // USER MANAGEMENT
    // ========================================

    @GetMapping("/users")
    public String listUsers(Model model) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String showCreateUserForm(Model model) {
        model.addAttribute("userDto", new UserDto());
        model.addAttribute("roles", userService.getAllRoles());
        model.addAttribute("charities", charityRepository.findByIsActiveTrue());
        return "admin/user-form";
    }

    @PostMapping("/users/new")
    public String createUser(
            @Valid @ModelAttribute("userDto") UserDto userDto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", userService.getAllRoles());
            model.addAttribute("charities", charityRepository.findByIsActiveTrue());
            return "admin/user-form";
        }

        try {
            userService.createUser(userDto);
            redirectAttributes.addFlashAttribute("success",
                    "User created successfully!");
            return "redirect:/admin/users";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", userService.getAllRoles());
            model.addAttribute("charities", charityRepository.findByIsActiveTrue());
            return "admin/user-form";
        }
    }

    @GetMapping("/users/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model) {
        User user = userService.getUserById(id);

        UserDto userDto = new UserDto();
        userDto.setUsername(user.getUsername());
        userDto.setEmail(user.getEmail());
        userDto.setFirstName(user.getFirstName());
        userDto.setLastName(user.getLastName());
        if (user.getCharity() != null) {
            userDto.setCharityId(user.getCharity().getId());
        }
        if (!user.getRoles().isEmpty()) {
            userDto.setRole(user.getRoles().iterator().next().getName());
        }

        model.addAttribute("userDto", userDto);
        model.addAttribute("userId", id);
        model.addAttribute("roles", userService.getAllRoles());
        model.addAttribute("charities", charityRepository.findByIsActiveTrue());
        model.addAttribute("isEdit", true);
        return "admin/user-form";
    }

    @PostMapping("/users/edit/{id}")
    public String updateUser(
            @PathVariable Long id,
            @Valid @ModelAttribute("userDto") UserDto userDto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("userId", id);
            model.addAttribute("roles", userService.getAllRoles());
            model.addAttribute("charities", charityRepository.findByIsActiveTrue());
            model.addAttribute("isEdit", true);
            return "admin/user-form";
        }

        try {
            userService.updateUser(id, userDto);
            redirectAttributes.addFlashAttribute("success", "User updated successfully!");
            return "redirect:/admin/users";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("userId", id);
            model.addAttribute("roles", userService.getAllRoles());
            model.addAttribute("charities", charityRepository.findByIsActiveTrue());
            model.addAttribute("isEdit", true);
            return "admin/user-form";
        }
    }

    @PostMapping("/users/delete/{id}")
    public String deleteUser(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success",
                    "User disabled successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error disabling user: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/users/reactivate/{id}")
    public String reactivateUser(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            userService.reactivateUser(id);
            redirectAttributes.addFlashAttribute("success",
                    "User reactivated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error reactivating user: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    // ========================================
    // CHARITY MANAGEMENT
    // ========================================

    @GetMapping("/charities")
    public String listCharities(Model model) {
        List<Charity> charities = charityRepository.findAll();
        model.addAttribute("charities", charities);
        return "admin/charities";
    }

    @GetMapping("/charities/new")
    public String showCreateCharityForm(Model model) {
        model.addAttribute("charity", new Charity());
        return "admin/charity-form";
    }

    @PostMapping("/charities/new")
    public String createCharity(
            @ModelAttribute Charity charity,
            RedirectAttributes redirectAttributes) {

        try {
            // Check for duplicate name
            if (charityRepository.findByCharityName(charity.getCharityName()).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Charity name already exists");
                return "redirect:/admin/charities/new";
            }

            // Check for duplicate EIN
            if (charity.getEinTaxId() != null && !charity.getEinTaxId().isEmpty()) {
                if (charityRepository.findByEinTaxId(charity.getEinTaxId()).isPresent()) {
                    redirectAttributes.addFlashAttribute("error", "EIN/Tax ID already exists");
                    return "redirect:/admin/charities/new";
                }
            }

            charityRepository.save(charity);
            redirectAttributes.addFlashAttribute("success", "Charity created successfully!");
            return "redirect:/admin/charities";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating charity: " + e.getMessage());
            return "redirect:/admin/charities/new";
        }
    }

    @GetMapping("/charities/edit/{id}")
    public String showEditCharityForm(@PathVariable Long id, Model model) {
        Charity charity = charityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charity not found"));
        model.addAttribute("charity", charity);
        model.addAttribute("isEdit", true);
        return "admin/charity-form";
    }

    @PostMapping("/charities/edit/{id}")
    public String updateCharity(
            @PathVariable Long id,
            @ModelAttribute Charity updatedCharity,
            RedirectAttributes redirectAttributes) {

        try {
            Charity existingCharity = charityRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Charity not found"));

            // Check for duplicate name (excluding current)
            charityRepository.findByCharityName(updatedCharity.getCharityName())
                    .ifPresent(c -> {
                        if (!c.getId().equals(id)) {
                            throw new RuntimeException("Charity name already exists");
                        }
                    });

            // Update fields
            existingCharity.setCharityName(updatedCharity.getCharityName());
            existingCharity.setOrganizationType(updatedCharity.getOrganizationType());
            existingCharity.setEinTaxId(updatedCharity.getEinTaxId());
            existingCharity.setContactName(updatedCharity.getContactName());
            existingCharity.setContactEmail(updatedCharity.getContactEmail());
            existingCharity.setContactPhone(updatedCharity.getContactPhone());
            existingCharity.setAddress(updatedCharity.getAddress());
            existingCharity.setCity(updatedCharity.getCity());
            existingCharity.setState(updatedCharity.getState());
            existingCharity.setZipCode(updatedCharity.getZipCode());
            existingCharity.setDescription(updatedCharity.getDescription());
            existingCharity.setMissionStatement(updatedCharity.getMissionStatement());
            existingCharity.setMaxReferralsPerMonth(updatedCharity.getMaxReferralsPerMonth());
            existingCharity.setAllowedZipCodes(updatedCharity.getAllowedZipCodes());
            existingCharity.setIsActive(updatedCharity.getIsActive());

            charityRepository.save(existingCharity);
            redirectAttributes.addFlashAttribute("success", "Charity updated successfully!");
            return "redirect:/admin/charities";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating charity: " + e.getMessage());
            return "redirect:/admin/charities/edit/" + id;
        }
    }

    @PostMapping("/charities/verify/{id}")
    public String verifyCharity(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            Charity charity = charityRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Charity not found"));

            charity.setIsVerified(true);
            charity.setVerifiedAt(java.time.LocalDateTime.now());
            charityRepository.save(charity);

            redirectAttributes.addFlashAttribute("success", "Charity verified successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error verifying charity: " + e.getMessage());
        }

        return "redirect:/admin/charities";
    }

    @PostMapping("/charities/toggle-active/{id}")
    public String toggleCharityActive(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            Charity charity = charityRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Charity not found"));

            charity.setIsActive(!charity.getIsActive());
            charityRepository.save(charity);

            String status = charity.getIsActive() ? "activated" : "deactivated";
            redirectAttributes.addFlashAttribute("success", "Charity " + status + " successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating charity: " + e.getMessage());
        }

        return "redirect:/admin/charities";
    }

    @PostMapping("/charities/delete/{id}")
    public String deleteCharity(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            charityRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Charity deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting charity: " + e.getMessage());
        }

        return "redirect:/admin/charities";
    }
}