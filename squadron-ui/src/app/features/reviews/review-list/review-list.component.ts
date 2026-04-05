import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReviewService } from '../../../core/services/review.service';
import { Review, ReviewStatus } from '../../../core/models/review.model';
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
  reviews = signal<Review[]>([]);
  loading = signal(true);
  filterStatus = '';

  ngOnInit(): void { this.loadReviews(); }

  loadReviews(): void {
    this.loading.set(true);
    const status = this.filterStatus ? this.filterStatus as ReviewStatus : undefined;
    this.reviewService.getReviews(status).subscribe({
      next: (res) => { this.reviews.set(res.content); this.loading.set(false); },
      error: (err) => {
        console.error('Failed to load reviews', err);
        this.reviews.set([]);
        this.loading.set(false);
      },
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
}
