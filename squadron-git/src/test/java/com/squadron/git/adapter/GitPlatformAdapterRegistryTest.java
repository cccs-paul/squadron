package com.squadron.git.adapter;

import com.squadron.git.adapter.github.GitHubPlatformAdapter;
import com.squadron.git.adapter.gitlab.GitLabPlatformAdapter;
import com.squadron.git.adapter.bitbucket.BitbucketPlatformAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitPlatformAdapterRegistryTest {

    @Mock
    private GitPlatformAdapter githubAdapter;

    @Mock
    private GitPlatformAdapter gitlabAdapter;

    @Mock
    private GitPlatformAdapter bitbucketAdapter;

    private GitPlatformAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        when(githubAdapter.getPlatformType()).thenReturn("GITHUB");
        when(gitlabAdapter.getPlatformType()).thenReturn("GITLAB");
        when(bitbucketAdapter.getPlatformType()).thenReturn("BITBUCKET");

        registry = new GitPlatformAdapterRegistry(List.of(githubAdapter, gitlabAdapter, bitbucketAdapter));
    }

    @Test
    void should_returnAdapter_forGitHub() {
        GitPlatformAdapter adapter = registry.getAdapter("GITHUB");
        assertNotNull(adapter);
        assertEquals("GITHUB", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forGitLab() {
        GitPlatformAdapter adapter = registry.getAdapter("GITLAB");
        assertNotNull(adapter);
        assertEquals("GITLAB", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forBitbucket() {
        GitPlatformAdapter adapter = registry.getAdapter("BITBUCKET");
        assertNotNull(adapter);
        assertEquals("BITBUCKET", adapter.getPlatformType());
    }

    @Test
    void should_throwException_forUnknownPlatform() {
        assertThrows(IllegalArgumentException.class, () -> registry.getAdapter("UNKNOWN"));
    }

    @Test
    void should_returnAllRegisteredPlatformTypes() {
        List<String> types = registry.getRegisteredPlatformTypes();
        assertEquals(3, types.size());
        assertTrue(types.contains("GITHUB"));
        assertTrue(types.contains("GITLAB"));
        assertTrue(types.contains("BITBUCKET"));
    }

    @Test
    void should_handleEmptyAdapterList() {
        GitPlatformAdapterRegistry emptyRegistry = new GitPlatformAdapterRegistry(List.of());
        assertTrue(emptyRegistry.getRegisteredPlatformTypes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> emptyRegistry.getAdapter("GITHUB"));
    }
}
