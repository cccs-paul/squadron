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
public class StreamChunk {
    private UUID conversationId;
    private UUID messageId;
    private String content;
    private String type;  // "chunk", "done", "error", "interrupted"
    private Integer tokenCount;
}
