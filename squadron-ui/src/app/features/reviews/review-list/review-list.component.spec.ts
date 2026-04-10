import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewListComponent } from './review-list.component';
import { ReviewService } from '../../../core/services/review.service';
import { AuthService } from '../../../core/auth/auth.service';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Review, ReviewStatus, ReviewerType, ReviewSeverity, ReviewCategory } from '../../../core/models/review.model';
import { TranslateModule } from '@ngx-translate/core';
import { signal } from '@angular/core';

function makeReview(overrides: Partial<Review> = {}): Review {
  return {
    id: 'r1',
    tenantId: 't1',
    taskId: 'task1',
    taskTitle: 'Implement RBAC',
    pullRequestUrl: 'https://github.com/org/repo/pull/42',
    pullRequestNumber: 42,
    repositoryName: 'org/repo',
    status: ReviewStatus.PENDING,
    severity: ReviewSeverity.MAJOR,
    comments: [],
    filesChanged: 5,
    linesAdded: 100,
    linesRemoved: 20,
    reviewerType: ReviewerType.AI,
    createdAt: '2026-04-01T10:00:00Z',
    updatedAt: '2026-04-02T10:00:00Z',
    ...overrides,
  };
}

function makeComment(overrides: Partial<any> = {}) {
  return {
    id: 'c1', filePath: 'a.ts', lineNumber: 1, body: 'Fix this',
    severity: ReviewSeverity.MAJOR, category: ReviewCategory.BUG,
    resolved: false, authorName: 'AI', authorType: ReviewerType.AI,
    createdAt: '2026-04-01T10:00:00Z',
    ...overrides,
  };
}

