import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReviewService } from '../../../core/services/review.service';
import { DiffService } from '../../../core/services/diff.service';
import { Review, ReviewStatus } from '../../../core/models/review.model';
import { DiffResult } from '../../../core/models/diff.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { DiffViewerComponent } from '../../diff-viewer/diff-viewer.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'sq-review-detail',
  standalone: true,
  imports: [RouterLink, TimeAgoPipe, DiffViewerComponent, TranslateModule],
  templateUrl: './review-detail.component.html',
  styleUrl: './review-detail.component.scss',
})
export class ReviewDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private reviewService = inject(ReviewService);
  private diffService = inject(DiffService);

  review = signal<Review | null>(null);
  loading = signal(true);
  activeTab = signal<'diff' | 'comments'>('diff');
  hasDiff = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.reviewService.getReview(id).subscribe({
        next: (r) => {
          this.review.set(r);
          this.loading.set(false);
          this.checkDiff(r.taskId);
        },
        error: (err) => {
          console.error('Failed to load review', err);
          this.review.set(null);
          this.loading.set(false);
        },
      });
    }
  }

  private checkDiff(taskId: string): void {
    this.diffService.getTaskDiff(taskId).subscribe({
      next: (result) => {
        this.hasDiff.set(result.files.length > 0);
      },
      error: () => {
        this.hasDiff.set(false);
      },
    });
  }

  approveReview(): void {
    const r = this.review();
    if (r) this.reviewService.approveReview(r.id).subscribe({ next: (u) => this.review.set(u), error: () => this.review.update(v => v ? { ...v, status: ReviewStatus.APPROVED } : v) });
  }

  rejectReview(): void {
    const r = this.review();
    if (r) this.review.update(v => v ? { ...v, status: ReviewStatus.REJECTED } : v);
  }

  setTab(tab: 'diff' | 'comments'): void {
    this.activeTab.set(tab);
  }

  severityClass(severity: string): string {
    switch (severity) { case 'CRITICAL': return 'error'; case 'MAJOR': return 'warning'; case 'MINOR': return 'primary'; default: return 'neutral'; }
  }

  unresolvedCount(): number {
    return this.review()?.comments.filter(c => !c.resolved).length ?? 0;
  }

  resolvedCount(): number {
    return this.review()?.comments.filter(c => c.resolved).length ?? 0;
  }
}
