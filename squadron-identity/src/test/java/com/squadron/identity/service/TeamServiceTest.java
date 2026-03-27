package com.squadron.identity.service;

import com.squadron.common.dto.TeamDto;
import com.squadron.identity.entity.Team;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamService teamService;

    private UUID teamId;
    private UUID tenantId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        team = Team.builder()
                .id(teamId)
                .tenantId(tenantId)
                .name("Dev Team")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_createTeam_when_validDto() {
        TeamDto dto = TeamDto.builder().name("Dev Team").build();
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        TeamDto result = teamService.createTeam(tenantId, dto);

        assertNotNull(result);
        assertEquals("Dev Team", result.getName());
        verify(teamRepository).save(any(Team.class));
    }

    @Test
    void should_getTeam_when_exists() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        TeamDto result = teamService.getTeam(teamId);

        assertEquals(teamId, result.getId());
        assertEquals("Dev Team", result.getName());
    }

    @Test
    void should_throwNotFound_when_teamDoesNotExist() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getTeam(teamId));
    }

    @Test
    void should_listTeamsByTenant_when_called() {
        when(teamRepository.findByTenantId(tenantId)).thenReturn(List.of(team));

        List<TeamDto> results = teamService.listTeamsByTenant(tenantId);

        assertEquals(1, results.size());
    }

    @Test
    void should_updateTeam_when_validDto() {
        TeamDto dto = TeamDto.builder().name("QA Team").build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);

        TeamDto result = teamService.updateTeam(teamId, dto);

        assertNotNull(result);
        assertEquals("QA Team", team.getName());
    }

    @Test
    void should_throwNotFound_when_updatingNonExistentTeam() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                teamService.updateTeam(teamId, new TeamDto()));
    }

    @Test
    void should_deleteTeam_when_exists() {
        when(teamRepository.existsById(teamId)).thenReturn(true);

        teamService.deleteTeam(teamId);

        verify(teamRepository).deleteById(teamId);
    }

    @Test
    void should_throwNotFound_when_deletingNonExistentTeam() {
        when(teamRepository.existsById(teamId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> teamService.deleteTeam(teamId));
    }
}
