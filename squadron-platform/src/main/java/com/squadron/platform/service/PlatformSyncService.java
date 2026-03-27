package com.squadron.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.adapter.PlatformAdapterRegistry;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformSyncService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSyncService.class);

    private final PlatformAdapterRegistry adapterRegistry;
    private final PlatformConnectionRepository connectionRepository;
    private final UserTokenService userTokenService;
    private final TokenEncryptionService encryptionService;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public PlatformSyncService(PlatformAdapterRegistry adapterRegistry,
                               PlatformConnectionRepository connectionRepository,
                               UserTokenService userTokenService,
                               TokenEncryptionService encryptionService,
                               NatsEventPublisher natsEventPublisher,
                               ObjectMapper objectMapper) {
        this.adapterRegistry = adapterRegistry;
        this.connectionRepository = connectionRepository;
        this.userTokenService = userTokenService;
        this.encryptionService = encryptionService;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches tasks from the external platform and publishes sync events via NATS.
     */
    public List<PlatformTaskDto> syncTasks(UUID connectionId, String projectKey) {
        log.info("Syncing tasks for connection {} with project {}", connectionId, projectKey);

        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        TicketingPlatformAdapter adapter = adapterRegistry.getAdapter(connection.getPlatformType());
        adapter.configure(connection.getBaseUrl(), extractAccessToken(connection));

        try {
            PlatformTaskFilter filter = PlatformTaskFilter.builder()
                    .projectKey(projectKey)
                    .build();

            List<PlatformTaskDto> tasks = adapter.fetchTasks(projectKey, filter);

            // Publish sync event
            SquadronEvent event = new SquadronEvent();
            event.setEventType("platform.tasks.synced");
            event.setTenantId(connection.getTenantId());
            event.setSource("squadron-platform");
            natsEventPublisher.publish("platform.sync." + connectionId, event);

            log.info("Synced {} tasks from connection {}", tasks.size(), connectionId);
            return tasks;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformIntegrationException(connection.getPlatformType(),
                    "Failed to sync tasks: " + e.getMessage(), e);
        }
    }

    /**
     * Pushes a status update back to the external platform using the user's delegated token.
     * Uses encrypted token storage: user tokens are decrypted via UserTokenService,
     * connection credentials are decrypted via TokenEncryptionService.
     */
    public void pushTaskStatus(UUID connectionId, String externalId, String status,
                               String comment, UUID userId) {
        log.info("Pushing status update for task {} on connection {} by user {}", externalId, connectionId, userId);

        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        // Use user's delegated token if available, decrypted automatically
        String accessToken;
        if (userId != null) {
            try {
                accessToken = userTokenService.getDecryptedAccessToken(userId, connectionId);
            } catch (ResourceNotFoundException e) {
                // Fallback to connection-level credentials if user has no linked token
                log.debug("No user token found for user {} on connection {}, using connection credentials",
                        userId, connectionId);
                accessToken = extractAccessToken(connection);
            }
        } else {
            accessToken = extractAccessToken(connection);
        }

        TicketingPlatformAdapter adapter = adapterRegistry.getAdapter(connection.getPlatformType());
        adapter.configure(connection.getBaseUrl(), accessToken);

        try {
            adapter.updateTaskStatus(externalId, status, comment);

            // Publish status push event
            SquadronEvent event = new SquadronEvent();
            event.setEventType("platform.task.status.pushed");
            event.setTenantId(connection.getTenantId());
            event.setSource("squadron-platform");
            natsEventPublisher.publish("platform.status." + connectionId, event);

            log.info("Successfully pushed status {} for task {} on connection {}", status, externalId, connectionId);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformIntegrationException(connection.getPlatformType(),
                    "Failed to push task status: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts and decrypts the access token from connection credentials.
     * Connection credentials JSONB stores sensitive values (accessToken, pat, apiKey) in encrypted form.
     */
    @SuppressWarnings("unchecked")
    private String extractAccessToken(PlatformConnection connection) {
        if (connection.getCredentials() == null) {
            return "";
        }
        try {
            Map<String, Object> creds = objectMapper.readValue(connection.getCredentials(), Map.class);

            // Look for known token field names in order of preference
            String encryptedToken = null;
            for (String key : List.of("accessToken", "pat", "apiKey")) {
                Object value = creds.get(key);
                if (value != null) {
                    encryptedToken = value.toString();
                    break;
                }
            }

            if (encryptedToken == null || encryptedToken.isEmpty()) {
                return "";
            }

            // Decrypt the token value
            return encryptionService.decrypt(encryptedToken);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse credentials for connection {}", connection.getId(), e);
            return "";
        } catch (SecurityException e) {
            log.warn("Failed to decrypt credentials for connection {}: {}", connection.getId(), e.getMessage());
            return "";
        }
    }
}
