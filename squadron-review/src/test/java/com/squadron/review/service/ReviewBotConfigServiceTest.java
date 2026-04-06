package com.squadron.review.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.review.dto.CreateReviewBotConfigRequest;
import com.squadron.review.dto.ReviewBotConfigDto;
import com.squadron.review.entity.ReviewBotConfig;
import com.squadron.review.repository.ReviewBotConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewBotConfigServiceTest {

    @Mock
    private ReviewBotConfigRepository repository;

    @Mock
    private TokenEncryptionService encryptionService;

    private ReviewBotConfigService service;

    @BeforeEach
    void setUp() {
        service = new ReviewBotConfigService(repository, encryptionService);
    }

    private ReviewBotConfig buildConfig(UUID id, UUID tenantId, UUID connectionId) {
        Instant now = Instant.now();
        ReviewBotConfig config = ReviewBotConfig.builder()
                .id(id)
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .botAccessToken("encrypted-token")
                .enabled(true)
                .autoAssign(true)
                .build();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return config;
    }

    @Test
    void should_createBotConfig_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .botAccessToken("plain-token")
                .enabled(true)
                .autoAssign(true)
                .build();

        when(repository.findByTenantIdAndConnectionId(tenantId, connectionId))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("plain-token")).thenReturn("encrypted-token");

        ReviewBotConfig saved = buildConfig(UUID.randomUUID(), tenantId, connectionId);
        when(repository.save(any(ReviewBotConfig.class))).thenReturn(saved);

        ReviewBotConfigDto result = service.createBotConfig(request);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals(connectionId, result.getConnectionId());
        assertEquals("squadron-bot", result.getBotUsername());
        assertTrue(result.isEnabled());
        assertTrue(result.isAutoAssign());
        verify(encryptionService).encrypt("plain-token");
        verify(repository).save(any(ReviewBotConfig.class));
    }

    @Test
    void should_throwException_when_duplicateConfig() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        ReviewBotConfig existing = buildConfig(UUID.randomUUID(), tenantId, connectionId);
        when(repository.findByTenantIdAndConnectionId(tenantId, connectionId))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.createBotConfig(request));
    }

    @Test
    void should_getBotConfig_when_exists() {
        UUID id = UUID.randomUUID();
        ReviewBotConfig config = buildConfig(id, UUID.randomUUID(), UUID.randomUUID());

        when(repository.findById(id)).thenReturn(Optional.of(config));

        ReviewBotConfigDto result = service.getBotConfig(id);

        assertEquals(id, result.getId());
        assertEquals("squadron-bot", result.getBotUsername());
    }

    @Test
    void should_throwNotFound_when_getBotConfigMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getBotConfig(id));
    }

    @Test
    void should_listBotConfigs_when_tenantHasConfigs() {
        UUID tenantId = UUID.randomUUID();
        ReviewBotConfig c1 = buildConfig(UUID.randomUUID(), tenantId, UUID.randomUUID());
        ReviewBotConfig c2 = buildConfig(UUID.randomUUID(), tenantId, UUID.randomUUID());

        when(repository.findByTenantId(tenantId)).thenReturn(List.of(c1, c2));

        List<ReviewBotConfigDto> result = service.listBotConfigsByTenant(tenantId);

        assertEquals(2, result.size());
    }

    @Test
    void should_returnEmptyList_when_tenantHasNoConfigs() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());

        List<ReviewBotConfigDto> result = service.listBotConfigsByTenant(tenantId);

        assertEquals(0, result.size());
    }

    @Test
    void should_getEnabledBotConfig_when_exists() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ReviewBotConfig config = buildConfig(UUID.randomUUID(), tenantId, connectionId);

        when(repository.findByTenantIdAndConnectionIdAndEnabledTrue(tenantId, connectionId))
                .thenReturn(Optional.of(config));

        Optional<ReviewBotConfig> result = service.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isPresent());
        assertEquals("squadron-bot", result.get().getBotUsername());
    }

    @Test
    void should_getDecryptedBotToken_when_configExists() {
        UUID id = UUID.randomUUID();
        ReviewBotConfig config = buildConfig(id, UUID.randomUUID(), UUID.randomUUID());

        when(repository.findById(id)).thenReturn(Optional.of(config));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");

        String result = service.getDecryptedBotToken(id);

        assertEquals("plain-token", result);
        verify(encryptionService).decrypt("encrypted-token");
    }

    @Test
    void should_throwNotFound_when_getDecryptedBotTokenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDecryptedBotToken(id));
    }

    @Test
    void should_updateBotConfig_when_exists() {
        UUID id = UUID.randomUUID();
        ReviewBotConfig config = buildConfig(id, UUID.randomUUID(), UUID.randomUUID());

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(config.getTenantId())
                .connectionId(config.getConnectionId())
                .botUsername("updated-bot")
                .botAccessToken("new-token")
                .enabled(false)
                .autoAssign(false)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(config));
        when(encryptionService.encrypt("new-token")).thenReturn("new-encrypted-token");
        when(repository.save(any(ReviewBotConfig.class))).thenReturn(config);

        ReviewBotConfigDto result = service.updateBotConfig(id, request);

        assertNotNull(result);
        verify(encryptionService).encrypt("new-token");
        verify(repository).save(any(ReviewBotConfig.class));
    }

    @Test
    void should_throwNotFound_when_updateBotConfigMissing() {
        UUID id = UUID.randomUUID();
        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateBotConfig(id, request));
    }

    @Test
    void should_deleteBotConfig_when_exists() {
        UUID id = UUID.randomUUID();
        ReviewBotConfig config = buildConfig(id, UUID.randomUUID(), UUID.randomUUID());

        when(repository.findById(id)).thenReturn(Optional.of(config));

        service.deleteBotConfig(id);

        verify(repository).delete(config);
    }

    @Test
    void should_throwNotFound_when_deleteBotConfigMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteBotConfig(id));
    }
}
