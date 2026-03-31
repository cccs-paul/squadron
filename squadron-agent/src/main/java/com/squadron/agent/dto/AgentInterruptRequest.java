package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentInterruptRequest {
    private UUID conversationId;
    private String reason;  // optional: "USER_CANCEL", "USER_PROMPT", "TIMEOUT"
}
