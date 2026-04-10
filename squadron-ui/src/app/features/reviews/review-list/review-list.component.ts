import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReviewService } from '../../../core/services/review.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Review, ReviewStatus, ReviewerType } from '../../../core/models/review.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'sq-review-list',
  standalone: true,
  imports: [RouterLink, FormsModule, TimeAgoPipe, TranslateModule],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.scss',
})
export class ReviewListComponent implements OnInit {
  private reviewService = inject(ReviewService);
  private authService = inject(AuthService);
  private router = inject(Router);

  reviews = signal<Review[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  /** Server-side status filter */
  filterStatus = signal('');
  /** Client-side reviewer type filter */
  filterReviewerType = signal('');
  /** Client-side search query (matches task title, repo name, PR number) */
  searchQuery = signal('');
  /** Sort field */
  sortField = signal<'updatedAt' | 'createdAt' | 'linesAdded' | 'status'>('updatedAt');
  /** Sort direction */
  sortDirection = signal<'asc' | 'desc'>('desc');

  /** All status values for filter dropdown */
  allStatuses = Object.values(ReviewStatus);
  /** All reviewer types for filter dropdown */
  allReviewerTypes = Object.values(ReviewerType);

  /** Filtered + searched reviews (client-side from loaded data) */
  filteredReviews = computed(() => {
    let result = this.reviews();
    const search = this.searchQuery().toLowerCase().trim();
    const reviewerType = this.filterReviewerType();

    if (reviewerType) {
      result = result.filter(r => r.reviewerType === reviewerType);
    }

    if (search) {
      result = result.filter(r =>
        r.taskTitle?.toLowerCase().includes(search) ||
        r.repositoryName?.toLowerCase().includes(search) ||
        String(r.pullRequestNumber).includes(search) ||
        r.status?.toLowerCase().includes(search),
      );
    }

    return this.sortReviews(result);
  });

  /** Summary counts */
  totalReviews = computed(() => this.filteredReviews().length);
  pendingCount = computed(() => this.filteredReviews().filter(r => r.status === ReviewStatus.PENDING).length);
  inProgressCount = computed(() => this.filteredReviews().filter(r => r.status === ReviewStatus.IN_PROGRESS).length);
  approvedCount = computed(() => this.filteredReviews().filter(r => r.status === ReviewStatus.APPROVED).length);
  changesRequestedCount = computed(() => this.filteredReviews().filter(r => r.status === ReviewStatus.CHANGES_REQUESTED).length);
  rejectedCount = computed(() => this.filteredReviews().filter(r => r.status === ReviewStatus.REJECTED).length);

  /** Total lines changed across all filtered reviews */
  totalLinesChanged = computed(() =>
    this.filteredReviews().reduce((sum, r) => sum + (r.linesAdded || 0) + (r.linesRemoved || 0), 0),
  );

  /** Total unresolved comments across all filtered reviews */
  totalUnresolved = computed(() =>
    this.filteredReviews().reduce((sum, r) =>
      sum + (r.comments?.filter(c => !c.resolved).length ?? 0), 0),
  );

  ngOnInit(): void { this.loadReviews(); }

  loadReviews(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = this.filterStatus() ? this.filterStatus() as ReviewStatus : undefined;
    this.reviewService.getReviews(status).subscribe({
      next: (res) => {
        this.reviews.set(res.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.reviews.set([]);
        this.error.set(err?.message || 'Failed to load reviews');
        this.loading.set(false);
      },
    });
  }

  /** Navigate to review detail */
  openReview(review: Review): void {
    this.router.navigate(['/reviews', review.id]);
  }

  /** Refresh the list */
  refresh(): void {
    this.loadReviews();
  }

  /** Change server-side status filter and reload */
  onFilterStatusChange(status: string): void {
    this.filterStatus.set(status);
    this.loadReviews();
  }

  /** Toggle sort direction or change sort field */
  toggleSort(field: 'updatedAt' | 'createdAt' | 'linesAdded' | 'status'): void {
    if (this.sortField() === field) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('desc');
    }
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

  statusColor(status: ReviewStatus | string): string {
    switch (status) {
      case ReviewStatus.PENDING: return '#94A3B8';
      case ReviewStatus.IN_PROGRESS: return '#06B6D4';
      case ReviewStatus.APPROVED: return '#10B981';
      case ReviewStatus.CHANGES_REQUESTED: return '#F59E0B';
      case ReviewStatus.REJECTED: return '#EF4444';
      default: return '#94A3B8';
    }
  }

  unresolvedCount(review: Review): number {
    return review.comments?.filter(c => !c.resolved).length ?? 0;
  }

  private sortReviews(reviews: Review[]): Review[] {
    const field = this.sortField();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;

    return [...reviews].sort((a, b) => {
      switch (field) {
        case 'updatedAt':
        case 'createdAt':
          return dir * (new Date(a[field]).getTime() - new Date(b[field]).getTime());
        case 'linesAdded':
          return dir * ((a.linesAdded + a.linesRemoved) - (b.linesAdded + b.linesRemoved));
        case 'status':
          return dir * a.status.localeCompare(b.status);
        default:
          return 0;
      }
    });
  }
}
