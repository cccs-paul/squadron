package com.squadron.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.adapter.PlatformAdapterRegistry;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.dto.CreateConnectionRequest;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PlatformConnectionService {

    private static final Logger log = LoggerFactory.getLogger(PlatformConnectionService.class);

    /**
     * Keys in the credentials map whose values should be encrypted before storage.
     */
    private static final List<String> SENSITIVE_CREDENTIAL_KEYS = List.of(
            "clientSecret", "accessToken", "pat", "apiKey", "privateKey", "password"
    );

    private final PlatformConnectionRepository connectionRepository;
    private final PlatformAdapterRegistry adapterRegistry;
    private final TokenEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public PlatformConnectionService(PlatformConnectionRepository connectionRepository,
                                     PlatformAdapterRegistry adapterRegistry,
                                     TokenEncryptionService encryptionService,
                                     ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.adapterRegistry = adapterRegistry;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new platform connection. Sensitive fields in the credentials map
     * (clientSecret, accessToken, pat, apiKey, privateKey, password) are encrypted
     * before storage using AES-256-GCM.
     */
    public PlatformConnection createConnection(CreateConnectionRequest request) {
        log.info("Creating platform connection for tenant {} of type {}", request.getTenantId(), request.getPlatformType());

        PlatformConnection connection = PlatformConnection.builder()
                .tenantId(request.getTenantId())
                .name(request.getName())
                .platformType(request.getPlatformType())
                .baseUrl(request.getBaseUrl())
                .authType(request.getAuthType())
                .credentials(encryptCredentials(request.getCredentials()))
                .metadata(serializeToJson(request.getMetadata()))
                .status("ACTIVE")
                .build();

        PlatformConnection saved = connectionRepository.save(connection);
        log.info("Created platform connection {} for tenant {}", saved.getId(), saved.getTenantId());
        return saved;
    }

    @Transactional(readOnly = true)
    public PlatformConnection getConnection(UUID id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", id));
    }

    @Transactional(readOnly = true)
    public List<PlatformConnection> listConnectionsByTenant(UUID tenantId) {
        return connectionRepository.findByTenantId(tenantId);
    }

    /**
     * Updates an existing platform connection. Re-encrypts credentials if provided.
     */
    public PlatformConnection updateConnection(UUID id, CreateConnectionRequest request) {
        PlatformConnection connection = getConnection(id);
        connection.setName(request.getName());
        connection.setPlatformType(request.getPlatformType());
        connection.setBaseUrl(request.getBaseUrl());
        connection.setAuthType(request.getAuthType());
        if (request.getCredentials() != null) {
            connection.setCredentials(encryptCredentials(request.getCredentials()));
        }
        if (request.getMetadata() != null) {
            connection.setMetadata(serializeToJson(request.getMetadata()));
        }
        return connectionRepository.save(connection);
    }

    public void deleteConnection(UUID id) {
        PlatformConnection connection = getConnection(id);
        connectionRepository.delete(connection);
        log.info("Deleted platform connection {}", id);
    }

    /**
     * Tests connectivity to the external platform. Decrypts credentials before use.
     */
    public boolean testConnection(UUID connectionId) {
        PlatformConnection connection = getConnection(connectionId);
        TicketingPlatformAdapter adapter = adapterRegistry.getAdapter(connection.getPlatformType());

        try {
            // Configure the adapter with decrypted connection details
            String accessToken = getDecryptedAccessToken(connection);
            adapter.configure(connection.getBaseUrl(), accessToken);
            boolean result = adapter.testConnection();

            if (result) {
                connection.setStatus("ACTIVE");
            } else {
                connection.setStatus("ERROR");
            }
            connectionRepository.save(connection);
            return result;
        } catch (Exception e) {
            connection.setStatus("ERROR");
            connectionRepository.save(connection);
            throw new PlatformIntegrationException(connection.getPlatformType(),
                    "Connection test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the available workflow statuses for a project from the remote ticketing platform.
     * Configures the adapter with decrypted credentials and delegates to the adapter's
     * {@link TicketingPlatformAdapter#getAvailableStatuses(String)} method.
     *
     * @param connectionId the platform connection to use
     * @param projectKey   the external project key (e.g., JIRA project key, GitHub repo name)
     * @return list of available status names from the remote platform
     */
    @Transactional(readOnly = true)
    public List<String> fetchProjectStatuses(UUID connectionId, String projectKey) {
        PlatformConnection connection = getConnection(connectionId);
        TicketingPlatformAdapter adapter = adapterRegistry.getAdapter(connection.getPlatformType());

        try {
            String accessToken = getDecryptedAccessToken(connection);
            adapter.configure(connection.getBaseUrl(), accessToken);
            List<String> statuses = adapter.getAvailableStatuses(projectKey);
            log.info("Fetched {} statuses for project '{}' from connection {} ({})",
                    statuses.size(), projectKey, connectionId, connection.getPlatformType());
            return statuses;
        } catch (Exception e) {
            throw new PlatformIntegrationException(connection.getPlatformType(),
                    "Failed to fetch statuses for project '" + projectKey + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns the decrypted credentials map for a connection.
     * Sensitive values are decrypted from their encrypted storage form.
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Map<String, String> getDecryptedCredentials(UUID connectionId) {
        PlatformConnection connection = getConnection(connectionId);
        if (connection.getCredentials() == null) {
            return Map.of();
        }
        try {
            Map<String, String> creds = objectMapper.readValue(connection.getCredentials(), Map.class);
            Map<String, String> decrypted = new HashMap<>(creds);
            for (String key : SENSITIVE_CREDENTIAL_KEYS) {
                String value = decrypted.get(key);
                if (value != null && !value.isEmpty()) {
                    decrypted.put(key, encryptionService.decrypt(value));
                }
            }
            return decrypted;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse credentials for connection " + connectionId, e);
        }
    }

    /**
     * Encrypts sensitive fields in the credentials map and serializes to JSON for storage.
     */
    private String encryptCredentials(Map<String, String> credentials) {
        if (credentials == null) {
            return null;
        }
        Map<String, String> encrypted = new HashMap<>(credentials);
        for (String key : SENSITIVE_CREDENTIAL_KEYS) {
            String value = encrypted.get(key);
            if (value != null && !value.isEmpty()) {
                encrypted.put(key, encryptionService.encrypt(value));
            }
        }
        return serializeToJson(encrypted);
    }

    /**
     * Extract and decrypt the access token from connection credentials.
     */
    @SuppressWarnings("unchecked")
    private String getDecryptedAccessToken(PlatformConnection connection) {
        if (connection.getCredentials() == null) {
            return "";
        }
        try {
            Map<String, Object> creds = objectMapper.readValue(connection.getCredentials(), Map.class);
            // Look for known token field names in order of preference
            for (String key : List.of("accessToken", "pat", "apiKey")) {
                Object value = creds.get(key);
                if (value != null && !value.toString().isEmpty()) {
                    return encryptionService.decrypt(value.toString());
                }
            }
            return "";
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse credentials for connection {}", connection.getId(), e);
            return "";
        }
    }

    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }
}
