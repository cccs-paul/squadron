/**
 * Service for managing review bot configurations.
 * Review bots post automated code review comments on PRs via the git platform.
 */
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { ReviewBotConfig, CreateReviewBotConfigRequest } from '../models/security.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class ReviewBotConfigService extends ApiService {

  /** Get all review bot configs for a tenant. */
  getConfigsByTenant(tenantId: string): Observable<ReviewBotConfig[]> {
    return this.get<ApiResponse<ReviewBotConfig[]>>(`/reviews/bot-config/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Create a new review bot configuration. */
  createConfig(request: CreateReviewBotConfigRequest): Observable<ReviewBotConfig> {
    return this.post<ApiResponse<ReviewBotConfig>>('/reviews/bot-config', request).pipe(
      map((response) => response.data),
    );
  }

  /** Update an existing review bot configuration. */
  updateConfig(id: string, request: Partial<CreateReviewBotConfigRequest>): Observable<ReviewBotConfig> {
    return this.put<ApiResponse<ReviewBotConfig>>(`/reviews/bot-config/${id}`, request).pipe(
      map((response) => response.data),
    );
  }

  /** Delete a review bot configuration. */
  deleteConfig(id: string): Observable<void> {
    return this.delete<void>(`/reviews/bot-config/${id}`);
  }
}
