package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private UUID conversationId;
    private UUID messageId;
    private String role;
    private String content;
    private Integer tokenCount;
    private String status;
}
