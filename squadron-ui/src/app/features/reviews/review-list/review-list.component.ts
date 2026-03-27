import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReviewService } from '../../../core/services/review.service';
import { Review, ReviewStatus } from '../../../core/models/review.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-review-list',
  standalone: true,
  imports: [RouterLink, FormsModule, TimeAgoPipe],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.scss',
})
export class ReviewListComponent implements OnInit {
  private reviewService = inject(ReviewService);
  reviews = signal<Review[]>([]);
  loading = signal(true);
  filterStatus = '';

  ngOnInit(): void { this.loadReviews(); }

  loadReviews(): void {
    this.loading.set(true);
    const status = this.filterStatus ? this.filterStatus as ReviewStatus : undefined;
    this.reviewService.getReviews(status).subscribe({
      next: (res) => { this.reviews.set(res.content); this.loading.set(false); },
      error: () => { this.reviews.set(this.getMockReviews()); this.loading.set(false); },
    });
  }

  statusClass(status: ReviewStatus): string {
    switch (status) {
      case ReviewStatus.APPROVED: return 'success';
      case ReviewStatus.CHANGES_REQUESTED: return 'warning';
      case ReviewStatus.REJECTED: return 'error';
      case ReviewStatus.IN_PROGRESS: return 'primary';
      default: return 'neutral';
    }
  }

  private getMockReviews(): Review[] {
    return [
      { id: '1', tenantId: '1', taskId: '7', taskTitle: 'Implement RBAC permissions', pullRequestUrl: 'https://github.com/org/repo/pull/35', pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING, severity: 'MAJOR' as any, comments: [], filesChanged: 12, linesAdded: 450, linesRemoved: 30, reviewerType: 'AI' as any, createdAt: new Date(Date.now() - 7200000).toISOString(), updatedAt: new Date(Date.now() - 3600000).toISOString() },
      { id: '2', tenantId: '1', taskId: '8', taskTitle: 'Update API documentation', pullRequestUrl: 'https://github.com/org/repo/pull/36', pullRequestNumber: 36, repositoryName: 'org/repo', status: ReviewStatus.APPROVED, severity: 'MINOR' as any, comments: [], filesChanged: 5, linesAdded: 120, linesRemoved: 45, reviewerType: 'AI' as any, createdAt: new Date(Date.now() - 86400000).toISOString(), updatedAt: new Date(Date.now() - 43200000).toISOString() },
      { id: '3', tenantId: '1', taskId: '5', taskTitle: 'Fix memory leak in WS handler', pullRequestUrl: 'https://github.com/org/repo/pull/37', pullRequestNumber: 37, repositoryName: 'org/repo', status: ReviewStatus.CHANGES_REQUESTED, severity: 'CRITICAL' as any, comments: [], filesChanged: 3, linesAdded: 25, linesRemoved: 80, reviewerType: 'HUMAN' as any, createdAt: new Date(Date.now() - 172800000).toISOString(), updatedAt: new Date(Date.now() - 86400000).toISOString() },
    ] as Review[];
  }
}
