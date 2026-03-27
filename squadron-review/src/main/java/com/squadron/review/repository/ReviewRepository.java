package com.squadron.review.repository;

import com.squadron.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByTaskId(UUID taskId);

    List<Review> findByTaskIdAndReviewerType(UUID taskId, String reviewerType);

    List<Review> findByReviewerId(UUID reviewerId);

    long countByTaskIdAndReviewerTypeAndStatus(UUID taskId, String reviewerType, String status);
}
