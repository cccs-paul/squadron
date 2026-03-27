package com.squadron.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private UUID conversationId;

    @NotNull(message = "Task ID is required")
    private UUID taskId;

    @NotBlank(message = "Agent type is required")
    private String agentType;

    @NotBlank(message = "Message is required")
    private String message;
}
