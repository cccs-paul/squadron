package com.squadron.platform.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that collects all {@link TicketingPlatformAdapter} beans
 * and provides lookup by platform type.
 */
@Component
public class PlatformAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdapterRegistry.class);

    private final Map<String, TicketingPlatformAdapter> adapters;

    public PlatformAdapterRegistry(List<TicketingPlatformAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(TicketingPlatformAdapter::getPlatformType, Function.identity()));
        log.info("Registered {} platform adapters: {}", adapters.size(), adapters.keySet());
    }

    /**
     * Returns the adapter for the given platform type.
     *
     * @param platformType the platform type identifier
     * @return the matching adapter
     * @throws IllegalArgumentException if no adapter is registered for the given type
     */
    public TicketingPlatformAdapter getAdapter(String platformType) {
        TicketingPlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for platform type: " + platformType);
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
