import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import {
  Review, ReviewStatus, ReviewSummary, QAReport,
  ReviewOrchestrationResult, SubmitReviewRequest,
} from '../models/review.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class ReviewService extends ApiService {

  // --- Review CRUD ---

  getReviews(status?: ReviewStatus, page = 0, size = 20): Observable<PageResponse<Review>> {
    const params: Record<string, string | number> = { page, size };
    if (status) params['status'] = status;
    return this.get<PageResponse<Review>>('/reviews', params);
  }

  getReview(id: string): Observable<Review> {
    return this.get<Review>(`/reviews/${id}`);
  }

  getReviewsForTask(taskId: string): Observable<Review[]> {
    return this.http.get<ApiResponse<Review[]>>(`${this.baseUrl}/reviews/task/${taskId}`)
      .pipe(map(r => r.data));
  }

  deleteReview(id: string): Observable<void> {
    return this.delete<void>(`/reviews/${id}`);
  }

  // --- Review actions ---

  approveReview(id: string, comment?: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/approve`, { comment });
  }

  rejectReview(id: string, reason: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/reject`, { reason });
  }

  requestChanges(id: string, comments: string): Observable<Review> {
    return this.post<Review>(`/reviews/${id}/request-changes`, { comments });
  }

  submitReview(request: SubmitReviewRequest): Observable<Review> {
    return this.http.post<ApiResponse<Review>>(`${this.baseUrl}/reviews/submit`, request)
      .pipe(map(r => r.data));
  }

  // --- Comments ---

  addComment(reviewId: string, comment: { filePath: string; lineNumber?: number; body: string; severity: string; category: string }): Observable<void> {
    return this.post<void>(`/reviews/${reviewId}/comments`, comment);
  }

  resolveComment(reviewId: string, commentId: string): Observable<void> {
    return this.post<void>(`/reviews/${reviewId}/comments/${commentId}/resolve`, {});
  }

  // --- Review gate ---

  checkReviewGate(taskId: string, tenantId: string, teamId?: string): Observable<ReviewSummary> {
    const params: Record<string, string> = { tenantId };
    if (teamId) params['teamId'] = teamId;
    return this.http.get<ApiResponse<ReviewSummary>>(`${this.baseUrl}/reviews/task/${taskId}/gate`, {
      params,
    }).pipe(map(r => r.data));
  }

  // --- Review orchestration ---

  orchestrateReview(taskId: string, tenantId: string, teamId?: string): Observable<ReviewOrchestrationResult> {
    const params: Record<string, string> = { tenantId };
    if (teamId) params['teamId'] = teamId;
    return this.http.post<ApiResponse<ReviewOrchestrationResult>>(
      `${this.baseUrl}/reviews/orchestration/task/${taskId}`, null, { params },
    ).pipe(map(r => r.data));
  }

  checkAndTransition(taskId: string, tenantId: string, teamId?: string): Observable<boolean> {
    const params: Record<string, string> = { tenantId };
    if (teamId) params['teamId'] = teamId;
    return this.http.get<ApiResponse<boolean>>(
      `${this.baseUrl}/reviews/orchestration/task/${taskId}/check`, { params },
    ).pipe(map(r => r.data));
  }

  // --- QA Reports ---

  getQAReports(taskId: string): Observable<QAReport[]> {
    return this.http.get<ApiResponse<QAReport[]>>(`${this.baseUrl}/qa-reports/task/${taskId}`)
      .pipe(map(r => r.data));
  }

  getLatestQAReport(taskId: string): Observable<QAReport> {
    return this.http.get<ApiResponse<QAReport>>(`${this.baseUrl}/qa-reports/task/${taskId}/latest`)
      .pipe(map(r => r.data));
  }

  checkQAGate(taskId: string): Observable<boolean> {
    return this.http.get<ApiResponse<boolean>>(`${this.baseUrl}/qa-reports/task/${taskId}/gate`)
      .pipe(map(r => r.data));
  }
}
