import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReviewService } from '../../../core/services/review.service';
import { DiffService } from '../../../core/services/diff.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  Review, ReviewStatus, ReviewSummary, QAReport, QAVerdict,
  ReviewOrchestrationResult,
} from '../../../core/models/review.model';
import { DiffResult } from '../../../core/models/diff.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { DiffViewerComponent } from '../../diff-viewer/diff-viewer.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'sq-review-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, TimeAgoPipe, DiffViewerComponent, TranslateModule],
  templateUrl: './review-detail.component.html',
  styleUrl: './review-detail.component.scss',
})
export class ReviewDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private reviewService = inject(ReviewService);
  private diffService = inject(DiffService);
  private authService = inject(AuthService);

  review = signal<Review | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  activeTab = signal<'diff' | 'comments' | 'qa'>('diff');
  hasDiff = signal(false);

  /** Review gate summary for the task */
  reviewGate = signal<ReviewSummary | null>(null);
  gateLoading = signal(false);

  /** QA report for the task */
  qaReport = signal<QAReport | null>(null);
  qaGatePassed = signal<boolean | null>(null);
  qaLoading = signal(false);

  /** Request changes state */
  requestChangesText = signal('');
  requestingChanges = signal(false);

  /** Re-review orchestration */
  orchestrating = signal(false);
  orchestrationResult = signal<ReviewOrchestrationResult | null>(null);

  /** Comment resolution */
  resolvingCommentId = signal<string | null>(null);

  /** Approve/reject in-flight state */
  approving = signal(false);
  rejecting = signal(false);

  /** QA verdict enum for template comparison */
  QAVerdict = QAVerdict;

  /** Computed counts */
  unresolvedCount = computed(() =>
    this.review()?.comments.filter(c => !c.resolved).length ?? 0,
  );
  resolvedCount = computed(() =>
    this.review()?.comments.filter(c => c.resolved).length ?? 0,
  );
  totalComments = computed(() => this.review()?.comments.length ?? 0);

  /** Whether the review can be acted on */
  canAct = computed(() => {
    const status = this.review()?.status;
    return status === ReviewStatus.PENDING || status === ReviewStatus.IN_PROGRESS;
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadReview(id);
    }
  }

  private loadReview(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.reviewService.getReview(id).subscribe({
      next: (r) => {
        this.review.set(r);
        this.loading.set(false);
        this.checkDiff(r.taskId);
        this.loadReviewGate(r.taskId);
        this.loadQAReport(r.taskId);
      },
      error: (err) => {
        this.review.set(null);
        this.error.set(err?.message || 'Failed to load review');
        this.loading.set(false);
      },
    });
  }

  private checkDiff(taskId: string): void {
    this.diffService.getTaskDiff(taskId).subscribe({
      next: (result) => this.hasDiff.set(result.files.length > 0),
      error: () => this.hasDiff.set(false),
    });
  }

  private loadReviewGate(taskId: string): void {
    const user = this.authService.user();
    if (!user?.tenantId) return;
    this.gateLoading.set(true);
    this.reviewService.checkReviewGate(taskId, user.tenantId).subscribe({
      next: (summary) => {
        this.reviewGate.set(summary);
        this.gateLoading.set(false);
      },
      error: () => this.gateLoading.set(false),
    });
  }

  private loadQAReport(taskId: string): void {
    this.qaLoading.set(true);
    this.reviewService.getLatestQAReport(taskId).subscribe({
      next: (report) => {
        this.qaReport.set(report);
        this.qaLoading.set(false);
      },
      error: () => {
        this.qaReport.set(null);
        this.qaLoading.set(false);
      },
    });
    this.reviewService.checkQAGate(taskId).subscribe({
      next: (passed) => this.qaGatePassed.set(passed),
      error: () => this.qaGatePassed.set(null),
    });
  }

  // --- Actions ---

  approveReview(): void {
    const r = this.review();
    if (!r) return;
    this.approving.set(true);
    this.reviewService.approveReview(r.id).subscribe({
      next: (u) => {
        this.review.set(u);
        this.approving.set(false);
      },
      error: () => {
        this.review.update(v => v ? { ...v, status: ReviewStatus.APPROVED } : v);
        this.approving.set(false);
      },
    });
  }

  rejectReview(): void {
    const r = this.review();
    if (!r) return;
    this.rejecting.set(true);
    this.reviewService.rejectReview(r.id, 'Rejected by reviewer').subscribe({
      next: (u) => {
        this.review.set(u);
        this.rejecting.set(false);
      },
      error: () => {
        this.review.update(v => v ? { ...v, status: ReviewStatus.REJECTED } : v);
        this.rejecting.set(false);
      },
    });
  }

  requestChanges(): void {
    const r = this.review();
    const text = this.requestChangesText().trim();
    if (!r || !text) return;
    this.requestingChanges.set(true);
    this.reviewService.requestChanges(r.id, text).subscribe({
      next: (u) => {
        this.review.set(u);
        this.requestChangesText.set('');
        this.requestingChanges.set(false);
      },
      error: () => {
        this.review.update(v => v ? { ...v, status: ReviewStatus.CHANGES_REQUESTED } : v);
        this.requestChangesText.set('');
        this.requestingChanges.set(false);
      },
    });
  }

  resolveComment(commentId: string): void {
    const r = this.review();
    if (!r) return;
    this.resolvingCommentId.set(commentId);
    this.reviewService.resolveComment(r.id, commentId).subscribe({
      next: () => {
        this.review.update(v => {
          if (!v) return v;
          return {
            ...v,
            comments: v.comments.map(c =>
              c.id === commentId ? { ...c, resolved: true } : c,
            ),
          };
        });
        this.resolvingCommentId.set(null);
      },
      error: () => this.resolvingCommentId.set(null),
    });
  }

  triggerReReview(): void {
    const r = this.review();
    const user = this.authService.user();
    if (!r || !user?.tenantId) return;
    this.orchestrating.set(true);
    this.orchestrationResult.set(null);
    this.reviewService.orchestrateReview(r.taskId, user.tenantId).subscribe({
      next: (result) => {
        this.orchestrationResult.set(result);
        this.orchestrating.set(false);
        // Reload review gate
        this.loadReviewGate(r.taskId);
      },
      error: () => this.orchestrating.set(false),
    });
  }

  // --- Tab management ---

  setTab(tab: 'diff' | 'comments' | 'qa'): void {
    this.activeTab.set(tab);
  }

  // --- Utility methods ---

  severityClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'error';
      case 'MAJOR': return 'warning';
      case 'MINOR': return 'primary';
      default: return 'neutral';
    }
  }

  verdictClass(verdict: QAVerdict): string {
    switch (verdict) {
      case QAVerdict.PASS: return 'success';
      case QAVerdict.CONDITIONAL_PASS: return 'warning';
      case QAVerdict.FAIL: return 'error';
      default: return 'neutral';
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

  coveragePercent(value?: number): string {
    if (value === undefined || value === null) return '-';
    return (value * 100).toFixed(1) + '%';
  }
}
