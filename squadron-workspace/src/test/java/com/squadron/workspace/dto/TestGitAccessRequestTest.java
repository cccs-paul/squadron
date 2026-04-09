package com.squadron.workspace.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TestGitAccessRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void should_buildTestGitAccessRequest() {
        UUID sshKeyId = UUID.randomUUID();
        TestGitAccessRequest request = TestGitAccessRequest.builder()
                .cloneUrl("https://github.com/test/repo.git")
                .accessToken("my-token")
                .sshKeyId(sshKeyId)
                .branch("main")
                .build();

        assertEquals("https://github.com/test/repo.git", request.getCloneUrl());
        assertEquals("my-token", request.getAccessToken());
        assertEquals(sshKeyId, request.getSshKeyId());
        assertEquals("main", request.getBranch());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TestGitAccessRequest request = new TestGitAccessRequest();
        assertNull(request.getCloneUrl());
        assertNull(request.getAccessToken());
        assertNull(request.getSshKeyId());
        assertNull(request.getBranch());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID sshKeyId = UUID.randomUUID();
        TestGitAccessRequest request = new TestGitAccessRequest(
                "git@github.com:test/repo.git", "token", sshKeyId, "develop");

        assertEquals("git@github.com:test/repo.git", request.getCloneUrl());
        assertEquals("token", request.getAccessToken());
        assertEquals(sshKeyId, request.getSshKeyId());
        assertEquals("develop", request.getBranch());
    }

    @Test
    void should_failValidation_whenCloneUrlIsBlank() {
        TestGitAccessRequest request = TestGitAccessRequest.builder()
                .cloneUrl("")
                .build();

        Set<ConstraintViolation<TestGitAccessRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("cloneUrl")));
    }

    @Test
    void should_failValidation_whenCloneUrlIsNull() {
        TestGitAccessRequest request = TestGitAccessRequest.builder().build();

        Set<ConstraintViolation<TestGitAccessRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("cloneUrl")));
    }

    @Test
    void should_passValidation_whenCloneUrlIsProvided() {
        TestGitAccessRequest request = TestGitAccessRequest.builder()
                .cloneUrl("https://github.com/test/repo.git")
                .build();

        Set<ConstraintViolation<TestGitAccessRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID sshKeyId = UUID.randomUUID();
        TestGitAccessRequest r1 = TestGitAccessRequest.builder()
                .cloneUrl("https://github.com/test/repo.git")
                .accessToken("token")
                .sshKeyId(sshKeyId)
                .branch("main")
                .build();
        TestGitAccessRequest r2 = TestGitAccessRequest.builder()
                .cloneUrl("https://github.com/test/repo.git")
                .accessToken("token")
                .sshKeyId(sshKeyId)
                .branch("main")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportToString() {
        TestGitAccessRequest request = TestGitAccessRequest.builder()
                .cloneUrl("https://github.com/test/repo.git")
                .branch("main")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("cloneUrl"));
        assertTrue(str.contains("https://github.com/test/repo.git"));
    }

    @Test
    void should_supportSetters() {
        TestGitAccessRequest request = new TestGitAccessRequest();
        UUID sshKeyId = UUID.randomUUID();

        request.setCloneUrl("https://github.com/test/repo.git");
        request.setAccessToken("my-token");
        request.setSshKeyId(sshKeyId);
        request.setBranch("feature/test");

        assertEquals("https://github.com/test/repo.git", request.getCloneUrl());
        assertEquals("my-token", request.getAccessToken());
        assertEquals(sshKeyId, request.getSshKeyId());
        assertEquals("feature/test", request.getBranch());
    }
}
