package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/preferences")
@CrossOrigin(origins = "*")
public class UserShoppingPreferenceController {

    private final UserShoppingPreferenceService preferenceService;

    public UserShoppingPreferenceController(UserShoppingPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<UserShoppingPreferenceDTO> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        validatePrincipal(principal);
        UserShoppingPreferenceDTO dto = preferenceService.getPreferences(principal.getId());
        return ResponseEntity.ok(dto);
    }

    @PutMapping
    public ResponseEntity<UserShoppingPreferenceDTO> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateShoppingPreferenceRequest request) {
        validatePrincipal(principal);
        UserShoppingPreferenceDTO dto = preferenceService.updatePreferences(principal.getId(), request);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping
    public ResponseEntity<Void> resetPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        validatePrincipal(principal);
        preferenceService.resetPreferences(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<UserShoppingPreferenceDTO> resetPreferencesPost(
            @AuthenticationPrincipal UserPrincipal principal) {
        validatePrincipal(principal);
        preferenceService.resetPreferences(principal.getId());
        UserShoppingPreferenceDTO dto = preferenceService.getPreferences(principal.getId());
        return ResponseEntity.ok(dto);
    }

    private void validatePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Authentication required to access user shopping preferences");
        }
    }
}
