package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.SecurityGroupDto;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.SecurityGroupService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityGroupControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SecurityGroupService securityGroupService;

    @InjectMocks
    private SecurityGroupController securityGroupController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(securityGroupController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_return201_when_createGroupSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        SecurityGroupDto created = SecurityGroupDto.builder()
                .id(groupId)
                .tenantId(tenantId)
                .name("Admins")
                .description("Administrator group")
                .accessLevel("ADMIN")
                .createdAt(Instant.now())
                .build();
        when(securityGroupService.createGroup(tenantId, "Admins", "Administrator group", "ADMIN"))
                .thenReturn(created);

        SecurityGroupDto request = SecurityGroupDto.builder()
                .tenantId(tenantId)
                .name("Admins")
                .description("Administrator group")
                .accessLevel("ADMIN")
                .build();

        mockMvc.perform(post("/api/security-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Admins"))
                .andExpect(jsonPath("$.data.accessLevel").value("ADMIN"));
    }

    @Test
    void should_returnGroups_when_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SecurityGroupDto g1 = SecurityGroupDto.builder().id(UUID.randomUUID()).name("Admins").build();
        SecurityGroupDto g2 = SecurityGroupDto.builder().id(UUID.randomUUID()).name("Developers").build();
        when(securityGroupService.listGroups(tenantId)).thenReturn(List.of(g1, g2));

        mockMvc.perform(get("/api/security-groups")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnGroup_when_getById() throws Exception {
        UUID groupId = UUID.randomUUID();
        SecurityGroupDto group = SecurityGroupDto.builder()
                .id(groupId)
                .name("Admins")
                .accessLevel("ADMIN")
                .build();
        when(securityGroupService.getGroup(groupId)).thenReturn(group);

        mockMvc.perform(get("/api/security-groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Admins"));
    }

    @Test
    void should_return404_when_groupNotFound() throws Exception {
        UUID groupId = UUID.randomUUID();
        when(securityGroupService.getGroup(groupId)).thenThrow(new ResourceNotFoundException("SecurityGroup", "id", groupId));

        mockMvc.perform(get("/api/security-groups/{id}", groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_returnUpdatedGroup_when_updateSuccessful() throws Exception {
        UUID groupId = UUID.randomUUID();
        SecurityGroupDto updated = SecurityGroupDto.builder()
                .id(groupId)
                .name("Super Admins")
                .description("Updated description")
                .accessLevel("ADMIN")
                .build();
        when(securityGroupService.updateGroup(groupId, "Super Admins", "Updated description", "ADMIN"))
                .thenReturn(updated);

        SecurityGroupDto request = SecurityGroupDto.builder()
                .name("Super Admins")
                .description("Updated description")
                .accessLevel("ADMIN")
                .build();

        mockMvc.perform(put("/api/security-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Super Admins"));
    }

    @Test
    void should_return200_when_deleteGroupSuccessful() throws Exception {
        UUID groupId = UUID.randomUUID();
        doNothing().when(securityGroupService).deleteGroup(groupId);

        mockMvc.perform(delete("/api/security-groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(securityGroupService).deleteGroup(groupId);
    }

    @Test
    void should_return201_when_addMemberSuccessful() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SecurityGroupMember member = SecurityGroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .memberType("USER")
                .memberId(memberId)
                .createdAt(Instant.now())
                .build();
        when(securityGroupService.addMember(groupId, "USER", memberId)).thenReturn(member);

        mockMvc.perform(post("/api/security-groups/{id}/members", groupId)
                        .param("memberType", "USER")
                        .param("memberId", memberId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberType").value("USER"))
                .andExpect(jsonPath("$.data.memberId").value(memberId.toString()));
    }

    @Test
    void should_return200_when_removeMemberSuccessful() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doNothing().when(securityGroupService).removeMember(groupId, "USER", memberId);

        mockMvc.perform(delete("/api/security-groups/{id}/members/{memberType}/{memberId}",
                        groupId, "USER", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(securityGroupService).removeMember(groupId, "USER", memberId);
    }

    @Test
    void should_returnMembers_when_getMembersSuccessful() throws Exception {
        UUID groupId = UUID.randomUUID();
        SecurityGroupMember m1 = SecurityGroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .memberType("USER")
                .memberId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        SecurityGroupMember m2 = SecurityGroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .memberType("TEAM")
                .memberId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        when(securityGroupService.getMembers(groupId)).thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/api/security-groups/{id}/members", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].memberType").value("USER"))
                .andExpect(jsonPath("$.data[1].memberType").value("TEAM"));
    }
}
