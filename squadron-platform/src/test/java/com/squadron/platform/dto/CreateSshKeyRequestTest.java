package com.squadron.platform.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateSshKeyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void should_passValidation_when_allFieldsProvided() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAA user@host")
                .privateKey("-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----")
                .keyType("ED25519")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void should_failValidation_when_tenantIdIsNull() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(null)
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("pub")
                .privateKey("priv")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("tenantId")));
    }

    @Test
    void should_failValidation_when_connectionIdIsNull() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(null)
                .name("Key")
                .publicKey("pub")
                .privateKey("priv")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("connectionId")));
    }

    @Test
    void should_failValidation_when_nameIsBlank() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("   ")
                .publicKey("pub")
                .privateKey("priv")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    void should_failValidation_when_publicKeyIsBlank() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("")
                .privateKey("priv")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("publicKey")));
    }

    @Test
    void should_failValidation_when_privateKeyIsBlank() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("pub")
                .privateKey("")
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("privateKey")));
    }

    @Test
    void should_passValidation_when_keyTypeIsNull() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("pub")
                .privateKey("priv")
                .keyType(null)
                .build();

        Set<ConstraintViolation<CreateSshKeyRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void should_buildWithBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Test Key")
                .publicKey("pub-key")
                .privateKey("priv-key")
                .keyType("RSA")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("Test Key", request.getName());
        assertEquals("pub-key", request.getPublicKey());
        assertEquals("priv-key", request.getPrivateKey());
        assertEquals("RSA", request.getKeyType());
    }
}
