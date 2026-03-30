package com.squadron.identity.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.squadron.common.security.AuthenticationResult;
import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.common.util.JsonUtils;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LDAP authentication provider supporting both Active Directory and OpenLDAP.
 */
@Component
public class LdapAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthProvider.class);

    private static final String DIRECTORY_TYPE_AD = "active_directory";
    private static final String DIRECTORY_TYPE_OPENLDAP = "openldap";

    private final TokenEncryptionService tokenEncryptionService;

    public LdapAuthProvider(TokenEncryptionService tokenEncryptionService) {
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public String getProviderType() {
        return SecurityConstants.AUTH_PROVIDER_LDAP;
    }

    @Override
    public boolean supports(String providerType) {
        return SecurityConstants.AUTH_PROVIDER_LDAP.equals(providerType);
    }

    @Override
    public AuthenticationResult authenticate(String username, String password, AuthProviderConfig config) {
        Map<String, Object> configMap = parseConfig(config.getConfig());

        String url = getString(configMap, "url");
        String baseDn = getString(configMap, "baseDn");
        String userSearchBase = getString(configMap, "userSearchBase", "");
        String userSearchFilter = getString(configMap, "userSearchFilter", "(uid={0})");
        String groupSearchBase = getString(configMap, "groupSearchBase", "");
        String groupSearchFilter = getString(configMap, "groupSearchFilter", "(member={0})");
        String bindDn = getString(configMap, "bindDn", "");
        String bindPassword = getString(configMap, "bindPassword", "");
        String adDomain = getString(configMap, "adDomain", "");
        String directoryType = getString(configMap, "directoryType", DIRECTORY_TYPE_OPENLDAP);

        @SuppressWarnings("unchecked")
        Map<String, String> roleMapping = configMap.containsKey("roleMapping")
                ? (Map<String, String>) configMap.get("roleMapping")
                : Collections.emptyMap();

        // Decrypt bind password if encrypted
        if (!bindPassword.isEmpty()) {
            try {
                bindPassword = tokenEncryptionService.decrypt(bindPassword);
            } catch (Exception e) {
                log.debug("Bind password does not appear to be encrypted, using as-is");
            }
        }

        try {
            // Step 1: Create an admin context to search for the user
            LdapContextSource searchContextSource = createContextSource(url, baseDn, bindDn, bindPassword);
            LdapTemplate searchTemplate = new LdapTemplate(searchContextSource);

            // Step 2: Search for the user DN
            String userDn = findUserDn(searchTemplate, userSearchBase, userSearchFilter, username, directoryType, adDomain);
            if (userDn == null) {
                throw new AuthenticationException("User not found in LDAP directory: " + username);
            }

            // Step 3: Bind as the user to verify password
            // Note: userDn from findUserDn is already a full DN (from getNameInNamespace())
            String bindDnForUser;
            if (DIRECTORY_TYPE_AD.equals(directoryType) && !adDomain.isEmpty()) {
                // AD: bind with userPrincipalName
                bindDnForUser = username.contains("@") ? username : username + "@" + adDomain;
            } else {
                // OpenLDAP: bind with the full DN (already includes base)
                bindDnForUser = userDn;
            }

            // Use empty base for user bind since bindDnForUser is already the full DN
            LdapContextSource userContextSource = createContextSource(url, "", bindDnForUser, password);
            try {
                userContextSource.getContext(bindDnForUser, password);
            } catch (Exception e) {
                throw new AuthenticationException("Invalid credentials for user: " + username);
            }

            // Step 4: Retrieve user attributes
            Map<String, String> userAttributes = getUserAttributes(searchTemplate, userSearchBase, userSearchFilter, username);

            // Step 5: Search for group memberships
            Set<String> ldapGroups = findUserGroups(searchTemplate, groupSearchBase, groupSearchFilter, userDn, baseDn, directoryType);

            // Step 6: Map LDAP groups to Squadron roles
            Set<String> roles = mapRoles(ldapGroups, roleMapping);
            if (roles.isEmpty()) {
                roles.add(SecurityConstants.ROLE_DEVELOPER); // default role
            }

            String email = userAttributes.getOrDefault("mail", username);
            String displayName = userAttributes.getOrDefault("displayName",
                    userAttributes.getOrDefault("cn", username));
            // userDn is already a full DN from getNameInNamespace()
            String externalId = userDn;

            return AuthenticationResult.builder()
                    .externalId(externalId)
                    .email(email)
                    .displayName(displayName)
                    .roles(roles)
                    .authProvider(SecurityConstants.AUTH_PROVIDER_LDAP)
                    .attributes(Map.of("ldapDn", externalId, "directoryType", directoryType))
                    .build();

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("LDAP authentication failed for user: {}", username, e);
            throw new AuthenticationException("LDAP authentication failed: " + e.getMessage());
        }
    }

    private LdapContextSource createContextSource(String url, String baseDn, String userDn, String password) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(url);
        contextSource.setBase(baseDn);
        if (userDn != null && !userDn.isEmpty()) {
            contextSource.setUserDn(userDn);
        }
        if (password != null && !password.isEmpty()) {
            contextSource.setPassword(password);
        }
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    private String findUserDn(LdapTemplate template, String userSearchBase, String userSearchFilter,
                               String username, String directoryType, String adDomain) {
        try {
            String filter;
            if (DIRECTORY_TYPE_AD.equals(directoryType)) {
                // Active Directory: search by sAMAccountName or userPrincipalName
                String sanitizedUsername = username.contains("@") ? username.split("@")[0] : username;
                filter = String.format("(|(sAMAccountName=%s)(userPrincipalName=%s@%s))",
                        sanitizedUsername, sanitizedUsername, adDomain);
            } else {
                // OpenLDAP: use configured filter
                filter = userSearchFilter.replace("{0}", username);
            }

            List<String> results = template.search(
                    LdapQueryBuilder.query()
                            .base(userSearchBase)
                            .filter(filter),
                    (AttributesMapper<String>) attrs -> {
                        Attribute dnAttr = attrs.get("dn");
                        return dnAttr != null ? dnAttr.get().toString() : null;
                    });

            // LdapTemplate search returns DNs relative to the base, so we get them from the NameClassPair
            if (results.isEmpty()) {
                // Try alternative search using raw filter
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                List<String> rawResults = template.search(
                        userSearchBase,
                        filter,
                        sc,
                        (AttributesMapper<String>) attrs -> "found");
                if (rawResults.isEmpty()) {
                    return null;
                }
            }

            // Use search that returns the DN via name
            SearchControls dnSearchControls = new SearchControls();
            dnSearchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            List<String> dnResults = template.search(
                    userSearchBase,
                    filter,
                    dnSearchControls,
                    (org.springframework.ldap.core.ContextMapper<String>) ctx -> ((javax.naming.Context) ctx).getNameInNamespace());

            return dnResults.isEmpty() ? null : dnResults.get(0);
        } catch (Exception e) {
            log.warn("User DN search failed for {}: {}", username, e.getMessage());
            return null;
        }
    }

    private Map<String, String> getUserAttributes(LdapTemplate template, String userSearchBase,
                                                    String userSearchFilter, String username) {
        try {
            String filter = userSearchFilter.replace("{0}", username);
            List<Map<String, String>> results = template.search(
                    userSearchBase,
                    filter,
                    SearchControls.SUBTREE_SCOPE,
                    (AttributesMapper<Map<String, String>>) attrs -> {
                        Map<String, String> map = new HashMap<>();
                        putIfPresent(map, attrs, "mail");
                        putIfPresent(map, attrs, "email");
                        putIfPresent(map, attrs, "displayName");
                        putIfPresent(map, attrs, "cn");
                        putIfPresent(map, attrs, "givenName");
                        putIfPresent(map, attrs, "sn");
                        putIfPresent(map, attrs, "uid");
                        putIfPresent(map, attrs, "sAMAccountName");
                        return map;
                    });

            if (!results.isEmpty()) {
                Map<String, String> attrs = results.get(0);
                // Normalize email attribute
                if (!attrs.containsKey("mail") && attrs.containsKey("email")) {
                    attrs.put("mail", attrs.get("email"));
                }
                return attrs;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to retrieve user attributes for {}: {}", username, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void putIfPresent(Map<String, String> map, Attributes attrs, String attrName) {
        try {
            Attribute attr = attrs.get(attrName);
            if (attr != null && attr.get() != null) {
                map.put(attrName, attr.get().toString());
            }
        } catch (NamingException e) {
            // ignore
        }
    }

    private Set<String> findUserGroups(LdapTemplate template, String groupSearchBase,
                                        String groupSearchFilter, String userDn,
                                        String baseDn, String directoryType) {
        Set<String> groups = new HashSet<>();
        try {
            String fullUserDn = userDn;
            String filter = groupSearchFilter.replace("{0}", fullUserDn);

            SearchControls groupSearchControls = new SearchControls();
            groupSearchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            List<String> groupDns = template.search(
                    groupSearchBase,
                    filter,
                    groupSearchControls,
                    (AttributesMapper<String>) attrs -> {
                        Attribute cn = attrs.get("cn");
                        Attribute dn = attrs.get("dn");
                        // Return the full DN for mapping
                        return cn != null ? cn.get().toString() : (dn != null ? dn.get().toString() : null);
                    });

            // Also get the full DNs for role mapping
            SearchControls fullDnSearchControls = new SearchControls();
            fullDnSearchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            List<String> fullGroupDns = template.search(
                    groupSearchBase,
                    filter,
                    fullDnSearchControls,
                    (org.springframework.ldap.core.ContextMapper<String>) ctx -> ((javax.naming.Context) ctx).getNameInNamespace());

            groups.addAll(groupDns);
            groups.addAll(fullGroupDns);

            // For AD, also check memberOf attribute on the user
            if (DIRECTORY_TYPE_AD.equals(directoryType)) {
                try {
                    String userFilter = "(distinguishedName=" + fullUserDn + ")";
                    List<Set<String>> memberOfResults = template.search(
                            "",
                            userFilter,
                            SearchControls.SUBTREE_SCOPE,
                            (AttributesMapper<Set<String>>) attrs -> {
                                Set<String> memberOf = new HashSet<>();
                                Attribute memberOfAttr = attrs.get("memberOf");
                                if (memberOfAttr != null) {
                                    NamingEnumeration<?> values = memberOfAttr.getAll();
                                    while (values.hasMore()) {
                                        memberOf.add(values.next().toString());
                                    }
                                }
                                return memberOf;
                            });
                    for (Set<String> memberOf : memberOfResults) {
                        groups.addAll(memberOf);
                    }
                } catch (Exception e) {
                    log.debug("Failed to read memberOf for AD user: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Group search failed for user DN {}: {}", userDn, e.getMessage());
        }
        return groups;
    }

    private Set<String> mapRoles(Set<String> ldapGroups, Map<String, String> roleMapping) {
        Set<String> roles = new HashSet<>();
        for (String ldapGroup : ldapGroups) {
            // Try exact match first
            if (roleMapping.containsKey(ldapGroup)) {
                roles.add(roleMapping.get(ldapGroup));
                continue;
            }
            // Try matching by CN
            for (Map.Entry<String, String> entry : roleMapping.entrySet()) {
                String mappingKey = entry.getKey();
                if (ldapGroup.equalsIgnoreCase(mappingKey) ||
                    ldapGroup.toLowerCase().contains(mappingKey.toLowerCase())) {
                    roles.add(entry.getValue());
                }
            }
        }
        return roles;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Collections.emptyMap();
        }
        return JsonUtils.fromJson(configJson, new TypeReference<Map<String, Object>>() {});
    }

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
