package com.squadron.review.repository;

import com.squadron.review.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

    List<ReviewComment> findByReviewId(UUID reviewId);

    List<ReviewComment> findByReviewIdAndSeverity(UUID reviewId, String severity);
}
