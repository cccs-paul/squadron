package com.squadron.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentDto {

    private String filePath;

    private Integer lineNumber;

    @NotBlank
    private String body;

    private String severity;

    private String category;
}
