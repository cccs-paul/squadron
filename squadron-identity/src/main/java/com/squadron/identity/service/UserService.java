package com.squadron.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.TeamDto;
import com.squadron.common.dto.UserDto;
import com.squadron.identity.entity.User;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.entity.UserTeamId;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.UserRepository;
import com.squadron.identity.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserTeamRepository userTeamRepository;
    private final ObjectMapper objectMapper;

    public UserDto createUser(UserDto dto) {
        User user = User.builder()
                .tenantId(dto.getTenantId())
                .externalId(dto.getExternalId())
                .email(dto.getEmail())
                .displayName(dto.getDisplayName())
                .role(dto.getRole() != null ? dto.getRole() : "DEVELOPER")
                .authProvider(dto.getAuthProvider() != null ? dto.getAuthProvider() : "ldap")
                .roles(dto.getRoles())
                .settings(dto.getSettings() != null ? dto.getSettings().toString() : null)
                .build();
        User saved = userRepository.save(user);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUserByExternalId(String externalId) {
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "externalId", externalId));
        return toDto(user);
    }

    /**
     * @deprecated Use {@link #getUserByExternalId(String)} instead.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public UserDto getUserByKeycloakId(String keycloakId) {
        return getUserByExternalId(keycloakId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsersByTenant(UUID tenantId) {
        return userRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public UserDto updateUser(UUID id, UserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getDisplayName() != null) {
            user.setDisplayName(dto.getDisplayName());
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getAuthProvider() != null) {
            user.setAuthProvider(dto.getAuthProvider());
        }
        if (dto.getRoles() != null) {
            user.setRoles(dto.getRoles());
        }
        if (dto.getSettings() != null) {
            user.setSettings(dto.getSettings().toString());
        }

        User saved = userRepository.save(user);
        return toDto(saved);
    }

    public void addUserToTeam(UUID userId, UUID teamId, String role) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        UserTeam userTeam = UserTeam.builder()
                .userId(userId)
                .teamId(teamId)
                .role(role != null ? role : "MEMBER")
                .build();
        userTeamRepository.save(userTeam);
    }

    public void removeUserFromTeam(UUID userId, UUID teamId) {
        UserTeamId id = new UserTeamId(userId, teamId);
        if (!userTeamRepository.existsById(id)) {
            throw new ResourceNotFoundException("UserTeam", "userId/teamId", userId + "/" + teamId);
        }
        userTeamRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getUserTeams(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        return userTeamRepository.findByUserId(userId).stream()
                .map(ut -> TeamDto.builder()
                        .id(ut.getTeamId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserPreferences(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return parseSettings(user.getSettings());
    }

    public Map<String, Object> updateUserPreferences(UUID userId, Map<String, Object> preferences) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Map<String, Object> existingSettings = parseSettings(user.getSettings());
        existingSettings.putAll(preferences);

        try {
            user.setSettings(objectMapper.writeValueAsString(existingSettings));
        } catch (Exception e) {
            log.error("Failed to serialize user preferences for userId={}", userId, e);
            throw new IllegalStateException("Failed to serialize user preferences", e);
        }
        userRepository.save(user);
        return existingSettings;
    }

    private Map<String, Object> parseSettings(String settings) {
        if (settings == null || settings.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(settings, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse user settings JSON, returning empty map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private UserDto toDto(User user) {
        Set<String> roles = user.getRoles();
        return UserDto.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .externalId(user.getExternalId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
