package com.squadron.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.dto.CreateSshKeyRequest;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.service.SshKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SshKeyController.class)
@ContextConfiguration(classes = {SshKeyController.class, SecurityConfig.class})
class SshKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SshKeyService sshKeyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private SshKey buildSshKey(UUID id, UUID tenantId, UUID connectionId, String name) {
        return SshKey.builder()
                .id(id)
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name(name)
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("encrypted-private-key")
                .fingerprint("SHA256:abc123def456")
                .keyType("ED25519")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // --- POST /api/platforms/ssh-keys ---

    @Test
    @WithMockUser
    void should_createSshKey_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----")
                .keyType("ED25519")
                .build();

        SshKey created = buildSshKey(keyId, tenantId, connectionId, "Deploy Key");
        when(sshKeyService.createSshKey(any(CreateSshKeyRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/platforms/ssh-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(keyId.toString()))
                .andExpect(jsonPath("$.data.name").value("Deploy Key"))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.fingerprint").value("SHA256:abc123def456"))
                .andExpect(jsonPath("$.data.keyType").value("ED25519"))
                .andExpect(jsonPath("$.data.publicKey").exists())
                .andExpect(jsonPath("$.data.privateKey").doesNotExist());

        verify(sshKeyService).createSshKey(any(CreateSshKeyRequest.class));
    }

    @Test
    void should_return401_when_createSshKeyUnauthenticated() throws Exception {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("ssh-ed25519 AAAA")
                .privateKey("private")
                .build();

        mockMvc.perform(post("/api/platforms/ssh-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/ssh-keys/{id} ---

    @Test
    @WithMockUser
    void should_getSshKey_when_exists() throws Exception {
        UUID keyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        SshKey key = buildSshKey(keyId, tenantId, connectionId, "My Key");
        when(sshKeyService.getSshKey(keyId)).thenReturn(key);

        mockMvc.perform(get("/api/platforms/ssh-keys/{id}", keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(keyId.toString()))
                .andExpect(jsonPath("$.data.name").value("My Key"))
                .andExpect(jsonPath("$.data.fingerprint").value("SHA256:abc123def456"))
                .andExpect(jsonPath("$.data.privateKey").doesNotExist());
    }

    @Test
    void should_return401_when_getSshKeyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/ssh-keys/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/ssh-keys/tenant/{tenantId} ---

    @Test
    @WithMockUser
    void should_listSshKeysByTenant_when_keysExist() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        SshKey k1 = buildSshKey(UUID.randomUUID(), tenantId, connectionId, "Key 1");
        SshKey k2 = buildSshKey(UUID.randomUUID(), tenantId, connectionId, "Key 2");

        when(sshKeyService.listSshKeysByTenant(tenantId)).thenReturn(List.of(k1, k2));

        mockMvc.perform(get("/api/platforms/ssh-keys/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Key 1"))
                .andExpect(jsonPath("$.data[1].name").value("Key 2"))
                .andExpect(jsonPath("$.data[0].privateKey").doesNotExist())
                .andExpect(jsonPath("$.data[1].privateKey").doesNotExist());
    }

    @Test
    @WithMockUser
    void should_returnEmptyList_when_noKeysForTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(sshKeyService.listSshKeysByTenant(tenantId)).thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/ssh-keys/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // --- GET /api/platforms/ssh-keys/connection/{connectionId} ---

    @Test
    @WithMockUser
    void should_listSshKeysByConnection_when_keysExist() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        SshKey k1 = buildSshKey(UUID.randomUUID(), tenantId, connectionId, "Conn Key");

        when(sshKeyService.listSshKeysByConnection(connectionId)).thenReturn(List.of(k1));

        mockMvc.perform(get("/api/platforms/ssh-keys/connection/{connectionId}", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Conn Key"))
                .andExpect(jsonPath("$.data[0].connectionId").value(connectionId.toString()));
    }

    @Test
    void should_return401_when_listByConnectionUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/ssh-keys/connection/{connectionId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/platforms/ssh-keys/{id} ---

    @Test
    @WithMockUser
    void should_deleteSshKey_when_authenticated() throws Exception {
        UUID keyId = UUID.randomUUID();
        doNothing().when(sshKeyService).deleteSshKey(keyId);

        mockMvc.perform(delete("/api/platforms/ssh-keys/{id}", keyId))
                .andExpect(status().isNoContent());

        verify(sshKeyService).deleteSshKey(keyId);
    }

    @Test
    void should_return401_when_deleteSshKeyUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/platforms/ssh-keys/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- Response omits private key ---

    @Test
    @WithMockUser
    void should_notExposePrivateKey_when_gettingKey() throws Exception {
        UUID keyId = UUID.randomUUID();
        SshKey key = buildSshKey(keyId, UUID.randomUUID(), UUID.randomUUID(), "Secure Key");

        when(sshKeyService.getSshKey(keyId)).thenReturn(key);

        mockMvc.perform(get("/api/platforms/ssh-keys/{id}", keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicKey").exists())
                .andExpect(jsonPath("$.data.privateKey").doesNotExist());
    }

    // --- GET /api/platforms/ssh-keys/{id}/private-key (internal endpoint) ---

    @Test
    @WithMockUser
    void should_getDecryptedPrivateKey_when_authenticated() throws Exception {
        UUID keyId = UUID.randomUUID();
        String decryptedKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nactual-private-key\n-----END OPENSSH PRIVATE KEY-----";

        when(sshKeyService.getDecryptedPrivateKey(keyId)).thenReturn(decryptedKey);

        mockMvc.perform(get("/api/platforms/ssh-keys/{id}/private-key", keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(decryptedKey));

        verify(sshKeyService).getDecryptedPrivateKey(keyId);
    }

    @Test
    void should_return401_when_getDecryptedPrivateKeyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/ssh-keys/{id}/private-key", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
