package com.squadron.identity.service;

import com.squadron.common.dto.TenantDto;
import com.squadron.identity.entity.Tenant;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.TenantRepository;
import com.squadron.identity.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantDto createTenant(TenantDto dto) {
        Tenant tenant = Tenant.builder()
                .name(dto.getName())
                .slug(SlugUtils.toSlug(dto.getName()))
                .status(dto.getStatus() != null ? dto.getStatus() : "ACTIVE")
                .settings(dto.getSettings() != null ? dto.getSettings().toString() : null)
                .build();
        Tenant saved = tenantRepository.save(tenant);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TenantDto getTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
        return toDto(tenant);
    }

    @Transactional(readOnly = true)
    public TenantDto getTenantBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "slug", slug));
        return toDto(tenant);
    }

    public TenantDto updateTenant(UUID id, TenantDto dto) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));

        if (dto.getName() != null) {
            tenant.setName(dto.getName());
            tenant.setSlug(SlugUtils.toSlug(dto.getName()));
        }
        if (dto.getStatus() != null) {
            tenant.setStatus(dto.getStatus());
        }
        if (dto.getSettings() != null) {
            tenant.setSettings(dto.getSettings().toString());
        }

        Tenant saved = tenantRepository.save(tenant);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TenantDto> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TenantDto> listActiveTenants() {
        return tenantRepository.findByStatus("ACTIVE").stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TenantDto toDto(Tenant tenant) {
        return TenantDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
