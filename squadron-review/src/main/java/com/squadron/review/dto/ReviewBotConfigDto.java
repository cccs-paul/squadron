package com.squadron.review.dto;

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
public class ReviewBotConfigDto {
    private UUID id;
    private UUID tenantId;
    private UUID connectionId;
    private String botUsername;
    private boolean enabled;
    private boolean autoAssign;
    private Instant createdAt;
    private Instant updatedAt;
}
