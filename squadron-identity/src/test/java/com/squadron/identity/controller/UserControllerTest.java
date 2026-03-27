package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.TeamDto;
import com.squadron.common.dto.UserDto;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_return201_when_createUserSuccessful() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserDto created = UserDto.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("user@example.com")
                .displayName("Test User")
                .authProvider("ldap")
                .roles(Set.of("USER"))
                .createdAt(Instant.now())
                .build();
        when(userService.createUser(any(UserDto.class))).thenReturn(created);

        UserDto request = UserDto.builder()
                .tenantId(tenantId)
                .email("user@example.com")
                .displayName("Test User")
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Test User"))
                .andExpect(jsonPath("$.data.authProvider").value("ldap"));
    }

    @Test
    void should_returnUser_when_getUserById() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDto user = UserDto.builder()
                .id(userId)
                .email("user@example.com")
                .displayName("Test User")
                .build();
        when(userService.getUser(userId)).thenReturn(user);

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void should_return404_when_userNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUser(userId)).thenThrow(new ResourceNotFoundException("User", "id", userId));

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_returnUsers_when_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserDto u1 = UserDto.builder().id(UUID.randomUUID()).email("a@example.com").build();
        UserDto u2 = UserDto.builder().id(UUID.randomUUID()).email("b@example.com").build();
        when(userService.listUsersByTenant(tenantId)).thenReturn(List.of(u1, u2));

        mockMvc.perform(get("/api/users/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnUpdatedUser_when_updateSuccessful() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDto updated = UserDto.builder()
                .id(userId)
                .email("updated@example.com")
                .displayName("Updated User")
                .build();
        when(userService.updateUser(eq(userId), any(UserDto.class))).thenReturn(updated);

        UserDto request = UserDto.builder()
                .email("updated@example.com")
                .displayName("Updated User")
                .build();

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"));
    }

    @Test
    void should_return201_when_addUserToTeam() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        doNothing().when(userService).addUserToTeam(userId, teamId, "LEAD");

        mockMvc.perform(post("/api/users/{userId}/teams/{teamId}", userId, teamId)
                        .param("role", "LEAD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).addUserToTeam(userId, teamId, "LEAD");
    }

    @Test
    void should_useDefaultRole_when_addUserToTeamWithoutRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        doNothing().when(userService).addUserToTeam(userId, teamId, "MEMBER");

        mockMvc.perform(post("/api/users/{userId}/teams/{teamId}", userId, teamId))
                .andExpect(status().isCreated());

        verify(userService).addUserToTeam(userId, teamId, "MEMBER");
    }

    @Test
    void should_return200_when_removeUserFromTeam() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        doNothing().when(userService).removeUserFromTeam(userId, teamId);

        mockMvc.perform(delete("/api/users/{userId}/teams/{teamId}", userId, teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).removeUserFromTeam(userId, teamId);
    }

    @Test
    void should_returnTeams_when_getUserTeams() throws Exception {
        UUID userId = UUID.randomUUID();
        TeamDto t1 = TeamDto.builder().id(UUID.randomUUID()).name("Alpha").build();
        TeamDto t2 = TeamDto.builder().id(UUID.randomUUID()).name("Beta").build();
        when(userService.getUserTeams(userId)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/users/{userId}/teams", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Alpha"));
    }
}
