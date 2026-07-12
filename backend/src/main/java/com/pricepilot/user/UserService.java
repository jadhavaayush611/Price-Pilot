package com.pricepilot.user;

import com.pricepilot.exception.EmailAlreadyExistsException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.user.dto.UserRequestDTO;
import com.pricepilot.user.dto.UserResponseDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDTO register(UserRequestDTO requestDTO) {
        if (requestDTO.getEmail() == null || requestDTO.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        String normalizedEmail = requestDTO.getEmail().trim().toLowerCase();

        // Regex for domain syntax verification (reject formats like abc@com, check for valid characters)
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        if (!normalizedEmail.matches(emailRegex)) {
            throw new IllegalArgumentException("Invalid email format");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email " + requestDTO.getEmail() + " is already in use");
        }

        UserEntity user = UserEntity.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(requestDTO.getPassword()))
                .firstName(requestDTO.getFirstName())
                .lastName(requestDTO.getLastName())
                .role(Role.USER) // Default role
                .enabled(true)
                .locked(false)
                .build();

        UserEntity savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    public UserResponseDTO getUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToResponse(user);
    }

    public UserResponseDTO getUserByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToResponse(user);
    }

    public UserResponseDTO mapToResponse(UserEntity user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .locked(user.isLocked())
                .build();
    }
}
