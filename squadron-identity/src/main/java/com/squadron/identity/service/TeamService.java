package com.squadron.identity.service;

import com.squadron.common.dto.TeamDto;
import com.squadron.identity.entity.Team;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamDto createTeam(UUID tenantId, TeamDto dto) {
        Team team = Team.builder()
                .tenantId(tenantId)
                .name(dto.getName())
                .settings(dto.getSettings() != null ? dto.getSettings().toString() : null)
                .build();
        Team saved = teamRepository.save(team);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TeamDto getTeam(UUID id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", id));
        return toDto(team);
    }

    @Transactional(readOnly = true)
    public List<TeamDto> listTeamsByTenant(UUID tenantId) {
        return teamRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TeamDto updateTeam(UUID id, TeamDto dto) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", id));

        if (dto.getName() != null) {
            team.setName(dto.getName());
        }
        if (dto.getSettings() != null) {
            team.setSettings(dto.getSettings().toString());
        }

        Team saved = teamRepository.save(team);
        return toDto(saved);
    }

    public void deleteTeam(UUID id) {
        if (!teamRepository.existsById(id)) {
            throw new ResourceNotFoundException("Team", "id", id);
        }
        teamRepository.deleteById(id);
    }

    private TeamDto toDto(Team team) {
        return TeamDto.builder()
                .id(team.getId())
                .tenantId(team.getTenantId())
                .name(team.getName())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }
}
