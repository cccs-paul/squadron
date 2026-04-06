package com.squadron.review.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.review.dto.CreateReviewBotConfigRequest;
import com.squadron.review.dto.ReviewBotConfigDto;
import com.squadron.review.entity.ReviewBotConfig;
import com.squadron.review.repository.ReviewBotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ReviewBotConfigService {

    private final ReviewBotConfigRepository repository;
    private final TokenEncryptionService encryptionService;

    public ReviewBotConfigDto createBotConfig(CreateReviewBotConfigRequest request) {
        log.info("Creating review bot config for tenant {} on connection {}", request.getTenantId(), request.getConnectionId());

        // Check for existing config
        Optional<ReviewBotConfig> existing = repository.findByTenantIdAndConnectionId(
                request.getTenantId(), request.getConnectionId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("A review bot config already exists for this tenant and connection");
        }

        ReviewBotConfig config = ReviewBotConfig.builder()
                .tenantId(request.getTenantId())
                .connectionId(request.getConnectionId())
                .botUsername(request.getBotUsername())
                .botAccessToken(encryptionService.encrypt(request.getBotAccessToken()))
                .enabled(request.isEnabled())
                .autoAssign(request.isAutoAssign())
                .build();

        config = repository.save(config);
        log.info("Created review bot config {} for tenant {}", config.getId(), request.getTenantId());
        return toDto(config);
    }

    @Transactional(readOnly = true)
    public ReviewBotConfigDto getBotConfig(UUID id) {
        ReviewBotConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewBotConfig", id));
        return toDto(config);
    }

    @Transactional(readOnly = true)
    public List<ReviewBotConfigDto> listBotConfigsByTenant(UUID tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ReviewBotConfig> getEnabledBotConfig(UUID tenantId, UUID connectionId) {
        return repository.findByTenantIdAndConnectionIdAndEnabledTrue(tenantId, connectionId);
    }

    /**
     * Get the decrypted bot access token for a specific config.
     */
    @Transactional(readOnly = true)
    public String getDecryptedBotToken(UUID id) {
        ReviewBotConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewBotConfig", id));
        return encryptionService.decrypt(config.getBotAccessToken());
    }

    public ReviewBotConfigDto updateBotConfig(UUID id, CreateReviewBotConfigRequest request) {
        ReviewBotConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewBotConfig", id));

        config.setBotUsername(request.getBotUsername());
        if (request.getBotAccessToken() != null && !request.getBotAccessToken().isBlank()) {
            config.setBotAccessToken(encryptionService.encrypt(request.getBotAccessToken()));
        }
        config.setEnabled(request.isEnabled());
        config.setAutoAssign(request.isAutoAssign());

        config = repository.save(config);
        log.info("Updated review bot config {}", id);
        return toDto(config);
    }

    public void deleteBotConfig(UUID id) {
        ReviewBotConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewBotConfig", id));
        repository.delete(config);
        log.info("Deleted review bot config {}", id);
    }

    private ReviewBotConfigDto toDto(ReviewBotConfig config) {
        return ReviewBotConfigDto.builder()
                .id(config.getId())
                .tenantId(config.getTenantId())
                .connectionId(config.getConnectionId())
                .botUsername(config.getBotUsername())
                .enabled(config.isEnabled())
                .autoAssign(config.isAutoAssign())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
