package com.squadron.agent.service;

import com.squadron.agent.dto.AgentProgressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentSessionManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<UUID, Disposable> activeStreams = new ConcurrentHashMap<>();
    private final Map<UUID, AgentProgressDto> progressMap = new ConcurrentHashMap<>();

    public AgentSessionManager(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void registerStream(UUID conversationId, Disposable disposable) {
        activeStreams.put(conversationId, disposable);
        log.debug("Registered active stream for conversation {}", conversationId);
    }

    public boolean cancelStream(UUID conversationId) {
        Disposable disposable = activeStreams.remove(conversationId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            progressMap.remove(conversationId);
            log.info("Cancelled stream for conversation {}", conversationId);
            return true;
        }
        return false;
    }

    public void removeStream(UUID conversationId) {
        activeStreams.remove(conversationId);
        progressMap.remove(conversationId);
    }

    public boolean isActive(UUID conversationId) {
        Disposable d = activeStreams.get(conversationId);
        return d != null && !d.isDisposed();
    }

    public void updateProgress(UUID conversationId, AgentProgressDto progress) {
        progressMap.put(conversationId, progress);
        messagingTemplate.convertAndSend(
                "/topic/progress/" + conversationId, progress);
    }

    public AgentProgressDto getProgress(UUID conversationId) {
        return progressMap.get(conversationId);
    }

    public int getActiveSessionCount() {
        // Clean up disposed entries
        activeStreams.entrySet().removeIf(e -> e.getValue().isDisposed());
        return activeStreams.size();
    }
}
