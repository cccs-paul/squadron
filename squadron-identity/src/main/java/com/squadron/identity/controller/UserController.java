package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.TeamDto;
import com.squadron.common.dto.UserDto;
import com.squadron.identity.service.UserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody UserDto dto) {
        UserDto created = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID id) {
        UserDto user = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<UserDto>>> listUsersByTenant(@PathVariable UUID tenantId) {
        List<UserDto> users = userService.listUsersByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(@PathVariable UUID id,
                                                            @Valid @RequestBody UserDto dto) {
        UserDto updated = userService.updateUser(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{userId}/teams/{teamId}")
    public ResponseEntity<ApiResponse<Void>> addUserToTeam(@PathVariable UUID userId,
                                                            @PathVariable UUID teamId,
                                                            @RequestParam(defaultValue = "MEMBER") String role) {
        userService.addUserToTeam(userId, teamId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @DeleteMapping("/{userId}/teams/{teamId}")
    public ResponseEntity<ApiResponse<Void>> removeUserFromTeam(@PathVariable UUID userId,
                                                                 @PathVariable UUID teamId) {
        userService.removeUserFromTeam(userId, teamId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{userId}/teams")
    public ResponseEntity<ApiResponse<List<TeamDto>>> getUserTeams(@PathVariable UUID userId) {
        List<TeamDto> teams = userService.getUserTeams(userId);
        return ResponseEntity.ok(ApiResponse.success(teams));
    }
}
