import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Review, ReviewStatus } from '../models/review.model';

@Injectable({ providedIn: 'root' })
export class ReviewService extends ApiService {
  getReviews(status?: ReviewStatus, page = 0, size = 20): Observable<PageResponse<Review>> {
    const params: Record<string, string | number> = { page, size };
    if (status) params['status'] = status;
    return this.get<PageResponse<Review>>('/reviews', params);
  }

  getReview(id: string): Observable<Review> {
    return this.get<Review>(`/reviews/${id}`);
  }

  approveReview(id: string, comment?: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/approve`, { comment });
  }

  rejectReview(id: string, reason: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/reject`, { reason });
  }

  requestChanges(id: string, comments: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/request-changes`, { comments });
  }

  addComment(reviewId: string, comment: { filePath: string; lineNumber?: number; body: string; severity: string; category: string }): Observable<void> {
    return this.post<void>(`/reviews/${reviewId}/comments`, comment);
  }

  resolveComment(reviewId: string, commentId: string): Observable<void> {
    return this.post<void>(`/reviews/${reviewId}/comments/${commentId}/resolve`, {});
  }
}
