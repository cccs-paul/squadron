package com.squadron.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewUpdatedEvent extends SquadronEvent {

    private UUID reviewId;
    private UUID taskId;
    private String reviewerType;
    private String status;

    {
        setEventType("REVIEW_UPDATED");
    }
}
