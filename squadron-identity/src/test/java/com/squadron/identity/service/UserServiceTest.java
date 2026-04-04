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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTeamRepository userTeamRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UUID tenantId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .displayName("Test User")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of("developer"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_createUser_when_validDto() {
        UserDto dto = UserDto.builder()
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .displayName("Test User")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of("developer"))
                .build();

        when(userRepository.save(any(User.class))).thenReturn(user);

        UserDto result = userService.createUser(dto);

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("DEVELOPER", result.getRole());
        assertEquals("ldap", result.getAuthProvider());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void should_createUserWithDefaults_when_nullRoleAndAuthProvider() {
        UserDto dto = UserDto.builder()
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .build();

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.createUser(dto);

        verify(userRepository).save(argThat(u ->
                "DEVELOPER".equals(u.getRole()) && "ldap".equals(u.getAuthProvider())));
    }

    @Test
    void should_getUser_when_userExists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDto result = userService.getUser(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void should_throwNotFound_when_userDoesNotExist() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUser(userId));
    }

    @Test
    void should_getUserByExternalId_when_exists() {
        when(userRepository.findByExternalId("ext-123")).thenReturn(Optional.of(user));

        UserDto result = userService.getUserByExternalId("ext-123");

        assertEquals("ext-123", result.getExternalId());
    }

    @Test
    void should_throwNotFound_when_externalIdNotFound() {
        when(userRepository.findByExternalId("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserByExternalId("missing"));
    }

    @Test
    void should_delegateToGetUserByExternalId_when_getUserByKeycloakIdCalled() {
        when(userRepository.findByExternalId("kc-123")).thenReturn(Optional.of(user));

        @SuppressWarnings("deprecation")
        UserDto result = userService.getUserByKeycloakId("kc-123");

        assertNotNull(result);
    }

    @Test
    void should_listUsersByTenant_when_called() {
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(user));

        List<UserDto> results = userService.listUsersByTenant(tenantId);

        assertEquals(1, results.size());
        assertEquals("test@example.com", results.get(0).getEmail());
    }

    @Test
    void should_updateUser_when_validDto() {
        UserDto dto = UserDto.builder()
                .email("new@example.com")
                .displayName("New Name")
                .role("ADMIN")
                .authProvider("keycloak")
                .roles(Set.of("admin"))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDto result = userService.updateUser(userId, dto);

        assertNotNull(result);
        verify(userRepository).save(user);
        assertEquals("new@example.com", user.getEmail());
        assertEquals("New Name", user.getDisplayName());
        assertEquals("ADMIN", user.getRole());
    }

    @Test
    void should_throwNotFound_when_updateNonExistentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(userId, new UserDto()));
    }

    @Test
    void should_addUserToTeam_when_userExists() {
        UUID teamId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.addUserToTeam(userId, teamId, "LEAD");

        verify(userTeamRepository).save(argThat(ut ->
                userId.equals(ut.getUserId()) && teamId.equals(ut.getTeamId()) && "LEAD".equals(ut.getRole())));
    }

    @Test
    void should_addUserToTeamWithDefaultRole_when_roleIsNull() {
        UUID teamId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.addUserToTeam(userId, teamId, null);

        verify(userTeamRepository).save(argThat(ut -> "MEMBER".equals(ut.getRole())));
    }

    @Test
    void should_throwNotFound_when_addingNonExistentUserToTeam() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                userService.addUserToTeam(userId, UUID.randomUUID(), "MEMBER"));
    }

    @Test
    void should_removeUserFromTeam_when_membershipExists() {
        UUID teamId = UUID.randomUUID();
        when(userTeamRepository.existsById(any(UserTeamId.class))).thenReturn(true);

        userService.removeUserFromTeam(userId, teamId);

        verify(userTeamRepository).deleteById(any(UserTeamId.class));
    }

    @Test
    void should_throwNotFound_when_removingNonExistentTeamMembership() {
        UUID teamId = UUID.randomUUID();
        when(userTeamRepository.existsById(any(UserTeamId.class))).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.removeUserFromTeam(userId, teamId));
    }

    @Test
    void should_getUserTeams_when_userExists() {
        UUID teamId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of(
                UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build()
        ));

        List<TeamDto> teams = userService.getUserTeams(userId);

        assertEquals(1, teams.size());
        assertEquals(teamId, teams.get(0).getId());
    }

    @Test
    void should_throwNotFound_when_getUserTeamsForNonExistentUser() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserTeams(userId));
    }

    // --- User Preferences Tests ---

    @Test
    void should_returnEmptyPreferences_when_settingsIsNull() throws Exception {
        User userWithNullSettings = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .settings(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithNullSettings));

        Map<String, Object> prefs = userService.getUserPreferences(userId);

        assertNotNull(prefs);
        assertTrue(prefs.isEmpty());
    }

    @Test
    void should_returnPreferences_when_settingsHasLanguage() throws Exception {
        User userWithSettings = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .settings("{\"language\":\"fr\"}")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSettings));
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("language", "fr");
        when(objectMapper.readValue(eq("{\"language\":\"fr\"}"), any(TypeReference.class))).thenReturn(parsed);

        Map<String, Object> prefs = userService.getUserPreferences(userId);

        assertNotNull(prefs);
        assertEquals("fr", prefs.get("language"));
    }

    @Test
    void should_throwNotFound_when_getUserPreferencesForNonExistentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserPreferences(userId));
    }

    @Test
    void should_mergeAndPersistPreferences_when_updateUserPreferences() throws Exception {
        User userWithSettings = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .settings("{\"theme\":\"dark\"}")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSettings));

        Map<String, Object> existing = new HashMap<>();
        existing.put("theme", "dark");
        when(objectMapper.readValue(eq("{\"theme\":\"dark\"}"), any(TypeReference.class))).thenReturn(existing);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"theme\":\"dark\",\"language\":\"fr\"}");
        when(userRepository.save(userWithSettings)).thenReturn(userWithSettings);

        Map<String, Object> result = userService.updateUserPreferences(userId, Map.of("language", "fr"));

        assertNotNull(result);
        assertEquals("dark", result.get("theme"));
        assertEquals("fr", result.get("language"));
        verify(userRepository).save(userWithSettings);
    }

    @Test
    void should_throwNotFound_when_updatePreferencesForNonExistentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                userService.updateUserPreferences(userId, Map.of("language", "en")));
    }

    @Test
    void should_handleMalformedSettings_when_getUserPreferences() throws Exception {
        User userWithBadSettings = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("test@example.com")
                .settings("not-valid-json")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithBadSettings));
        when(objectMapper.readValue(eq("not-valid-json"), any(TypeReference.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"));

        Map<String, Object> prefs = userService.getUserPreferences(userId);

        assertNotNull(prefs);
        assertTrue(prefs.isEmpty());
    }
}
