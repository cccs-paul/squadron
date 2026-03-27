package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private UUID userId;
    private String agentType;
    private String provider;
    private String model;
    private String status;
    private Long totalTokens;
    private Instant createdAt;
}
