package com.squadron.git.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that collects all {@link GitPlatformAdapter} beans
 * and provides lookup by platform type.
 */
@Component
public class GitPlatformAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(GitPlatformAdapterRegistry.class);

    private final Map<String, GitPlatformAdapter> adapters;

    public GitPlatformAdapterRegistry(List<GitPlatformAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(GitPlatformAdapter::getPlatformType, Function.identity()));
        log.info("Registered {} git platform adapters: {}", adapters.size(), adapters.keySet());
    }

    /**
     * Returns the adapter for the given platform type.
     *
     * @param platformType the platform type identifier (GITHUB, GITLAB, BITBUCKET)
     * @return the matching adapter
     * @throws IllegalArgumentException if no adapter is registered for the given type
     */
    public GitPlatformAdapter getAdapter(String platformType) {
        GitPlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            throw new IllegalArgumentException("No git platform adapter registered for type: " + platformType);
        }
        return adapter;
    }

    /**
     * Returns all registered platform types.
     */
    public List<String> getRegisteredPlatformTypes() {
        return List.copyOf(adapters.keySet());
    }
}
