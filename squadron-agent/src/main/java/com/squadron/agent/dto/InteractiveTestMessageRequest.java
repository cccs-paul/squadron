package com.squadron.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to send a message in an interactive test session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveTestMessageRequest {

    @NotNull(message = "Session ID is required")
    private UUID sessionId;

    @NotBlank(message = "Message is required")
    private String message;
}
