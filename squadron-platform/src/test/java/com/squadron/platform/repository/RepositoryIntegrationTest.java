package com.squadron.platform.repository;

import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.entity.UserPlatformToken;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.platform.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.platform.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_platform_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private UserPlatformTokenRepository userPlatformTokenRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private PlatformConnection createConnection(UUID tenantId, String platformType, String status) {
        PlatformConnection conn = PlatformConnection.builder()
                .tenantId(tenantId)
                .name(platformType + " Connection")
                .platformType(platformType)
                .baseUrl("https://" + platformType.toLowerCase() + ".example.com")
                .authType("OAUTH2")
                .status(status)
                .build();
        return entityManager.persistFlushFind(conn);
    }

    private PlatformConnection createConnection(UUID tenantId, String platformType) {
        return createConnection(tenantId, platformType, "ACTIVE");
    }

    private UserPlatformToken createToken(UUID userId, UUID connectionId) {
        UserPlatformToken token = UserPlatformToken.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-access-token-" + UUID.randomUUID())
                .refreshToken("encrypted-refresh-token-" + UUID.randomUUID())
                .scopes("read,write")
                .tokenType("oauth2")
                .build();
        return entityManager.persistFlushFind(token);
    }

    // =========================================================================
    // PlatformConnectionRepository Tests
    // =========================================================================

    @Test
    void should_savePlatformConnection_when_validEntity() {
        PlatformConnection conn = PlatformConnection.builder()
                .tenantId(UUID.randomUUID())
                .name("JIRA Cloud Test Connection")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://myorg.atlassian.net")
                .authType("OAUTH2")
                .credentials("{\"clientId\": \"abc\"}")
                .build();

        PlatformConnection saved = platformConnectionRepository.save(conn);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPlatformType()).isEqualTo("JIRA_CLOUD");
        assertThat(saved.getBaseUrl()).isEqualTo("https://myorg.atlassian.net");
        assertThat(saved.getAuthType()).isEqualTo("OAUTH2");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCredentials()).isEqualTo("{\"clientId\": \"abc\"}");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findById_when_connectionExists() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");

        Optional<PlatformConnection> found = platformConnectionRepository.findById(conn.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPlatformType()).isEqualTo("GITHUB");
    }

    @Test
    void should_returnEmpty_when_connectionNotFound() {
        Optional<PlatformConnection> found = platformConnectionRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTenantId_when_connectionsExist() {
        UUID tenantId = UUID.randomUUID();
        createConnection(tenantId, "JIRA_CLOUD");
        createConnection(tenantId, "GITHUB");
        createConnection(UUID.randomUUID(), "GITLAB");

        List<PlatformConnection> results = platformConnectionRepository.findByTenantId(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getTenantId().equals(tenantId));
    }

    @Test
    void should_findByTenantIdAndPlatformType_when_matching() {
        UUID tenantId = UUID.randomUUID();
        createConnection(tenantId, "JIRA_CLOUD");
        createConnection(tenantId, "JIRA_CLOUD");
        createConnection(tenantId, "GITHUB");

        List<PlatformConnection> jiraResults =
                platformConnectionRepository.findByTenantIdAndPlatformType(tenantId, "JIRA_CLOUD");
        List<PlatformConnection> githubResults =
                platformConnectionRepository.findByTenantIdAndPlatformType(tenantId, "GITHUB");

        assertThat(jiraResults).hasSize(2);
        assertThat(jiraResults).allMatch(c -> "JIRA_CLOUD".equals(c.getPlatformType()));
        assertThat(githubResults).hasSize(1);
    }

    @Test
    void should_findByTenantIdAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        createConnection(tenantId, "JIRA_CLOUD", "ACTIVE");
        createConnection(tenantId, "GITHUB", "ACTIVE");
        createConnection(tenantId, "GITLAB", "DISABLED");

        List<PlatformConnection> activeResults =
                platformConnectionRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        List<PlatformConnection> disabledResults =
                platformConnectionRepository.findByTenantIdAndStatus(tenantId, "DISABLED");

        assertThat(activeResults).hasSize(2);
        assertThat(activeResults).allMatch(c -> "ACTIVE".equals(c.getStatus()));
        assertThat(disabledResults).hasSize(1);
        assertThat(disabledResults.get(0).getPlatformType()).isEqualTo("GITLAB");
    }

    @Test
    void should_findByPlatformTypeAndStatus_when_matching() {
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        createConnection(tenant1, "JIRA_CLOUD", "ACTIVE");
        createConnection(tenant2, "JIRA_CLOUD", "ACTIVE");
        createConnection(tenant1, "JIRA_CLOUD", "DISABLED");

        List<PlatformConnection> results =
                platformConnectionRepository.findByPlatformTypeAndStatus("JIRA_CLOUD", "ACTIVE");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> "JIRA_CLOUD".equals(c.getPlatformType()) && "ACTIVE".equals(c.getStatus()));
    }

    @Test
    void should_findByPlatformTypeInAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        createConnection(tenantId, "JIRA_CLOUD", "ACTIVE");
        createConnection(tenantId, "JIRA_SERVER", "ACTIVE");
        createConnection(tenantId, "GITLAB", "ACTIVE");
        createConnection(tenantId, "AZURE_DEVOPS", "DISABLED");

        List<PlatformConnection> results =
                platformConnectionRepository.findByPlatformTypeInAndStatus(
                        List.of("JIRA_CLOUD", "JIRA_SERVER"), "ACTIVE");

        // Filter to test tenant only (V7 migration may seed additional rows)
        List<PlatformConnection> tenantResults = results.stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .toList();

        assertThat(tenantResults).hasSize(2);
        assertThat(tenantResults).allMatch(c -> "ACTIVE".equals(c.getStatus()));
        assertThat(tenantResults).extracting(PlatformConnection::getPlatformType)
                .containsExactlyInAnyOrder("JIRA_CLOUD", "JIRA_SERVER");
    }

    @Test
    void should_deletePlatformConnection_when_exists() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "JIRA_CLOUD");
        UUID connId = conn.getId();

        platformConnectionRepository.deleteById(connId);
        entityManager.flush();

        assertThat(platformConnectionRepository.findById(connId)).isEmpty();
    }

    @Test
    void should_updatePlatformConnection_when_fieldsChanged() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "JIRA_CLOUD");
        conn.setStatus("DISABLED");
        conn.setBaseUrl("https://new-jira.example.com");

        platformConnectionRepository.save(conn);
        entityManager.flush();
        entityManager.clear();

        PlatformConnection updated = platformConnectionRepository.findById(conn.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
        assertThat(updated.getBaseUrl()).isEqualTo("https://new-jira.example.com");
    }

    @Test
    void should_returnEmptyList_when_noConnectionsForTenant() {
        List<PlatformConnection> results = platformConnectionRepository.findByTenantId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // UserPlatformTokenRepository Tests
    // =========================================================================

    @Test
    void should_saveUserPlatformToken_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");

        UserPlatformToken token = UserPlatformToken.builder()
                .userId(UUID.randomUUID())
                .connectionId(conn.getId())
                .accessToken("enc-access-token")
                .refreshToken("enc-refresh-token")
                .scopes("repo,user")
                .tokenType("oauth2")
                .build();

        UserPlatformToken saved = userPlatformTokenRepository.save(token);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAccessToken()).isEqualTo("enc-access-token");
        assertThat(saved.getRefreshToken()).isEqualTo("enc-refresh-token");
        assertThat(saved.getScopes()).isEqualTo("repo,user");
        assertThat(saved.getTokenType()).isEqualTo("oauth2");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findTokenById_when_tokenExists() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");
        UserPlatformToken token = createToken(UUID.randomUUID(), conn.getId());

        Optional<UserPlatformToken> found = userPlatformTokenRepository.findById(token.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getConnectionId()).isEqualTo(conn.getId());
    }

    @Test
    void should_findByUserIdAndConnectionId_when_tokenExists() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "JIRA_CLOUD");
        createToken(userId, conn.getId());

        Optional<UserPlatformToken> found =
                userPlatformTokenRepository.findByUserIdAndConnectionId(userId, conn.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getConnectionId()).isEqualTo(conn.getId());
    }

    @Test
    void should_returnEmpty_when_userIdAndConnectionIdNotFound() {
        Optional<UserPlatformToken> found =
                userPlatformTokenRepository.findByUserIdAndConnectionId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByUserId_when_tokensExist() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlatformConnection conn1 = createConnection(tenantId, "JIRA_CLOUD");
        PlatformConnection conn2 = createConnection(tenantId, "GITHUB");
        createToken(userId, conn1.getId());
        createToken(userId, conn2.getId());
        createToken(UUID.randomUUID(), conn1.getId());

        List<UserPlatformToken> tokens = userPlatformTokenRepository.findByUserId(userId);

        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(t -> t.getUserId().equals(userId));
    }

    @Test
    void should_returnEmptyList_when_noTokensForUser() {
        List<UserPlatformToken> tokens = userPlatformTokenRepository.findByUserId(UUID.randomUUID());

        assertThat(tokens).isEmpty();
    }

    @Test
    void should_deleteToken_when_tokenExists() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");
        UserPlatformToken token = createToken(UUID.randomUUID(), conn.getId());
        UUID tokenId = token.getId();

        userPlatformTokenRepository.deleteById(tokenId);
        entityManager.flush();

        assertThat(userPlatformTokenRepository.findById(tokenId)).isEmpty();
    }

    @Test
    void should_cascadeDeleteTokens_when_connectionDeleted() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");
        UserPlatformToken token = createToken(UUID.randomUUID(), conn.getId());
        UUID tokenId = token.getId();

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        // (JPA entityManager.remove() does not trigger database-level cascades)
        EntityManager em = entityManager.getEntityManager();
        em.flush();
        em.clear();
        em.createNativeQuery("DELETE FROM platform_connections WHERE id = :id")
                .setParameter("id", conn.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        assertThat(userPlatformTokenRepository.findById(tokenId)).isEmpty();
    }

    @Test
    void should_updateToken_when_fieldsChanged() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection conn = createConnection(tenantId, "GITHUB");
        UserPlatformToken token = createToken(UUID.randomUUID(), conn.getId());

        token.setAccessToken("new-encrypted-access-token");
        token.setTokenType("pat");

        userPlatformTokenRepository.save(token);
        entityManager.flush();
        entityManager.clear();

        UserPlatformToken updated = userPlatformTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updated.getAccessToken()).isEqualTo("new-encrypted-access-token");
        assertThat(updated.getTokenType()).isEqualTo("pat");
    }
}
