package com.pricepilot.user.dto;

import com.pricepilot.user.Role;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;
    private boolean locked;
}
