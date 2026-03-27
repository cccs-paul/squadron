package com.squadron.identity.repository;

import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.entity.ResourcePermission;
import com.squadron.identity.entity.SecurityGroup;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.entity.Team;
import com.squadron.identity.entity.Tenant;
import com.squadron.identity.entity.User;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.entity.UserTeamId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_identity_test");

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

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
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserTeamRepository userTeamRepository;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;

    @Autowired
    private SecurityGroupRepository securityGroupRepository;

    @Autowired
    private SecurityGroupMemberRepository securityGroupMemberRepository;

    @Autowired
    private ResourcePermissionRepository resourcePermissionRepository;

    // =========================================================================
    // Helper methods to create test entities
    // =========================================================================

    private Tenant createTenant(String name, String slug) {
        return createTenant(name, slug, "ACTIVE");
    }

    private Tenant createTenant(String name, String slug, String status) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .status(status)
                .build();
        return entityManager.persistFlushFind(tenant);
    }

    private User createUser(UUID tenantId, String email, String externalId) {
        return createUser(tenantId, email, externalId, "DEVELOPER");
    }

    private User createUser(UUID tenantId, String email, String externalId, String role) {
        User user = User.builder()
                .tenantId(tenantId)
                .email(email)
                .externalId(externalId)
                .displayName(email.split("@")[0])
                .role(role)
                .authProvider("ldap")
                .roles(Set.of("developer"))
                .build();
        return entityManager.persistFlushFind(user);
    }

    private Team createTeam(UUID tenantId, String name) {
        Team team = Team.builder()
                .tenantId(tenantId)
                .name(name)
                .build();
        return entityManager.persistFlushFind(team);
    }

    // =========================================================================
    // TenantRepository Tests
    // =========================================================================

    @Test
    void should_saveTenant_when_validEntity() {
        Tenant tenant = Tenant.builder()
                .name("Test Corp")
                .slug("test-corp")
                .build();

        Tenant saved = tenantRepository.save(tenant);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Corp");
        assertThat(saved.getSlug()).isEqualTo("test-corp");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findTenantById_when_tenantExists() {
        Tenant tenant = createTenant("Find Corp", "find-corp");

        Optional<Tenant> found = tenantRepository.findById(tenant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Find Corp");
    }

    @Test
    void should_returnEmpty_when_tenantNotFound() {
        Optional<Tenant> found = tenantRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findBySlug_when_slugExists() {
        createTenant("Slug Corp", "slug-corp-unique");

        Optional<Tenant> found = tenantRepository.findBySlug("slug-corp-unique");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Slug Corp");
    }

    @Test
    void should_returnEmpty_when_slugNotFound() {
        Optional<Tenant> found = tenantRepository.findBySlug("nonexistent-slug");

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByStatus_when_tenantsExist() {
        createTenant("Active Corp 1", "active-1", "ACTIVE");
        createTenant("Active Corp 2", "active-2", "ACTIVE");
        createTenant("Suspended Corp", "suspended-1", "SUSPENDED");

        List<Tenant> activeList = tenantRepository.findByStatus("ACTIVE");
        List<Tenant> suspendedList = tenantRepository.findByStatus("SUSPENDED");

        assertThat(activeList).hasSizeGreaterThanOrEqualTo(2);
        assertThat(activeList).allMatch(t -> "ACTIVE".equals(t.getStatus()));
        assertThat(suspendedList).hasSizeGreaterThanOrEqualTo(1);
        assertThat(suspendedList).allMatch(t -> "SUSPENDED".equals(t.getStatus()));
    }

    @Test
    void should_deleteTenant_when_tenantExists() {
        Tenant tenant = createTenant("Delete Corp", "delete-corp");
        UUID id = tenant.getId();

        tenantRepository.deleteById(id);
        entityManager.flush();

        Optional<Tenant> found = tenantRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void should_updateTenant_when_fieldsChanged() {
        Tenant tenant = createTenant("Old Name", "update-slug");
        tenant.setName("New Name");
        tenant.setStatus("SUSPENDED");

        tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();

        Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getStatus()).isEqualTo("SUSPENDED");
    }

    // =========================================================================
    // UserRepository Tests
    // =========================================================================

    @Test
    void should_saveUser_when_validEntity() {
        Tenant tenant = createTenant("User Tenant", "user-tenant-save");

        User user = User.builder()
                .tenantId(tenant.getId())
                .externalId("ext-save-user")
                .email("save@example.com")
                .displayName("Save User")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of("developer", "viewer"))
                .build();

        User saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("save@example.com");
        assertThat(saved.getExternalId()).isEqualTo("ext-save-user");
        assertThat(saved.getAuthProvider()).isEqualTo("ldap");
        assertThat(saved.getRoles()).containsExactlyInAnyOrder("developer", "viewer");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByExternalId_when_userExists() {
        Tenant tenant = createTenant("ExtId Tenant", "extid-tenant");
        createUser(tenant.getId(), "ext@example.com", "ext-unique-123");

        Optional<User> found = userRepository.findByExternalId("ext-unique-123");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("ext@example.com");
    }

    @Test
    void should_findByExternalIdAndTenantId_when_userExists() {
        Tenant tenant = createTenant("ExtTenant", "ext-tenant-combo");
        User user = createUser(tenant.getId(), "combo@example.com", "ext-combo-456");

        Optional<User> found = userRepository.findByExternalIdAndTenantId(
                "ext-combo-456", tenant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    void should_returnEmpty_when_externalIdAndTenantIdDoNotMatch() {
        Tenant tenant = createTenant("Mismatch Tenant", "mismatch-tenant");
        createUser(tenant.getId(), "mismatch@example.com", "ext-mismatch");

        Optional<User> found = userRepository.findByExternalIdAndTenantId(
                "ext-mismatch", UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByEmailAndTenantId_when_userExists() {
        Tenant tenant = createTenant("Email Tenant", "email-tenant");
        createUser(tenant.getId(), "emailquery@example.com", "ext-email-q");

        Optional<User> found = userRepository.findByEmailAndTenantId(
                "emailquery@example.com", tenant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getExternalId()).isEqualTo("ext-email-q");
    }

    @Test
    void should_findByEmail_when_userExists() {
        Tenant tenant = createTenant("EmailOnly Tenant", "emailonly-tenant");
        createUser(tenant.getId(), "onlyemail@example.com", "ext-only-email");

        Optional<User> found = userRepository.findByEmail("onlyemail@example.com");

        assertThat(found).isPresent();
    }

    @Test
    void should_findByTenantId_when_usersExist() {
        Tenant tenant = createTenant("Multi User Tenant", "multi-user-tenant");
        createUser(tenant.getId(), "user1@example.com", "ext-multi-1");
        createUser(tenant.getId(), "user2@example.com", "ext-multi-2");
        createUser(tenant.getId(), "user3@example.com", "ext-multi-3");

        List<User> users = userRepository.findByTenantId(tenant.getId());

        assertThat(users).hasSize(3);
        assertThat(users).allMatch(u -> u.getTenantId().equals(tenant.getId()));
    }

    @Test
    void should_findByTenantIdAndRole_when_usersMatchRole() {
        Tenant tenant = createTenant("Role Tenant", "role-tenant");
        createUser(tenant.getId(), "admin1@example.com", "ext-admin-1", "ADMIN");
        createUser(tenant.getId(), "admin2@example.com", "ext-admin-2", "ADMIN");
        createUser(tenant.getId(), "dev1@example.com", "ext-dev-1", "DEVELOPER");

        List<User> admins = userRepository.findByTenantIdAndRole(tenant.getId(), "ADMIN");
        List<User> devs = userRepository.findByTenantIdAndRole(tenant.getId(), "DEVELOPER");

        assertThat(admins).hasSize(2);
        assertThat(admins).allMatch(u -> "ADMIN".equals(u.getRole()));
        assertThat(devs).hasSize(1);
        assertThat(devs).allMatch(u -> "DEVELOPER".equals(u.getRole()));
    }

    @Test
    void should_returnEmptyList_when_noUsersForTenant() {
        List<User> users = userRepository.findByTenantId(UUID.randomUUID());

        assertThat(users).isEmpty();
    }

    @Test
    void should_deleteUser_when_userExists() {
        Tenant tenant = createTenant("Delete User Tenant", "del-user-tenant");
        User user = createUser(tenant.getId(), "delete@example.com", "ext-delete-user");
        UUID userId = user.getId();

        userRepository.deleteById(userId);
        entityManager.flush();

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    // =========================================================================
    // TeamRepository Tests
    // =========================================================================

    @Test
    void should_saveTeam_when_validEntity() {
        Tenant tenant = createTenant("Team Tenant", "team-tenant-save");

        Team team = Team.builder()
                .tenantId(tenant.getId())
                .name("Alpha Team")
                .settings("{\"color\": \"blue\"}")
                .build();

        Team saved = teamRepository.save(team);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Alpha Team");
        assertThat(saved.getSettings()).isEqualTo("{\"color\": \"blue\"}");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findTeamById_when_teamExists() {
        Tenant tenant = createTenant("FindTeam Tenant", "findteam-tenant");
        Team team = createTeam(tenant.getId(), "Find Team");

        Optional<Team> found = teamRepository.findById(team.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Find Team");
    }

    @Test
    void should_findByTenantId_when_teamsExist() {
        Tenant tenant = createTenant("Multi Team Tenant", "multi-team-tenant");
        createTeam(tenant.getId(), "Team Alpha");
        createTeam(tenant.getId(), "Team Beta");

        Tenant otherTenant = createTenant("Other Tenant", "other-team-tenant");
        createTeam(otherTenant.getId(), "Other Team");

        List<Team> teams = teamRepository.findByTenantId(tenant.getId());

        assertThat(teams).hasSize(2);
        assertThat(teams).allMatch(t -> t.getTenantId().equals(tenant.getId()));
        assertThat(teams).extracting(Team::getName)
                .containsExactlyInAnyOrder("Team Alpha", "Team Beta");
    }

    @Test
    void should_returnEmptyList_when_noTeamsForTenant() {
        List<Team> teams = teamRepository.findByTenantId(UUID.randomUUID());

        assertThat(teams).isEmpty();
    }

    @Test
    void should_deleteTeam_when_teamExists() {
        Tenant tenant = createTenant("DelTeam Tenant", "delteam-tenant");
        Team team = createTeam(tenant.getId(), "Delete Team");
        UUID teamId = team.getId();

        teamRepository.deleteById(teamId);
        entityManager.flush();

        assertThat(teamRepository.findById(teamId)).isEmpty();
    }

    @Test
    void should_updateTeam_when_fieldsChanged() {
        Tenant tenant = createTenant("UpdTeam Tenant", "updteam-tenant");
        Team team = createTeam(tenant.getId(), "Old Team Name");
        team.setName("New Team Name");
        team.setSettings("{\"updated\": true}");

        teamRepository.save(team);
        entityManager.flush();
        entityManager.clear();

        Team updated = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Team Name");
        assertThat(updated.getSettings()).isEqualTo("{\"updated\": true}");
    }

    // =========================================================================
    // UserTeamRepository Tests
    // =========================================================================

    @Test
    void should_saveUserTeam_when_validEntity() {
        Tenant tenant = createTenant("UT Save Tenant", "ut-save-tenant");
        User user = createUser(tenant.getId(), "utuser@example.com", "ext-ut-save");
        Team team = createTeam(tenant.getId(), "UT Save Team");

        UserTeam userTeam = UserTeam.builder()
                .userId(user.getId())
                .teamId(team.getId())
                .role("LEAD")
                .build();

        UserTeam saved = userTeamRepository.save(userTeam);
        entityManager.flush();

        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getTeamId()).isEqualTo(team.getId());
        assertThat(saved.getRole()).isEqualTo("LEAD");
    }

    @Test
    void should_findByCompositeId_when_userTeamExists() {
        Tenant tenant = createTenant("UT Find Tenant", "ut-find-tenant");
        User user = createUser(tenant.getId(), "utfind@example.com", "ext-ut-find");
        Team team = createTeam(tenant.getId(), "UT Find Team");

        UserTeam userTeam = UserTeam.builder()
                .userId(user.getId())
                .teamId(team.getId())
                .build();
        entityManager.persistAndFlush(userTeam);

        UserTeamId id = new UserTeamId(user.getId(), team.getId());
        Optional<UserTeam> found = userTeamRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo("MEMBER");
    }

    @Test
    void should_findByTeamId_when_membersExist() {
        Tenant tenant = createTenant("UT Team Tenant", "ut-team-tenant");
        User user1 = createUser(tenant.getId(), "utteam1@example.com", "ext-ut-team-1");
        User user2 = createUser(tenant.getId(), "utteam2@example.com", "ext-ut-team-2");
        Team team = createTeam(tenant.getId(), "UT Members Team");

        entityManager.persistAndFlush(UserTeam.builder()
                .userId(user1.getId()).teamId(team.getId()).role("LEAD").build());
        entityManager.persistAndFlush(UserTeam.builder()
                .userId(user2.getId()).teamId(team.getId()).role("MEMBER").build());

        List<UserTeam> members = userTeamRepository.findByTeamId(team.getId());

        assertThat(members).hasSize(2);
        assertThat(members).allMatch(ut -> ut.getTeamId().equals(team.getId()));
    }

    @Test
    void should_findByUserId_when_teamsExist() {
        Tenant tenant = createTenant("UT User Tenant", "ut-user-tenant");
        User user = createUser(tenant.getId(), "utuser2@example.com", "ext-ut-user-2");
        Team team1 = createTeam(tenant.getId(), "UT User Team 1");
        Team team2 = createTeam(tenant.getId(), "UT User Team 2");

        entityManager.persistAndFlush(UserTeam.builder()
                .userId(user.getId()).teamId(team1.getId()).build());
        entityManager.persistAndFlush(UserTeam.builder()
                .userId(user.getId()).teamId(team2.getId()).build());

        List<UserTeam> memberships = userTeamRepository.findByUserId(user.getId());

        assertThat(memberships).hasSize(2);
        assertThat(memberships).allMatch(ut -> ut.getUserId().equals(user.getId()));
    }

    @Test
    void should_deleteUserTeam_when_exists() {
        Tenant tenant = createTenant("UT Del Tenant", "ut-del-tenant");
        User user = createUser(tenant.getId(), "utdel@example.com", "ext-ut-del");
        Team team = createTeam(tenant.getId(), "UT Del Team");

        UserTeam userTeam = UserTeam.builder()
                .userId(user.getId()).teamId(team.getId()).build();
        entityManager.persistAndFlush(userTeam);

        UserTeamId id = new UserTeamId(user.getId(), team.getId());
        userTeamRepository.deleteById(id);
        entityManager.flush();

        assertThat(userTeamRepository.findById(id)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noMembersForTeam() {
        List<UserTeam> members = userTeamRepository.findByTeamId(UUID.randomUUID());

        assertThat(members).isEmpty();
    }

    // =========================================================================
    // AuthProviderConfigRepository Tests
    // =========================================================================

    @Test
    void should_saveAuthProviderConfig_when_validEntity() {
        Tenant tenant = createTenant("Auth Tenant", "auth-tenant-save");

        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(tenant.getId())
                .providerType("OIDC")
                .name("Corporate SSO")
                .enabled(true)
                .priority(10)
                .config("{\"issuerUri\": \"https://sso.example.com\"}")
                .build();

        AuthProviderConfig saved = authProviderConfigRepository.save(config);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProviderType()).isEqualTo("OIDC");
        assertThat(saved.getName()).isEqualTo("Corporate SSO");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPriority()).isEqualTo(10);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByTenantId_when_authConfigsExist() {
        Tenant tenant = createTenant("Auth Multi Tenant", "auth-multi-tenant");

        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("Corp LDAP")
                .config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("OIDC").name("Corp OIDC")
                .config("{}").build());

        List<AuthProviderConfig> configs =
                authProviderConfigRepository.findByTenantId(tenant.getId());

        assertThat(configs).hasSize(2);
        assertThat(configs).allMatch(c -> c.getTenantId().equals(tenant.getId()));
    }

    @Test
    void should_findByTenantIdAndProviderType_when_matching() {
        Tenant tenant = createTenant("Auth Type Tenant", "auth-type-tenant");

        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("LDAP 1")
                .config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("OIDC").name("OIDC 1")
                .config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("LDAP 2")
                .config("{}").build());

        List<AuthProviderConfig> ldapConfigs =
                authProviderConfigRepository.findByTenantIdAndProviderType(
                        tenant.getId(), "LDAP");

        assertThat(ldapConfigs).hasSize(2);
        assertThat(ldapConfigs).allMatch(c -> "LDAP".equals(c.getProviderType()));
    }

    @Test
    void should_findByTenantIdAndEnabled_when_matching() {
        Tenant tenant = createTenant("Auth Enabled Tenant", "auth-enabled-tenant");

        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("Enabled LDAP")
                .enabled(true).config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("OIDC").name("Disabled OIDC")
                .enabled(false).config("{}").build());

        List<AuthProviderConfig> enabledConfigs =
                authProviderConfigRepository.findByTenantIdAndEnabled(tenant.getId(), true);
        List<AuthProviderConfig> disabledConfigs =
                authProviderConfigRepository.findByTenantIdAndEnabled(tenant.getId(), false);

        assertThat(enabledConfigs).hasSize(1);
        assertThat(enabledConfigs.get(0).getName()).isEqualTo("Enabled LDAP");
        assertThat(disabledConfigs).hasSize(1);
        assertThat(disabledConfigs.get(0).getName()).isEqualTo("Disabled OIDC");
    }

    @Test
    void should_findByTenantIdAndEnabledOrderByPriorityAsc_when_multipleConfigs() {
        Tenant tenant = createTenant("Auth Priority Tenant", "auth-priority-tenant");

        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("OIDC").name("Low Priority")
                .enabled(true).priority(30).config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("High Priority")
                .enabled(true).priority(5).config("{}").build());
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("KEYCLOAK").name("Mid Priority")
                .enabled(true).priority(15).config("{}").build());
        // disabled config should not appear
        entityManager.persistAndFlush(AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("SAML").name("Disabled")
                .enabled(false).priority(1).config("{}").build());

        List<AuthProviderConfig> orderedConfigs =
                authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(
                        tenant.getId(), true);

        assertThat(orderedConfigs).hasSize(3);
        assertThat(orderedConfigs.get(0).getName()).isEqualTo("High Priority");
        assertThat(orderedConfigs.get(0).getPriority()).isEqualTo(5);
        assertThat(orderedConfigs.get(1).getName()).isEqualTo("Mid Priority");
        assertThat(orderedConfigs.get(1).getPriority()).isEqualTo(15);
        assertThat(orderedConfigs.get(2).getName()).isEqualTo("Low Priority");
        assertThat(orderedConfigs.get(2).getPriority()).isEqualTo(30);
    }

    @Test
    void should_deleteAuthProviderConfig_when_exists() {
        Tenant tenant = createTenant("Auth Del Tenant", "auth-del-tenant");

        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(tenant.getId()).providerType("LDAP").name("Delete Me")
                .config("{}").build();
        config = entityManager.persistFlushFind(config);
        UUID configId = config.getId();

        authProviderConfigRepository.deleteById(configId);
        entityManager.flush();

        assertThat(authProviderConfigRepository.findById(configId)).isEmpty();
    }

    // =========================================================================
    // SecurityGroupRepository Tests
    // =========================================================================

    @Test
    void should_saveSecurityGroup_when_validEntity() {
        Tenant tenant = createTenant("SG Save Tenant", "sg-save-tenant");

        SecurityGroup group = SecurityGroup.builder()
                .tenantId(tenant.getId())
                .name("Admins")
                .description("Administrator group")
                .accessLevel("ADMIN")
                .build();

        SecurityGroup saved = securityGroupRepository.save(group);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Admins");
        assertThat(saved.getDescription()).isEqualTo("Administrator group");
        assertThat(saved.getAccessLevel()).isEqualTo("ADMIN");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findByTenantId_when_securityGroupsExist() {
        Tenant tenant = createTenant("SG Multi Tenant", "sg-multi-tenant");

        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Group A").accessLevel("READ").build());
        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Group B").accessLevel("WRITE").build());

        Tenant otherTenant = createTenant("SG Other Tenant", "sg-other-tenant");
        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(otherTenant.getId()).name("Other Group").accessLevel("READ").build());

        List<SecurityGroup> groups = securityGroupRepository.findByTenantId(tenant.getId());

        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g -> g.getTenantId().equals(tenant.getId()));
    }

    @Test
    void should_findByTenantIdAndName_when_groupExists() {
        Tenant tenant = createTenant("SG Name Tenant", "sg-name-tenant");

        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Developers").accessLevel("WRITE").build());
        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Reviewers").accessLevel("READ").build());

        Optional<SecurityGroup> found =
                securityGroupRepository.findByTenantIdAndName(tenant.getId(), "Developers");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Developers");
        assertThat(found.get().getAccessLevel()).isEqualTo("WRITE");
    }

    @Test
    void should_returnEmpty_when_securityGroupNameNotFoundInTenant() {
        Tenant tenant = createTenant("SG Missing Tenant", "sg-missing-tenant");

        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Exists").accessLevel("READ").build());

        Optional<SecurityGroup> found =
                securityGroupRepository.findByTenantIdAndName(tenant.getId(), "DoesNotExist");

        assertThat(found).isEmpty();
    }

    @Test
    void should_returnEmpty_when_groupNameExistsInDifferentTenant() {
        Tenant tenant1 = createTenant("SG Tenant 1", "sg-tenant-1");
        Tenant tenant2 = createTenant("SG Tenant 2", "sg-tenant-2");

        entityManager.persistAndFlush(SecurityGroup.builder()
                .tenantId(tenant1.getId()).name("SharedName").accessLevel("READ").build());

        Optional<SecurityGroup> found =
                securityGroupRepository.findByTenantIdAndName(tenant2.getId(), "SharedName");

        assertThat(found).isEmpty();
    }

    @Test
    void should_deleteSecurityGroup_when_exists() {
        Tenant tenant = createTenant("SG Del Tenant", "sg-del-tenant");

        SecurityGroup group = SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Delete Group").accessLevel("READ").build();
        group = entityManager.persistFlushFind(group);
        UUID groupId = group.getId();

        securityGroupRepository.deleteById(groupId);
        entityManager.flush();

        assertThat(securityGroupRepository.findById(groupId)).isEmpty();
    }

    // =========================================================================
    // SecurityGroupMemberRepository Tests
    // =========================================================================

    @Test
    void should_saveSecurityGroupMember_when_validEntity() {
        Tenant tenant = createTenant("SGM Save Tenant", "sgm-save-tenant");

        SecurityGroup group = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Group").accessLevel("READ").build());

        SecurityGroupMember member = SecurityGroupMember.builder()
                .groupId(group.getId())
                .memberType("USER")
                .memberId(UUID.randomUUID())
                .build();

        SecurityGroupMember saved = securityGroupMemberRepository.save(member);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGroupId()).isEqualTo(group.getId());
        assertThat(saved.getMemberType()).isEqualTo("USER");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByGroupId_when_membersExist() {
        Tenant tenant = createTenant("SGM Find Tenant", "sgm-find-tenant");

        SecurityGroup group = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Find Group").accessLevel("READ").build());

        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("USER")
                .memberId(UUID.randomUUID()).build());
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("TEAM")
                .memberId(UUID.randomUUID()).build());

        List<SecurityGroupMember> members =
                securityGroupMemberRepository.findByGroupId(group.getId());

        assertThat(members).hasSize(2);
        assertThat(members).allMatch(m -> m.getGroupId().equals(group.getId()));
    }

    @Test
    void should_findByMemberTypeAndMemberId_when_memberExists() {
        Tenant tenant = createTenant("SGM Type Tenant", "sgm-type-tenant");

        SecurityGroup group1 = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Type Group 1").accessLevel("READ").build());
        SecurityGroup group2 = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Type Group 2").accessLevel("WRITE").build());

        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group1.getId()).memberType("USER").memberId(userId).build());
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group2.getId()).memberType("USER").memberId(userId).build());

        List<SecurityGroupMember> memberships =
                securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId);

        assertThat(memberships).hasSize(2);
        assertThat(memberships).allMatch(m -> m.getMemberId().equals(userId));
    }

    @Test
    void should_existsByGroupIdAndMemberTypeAndMemberId_when_memberExists() {
        Tenant tenant = createTenant("SGM Exists Tenant", "sgm-exists-tenant");

        SecurityGroup group = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Exists Group").accessLevel("READ").build());

        UUID memberId = UUID.randomUUID();
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("USER").memberId(memberId).build());

        boolean exists = securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(
                group.getId(), "USER", memberId);
        boolean notExists = securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(
                group.getId(), "USER", UUID.randomUUID());

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void should_deleteByGroupIdAndMemberTypeAndMemberId_when_memberExists() {
        Tenant tenant = createTenant("SGM Del Tenant", "sgm-del-tenant");

        SecurityGroup group = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("SGM Del Group").accessLevel("READ").build());

        UUID memberId = UUID.randomUUID();
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("USER").memberId(memberId).build());

        securityGroupMemberRepository.deleteByGroupIdAndMemberTypeAndMemberId(
                group.getId(), "USER", memberId);
        entityManager.flush();

        boolean exists = securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(
                group.getId(), "USER", memberId);
        assertThat(exists).isFalse();
    }

    @Test
    void should_returnEmptyList_when_noMembersInGroup() {
        List<SecurityGroupMember> members =
                securityGroupMemberRepository.findByGroupId(UUID.randomUUID());

        assertThat(members).isEmpty();
    }

    // =========================================================================
    // ResourcePermissionRepository Tests
    // =========================================================================

    @Test
    void should_saveResourcePermission_when_validEntity() {
        Tenant tenant = createTenant("RP Save Tenant", "rp-save-tenant");

        ResourcePermission permission = ResourcePermission.builder()
                .tenantId(tenant.getId())
                .resourceType("PROJECT")
                .resourceId(UUID.randomUUID())
                .granteeType("USER")
                .granteeId(UUID.randomUUID())
                .accessLevel("WRITE")
                .build();

        ResourcePermission saved = resourcePermissionRepository.save(permission);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getResourceType()).isEqualTo("PROJECT");
        assertThat(saved.getGranteeType()).isEqualTo("USER");
        assertThat(saved.getAccessLevel()).isEqualTo("WRITE");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByTenantIdAndResourceTypeAndResourceId_when_permissionsExist() {
        Tenant tenant = createTenant("RP Resource Tenant", "rp-resource-tenant");
        UUID projectId = UUID.randomUUID();

        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("PROJECT").resourceId(projectId)
                .granteeType("USER").granteeId(UUID.randomUUID()).accessLevel("READ").build());
        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("PROJECT").resourceId(projectId)
                .granteeType("TEAM").granteeId(UUID.randomUUID()).accessLevel("WRITE").build());
        // different resource
        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("PROJECT").resourceId(UUID.randomUUID())
                .granteeType("USER").granteeId(UUID.randomUUID()).accessLevel("READ").build());

        List<ResourcePermission> permissions =
                resourcePermissionRepository.findByTenantIdAndResourceTypeAndResourceId(
                        tenant.getId(), "PROJECT", projectId);

        assertThat(permissions).hasSize(2);
        assertThat(permissions).allMatch(p -> p.getResourceId().equals(projectId));
    }

    @Test
    void should_findByGranteeTypeAndGranteeId_when_permissionsExist() {
        Tenant tenant = createTenant("RP Grantee Tenant", "rp-grantee-tenant");
        UUID userId = UUID.randomUUID();

        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("PROJECT").resourceId(UUID.randomUUID())
                .granteeType("USER").granteeId(userId).accessLevel("READ").build());
        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("REPOSITORY").resourceId(UUID.randomUUID())
                .granteeType("USER").granteeId(userId).accessLevel("WRITE").build());

        List<ResourcePermission> permissions =
                resourcePermissionRepository.findByGranteeTypeAndGranteeId("USER", userId);

        assertThat(permissions).hasSize(2);
        assertThat(permissions).allMatch(p -> p.getGranteeId().equals(userId));
    }

    @Test
    void should_findByTenantIdAndGranteeTypeAndGranteeId_when_permissionsExist() {
        Tenant tenant1 = createTenant("RP Scoped Tenant 1", "rp-scoped-tenant-1");
        Tenant tenant2 = createTenant("RP Scoped Tenant 2", "rp-scoped-tenant-2");
        UUID teamId = UUID.randomUUID();

        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant1.getId()).resourceType("PROJECT").resourceId(UUID.randomUUID())
                .granteeType("TEAM").granteeId(teamId).accessLevel("READ").build());
        entityManager.persistAndFlush(ResourcePermission.builder()
                .tenantId(tenant2.getId()).resourceType("PROJECT").resourceId(UUID.randomUUID())
                .granteeType("TEAM").granteeId(teamId).accessLevel("WRITE").build());

        List<ResourcePermission> tenant1Perms =
                resourcePermissionRepository.findByTenantIdAndGranteeTypeAndGranteeId(
                        tenant1.getId(), "TEAM", teamId);
        List<ResourcePermission> tenant2Perms =
                resourcePermissionRepository.findByTenantIdAndGranteeTypeAndGranteeId(
                        tenant2.getId(), "TEAM", teamId);

        assertThat(tenant1Perms).hasSize(1);
        assertThat(tenant1Perms.get(0).getTenantId()).isEqualTo(tenant1.getId());
        assertThat(tenant2Perms).hasSize(1);
        assertThat(tenant2Perms.get(0).getTenantId()).isEqualTo(tenant2.getId());
    }

    @Test
    void should_returnEmptyList_when_noPermissionsForResource() {
        Tenant tenant = createTenant("RP Empty Tenant", "rp-empty-tenant");

        List<ResourcePermission> permissions =
                resourcePermissionRepository.findByTenantIdAndResourceTypeAndResourceId(
                        tenant.getId(), "PROJECT", UUID.randomUUID());

        assertThat(permissions).isEmpty();
    }

    @Test
    void should_deleteResourcePermission_when_exists() {
        Tenant tenant = createTenant("RP Del Tenant", "rp-del-tenant");

        ResourcePermission permission = ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("PROJECT").resourceId(UUID.randomUUID())
                .granteeType("USER").granteeId(UUID.randomUUID()).accessLevel("READ").build();
        permission = entityManager.persistFlushFind(permission);
        UUID permId = permission.getId();

        resourcePermissionRepository.deleteById(permId);
        entityManager.flush();

        assertThat(resourcePermissionRepository.findById(permId)).isEmpty();
    }

    @Test
    void should_savePermissionWithDefaultAccessLevel_when_notSpecified() {
        Tenant tenant = createTenant("RP Default Tenant", "rp-default-tenant");

        ResourcePermission permission = ResourcePermission.builder()
                .tenantId(tenant.getId()).resourceType("REPOSITORY").resourceId(UUID.randomUUID())
                .granteeType("USER").granteeId(UUID.randomUUID()).build();

        ResourcePermission saved = resourcePermissionRepository.save(permission);
        entityManager.flush();

        assertThat(saved.getAccessLevel()).isEqualTo("READ");
    }

    // =========================================================================
    // Cross-entity integration tests
    // =========================================================================

    @Test
    void should_cascadeDeleteUsers_when_tenantDeleted() {
        Tenant tenant = createTenant("Cascade Tenant", "cascade-tenant");
        User user = createUser(tenant.getId(), "cascade@example.com", "ext-cascade");

        UUID tenantId = tenant.getId();
        UUID userId = user.getId();

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM tenants WHERE id = :id")
                .setParameter("id", tenantId).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(tenantRepository.findById(tenantId)).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void should_cascadeDeleteTeams_when_tenantDeleted() {
        Tenant tenant = createTenant("Cascade Team Tenant", "cascade-team-tenant");
        Team team = createTeam(tenant.getId(), "Cascade Team");

        UUID tenantId = tenant.getId();
        UUID teamId = team.getId();

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM tenants WHERE id = :id")
                .setParameter("id", tenantId).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(tenantRepository.findById(tenantId)).isEmpty();
        assertThat(teamRepository.findById(teamId)).isEmpty();
    }

    @Test
    void should_cascadeDeleteUserTeams_when_userDeleted() {
        Tenant tenant = createTenant("Cascade UT Tenant", "cascade-ut-tenant");
        User user = createUser(tenant.getId(), "cascadeut@example.com", "ext-cascade-ut");
        Team team = createTeam(tenant.getId(), "Cascade UT Team");

        entityManager.persistAndFlush(UserTeam.builder()
                .userId(user.getId()).teamId(team.getId()).build());

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM users WHERE id = :id")
                .setParameter("id", user.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<UserTeam> memberships = userTeamRepository.findByTeamId(team.getId());
        assertThat(memberships).isEmpty();
    }

    @Test
    void should_cascadeDeleteGroupMembers_when_securityGroupDeleted() {
        Tenant tenant = createTenant("Cascade SG Tenant", "cascade-sg-tenant");

        SecurityGroup group = entityManager.persistFlushFind(SecurityGroup.builder()
                .tenantId(tenant.getId()).name("Cascade SG").accessLevel("READ").build());

        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("USER").memberId(UUID.randomUUID()).build());
        entityManager.persistAndFlush(SecurityGroupMember.builder()
                .groupId(group.getId()).memberType("TEAM").memberId(UUID.randomUUID()).build());

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM security_groups WHERE id = :id")
                .setParameter("id", group.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<SecurityGroupMember> members =
                securityGroupMemberRepository.findByGroupId(group.getId());
        assertThat(members).isEmpty();
    }

    @Test
    void should_isolateDataByTenantId_when_queryingUsers() {
        Tenant tenantA = createTenant("Isolation A", "isolation-a");
        Tenant tenantB = createTenant("Isolation B", "isolation-b");

        createUser(tenantA.getId(), "a1@example.com", "ext-iso-a1");
        createUser(tenantA.getId(), "a2@example.com", "ext-iso-a2");
        createUser(tenantB.getId(), "b1@example.com", "ext-iso-b1");

        List<User> tenantAUsers = userRepository.findByTenantId(tenantA.getId());
        List<User> tenantBUsers = userRepository.findByTenantId(tenantB.getId());

        assertThat(tenantAUsers).hasSize(2);
        assertThat(tenantAUsers).noneMatch(u -> u.getTenantId().equals(tenantB.getId()));
        assertThat(tenantBUsers).hasSize(1);
        assertThat(tenantBUsers).noneMatch(u -> u.getTenantId().equals(tenantA.getId()));
    }

    @Test
    void should_isolateDataByTenantId_when_queryingTeams() {
        Tenant tenantA = createTenant("Team Iso A", "team-iso-a");
        Tenant tenantB = createTenant("Team Iso B", "team-iso-b");

        createTeam(tenantA.getId(), "Team A1");
        createTeam(tenantA.getId(), "Team A2");
        createTeam(tenantA.getId(), "Team A3");
        createTeam(tenantB.getId(), "Team B1");

        List<Team> tenantATeams = teamRepository.findByTenantId(tenantA.getId());
        List<Team> tenantBTeams = teamRepository.findByTenantId(tenantB.getId());

        assertThat(tenantATeams).hasSize(3);
        assertThat(tenantBTeams).hasSize(1);
    }

    @Test
    void should_handleJsonbSettings_when_savingAndLoading() {
        Tenant tenant = createTenant("JSONB Tenant", "jsonb-tenant");
        tenant.setSettings("{\"theme\": \"dark\", \"notifications\": true}");
        tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();

        Tenant loaded = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(loaded.getSettings()).contains("\"theme\"");
        assertThat(loaded.getSettings()).contains("dark");
    }

    @Test
    void should_handleUserRolesJsonb_when_savingAndLoading() {
        Tenant tenant = createTenant("Roles Tenant", "roles-tenant");

        User user = User.builder()
                .tenantId(tenant.getId())
                .externalId("ext-roles-test")
                .email("roles@example.com")
                .displayName("Roles User")
                .role("ADMIN")
                .authProvider("oidc")
                .roles(Set.of("admin", "developer", "reviewer"))
                .build();

        user = entityManager.persistFlushFind(user);
        entityManager.clear();

        User loaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getRoles()).containsExactlyInAnyOrder("admin", "developer", "reviewer");
        assertThat(loaded.getAuthProvider()).isEqualTo("oidc");
    }
}
