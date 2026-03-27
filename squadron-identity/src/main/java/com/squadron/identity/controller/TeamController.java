package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.TeamDto;
import com.squadron.identity.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<ApiResponse<TeamDto>> createTeam(@Valid @RequestBody TeamDto dto) {
        TeamDto created = teamService.createTeam(dto.getTenantId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<TeamDto>>> listTeamsByTenant(@PathVariable UUID tenantId) {
        List<TeamDto> teams = teamService.listTeamsByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(teams));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamDto>> getTeam(@PathVariable UUID id) {
        TeamDto team = teamService.getTeam(id);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamDto>> updateTeam(@PathVariable UUID id,
                                                            @Valid @RequestBody TeamDto dto) {
        TeamDto updated = teamService.updateTeam(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable UUID id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
