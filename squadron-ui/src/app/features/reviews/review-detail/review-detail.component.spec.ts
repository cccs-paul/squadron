import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewDetailComponent } from './review-detail.component';
import { ReviewService } from '../../../core/services/review.service';
import { DiffService } from '../../../core/services/diff.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import {
  Review, ReviewStatus, ReviewSeverity, ReviewerType, ReviewCategory,
  ReviewSummary, QAReport, QAVerdict, ReviewOrchestrationResult,
} from '../../../core/models/review.model';
import { DiffResult } from '../../../core/models/diff.model';
import { TranslateModule } from '@ngx-translate/core';
import { signal } from '@angular/core';

function makeReview(overrides: Partial<Review> = {}): Review {
  return {
    id: 'r1',
    tenantId: 't1',
    taskId: 'task1',
    taskTitle: 'Implement RBAC',
    pullRequestUrl: 'https://github.com/org/repo/pull/35',
    pullRequestNumber: 35,
    repositoryName: 'org/repo',
    status: ReviewStatus.PENDING,
    severity: ReviewSeverity.MAJOR,
    comments: [],
    filesChanged: 5,
    linesAdded: 100,
    linesRemoved: 10,
    reviewerType: ReviewerType.AI,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeComment(overrides: Partial<any> = {}) {
  return {
    id: 'c1', filePath: 'a.ts', lineNumber: 1, body: 'Fix this',
    severity: ReviewSeverity.MAJOR, category: ReviewCategory.BUG,
    resolved: false, authorName: 'Vega', authorType: ReviewerType.AI,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

const mockDiffResult: DiffResult = {
  files: [{ filename: 'src/app.ts', status: 'modified', additions: 10, deletions: 3, patch: '@@ -1,5 +1,12 @@\n-old\n+new' }],
  totalAdditions: 10,
  totalDeletions: 3,
};

const mockAuthUser = { id: 'u1', tenantId: 'tenant1', username: 'dev', email: 'dev@test.com', displayName: 'Dev', tenantName: 'Test', roles: ['developer'], permissions: [] };

describe('ReviewDetailComponent', () => {
  let component: ReviewDetailComponent;
  let fixture: ComponentFixture<ReviewDetailComponent>;
  let reviewServiceSpy: jasmine.SpyObj<ReviewService>;
  let diffServiceSpy: jasmine.SpyObj<DiffService>;

  beforeEach(async () => {
    reviewServiceSpy = jasmine.createSpyObj('ReviewService', [
      'getReview', 'approveReview', 'rejectReview', 'requestChanges',
      'resolveComment', 'checkReviewGate', 'orchestrateReview',
      'getLatestQAReport', 'checkQAGate',
    ]);
    diffServiceSpy = jasmine.createSpyObj('DiffService', ['getTaskDiff', 'getCodeGenerationStatus', 'getPullRequestDiff']);
    diffServiceSpy.getTaskDiff.and.returnValue(of(mockDiffResult));

    // Default: gate and QA return errors (not available)
    reviewServiceSpy.checkReviewGate.and.returnValue(throwError(() => new Error('no gate')));
    reviewServiceSpy.getLatestQAReport.and.returnValue(throwError(() => new Error('no report')));
    reviewServiceSpy.checkQAGate.and.returnValue(throwError(() => new Error('no gate')));

    const authServiceMock = { user: signal(mockAuthUser), isAuthenticated: signal(true), isAdmin: signal(false) };

    await TestBed.configureTestingModule({
      imports: [ReviewDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReviewService, useValue: reviewServiceSpy },
        { provide: DiffService, useValue: diffServiceSpy },
        { provide: AuthService, useValue: authServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'r1' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewDetailComponent);
    component = fixture.componentInstance;
  });

  // --- Creation ---

  it('should_create', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // --- Loading / Error ---

  it('should_loadReview_when_initialized', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    expect(component.review()!.taskTitle).toBe('Implement RBAC');
    expect(component.loading()).toBeFalse();
  });

  it('should_showNullReview_when_apiReturnsError', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('not found')));
    fixture.detectChanges();
    expect(component.review()).toBeNull();
    expect(component.error()).toBe('not found');
    expect(component.loading()).toBeFalse();
  });

  it('should_setDefaultErrorMessage_when_apiErrorHasNoMessage', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => ({})));
    fixture.detectChanges();
    expect(component.error()).toBe('Failed to load review');
  });

  // --- checkDiff on load ---

  it('should_setHasDiffTrue_when_diffServiceReturnsFiles', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    fixture.detectChanges();
    expect(component.hasDiff()).toBeTrue();
  });

  it('should_setHasDiffFalse_when_diffServiceFails', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    diffServiceSpy.getTaskDiff.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.hasDiff()).toBeFalse();
  });

  it('should_setHasDiffFalse_when_diffServiceReturnsEmpty', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    diffServiceSpy.getTaskDiff.and.returnValue(of({ files: [], totalAdditions: 0, totalDeletions: 0 }));
    fixture.detectChanges();
    expect(component.hasDiff()).toBeFalse();
  });

  // --- loadReviewGate on load ---

  it('should_loadReviewGate_when_reviewLoaded', () => {
    const gate: ReviewSummary = {
      taskId: 'task1', totalReviews: 2, humanApprovals: 1,
      aiApproval: true, policyMet: true, reviews: [],
    };
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.checkReviewGate.and.returnValue(of(gate));
    fixture.detectChanges();
    expect(component.reviewGate()!.policyMet).toBeTrue();
    expect(component.gateLoading()).toBeFalse();
  });

  it('should_handleGateError_when_gateServiceFails', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.checkReviewGate.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.reviewGate()).toBeNull();
    expect(component.gateLoading()).toBeFalse();
  });

  // --- loadQAReport on load ---

  it('should_loadQAReport_when_reviewLoaded', () => {
    const qaReport: QAReport = {
      id: 'qa1', tenantId: 't1', taskId: 'task1', verdict: QAVerdict.PASS,
      summary: 'All good', lineCoverage: 0.85, branchCoverage: 0.72,
      testsPassed: 42, testsFailed: 0, testsSkipped: 2, createdAt: new Date().toISOString(),
    };
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.getLatestQAReport.and.returnValue(of(qaReport));
    reviewServiceSpy.checkQAGate.and.returnValue(of(true));
    fixture.detectChanges();
    expect(component.qaReport()!.verdict).toBe(QAVerdict.PASS);
    expect(component.qaGatePassed()).toBeTrue();
    expect(component.qaLoading()).toBeFalse();
  });

  it('should_handleQAReportError_when_qaServiceFails', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.getLatestQAReport.and.returnValue(throwError(() => new Error('fail')));
    reviewServiceSpy.checkQAGate.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.qaReport()).toBeNull();
    expect(component.qaGatePassed()).toBeNull();
    expect(component.qaLoading()).toBeFalse();
  });

  // --- Approve ---

  it('should_approveReview_when_approveCalledSuccess', () => {
    const review = makeReview();
    const approved = { ...review, status: ReviewStatus.APPROVED };
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.approveReview.and.returnValue(of(approved));
    component.approveReview();
    expect(reviewServiceSpy.approveReview).toHaveBeenCalledWith('r1');
    expect(component.review()!.status).toBe(ReviewStatus.APPROVED);
    expect(component.approving()).toBeFalse();
  });

  it('should_optimisticallyApprove_when_approveApiErrors', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.approveReview.and.returnValue(throwError(() => new Error('fail')));
    component.approveReview();
    expect(component.review()!.status).toBe(ReviewStatus.APPROVED);
    expect(component.approving()).toBeFalse();
  });

  it('should_doNothing_when_approveCalledWithNoReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.approveReview();
    expect(reviewServiceSpy.approveReview).not.toHaveBeenCalled();
  });

  // --- Reject ---

  it('should_rejectReview_when_rejectCalledSuccess', () => {
    const review = makeReview();
    const rejected = { ...review, status: ReviewStatus.REJECTED };
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.rejectReview.and.returnValue(of(rejected));
    component.rejectReview();
    expect(reviewServiceSpy.rejectReview).toHaveBeenCalledWith('r1', 'Rejected by reviewer');
    expect(component.review()!.status).toBe(ReviewStatus.REJECTED);
    expect(component.rejecting()).toBeFalse();
  });

  it('should_optimisticallyReject_when_rejectApiErrors', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.rejectReview.and.returnValue(throwError(() => new Error('fail')));
    component.rejectReview();
    expect(component.review()!.status).toBe(ReviewStatus.REJECTED);
    expect(component.rejecting()).toBeFalse();
  });

  it('should_doNothing_when_rejectCalledWithNoReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.rejectReview();
    expect(reviewServiceSpy.rejectReview).not.toHaveBeenCalled();
  });

  // --- Request Changes ---

  it('should_requestChanges_when_calledWithText', () => {
    const review = makeReview();
    const changed = { ...review, status: ReviewStatus.CHANGES_REQUESTED };
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.requestChanges.and.returnValue(of(changed));
    component.requestChangesText.set('Please fix formatting');
    component.requestChanges();
    expect(reviewServiceSpy.requestChanges).toHaveBeenCalledWith('r1', 'Please fix formatting');
    expect(component.review()!.status).toBe(ReviewStatus.CHANGES_REQUESTED);
    expect(component.requestChangesText()).toBe('');
    expect(component.requestingChanges()).toBeFalse();
  });

  it('should_optimisticallyRequestChanges_when_apiErrors', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.requestChanges.and.returnValue(throwError(() => new Error('fail')));
    component.requestChangesText.set('Fix this');
    component.requestChanges();
    expect(component.review()!.status).toBe(ReviewStatus.CHANGES_REQUESTED);
    expect(component.requestChangesText()).toBe('');
    expect(component.requestingChanges()).toBeFalse();
  });

  it('should_doNothing_when_requestChangesTextIsEmpty', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    component.requestChangesText.set('   ');
    component.requestChanges();
    expect(reviewServiceSpy.requestChanges).not.toHaveBeenCalled();
  });

  it('should_doNothing_when_requestChangesCalledWithNoReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.requestChangesText.set('Fix this');
    component.requestChanges();
    expect(reviewServiceSpy.requestChanges).not.toHaveBeenCalled();
  });

  // --- Resolve Comment ---

  it('should_resolveComment_when_calledSuccess', () => {
    const review = makeReview({
      comments: [makeComment({ id: 'c1', resolved: false }), makeComment({ id: 'c2', resolved: false })],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.resolveComment.and.returnValue(of(undefined as any));
    component.resolveComment('c1');
    expect(reviewServiceSpy.resolveComment).toHaveBeenCalledWith('r1', 'c1');
    expect(component.review()!.comments.find(c => c.id === 'c1')!.resolved).toBeTrue();
    expect(component.review()!.comments.find(c => c.id === 'c2')!.resolved).toBeFalse();
    expect(component.resolvingCommentId()).toBeNull();
  });

  it('should_resetResolvingId_when_resolveCommentFails', () => {
    const review = makeReview({ comments: [makeComment({ id: 'c1', resolved: false })] });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.resolveComment.and.returnValue(throwError(() => new Error('fail')));
    component.resolveComment('c1');
    expect(component.resolvingCommentId()).toBeNull();
    expect(component.review()!.comments[0].resolved).toBeFalse();
  });

  it('should_doNothing_when_resolveCommentCalledWithNoReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.resolveComment('c1');
    expect(reviewServiceSpy.resolveComment).not.toHaveBeenCalled();
  });

  // --- Trigger Re-Review ---

  it('should_triggerReReview_when_calledSuccess', () => {
    const review = makeReview();
    const orchResult: ReviewOrchestrationResult = {
      taskId: 'task1', createdReviewIds: ['r10', 'r11'],
      aiReviewCreated: true, pendingHumanReviews: 1, policyMet: false,
    };
    reviewServiceSpy.getReview.and.returnValue(of(review));
    reviewServiceSpy.checkReviewGate.and.returnValue(throwError(() => new Error('not yet')));
    fixture.detectChanges();

    reviewServiceSpy.orchestrateReview.and.returnValue(of(orchResult));
    // After orchestration, it reloads the gate
    const gate: ReviewSummary = {
      taskId: 'task1', totalReviews: 3, humanApprovals: 1,
      aiApproval: true, policyMet: false, reviews: [],
    };
    reviewServiceSpy.checkReviewGate.and.returnValue(of(gate));
    component.triggerReReview();
    expect(reviewServiceSpy.orchestrateReview).toHaveBeenCalledWith('task1', 'tenant1');
    expect(component.orchestrationResult()!.createdReviewIds.length).toBe(2);
    expect(component.orchestrating()).toBeFalse();
  });

  it('should_handleError_when_reReviewFails', () => {
    const review = makeReview();
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.orchestrateReview.and.returnValue(throwError(() => new Error('fail')));
    component.triggerReReview();
    expect(component.orchestrating()).toBeFalse();
    expect(component.orchestrationResult()).toBeNull();
  });

  it('should_doNothing_when_reReviewCalledWithNoReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.triggerReReview();
    expect(reviewServiceSpy.orchestrateReview).not.toHaveBeenCalled();
  });

  // --- Tabs ---

  it('should_defaultToDiffTab_when_initialized', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.activeTab()).toBe('diff');
  });

  it('should_switchToCommentsTab_when_setTabCalled', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('comments');
    expect(component.activeTab()).toBe('comments');
  });

  it('should_switchToQATab_when_setTabCalled', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('qa');
    expect(component.activeTab()).toBe('qa');
  });

  it('should_switchBackToDiff_when_setTabCalledWithDiff', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('comments');
    component.setTab('diff');
    expect(component.activeTab()).toBe('diff');
  });

  // --- canAct computed ---

  it('should_returnTrue_when_statusIsPending', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.PENDING })));
    fixture.detectChanges();
    expect(component.canAct()).toBeTrue();
  });

  it('should_returnTrue_when_statusIsInProgress', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.IN_PROGRESS })));
    fixture.detectChanges();
    expect(component.canAct()).toBeTrue();
  });

  it('should_returnFalse_when_statusIsApproved', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.APPROVED })));
    fixture.detectChanges();
    expect(component.canAct()).toBeFalse();
  });

  it('should_returnFalse_when_statusIsRejected', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.REJECTED })));
    fixture.detectChanges();
    expect(component.canAct()).toBeFalse();
  });

  it('should_returnFalse_when_statusIsChangesRequested', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.CHANGES_REQUESTED })));
    fixture.detectChanges();
    expect(component.canAct()).toBeFalse();
  });

  // --- Computed counts ---

  it('should_computeUnresolvedCount_when_commentsExist', () => {
    const review = makeReview({
      comments: [
        makeComment({ id: 'c1', resolved: false }),
        makeComment({ id: 'c2', resolved: false }),
        makeComment({ id: 'c3', resolved: true }),
      ],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    expect(component.unresolvedCount()).toBe(2);
  });

  it('should_computeResolvedCount_when_commentsExist', () => {
    const review = makeReview({
      comments: [
        makeComment({ id: 'c1', resolved: false }),
        makeComment({ id: 'c2', resolved: true }),
        makeComment({ id: 'c3', resolved: true }),
      ],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    expect(component.resolvedCount()).toBe(2);
  });

  it('should_computeTotalComments_when_commentsExist', () => {
    const review = makeReview({
      comments: [makeComment({ id: 'c1' }), makeComment({ id: 'c2' }), makeComment({ id: 'c3' })],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    expect(component.totalComments()).toBe(3);
  });

  it('should_returnZeroCounts_when_noReview', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.unresolvedCount()).toBe(0);
    expect(component.resolvedCount()).toBe(0);
    expect(component.totalComments()).toBe(0);
  });

  // --- verdictClass ---

  it('should_returnSuccess_when_verdictIsPass', () => {
    expect(component.verdictClass(QAVerdict.PASS)).toBe('success');
  });

  it('should_returnWarning_when_verdictIsConditionalPass', () => {
    expect(component.verdictClass(QAVerdict.CONDITIONAL_PASS)).toBe('warning');
  });

  it('should_returnError_when_verdictIsFail', () => {
    expect(component.verdictClass(QAVerdict.FAIL)).toBe('error');
  });

  // --- severityClass ---

  it('should_returnError_when_severityIsCritical', () => {
    expect(component.severityClass('CRITICAL')).toBe('error');
  });

  it('should_returnWarning_when_severityIsMajor', () => {
    expect(component.severityClass('MAJOR')).toBe('warning');
  });

  it('should_returnPrimary_when_severityIsMinor', () => {
    expect(component.severityClass('MINOR')).toBe('primary');
  });

  it('should_returnNeutral_when_severityIsInfo', () => {
    expect(component.severityClass('INFO')).toBe('neutral');
  });

  // --- statusClass ---

  it('should_returnCorrectStatusClass_when_statusIsApproved', () => {
    expect(component.statusClass(ReviewStatus.APPROVED)).toBe('success');
  });

  it('should_returnCorrectStatusClass_when_statusIsChangesRequested', () => {
    expect(component.statusClass(ReviewStatus.CHANGES_REQUESTED)).toBe('warning');
  });

  it('should_returnCorrectStatusClass_when_statusIsRejected', () => {
    expect(component.statusClass(ReviewStatus.REJECTED)).toBe('error');
  });

  it('should_returnCorrectStatusClass_when_statusIsInProgress', () => {
    expect(component.statusClass(ReviewStatus.IN_PROGRESS)).toBe('primary');
  });

  it('should_returnCorrectStatusClass_when_statusIsPending', () => {
    expect(component.statusClass(ReviewStatus.PENDING)).toBe('neutral');
  });

  // --- coveragePercent ---

  it('should_returnFormattedPercent_when_valueProvided', () => {
    expect(component.coveragePercent(0.856)).toBe('85.6%');
  });

  it('should_returnDash_when_valueUndefined', () => {
    expect(component.coveragePercent(undefined)).toBe('-');
  });

  it('should_returnDash_when_valueNull', () => {
    expect(component.coveragePercent(null as any)).toBe('-');
  });

  it('should_returnZeroPercent_when_valueIsZero', () => {
    expect(component.coveragePercent(0)).toBe('0.0%');
  });

  it('should_return100Percent_when_valueIsOne', () => {
    expect(component.coveragePercent(1)).toBe('100.0%');
  });

  // --- DOM rendering ---

  it('should_renderDiffViewer_when_diffTabActive', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    fixture.detectChanges();
    component.setTab('diff');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const diffViewer = el.querySelector('sq-diff-viewer');
    expect(diffViewer).toBeTruthy();
  });

  it('should_render3Tabs_when_reviewLoaded', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const tabs = el.querySelectorAll('.review-tab');
    expect(tabs.length).toBe(3); // diff, comments, qa
  });

  it('should_renderComments_when_commentsTabActive', () => {
    const review = makeReview({
      comments: [makeComment({ id: 'c1' }), makeComment({ id: 'c2' })],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const comments = el.querySelectorAll('.comment');
    expect(comments.length).toBe(2);
  });

  it('should_renderNoCommentsMessage_when_commentsTabEmptyAndActive', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const noComments = el.querySelector('.no-comments');
    expect(noComments).toBeTruthy();
  });

  it('should_renderActionButtons_when_canAct', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.PENDING })));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = el.querySelectorAll('.action-buttons .sq-btn');
    // approve + request changes + reject + re-review = 4
    expect(buttons.length).toBe(4);
  });

  it('should_renderOnlyReReviewButton_when_cannotAct', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.APPROVED })));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = el.querySelectorAll('.action-buttons .sq-btn');
    // Only re-review button
    expect(buttons.length).toBe(1);
  });

  it('should_renderReviewGate_when_gateLoaded', () => {
    const gate: ReviewSummary = {
      taskId: 'task1', totalReviews: 2, humanApprovals: 1,
      aiApproval: true, policyMet: true, reviews: [],
    };
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.checkReviewGate.and.returnValue(of(gate));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const gateEl = el.querySelector('.review-gate');
    expect(gateEl).toBeTruthy();
  });

  it('should_renderOrchestrationResult_when_orchestrationCompleted', () => {
    const review = makeReview();
    const orchResult: ReviewOrchestrationResult = {
      taskId: 'task1', createdReviewIds: ['r10'],
      aiReviewCreated: true, pendingHumanReviews: 1, policyMet: false,
    };
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();

    reviewServiceSpy.orchestrateReview.and.returnValue(of(orchResult));
    reviewServiceSpy.checkReviewGate.and.returnValue(throwError(() => new Error('n/a')));
    component.triggerReReview();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const alert = el.querySelector('.sq-alert--info');
    expect(alert).toBeTruthy();
  });

  it('should_renderErrorAlert_when_errorExists', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('bad request')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const alert = el.querySelector('.sq-alert--error');
    expect(alert).toBeTruthy();
  });

  it('should_renderLoadingMessage_when_loading', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    // Don't call detectChanges, loading is still true
    expect(component.loading()).toBeTrue();
  });

  it('should_renderQASection_when_qaTabActive', () => {
    const qaReport: QAReport = {
      id: 'qa1', tenantId: 't1', taskId: 'task1', verdict: QAVerdict.PASS,
      summary: 'All good', lineCoverage: 0.85, branchCoverage: 0.72,
      testsPassed: 42, testsFailed: 0, testsSkipped: 2, createdAt: new Date().toISOString(),
    };
    reviewServiceSpy.getReview.and.returnValue(of(makeReview()));
    reviewServiceSpy.getLatestQAReport.and.returnValue(of(qaReport));
    reviewServiceSpy.checkQAGate.and.returnValue(of(true));
    fixture.detectChanges();
    component.setTab('qa');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const qaSection = el.querySelector('.qa-section');
    expect(qaSection).toBeTruthy();
  });

  it('should_renderRequestChangesForm_when_commentsTabAndCanAct', () => {
    reviewServiceSpy.getReview.and.returnValue(of(makeReview({ status: ReviewStatus.PENDING })));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const form = el.querySelector('.request-changes-form');
    expect(form).toBeTruthy();
  });

  it('should_renderResolveButtons_when_unresolvedComments', () => {
    const review = makeReview({
      comments: [makeComment({ id: 'c1', resolved: false })],
      status: ReviewStatus.PENDING,
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const resolveBtn = el.querySelector('.sq-btn--ghost');
    expect(resolveBtn).toBeTruthy();
  });

  it('should_renderResolvedBadge_when_commentIsResolved', () => {
    const review = makeReview({
      comments: [makeComment({ id: 'c1', resolved: true })],
    });
    reviewServiceSpy.getReview.and.returnValue(of(review));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const resolvedBadge = el.querySelector('.sq-badge--success');
    expect(resolvedBadge).toBeTruthy();
  });
});
