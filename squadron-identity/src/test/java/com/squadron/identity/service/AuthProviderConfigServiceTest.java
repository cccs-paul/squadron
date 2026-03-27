package com.squadron.identity.service;

import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.AuthProviderConfigRepository;
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
class AuthProviderConfigServiceTest {

    @Mock
    private AuthProviderConfigRepository authProviderConfigRepository;

    @InjectMocks
    private AuthProviderConfigService service;

    private UUID configId;
    private UUID tenantId;
    private AuthProviderConfig config;

    @BeforeEach
    void setUp() {
        configId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        config = AuthProviderConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .providerType("ldap")
                .name("Corporate LDAP")
                .config("{\"url\":\"ldap://localhost\"}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_createConfig_when_validInput() {
        when(authProviderConfigRepository.save(any(AuthProviderConfig.class))).thenReturn(config);

        AuthProviderConfig result = service.createConfig(tenantId, "ldap", "Corporate LDAP", "{}", true, 0);

        assertNotNull(result);
        assertEquals("Corporate LDAP", result.getName());
    }

    @Test
    void should_useEmptyJsonConfig_when_configJsonIsNull() {
        when(authProviderConfigRepository.save(any(AuthProviderConfig.class))).thenReturn(config);

        service.createConfig(tenantId, "ldap", "LDAP", null, true, 0);

        verify(authProviderConfigRepository).save(argThat(c -> "{}".equals(c.getConfig())));
    }

    @Test
    void should_getConfig_when_exists() {
        when(authProviderConfigRepository.findById(configId)).thenReturn(Optional.of(config));

        AuthProviderConfig result = service.getConfig(configId);

        assertEquals(configId, result.getId());
    }

    @Test
    void should_throwNotFound_when_configDoesNotExist() {
        when(authProviderConfigRepository.findById(configId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getConfig(configId));
    }

    @Test
    void should_listConfigs_when_called() {
        when(authProviderConfigRepository.findByTenantId(tenantId)).thenReturn(List.of(config));

        List<AuthProviderConfig> results = service.listConfigs(tenantId);

        assertEquals(1, results.size());
    }

    @Test
    void should_updateConfig_when_allFieldsProvided() {
        when(authProviderConfigRepository.findById(configId)).thenReturn(Optional.of(config));
        when(authProviderConfigRepository.save(config)).thenReturn(config);

        AuthProviderConfig result = service.updateConfig(configId, "Updated", "keycloak",
                "{\"new\":true}", false, 10);

        assertEquals("Updated", config.getName());
        assertEquals("keycloak", config.getProviderType());
        assertEquals("{\"new\":true}", config.getConfig());
        assertFalse(config.isEnabled());
        assertEquals(10, config.getPriority());
    }

    @Test
    void should_updateOnlyProvidedFields_when_someFieldsAreNull() {
        when(authProviderConfigRepository.findById(configId)).thenReturn(Optional.of(config));
        when(authProviderConfigRepository.save(config)).thenReturn(config);

        service.updateConfig(configId, null, null, null, null, null);

        assertEquals("Corporate LDAP", config.getName()); // unchanged
        assertEquals("ldap", config.getProviderType()); // unchanged
        assertTrue(config.isEnabled()); // unchanged
    }

    @Test
    void should_throwNotFound_when_updatingNonExistentConfig() {
        when(authProviderConfigRepository.findById(configId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.updateConfig(configId, "name", null, null, null, null));
    }

    @Test
    void should_deleteConfig_when_exists() {
        when(authProviderConfigRepository.existsById(configId)).thenReturn(true);

        service.deleteConfig(configId);

        verify(authProviderConfigRepository).deleteById(configId);
    }

    @Test
    void should_throwNotFound_when_deletingNonExistentConfig() {
        when(authProviderConfigRepository.existsById(configId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteConfig(configId));
    }

    @Test
    void should_getEnabledConfigs_when_called() {
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of(config));

        List<AuthProviderConfig> results = service.getEnabledConfigs(tenantId);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isEnabled());
    }
}