describe('ReviewListComponent', () => {
  let component: ReviewListComponent;
  let fixture: ComponentFixture<ReviewListComponent>;
  let reviewServiceSpy: jasmine.SpyObj<ReviewService>;
  let router: Router;

  const mockAuthUser = { id: 'u1', tenantId: 'tenant1', username: 'dev', email: 'dev@test.com', displayName: 'Dev', tenantName: 'Test', roles: ['developer'], permissions: [] };

  beforeEach(async () => {
    reviewServiceSpy = jasmine.createSpyObj('ReviewService', ['getReviews']);
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [], totalElements: 0, totalPages: 0, page: 0, size: 20,
    }));

    const authServiceMock = { user: signal(mockAuthUser), isAuthenticated: signal(true), isAdmin: signal(false) };

    await TestBed.configureTestingModule({
      imports: [ReviewListComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ReviewService, useValue: reviewServiceSpy },
        { provide: AuthService, useValue: authServiceMock },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(ReviewListComponent);
    component = fixture.componentInstance;
  });

  // --- Creation ---

  it('should_create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // --- Loading / Error ---

  it('should_setLoadingFalse_when_reviewsLoaded', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  it('should_showEmptyState_when_apiReturnsError', () => {
    reviewServiceSpy.getReviews.and.returnValue(throwError(() => new Error('api down')));
    fixture.detectChanges();
    expect(component.reviews().length).toBe(0);
    expect(component.error()).toBe('api down');
    expect(component.loading()).toBeFalse();
  });

  it('should_setErrorMessage_when_apiErrorHasNoMessage', () => {
    reviewServiceSpy.getReviews.and.returnValue(throwError(() => ({})));
    fixture.detectChanges();
    expect(component.error()).toBe('Failed to load reviews');
  });

  it('should_loadReviews_when_apiReturnsData', () => {
    const reviews = [makeReview(), makeReview({ id: 'r2', status: ReviewStatus.APPROVED })];
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: reviews, totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.reviews().length).toBe(2);
  });

  // --- Summary counts ---

  it('should_computeTotalReviews_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview(), makeReview({ id: 'r2' }), makeReview({ id: 'r3' })],
      totalElements: 3, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.totalReviews()).toBe(3);
  });

  it('should_computePendingCount_when_reviewsHaveMixedStatuses', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', status: ReviewStatus.PENDING }),
        makeReview({ id: 'r2', status: ReviewStatus.APPROVED }),
        makeReview({ id: 'r3', status: ReviewStatus.PENDING }),
      ],
      totalElements: 3, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.pendingCount()).toBe(2);
  });

  it('should_computeInProgressCount_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview({ id: 'r1', status: ReviewStatus.IN_PROGRESS })],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.inProgressCount()).toBe(1);
  });

  it('should_computeApprovedCount_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', status: ReviewStatus.APPROVED }),
        makeReview({ id: 'r2', status: ReviewStatus.APPROVED }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.approvedCount()).toBe(2);
  });

  it('should_computeChangesRequestedCount_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview({ id: 'r1', status: ReviewStatus.CHANGES_REQUESTED })],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.changesRequestedCount()).toBe(1);
  });

  it('should_computeRejectedCount_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview({ id: 'r1', status: ReviewStatus.REJECTED })],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.rejectedCount()).toBe(1);
  });

  // --- totalLinesChanged ---

  it('should_computeTotalLinesChanged_when_reviewsLoaded', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', linesAdded: 50, linesRemoved: 10 }),
        makeReview({ id: 'r2', linesAdded: 30, linesRemoved: 5 }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.totalLinesChanged()).toBe(95);
  });

  // --- totalUnresolved ---

  it('should_computeTotalUnresolved_when_reviewsHaveComments', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', comments: [makeComment({ resolved: false }), makeComment({ id: 'c2', resolved: true })] }),
        makeReview({ id: 'r2', comments: [makeComment({ id: 'c3', resolved: false })] }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.totalUnresolved()).toBe(2);
  });

  it('should_returnZeroUnresolved_when_noComments', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview()],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.totalUnresolved()).toBe(0);
  });

  // --- filteredReviews: search ---

  it('should_filterByTaskTitle_when_searchQuerySet', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', taskTitle: 'Implement RBAC' }),
        makeReview({ id: 'r2', taskTitle: 'Fix Login Bug' }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.searchQuery.set('rbac');
    expect(component.filteredReviews().length).toBe(1);
    expect(component.filteredReviews()[0].id).toBe('r1');
  });

  it('should_filterByRepositoryName_when_searchQueryMatchesRepo', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', repositoryName: 'org/frontend' }),
        makeReview({ id: 'r2', repositoryName: 'org/backend' }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.searchQuery.set('frontend');
    expect(component.filteredReviews().length).toBe(1);
    expect(component.filteredReviews()[0].id).toBe('r1');
  });

  it('should_filterByPRNumber_when_searchQueryMatchesNumber', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', pullRequestNumber: 42 }),
        makeReview({ id: 'r2', pullRequestNumber: 99 }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.searchQuery.set('99');
    expect(component.filteredReviews().length).toBe(1);
    expect(component.filteredReviews()[0].id).toBe('r2');
  });

  it('should_showAllReviews_when_searchQueryIsEmpty', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview({ id: 'r1' }), makeReview({ id: 'r2' })],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.searchQuery.set('');
    expect(component.filteredReviews().length).toBe(2);
  });

  // --- filteredReviews: reviewer type filter ---

  it('should_filterByReviewerType_when_filterSet', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', reviewerType: ReviewerType.AI }),
        makeReview({ id: 'r2', reviewerType: ReviewerType.HUMAN }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.filterReviewerType.set(ReviewerType.HUMAN);
    expect(component.filteredReviews().length).toBe(1);
    expect(component.filteredReviews()[0].reviewerType).toBe(ReviewerType.HUMAN);
  });

  it('should_showAllReviewerTypes_when_filterIsEmpty', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', reviewerType: ReviewerType.AI }),
        makeReview({ id: 'r2', reviewerType: ReviewerType.HUMAN }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.filterReviewerType.set('');
    expect(component.filteredReviews().length).toBe(2);
  });

  // --- Sorting ---

  it('should_sortByUpdatedAtDesc_when_defaultSort', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', updatedAt: '2026-04-01T10:00:00Z' }),
        makeReview({ id: 'r2', updatedAt: '2026-04-03T10:00:00Z' }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.sortField()).toBe('updatedAt');
    expect(component.sortDirection()).toBe('desc');
    expect(component.filteredReviews()[0].id).toBe('r2');
  });

  it('should_toggleSortDirection_when_sameFieldClicked', () => {
    fixture.detectChanges();
    expect(component.sortDirection()).toBe('desc');
    component.toggleSort('updatedAt');
    expect(component.sortDirection()).toBe('asc');
    component.toggleSort('updatedAt');
    expect(component.sortDirection()).toBe('desc');
  });

  it('should_changeSortField_when_differentFieldClicked', () => {
    fixture.detectChanges();
    component.toggleSort('status');
    expect(component.sortField()).toBe('status');
    expect(component.sortDirection()).toBe('desc');
  });

  it('should_sortByLinesAdded_when_linesAddedSortSelected', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', linesAdded: 10, linesRemoved: 5 }),
        makeReview({ id: 'r2', linesAdded: 200, linesRemoved: 50 }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.toggleSort('linesAdded');
    expect(component.filteredReviews()[0].id).toBe('r2'); // desc by default
  });

  it('should_sortByStatusAlphabetically_when_statusSortSelected', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', status: ReviewStatus.PENDING }),
        makeReview({ id: 'r2', status: ReviewStatus.APPROVED }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.toggleSort('status');
    // desc: PENDING > APPROVED alphabetically
    expect(component.filteredReviews()[0].id).toBe('r1');
  });

  it('should_sortByCreatedAt_when_createdAtSortSelected', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [
        makeReview({ id: 'r1', createdAt: '2026-04-01T10:00:00Z' }),
        makeReview({ id: 'r2', createdAt: '2026-04-05T10:00:00Z' }),
      ],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    component.toggleSort('createdAt');
    expect(component.filteredReviews()[0].id).toBe('r2'); // desc
  });

  // --- statusClass ---

  it('should_returnSuccess_when_statusIsApproved', () => {
    expect(component.statusClass(ReviewStatus.APPROVED)).toBe('success');
  });

  it('should_returnWarning_when_statusIsChangesRequested', () => {
    expect(component.statusClass(ReviewStatus.CHANGES_REQUESTED)).toBe('warning');
  });

  it('should_returnError_when_statusIsRejected', () => {
    expect(component.statusClass(ReviewStatus.REJECTED)).toBe('error');
  });

  it('should_returnPrimary_when_statusIsInProgress', () => {
    expect(component.statusClass(ReviewStatus.IN_PROGRESS)).toBe('primary');
  });

  it('should_returnNeutral_when_statusIsPending', () => {
    expect(component.statusClass(ReviewStatus.PENDING)).toBe('neutral');
  });

  // --- statusColor ---

  it('should_returnCorrectColor_when_statusIsPending', () => {
    expect(component.statusColor(ReviewStatus.PENDING)).toBe('#94A3B8');
  });

  it('should_returnCorrectColor_when_statusIsInProgress', () => {
    expect(component.statusColor(ReviewStatus.IN_PROGRESS)).toBe('#06B6D4');
  });

  it('should_returnCorrectColor_when_statusIsApproved', () => {
    expect(component.statusColor(ReviewStatus.APPROVED)).toBe('#10B981');
  });

  it('should_returnCorrectColor_when_statusIsChangesRequested', () => {
    expect(component.statusColor(ReviewStatus.CHANGES_REQUESTED)).toBe('#F59E0B');
  });

  it('should_returnCorrectColor_when_statusIsRejected', () => {
    expect(component.statusColor(ReviewStatus.REJECTED)).toBe('#EF4444');
  });

  // --- unresolvedCount ---

  it('should_returnUnresolvedCount_when_reviewHasComments', () => {
    const review = makeReview({
      comments: [
        makeComment({ id: 'c1', resolved: false }),
        makeComment({ id: 'c2', resolved: true }),
        makeComment({ id: 'c3', resolved: false }),
      ],
    });
    expect(component.unresolvedCount(review)).toBe(2);
  });

  it('should_returnZero_when_reviewHasNoComments', () => {
    const review = makeReview({ comments: [] });
    expect(component.unresolvedCount(review)).toBe(0);
  });

  // --- onFilterStatusChange ---

  it('should_reloadReviews_when_filterStatusChanges', () => {
    fixture.detectChanges();
    reviewServiceSpy.getReviews.calls.reset();
    component.onFilterStatusChange('APPROVED');
    expect(component.filterStatus()).toBe('APPROVED');
    expect(reviewServiceSpy.getReviews).toHaveBeenCalledWith(ReviewStatus.APPROVED);
  });

  it('should_passUndefined_when_filterStatusIsEmpty', () => {
    fixture.detectChanges();
    reviewServiceSpy.getReviews.calls.reset();
    component.onFilterStatusChange('');
    expect(reviewServiceSpy.getReviews).toHaveBeenCalledWith(undefined);
  });

  // --- openReview ---

  it('should_navigateToReviewDetail_when_openReviewCalled', () => {
    const review = makeReview({ id: 'r42' });
    component.openReview(review);
    expect(router.navigate).toHaveBeenCalledWith(['/reviews', 'r42']);
  });

  // --- refresh ---

  it('should_reloadReviews_when_refreshCalled', () => {
    fixture.detectChanges();
    reviewServiceSpy.getReviews.calls.reset();
    component.refresh();
    expect(reviewServiceSpy.getReviews).toHaveBeenCalled();
  });

  // --- allStatuses / allReviewerTypes ---

  it('should_exposeAllStatuses_when_accessed', () => {
    expect(component.allStatuses).toEqual(Object.values(ReviewStatus));
  });

  it('should_exposeAllReviewerTypes_when_accessed', () => {
    expect(component.allReviewerTypes).toEqual(Object.values(ReviewerType));
  });

  // --- DOM rendering ---

  it('should_renderEmptyRow_when_noReviews', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1); // @empty row
  });

  it('should_renderReviewRows_when_reviewsExist', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [makeReview({ id: 'r1' }), makeReview({ id: 'r2' })],
      totalElements: 2, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('tbody tr.clickable-row');
    expect(rows.length).toBe(2);
  });

  it('should_renderStatsBar_when_componentInitialized', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const stats = el.querySelectorAll('.stat-item');
    expect(stats.length).toBeGreaterThanOrEqual(5);
  });

  it('should_renderFilterInputs_when_componentInitialized', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const searchInput = el.querySelector('.sq-input');
    const selects = el.querySelectorAll('.sq-select');
    expect(searchInput).toBeTruthy();
    expect(selects.length).toBe(2); // status filter + reviewer type filter
  });

  it('should_renderSortableHeaders_when_componentInitialized', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const sortable = el.querySelectorAll('.sortable');
    expect(sortable.length).toBe(3); // status, linesAdded, updatedAt
  });

  it('should_showErrorAlert_when_errorExists', () => {
    reviewServiceSpy.getReviews.and.returnValue(throwError(() => new Error('server error')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const alert = el.querySelector('.sq-alert--error');
    expect(alert).toBeTruthy();
  });
});
