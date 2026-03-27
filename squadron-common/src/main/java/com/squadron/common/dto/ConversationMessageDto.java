package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageDto {

    private UUID id;
    private UUID conversationId;
    private String role;
    private String content;
    private Map<String, Object> toolCalls;
    private Integer tokenCount;
    private Instant createdAt;
}
