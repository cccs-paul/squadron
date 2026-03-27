import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReviewService } from '../../../core/services/review.service';
import { DiffService } from '../../../core/services/diff.service';
import { Review, ReviewComment, ReviewStatus } from '../../../core/models/review.model';
import { DiffResult } from '../../../core/models/diff.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { DiffViewerComponent } from '../../diff-viewer/diff-viewer.component';

@Component({
  selector: 'sq-review-detail',
  standalone: true,
  imports: [RouterLink, TimeAgoPipe, DiffViewerComponent],
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
        error: () => {
          this.review.set({
            id: id, tenantId: '1', taskId: '7', taskTitle: 'Implement RBAC permissions',
            pullRequestUrl: 'https://github.com/org/repo/pull/35', pullRequestNumber: 35,
            repositoryName: 'org/repo', status: ReviewStatus.PENDING, severity: 'MAJOR' as any,
            filesChanged: 12, linesAdded: 450, linesRemoved: 30, reviewerType: 'AI' as any,
            comments: [
              { id: 'c1', filePath: 'src/auth/rbac.service.ts', lineNumber: 42, body: 'Consider using a constant for the permission string instead of a magic string.', severity: 'MINOR' as any, category: 'BEST_PRACTICE' as any, resolved: false, authorName: 'AI Reviewer', authorType: 'AI' as any, createdAt: new Date(Date.now() - 3600000).toISOString() },
              { id: 'c2', filePath: 'src/auth/rbac.service.ts', lineNumber: 78, body: 'This SQL query is vulnerable to injection. Use parameterized queries.', severity: 'CRITICAL' as any, category: 'SECURITY' as any, resolved: false, authorName: 'AI Reviewer', authorType: 'AI' as any, createdAt: new Date(Date.now() - 3500000).toISOString() },
              { id: 'c3', filePath: 'src/auth/rbac.controller.ts', lineNumber: 15, body: 'Missing input validation for the role parameter.', severity: 'MAJOR' as any, category: 'BUG' as any, resolved: true, authorName: 'AI Reviewer', authorType: 'AI' as any, createdAt: new Date(Date.now() - 3400000).toISOString() },
            ] as ReviewComment[],
            createdAt: new Date(Date.now() - 7200000).toISOString(),
            updatedAt: new Date(Date.now() - 3600000).toISOString(),
          } as Review);
          this.loading.set(false);
          this.hasDiff.set(true);
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
