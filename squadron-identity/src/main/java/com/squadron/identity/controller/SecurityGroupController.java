package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.SecurityGroupDto;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.service.SecurityGroupService;
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

/**
 * REST controller for security group management.
 */
@RestController
@RequestMapping("/api/security-groups")
@RequiredArgsConstructor
public class SecurityGroupController {

    private final SecurityGroupService securityGroupService;

    @PostMapping
    public ResponseEntity<ApiResponse<SecurityGroupDto>> createGroup(@Valid @RequestBody SecurityGroupDto dto) {
        SecurityGroupDto created = securityGroupService.createGroup(
                dto.getTenantId(), dto.getName(), dto.getDescription(), dto.getAccessLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SecurityGroupDto>>> listGroups(@RequestParam UUID tenantId) {
        List<SecurityGroupDto> groups = securityGroupService.listGroups(tenantId);
        return ResponseEntity.ok(ApiResponse.success(groups));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SecurityGroupDto>> getGroup(@PathVariable UUID id) {
        SecurityGroupDto group = securityGroupService.getGroup(id);
        return ResponseEntity.ok(ApiResponse.success(group));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SecurityGroupDto>> updateGroup(@PathVariable UUID id,
                                                                      @Valid @RequestBody SecurityGroupDto dto) {
        SecurityGroupDto updated = securityGroupService.updateGroup(id, dto.getName(), dto.getDescription(), dto.getAccessLevel());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable UUID id) {
        securityGroupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<SecurityGroupMember>> addMember(
            @PathVariable UUID id,
            @RequestParam String memberType,
            @RequestParam UUID memberId) {
        SecurityGroupMember member = securityGroupService.addMember(id, memberType, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(member));
    }

    @DeleteMapping("/{id}/members/{memberType}/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID id,
            @PathVariable String memberType,
            @PathVariable UUID memberId) {
        securityGroupService.removeMember(id, memberType, memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<SecurityGroupMember>>> getMembers(@PathVariable UUID id) {
        List<SecurityGroupMember> members = securityGroupService.getMembers(id);
        return ResponseEntity.ok(ApiResponse.success(members));
    }
}
